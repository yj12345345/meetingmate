# MeetingMate Backend

MeetingMate Backend는 조건 기반 AI 장소/코스 추천을 제공하는 Spring Boot API 서버입니다.
현재 코드 기준으로 인증(JWT access token + refresh token, OAuth2), 사용자 관리, OpenAI 기반 추천과 장애 대응이 구현되어 있습니다.

## 1. 핵심 기능

- 로컬 회원가입/로그인 (`/api/auth/signup`, `/api/auth/login`)
- OAuth2 로그인 (Kakao, Google)
- access token 15분 + refresh token 14일 (회전, 재사용 탐지, 로그아웃/탈퇴 시 폐기)
- 사용자 정보 조회, 닉네임 수정, 회원 탈퇴
- AI 추천
  - 키워드/조건 기반 카테고리별 장소와 코스: `GET /api/recommend`
  - 선택 장소 기반 코스 재추천: `POST /api/recommend/selected-routes`
  - 기본 추천: `POST /api/recommendations` (현재 프론트에서는 호출하지 않음)
- AI가 실패하면 기본 코스(FALLBACK)로 응답

## 2. 기술 스택

- Java 21, Spring Boot 4.0.0
- Spring Security, OAuth2 Client, JWT (jjwt 0.11.5)
- Spring Data JPA, MySQL (local/prod), H2 (test)
- WebClient (Reactor Netty)로 OpenAI 호출, Resilience4j 2.3.0 (core 모듈 직접 구성)
- Spring Boot Actuator + Micrometer Prometheus
- springdoc-openapi (Swagger UI)
- JUnit 5, Mockito

## 3. 인증/인가 흐름

### Local Login

1. `POST /api/auth/signup`으로 계정 생성
2. `POST /api/auth/login`으로 로그인
3. 서버 응답
   - 본문: access token (JWT, 기본 15분)
   - `Set-Cookie`: `REFRESH_TOKEN` (`HttpOnly; Secure; SameSite=Strict; Path=/api/auth`, 14일)
4. 보호 API 호출 시 `Authorization: Bearer <access token>` 헤더 사용

### OAuth2 Login

1. `/oauth2/authorization/kakao` 또는 `/oauth2/authorization/google`로 이동
2. OAuth2 인증 완료
3. `OAuth2SuccessHandler`가 refresh token 쿠키를 심고 `app.frontend-redirect-url`로 리다이렉트
   - URL에는 토큰을 붙이지 않습니다.
4. 프론트 콜백 페이지가 `POST /api/auth/refresh`를 호출해 access token을 받음

### 토큰 갱신과 로그아웃

- **`POST /api/auth/refresh`:** 쿠키의 refresh token을 회전(새 토큰 발급, 이전 토큰 폐기)하고 새 access token을 돌려줍니다.
  - 이미 회전된 토큰이 다시 오면 재사용으로 보고, 같은 로그인의 토큰 전체를 폐기한 뒤 `401 REFRESH_TOKEN_INVALID`로 응답합니다.
  - 단, 회전 후 10초 안의 동시 요청은 허용합니다.
- **`POST /api/auth/logout`:** 해당 로그인의 refresh token을 폐기하고 쿠키를 지웁니다. `204`로 응답합니다.
- **회원 탈퇴:** 그 사용자의 모든 refresh token을 폐기합니다.
- DB에는 refresh token의 SHA-256 해시만 저장합니다(`refresh_token` 테이블).

### 요청 인증 (stateless)

`JwtAuthenticationFilter`는 서명과 만료만 검증하고, DB를 조회하지 않습니다. 그래서 MySQL 장애 중에도 AI 추천은 동작합니다.
탈퇴한 사용자의 남은 토큰으로 사용자 API를 부르면 `401`을 받습니다.

## 4. 주요 API

모든 응답은 아래 래퍼 형식을 사용합니다(`logout`의 `204` 제외).

```json
{
  "success": true,
  "code": "SOME_CODE",
  "message": "설명",
  "data": {}
}
```

### Auth / User

- `POST /api/auth/signup`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/user/me`
- `PUT /api/user/profile`
- `DELETE /api/user`

### Recommendation

- `GET /api/recommend` (keyword + meetingType/mood/location/planHint)
- `POST /api/recommend/selected-routes` (선택 장소 기반 코스 재추천)
- `POST /api/recommendations` (meetingType/category/preferences 기반 기본 추천)

`/api/recommend` 계열 응답에는 `source`(`AI` / `FALLBACK`)와 `warning`이 들어갈 수 있습니다. `warning`은 사용자용 안내 문구이고, 내부 오류 원인은 서버 로그에만 남습니다.

### 에러 응답

| 상황 | 상태 코드 |
|---|---|
| 없는 경로 / 잘못된 메서드 | 404 / 405 |
| 잘못된 JSON, 필수 파라미터 누락, 검증 실패 | 400 |
| 지원하지 않는 Content-Type | 415 |
| `/api/**` 인증 없음, 만료되거나 잘못된 토큰 | 401 (JSON, 리다이렉트하지 않음) |
| DB에 연결할 수 없음 | 503 + `Retry-After: 30` |
| 그 밖의 예상하지 못한 오류 | 500 (요청 ID와 함께 ERROR 로그) |

## 5. OpenAI 호출 장애 대응

`OpenAiClientImpl` + `OpenAiResilienceConfig`, 순서는 `CircuitBreaker( Retry( Bulkhead( call ) ) )`입니다.

| 장치 | 기본값 | 설정 키 |
|---|---|---|
| 연결 / 전체 호출 타임아웃 | 2 s / 20 s | `openai.connect-timeout`, `openai.call-timeout` |
| 벌크헤드 (동시 호출 수, 대기 없이 거절) | 100 | `openai.max-concurrent-calls` |
| 커넥션 풀 | 벌크헤드와 같은 값 | — |
| 서킷 브레이커 | 최근 20건 중 50% 실패 → 30 s OPEN → 시험 호출 3건 | 코드 상수 |
| 재시도 | 1회, 429/502/503/504와 연결 오류만 (타임아웃은 재시도 안 함) | 코드 상수 |

설계 근거와 장애 주입 결과는 `ops-report/README.md`에 있습니다.

## 6. 관측

- Actuator는 별도 관리 포트(`MANAGEMENT_PORT`, 기본 8081)에서 `MANAGEMENT_ADDRESS`(기본 `127.0.0.1`)에만 열립니다. `health`와 `prometheus`만 노출합니다.
- `/actuator/health/readiness`에는 DB 상태가 포함되고, liveness에는 포함되지 않습니다.
- 요청마다 `X-Request-Id`를 받거나 새로 만들어 응답 헤더와 로그(`[requestId]`)에 남기고, 요청당 접근 로그 한 줄을 씁니다.

## 7. 설정 파일

- `src/main/resources/application.yaml`: 공통 설정 (JWT, refresh token, OpenAI, Hikari, actuator)
- `src/main/resources/application-local.yaml`: 로컬 개발. 비밀값은 `.env.local.properties`에서 읽습니다(git 무시 대상, `.env.local.properties.example` 참고).
- `src/main/resources/application-prod.yaml`: 운영
- `src/test/resources/application-test.yaml`: 테스트 (H2, MySQL 모드)

## 8. 실행 방법

### 로컬 실행

`localhost:3306`에 MySQL과 `meetingmate` DB가 필요합니다.

```bash
cd backend
./gradlew bootRun --args='--spring.profiles.active=local'
```

### 테스트 실행

```bash
cd backend
./gradlew test
```

### 로컬 관측 스택 (운영과 같은 `prod` 프로필)

```bash
./gradlew bootJar -x test
cd .. && docker compose -f docker-compose.observability.yml --env-file .env.observability up -d
```

### Swagger

- [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

## 9. 환경 변수

| 구분 | 변수 |
|---|---|
| JWT / 토큰 | `JWT_SECRET`, `JWT_ACCESS_TOKEN_EXPIRY_MS` (기본 900000 = 15분), `REFRESH_TOKEN_TTL` (기본 `14d`), `REFRESH_COOKIE_SECURE` (기본 `true`, http 로컬 스택에서만 `false`) |
| OAuth | `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `APP_FRONTEND_REDIRECT_URL` |
| OpenAI | `MEETINGMATE_OPENAI_API_KEY`, `OPENAI_MODEL`, `OPENAI_BASE_URL` (기본 `https://api.openai.com/v1`), `OPENAI_CONNECT_TIMEOUT`, `OPENAI_CALL_TIMEOUT` |
| DB (prod) | `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `DB_CONNECTION_TIMEOUT_MS` (기본 3000) |
| DB (local) | `LOCAL_DB_USERNAME`, `LOCAL_DB_PASSWORD` (`.env.local.properties`) |
| 기타 | `SERVER_PORT`, `MANAGEMENT_PORT`, `MANAGEMENT_ADDRESS` |

> `application.yaml`의 `JWT_SECRET`과 `application-prod.yaml`의 DB 비밀번호에는 기본값이 들어 있습니다. 운영에서는 반드시 `app.env`로 값을 덮어써야 합니다.

## 10. 테스트 코드 (현재 포함)

| 영역 | 테스트 |
|---|---|
| 추천 | `RecommendControllerTest`, `RecommendationControllerTest`, `RecommendationServiceTest` |
| 에러 응답 | `ErrorResponseIntegrationTest`, `DatabaseUnavailableIntegrationTest` |
| OpenAI 장애 대응 | `OpenAiResilienceConfigTest` |
| 인증 | `RefreshTokenServiceTest`, `RefreshTokenFlowIntegrationTest`, `OAuth2SuccessHandlerTest`, `UserServiceDeletedUserTest` |
| 기타 | `RequestIdFilterTest`, `MeetingmateApplicationTests` |

- `RecommendationServiceTest`는 클래스 이름과 달리 `RecommendationService.java` 파일에 있습니다.
- `RecommendationServiceRealTest`는 실제 OpenAI/DB 자격 정보가 필요한 수동 통합 테스트라 기본으로 비활성화돼 있습니다.

## 11. 운영 분석

부하 테스트와 장애 주입으로 찾은 문제, 개선 내용, 계산 근거는 [`ops-report/README.md`](ops-report/README.md)에 있습니다.
