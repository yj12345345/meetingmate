# MeetingMate Backend

MeetingMate Backend는 모임 일정/장소 의사결정을 지원하는 Spring Boot API 서버입니다.
현재 코드 기준으로 인증(JWT + OAuth2), 모임/시간/장소 투표, OpenAI 기반 추천 기능이 구현되어 있습니다.

## 1. 핵심 기능

- 로컬 회원가입/로그인 (`/api/auth/signup`, `/api/auth/login`)
- OAuth2 로그인 (Kakao, Google) + 로그인 성공 시 JWT 발급
- 사용자 정보 조회/닉네임 수정/회원 탈퇴
- 모임 생성, 초대코드 참여, 내 모임 목록 조회, 모임 상세 조회
- 모임 멤버 가능 시간 등록/조회 (30분 단위 검증 포함)
- 장소 후보 등록/조회, 투표, 모임장 장소 확정
- AI 추천
  - 기본 추천: `/api/recommendations`
  - 키워드 기반 카테고리/코스 추천: `/api/recommend`
  - 선택 장소 기반 코스 재추천: `/api/recommend/selected-routes`
- AI 실패 시 DB 후보/기본 fallback 추천 제공

## 2. 기술 스택

- Java 21
- Spring Boot 4.0.0
- Spring Security, OAuth2 Client, JWT
- Spring Data JPA
- MySQL (local/prod), H2 (test)
- WebClient (OpenAI 호출)
- springdoc-openapi (Swagger UI)
- JUnit5, Mockito

## 3. 인증/인가 흐름

### Local Login

1. 클라이언트가 `/api/auth/signup`으로 계정 생성
2. `/api/auth/login`으로 로그인
3. 서버가 JWT access token 발급
4. 이후 보호 API 호출 시 `Authorization: Bearer <token>` 헤더 사용

### OAuth2 Login

1. `/oauth2/authorization/kakao` 또는 `/oauth2/authorization/google` 호출
2. OAuth2 인증 완료
3. `OAuth2SuccessHandler`에서 JWT 발급
4. `app.frontend-redirect-url`로 `?token=` 쿼리를 붙여 리다이렉트

## 4. 주요 API

모든 응답은 아래 래퍼 형식을 사용합니다.

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
- `GET /api/user/me`
- `PUT /api/user/profile`
- `DELETE /api/user`

### Group / Availability / Place

- `POST /api/groups`
- `POST /api/groups/join`
- `GET /api/groups/me`
- `GET /api/groups/{groupId}`
- `POST /api/groups/{groupId}/availability`
- `GET /api/groups/{groupId}/availability`
- `POST /api/groups/{groupId}/places`
- `GET /api/groups/{groupId}/places`
- `POST /api/groups/{groupId}/places/{placeId}/vote`
- `POST /api/groups/{groupId}/places/{placeId}/confirm`

### Recommendation

- `POST /api/recommendations` (meetingType/category/preferences 기반)
- `GET /api/recommend` (keyword + meetingType/mood/location/planHint)
- `POST /api/recommend/selected-routes` (선택 장소 기반 코스 재추천)

`/api/recommend` 계열 응답에는 `source`(`AI`/`DB`/`FALLBACK`)와 `warning`이 포함될 수 있습니다.

## 5. 설정 파일

- `src/main/resources/application.yaml`: 공통 설정
- `src/main/resources/application-local.yaml`: 로컬 개발
- `src/main/resources/application-prod.yaml`: 운영
- `src/test/resources/application-test.yaml`: 테스트

## 6. 실행 방법

### 로컬 실행

```bash
cd meetingmate
./gradlew bootRun --args='--spring.profiles.active=local'
```

### 테스트 실행

```bash
cd meetingmate
./gradlew test
```

### Swagger

- [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

## 7. 환경 변수(주요)

- `JWT_SECRET`
- `JWT_ACCESS_TOKEN_EXPIRY_MS`
- `APP_FRONTEND_REDIRECT_URL`
- `MEETINGMATE_OPENAI_API_KEY`
- `OPENAI_MODEL`
- `SERVER_PORT`
- `GOOGLE_CLIENT_ID`
- `GOOGLE_CLIENT_SECRET`

## 8. Docker (선택)

루트의 `docker-compose.yml` 기준:

```bash
docker compose up --build
```

구성:
- MySQL
- Backend(Spring Boot)
- Frontend(React 빌드 결과)
- Nginx 리버스 프록시

## 9. 테스트 코드(현재 포함)

- `RecommendationControllerTest`
- `RecommendControllerTest`
- `RecommendationServiceTest`
- `GroupServiceTest`
- `PlaceServiceTest`

`RecommendationServiceRealTest`는 실제 OpenAI/DB 자격 정보가 필요한 수동 통합 테스트로 기본 비활성화되어 있습니다.
