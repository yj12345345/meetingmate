# MeetingMate 운영 분석 보고서 (2026-11-09 ~ 2026-11-10)

> 실제 운영에서 생길 수 있는 문제를 **로컬에서 재현 → 측정 → 개선 → 재측정**한 기록입니다.
> 모든 수치는 로컬 docker-compose 환경(운영과 같은 `prod` 프로필, 백엔드 CPU 2 / 메모리 768MB, MySQL 512MB)에서 직접 측정했습니다.
> 실험별 원본 기록은 [`docs/ops-log/`](../../docs/ops-log/)에, 원시 측정값은 [`load-test/results/`](../../load-test/results/)에 있습니다.

## 한눈에 보기

| # | 문제 | 개선 전 | 개선 후 | 핵심 수정 |
|---|---|---|---|---|
| 1 | AI 대기 중 DB 커넥션 점유 (OSIV) | AI 외 API p95 **1.11 s** | **121 ms** | `spring.jpa.open-in-view: false` |
| 2 | 존재하지 않는 경로가 500 | 에러 10종 중 **7종 오답** | **10/10 정답** | `ResponseEntityExceptionHandler` 상속, API 401 진입점 |
| 3 | OpenAI 무응답 시 서비스 전체 중단 | 로그인 실패 **100%**, 장애 제거 후 **3분 20초** 더 중단 | 실패 **0%**, 로그인 최대 **144 ms** | 타임아웃 + 벌크헤드 + 서킷 브레이커 + 재시도 |
| 4 | MySQL 중단 시 30초 매달림 + 핵심 기능까지 실패 | 500 / 약 30 s, 추천 실패 **77~99%** | 503 / 약 3 s, 추천 실패 **0%** | Hikari 3 s, 503 매핑, stateless JWT 인증 |
| 5 | access token 만료 시 작업이 끊김 | "인증이 필요합니다" + 처음부터 다시 | 보이지 않게 갱신 (refresh 1회) | refresh token (회전, 재사용 탐지) |
| 6 | 미사용 API 노출(IDOR) / 비밀값 커밋 | 공개 저장소에 카카오 시크릿, 남의 모임 데이터 조회 가능 | 제거 | gitignore, 미사용 API 삭제 |

---

## 0. 측정 환경과 방법

- **관측:** Spring Boot Actuator + Micrometer → Prometheus → Grafana
  - 관리 포트 8081을 `127.0.0.1`에만 바인딩해서, Nginx(8080 프록시)를 통해서는 노출되지 않게 했습니다.
  - 응답 시간은 히스토그램 버킷으로 수집해 p50/p95/p99를 계산합니다.
  - 요청마다 `X-Request-Id`를 MDC에 넣어 로그 한 줄 한 줄과 클라이언트 요청을 연결합니다.
- **부하:** k6
  - [`recommend-flow.js`](../../load-test/recommend-flow.js): 실제 화면 흐름 그대로 로그인 → 추천 → 결과 확인 → 선택 장소 코스 추천 → 마이페이지. 최대 60 VU.
  - [`openai-fault.js`](../../load-test/openai-fault.js): 장애 주입. 0~60초 정상 → 60~180초 장애 → 180~300초 회복. 요청이 앞 요청과 상관없이 일정한 비율로 계속 들어오는 **열린 부하 모델**입니다.
- **OpenAI 대체:** 비용 원칙에 따라 실제 API 대신 WireMock mock을 썼습니다. 응답 형식은 실제와 같고, 지연은 lognormal 분포(중앙값 3 s, σ 0.4)입니다.
- **장애 주입:**
  - OpenAI 장애는 WireMock 관리 API로 실행 중에 바꿉니다.
  - MySQL 장애는 [`db-fault.sh`](../../load-test/db-fault.sh)가 같은 타임라인에 맞춰 컨테이너를 멈추고 다시 띄웁니다.
- **변수 통제:** 한 번에 하나만 바꾸고 다시 쟀습니다. 실행할 때마다 설정이 실제로 반영됐는지(환경변수, 시작 로그, 커넥션 점유 시간) 확인했습니다.

---

## 1. DB 커넥션 풀 고갈 (OSIV × 외부 API 대기)

**현상:** 사용자 60명이 실제 흐름을 반복하자, PK 조회 하나뿐인 `GET /api/user/me`의 p95가 1.4 s, 로그인 p95가 991 ms까지 늘었습니다. CPU에는 여유가 있었습니다.

**원인:** Spring Boot 기본값 OSIV(`open-in-view=true`)에서는 요청 중 처음 얻은 DB 커넥션을 **응답이 끝날 때까지** 반납하지 않습니다. 측정 당시 추천 API는 DB를 조회한 뒤 OpenAI 응답(약 3.4 s)을 기다렸으므로, 그동안 커넥션을 쥐고 있었습니다.

> **현재 코드:** 이후 추천 경로의 DB 조회(`ad0008f`)와 JWT 필터의 사용자 조회(`2e7fdef`)가 모두 없어졌습니다. 그래서 지금 코드에서는 이 조건이 그대로 재현되지 않습니다. `open-in-view: false`는 외부 호출 전에 DB를 조회하는 코드가 다시 생길 때를 막는 장치로 유지합니다.

**계산 (Little's Law, L = λ × W):**

| | λ (DB를 쓰는 요청/s) | W (커넥션 점유 시간) | L (동시에 필요한 커넥션) | 풀 크기 |
|---|---|---|---|---|
| 개선 전 | 약 5.8 | 평균 **821 ms** (실측) | 약 4.8 (평균), 추천이 몰리면 10 초과 | 10 |
| 개선 후 | 약 5.8 | 평균 **2.2 ms** (실측) | 약 0.01 | 10 |

평균만 보면 10개 안에 들어가지만, 3초짜리 점유가 몰리는 순간 10개를 넘어 **최대 12개 요청이 커넥션을 기다렸습니다**(Hikari pending 12). 점유 시간을 약 370분의 1로 줄이자 대기 자체가 사라졌습니다.

**결과 (60 VU, OSIV만 바꿔서 같은 시나리오):**

| 지표 | OSIV on | OSIV off |
|---|---|---|
| AI 외 API p95 (k6) | 1.11 s | **121 ms** |
| `user/me` 서버 p95 | 1,432 ms | **25 ms** |
| 로그인 서버 p95 | 991 ms | **132 ms** |
| Hikari pending (max) | 12 | **0** |
| AI 추천 p50 / p95 | 3.40 / 6.67 s | 3.37 / 6.30 s (변화 없음, mock 지연에 묶임) |

**풀 크기를 늘리지 않은 이유:** 원인(외부 API를 기다리는 동안 커넥션을 쥐는 구조)이 그대로 남습니다. 게다가 운영 MySQL이 같은 소형 EC2에 있어서, 커넥션이 늘수록 메모리를 씁니다.

**부가 발견 (미사용 기능 또는 합성 데이터 기준, 실사용 효과는 작음):**
- **N+1:** 장소 목록 조회의 SQL 수가 2 + N개였습니다. `IN + GROUP BY`로 바꿔 **N=20에서 22개 → 3개**가 됐습니다. 이후 기능 자체를 삭제했습니다.
- **인덱스:** 사용자 20만 / 장소 후보 50만 건 합성 데이터에서 `EXPLAIN ANALYZE`로 확인했습니다. 풀 스캔(50만 행, 103 ms)이 인덱스 조회(20행, 0.5 ms)로 바뀌었습니다. 로그인 경로의 `user(email, provider)`는 47 ms → 1.0 ms입니다.
- **철회한 가설:** `LIKE '%키워드%'` 병목은 합성 데이터가 만든 인위적인 병목이라 철회했습니다. 검색 대상 테이블이 운영에서는 거의 비어 있습니다.

---

## 2. 전역 예외 처리: 404 → 500 문제

**원인:**
- `@ExceptionHandler(Exception.class)`가 Spring이 상태 코드를 정해 둔 예외(`NoResourceFoundException` 등)까지 500으로 바꾸고 있었고, **로그도 남기지 않았습니다.**
- 미인증 API 요청은 `oauth2Login()` 기본 동작 때문에 `/login`으로 **302 리다이렉트**됐습니다.

| 상황 | 기대 | 개선 전 | 개선 후 |
|---|---|---|---|
| 없는 경로 (API / 비API) | 404 | 500 / 500 | 404 / 404 |
| 잘못된 HTTP 메서드 | 405 | 500 | 405 |
| 깨진 JSON / 잘못된 Content-Type | 400 / 415 | 500 / 500 | 400 / 415 |
| 필수 파라미터 누락 | 400 | 500 | 400 |
| 토큰 없음 / 만료된 토큰 | 401 | 302 / 302 | 401 / 401 (JSON) |
| 검증 실패, 정상 요청 | 400, 200 | 400, 200 | 400, 200 |

**수정:**
- `ResponseEntityExceptionHandler`를 상속해 Spring의 표준 매핑을 그대로 쓰고, 응답 모양만 `ApiResponse`로 맞췄습니다. 나중에 새로 생기는 프레임워크 예외도 자동으로 올바른 코드가 됩니다.
- 예상하지 못한 예외는 요청 ID와 함께 ERROR 로그로 남깁니다.
- 사용자 응답에 그대로 나가던 내부 예외 메시지(`Did not observe any item within 20000ms`)는 일반 안내 문구로 바꾸고, 원인은 서버 로그에만 남깁니다.

---

## 3. OpenAI 장애 격리

**현상 (개선 전, AI 초당 4건, OpenAI가 300초 동안 무응답):**
- 장애 시작 75초 만에 Tomcat 스레드 **200개가 모두** `OpenAiClientImpl` → `Mono.block()`에서 기다리며 찼습니다(스레드 덤프로 확인).
- 로그인과 마이페이지 실패율이 **100%** 였고, **OpenAI가 회복된 뒤에도 약 3분 20초 동안** 서비스가 죽어 있었습니다.

**핵심 계산: 묶이는 스레드 수 = 도착률 λ × 대기 시간 W**

| 조건 | 예측 | 실측 (Tomcat 바쁜 스레드 max) |
|---|---|---|
| 타임아웃 없음 | W = ∞ → 200 고갈 | **200** |
| 타임아웃 20 s, λ = 4/s | 4 × 20 = **80** | **80** |
| 타임아웃 20 s, λ = 12/s | 12 × 20 = **240 > 200** → 고갈 | **200** (로그인 최대 7.41 s 대기) |
| + 벌크헤드 100 | min(240, 100) = **100** | **101** |

타임아웃은 W(얼마나 오래)를, 벌크헤드는 동시 개수(몇 개나)를 제한합니다. 어느 한쪽만으로는 도착률이 오르면 다시 뚫립니다.

**설정값과 근거:**

| 장치 | 값 | 근거 |
|---|---|---|
| 연결 타임아웃 | 2 s | 연결조차 안 되면 오래 기다릴 이유가 없음 |
| 호출 타임아웃 | 20 s | mock p99 약 8 s에 여유를 둠. **운영에서 실측한 뒤 조정해야 할 값** |
| 벌크헤드 | 동시 100, 대기 0 | Tomcat 200 중 절반을 AI가 아닌 기능 몫으로 보장. 기다리게 하면 그것 자체로 스레드를 잡으므로 즉시 거절 |
| Netty 커넥션 풀 | 16 → 100 | 기본값이 max(CPU 수, 8) × 2 = **16**(CPU 2개). 정상 상황에서도 클라이언트 안에 숨은 대기열이 있었음 |
| 서킷 브레이커 | 최근 20건 중 50% 실패(최소 10건) → 30 s OPEN → 시험 3건 | 고장 난 의존성에 매번 20 s씩 기다리지 않게 함 |
| 재시도 | 1회, 429/502/503/504와 연결 오류만, 지수 백오프와 지터 | **타임아웃은 재시도하지 않음.** 이미 20 s와 비용을 쓴 호출을 다시 하면 둘 다 두 배가 됨 |

**서킷 브레이커 타임라인 (예측 vs 실측, AI 초당 12건):**

| 사건 | 예측 | 실측 |
|---|---|---|
| OPEN | 장애 60 s + 첫 타임아웃 20 s = t+80 s | **t+81 s** |
| CLOSED | 장애 제거 180 s 이후 다음 HALF_OPEN(30 s 주기) + 시험 호출 | **t+215~217 s** (제거 35~37 s 후) |
| 장애 중 추천 서버 p50 | 대부분 즉시 FALLBACK | **11 ms** |

**재시도와 서킷 순서 (요청 20건을 순서대로 보냄):**

| 상황 | `Retry(CB(…))` (문서 권장 순서) | `CB(Retry(…))` (채택) |
|---|---|---|
| 503과 정상이 번갈아 옴 | AI 9건, FALLBACK 11건, **서킷 열림** | **AI 20건**, 서킷 닫힘 |
| 계속 503 | — | 10건 × 2회 시도 → OPEN → 나머지 10건은 호출 안 함: OpenAI 호출 **20회** (서킷 없으면 40회) |

권장 순서에서는 서킷이 재시도 한 번 한 번을 실패로 셌습니다. 그래서 순서를 바꿔 서킷이 **요청의 최종 결과**만 보게 했습니다.

**최종 결과 (AI 초당 12건, OpenAI 무응답):** 추천 실패 0%(FALLBACK 응답), 로그인 실패 0% / 최대 144 ms, 마이페이지 최대 31 ms.

---

## 4. MySQL 중단 대응

**현상:** JWT 필터가 요청마다 `userRepository.findById`를 호출했습니다. 그래서 **DB가 필요 없는 AI 추천까지** MySQL 장애에 함께 죽었습니다. 실패한 요청은 Hikari 기본값 때문에 약 30 s를 기다린 뒤 500을 받았습니다.

**계산 (DB 장애 중 묶이는 스레드 = λ × Hikari 연결 대기 시간, λ ≈ 6 req/s):**

| 연결 대기 시간 | 예측 | 실측 |
|---|---|---|
| 30 s (기본값) | 6 × 30 = 180 | **170** |
| 3 s | 6 × 3 = 18 | **18** |

정상일 때 커넥션 획득 시간은 평균 0.1 ms(실측)라서 3 s도 충분히 넉넉합니다.

**3번의 측정 (MySQL 2분 중단):**

| 지표 | ① 개선 전 | ② 3 s + 503 | ③ + stateless 인증 |
|---|---|---|---|
| AI 추천 실패율 | 77% | 99% | **0%** |
| 로그인 / 마이페이지 | 500, 약 30 s | 503 + Retry-After, 약 3 s | 503 + Retry-After, 약 3 s (DB 필수라 의도된 동작) |
| Tomcat 바쁜 스레드 | 170 | 18 | 22 |
| DB 복구 직후 로그인 최대 | 7.2 s | 3.0 s | 3.0 s |

②는 **실패하는 방식**을 고쳤을 뿐 가용성은 높이지 못했습니다(그대로 기록함). 가용성은 ③에서 인증의 DB 의존을 없애고 나서야 해결됐습니다.

**설계 판단:**
- DB 장애는 버그가 아니라 의존성 장애이므로 **503**으로 응답합니다.
- 필터에서 던진 예외는 `@RestControllerAdvice`에 닿지 않습니다. 그래서 ②에서는 JWT 필터 안에서 직접 503을 응답했습니다. ③에서 필터가 DB를 조회하지 않게 되면서 이 처리는 필요 없어져 제거했습니다. 현재 503 응답은 컨트롤러 이후 경로의 `GlobalExceptionHandler`가 담당합니다.
- readiness 헬스체크에는 DB를 넣고, liveness에는 넣지 않았습니다. DB 장애 때 앱을 재시작해 봐야 해결되지 않고 재시작만 반복되기 때문입니다.
- **stateless 인증의 대가:** 탈퇴한 사용자의 access token이 만료 전까지 유효합니다. 그래서 access token을 15분으로 줄였습니다. 사용자 조회 API는 탈퇴한 사용자에게 401을 줍니다.

---

## 5. access token 만료 사용자 경험 → refresh token

**재현 (access token 60 s, 브라우저):** 결과 화면에서 장소를 고르는 사이 토큰이 만료되고, "다음 단계"를 누르면 이렇게 됐습니다.
- 화면에는 **"인증이 필요합니다."** 문구만 나옵니다.
- 헤더는 여전히 로그인 상태로 보입니다.
- 사용자는 처음부터 다시 해야 합니다.

추가로, OAuth 로그인 후 **URL에 `?token=<JWT>`가 노출**되고 있었습니다.

| 항목 | 결정 |
|---|---|
| access token | 1시간 → **15분**, `Authorization` 헤더 |
| refresh token | 256비트 임의값, DB에는 **SHA-256 해시만** 저장, 14일 |
| 전달 | `HttpOnly; Secure; SameSite=Strict; Path=/api/auth` 쿠키 (XSS와 CSRF 방어) |
| 회전 / 재사용 탐지 | 쓸 때마다 교체하고, 이미 회전된 토큰이 재사용되면 **토큰 계열 전체 폐기** |
| 동시성 | 프론트 **single-flight** + DB 원자적 갱신(`WHERE revoked_at IS NULL`) + 서버 **10 s 유예** |
| OAuth | URL에서 토큰을 제거하고, 콜백에서 `/api/auth/refresh`로 교환 |
| 정리 | 만료 토큰을 **1,000건씩 배치로** 삭제 (대량 DELETE로 512MB MySQL이 OOM으로 죽었던 경험 반영) |

**검증 (같은 시나리오):**
- 네트워크: `selected-routes` **401 × 2 → refresh 1회 → 200 × 2**. 에러 없이 결과가 표시됐습니다.
- DB: 로그인 토큰이 `ROTATED` 1회, 새 토큰은 로그아웃 때 `LOGOUT`으로 폐기됐고 같은 계열입니다.
- 세션이 정말 끝난 경우: `/login`으로 이동하고 돌아갈 위치(`/mypage`)를 저장합니다.

---

## 6. 보안 정리

| 발견 | 조치 |
|---|---|
| 공개 저장소에 **카카오 client-secret**과 로컬 DB 비밀번호 커밋 (`application-local.yaml`. `.gitignore`는 `.yml`만 막고 있었음) | 자리표시자로 바꾸고 실제 값은 무시되는 파일로 이동, gitignore 보강. **히스토리에는 남아 있으므로 시크릿 재발급이 필요** |
| 미사용 모임/장소 API 운영 노출, 장소 목록 **IDOR** | API 계층 삭제 (탈퇴 시 데이터 정리를 위해 엔티티는 유지) |
| 추천이 다른 사용자의 모임 장소 후보를 프롬프트와 결과에 사용 | 제거 |
| OAuth 리다이렉트 URL에 access token | 제거 (HttpOnly 쿠키 + refresh 교환) |
| 내부 예외 메시지가 API 응답에 노출 | 일반 문구로 바꾸고 원인은 로그로 |
| 외부에서 받은 `X-Request-Id`로 로그 위조 가능성 | 형식 검증(`[A-Za-z0-9._-]{8,64}`) 후에만 그대로 사용 |

---

## 7. 수정한 코드 (`backend/`)

| 영역 | 파일 |
|---|---|
| 관측 | `common/logging/RequestIdFilter.java`, `application.yaml`(관리 포트, 히스토그램, Tomcat MBean), `build.gradle`(actuator, prometheus) |
| DB 성능 | `application.yaml`(`open-in-view: false`, Hikari `connection-timeout: 3s`), 엔티티 `@Index` (`User`, `GroupMember`, `PlaceCandidate`) |
| 예외 처리 | `exception/GlobalExceptionHandler.java`, `exception/DatabaseUnavailable.java`, `exception/AuthenticatedUserNotFoundException.java`, `security/ApiAuthenticationEntryPoint.java`, `config/SecurityConfig.java` |
| OpenAI 복원력 | `service/openai/OpenAiClientImpl.java`(타임아웃, 커넥션 풀, 데코레이터), `service/openai/OpenAiResilienceConfig.java`, `service/RecommendationService.java`(FALLBACK 문구, 타 사용자 데이터 제거) |
| 인증 | `security/JwtAuthenticationFilter.java`(stateless), `security/JwtProvider.java`(15분), `security/OAuth2SuccessHandler.java`, `auth/*`(RefreshToken 엔티티, 리포지토리, 서비스, 쿠키, 설정), `controller/LocalAuthController.java`(`/refresh`, `/logout`), `service/UserService.java`(탈퇴 시 폐기) |
| 삭제 | 모임/장소/가능시간 컨트롤러, 서비스, DTO, 전용 예외 |
| 테스트 | `ErrorResponseIntegrationTest`, `DatabaseUnavailableIntegrationTest`, `OpenAiResilienceConfigTest`, `RefreshTokenServiceTest`, `RefreshTokenFlowIntegrationTest`, `OAuth2SuccessHandlerTest`, `UserServiceDeletedUserTest`, `RequestIdFilterTest` 등 → **31개 통과 (1개는 원래 skip)**. 테스트 H2에서 `user` 테이블이 생성되지 않던 문제(`NON_KEYWORDS=USER`)도 수정 |

프론트엔드: `api/http.ts`(401 → single-flight refresh → 재시도), `pages/OAuthCallbackPage.tsx`, `components/Header.tsx`(서버 로그아웃), `vite.config.ts`(개발 프록시). 미사용 `api/group.ts`, `api/place.ts`는 삭제했습니다.

**커밋 (시간순):**

| 커밋 | 내용 |
|---|---|
| `e90845c` | 로컬 비밀값 제거, gitignore 보강 |
| `89fc888` | Prometheus/Grafana 스택, actuator, 요청 ID |
| `6d043f2` | OSIV off, N+1 제거, 인덱스 |
| `a998e3b` | 실제 서비스 흐름으로 OSIV 재측정 |
| `d012709` | 4xx/401 올바른 응답, 예외 로그 |
| `ad0008f` | 미사용 API와 타 사용자 후보 검색 제거 |
| `ec9f94a` | OpenAI 타임아웃, 벌크헤드, 서킷, 재시도 |
| `8dd201f` | MySQL 장애 시 3 s 안의 503 |
| `2e7fdef` | stateless JWT 인증 |
| `6b894fe` | refresh token |

---

## 8. 한계 (정직하게)

- 모든 수치는 **로컬 환경**의 값입니다. **운영(EC2) 기준 측정은 아직 하지 않았습니다.** 인스턴스 유형과 CPU 크레딧 모드를 확인한 뒤 최대 30 VU, 3분 이내로 진행할 예정입니다.
- OpenAI 지연은 **mock 분포(가정)** 입니다. 호출 타임아웃 20 s와 느린 호출 기준 15 s는 운영 로그(`durationMs`)로 다시 정해야 합니다.
- 인덱스 효과는 **합성 데이터** 기준입니다.
- 재시도 검증은 순차 요청 20건입니다. 동시 부하에서의 재시도 폭주는 서킷이 막는다는 논리로만 확인했습니다.
- 첫 부하 시나리오는 프론트에서 쓰지 않는 기능을 포함해 OSIV 효과를 과장했습니다(4.06 s → 25 ms). 본문에는 실제 흐름으로 다시 잰 값(1.11 s → 121 ms)을 썼습니다.

## 9. 운영 반영 전 확인 사항

1. **카카오 Client Secret 재발급** (공개 저장소 히스토리에 남아 있음)
2. **프론트와 백엔드를 함께 배포** (OAuth 콜백 방식이 바뀌었기 때문)
3. `/etc/meetingmate/app.env`의 `JWT_ACCESS_TOKEN_EXPIRY_MS`(명시돼 있으면 15분 기본값이 적용되지 않음), `JWT_SECRET`이 있는지 확인
4. 저장소의 `nginx/default.conf`(`/api/`를 떼고 프록시)와 실제 서버 설정 비교
5. 운영 DB의 예전 모임 테이블 건수 확인 ([`legacy-group-tables-check.sql`](../../docs/ops-log/legacy-group-tables-check.sql), 읽기 전용)

## 10. 재현 방법

```bash
cp .env.observability.example .env.observability          # 값 채우기
(cd backend && ./gradlew bootJar -x test)
docker compose -f docker-compose.observability.yml --env-file .env.observability up -d

./load-test/run.sh recommend-flow baseline                 # 실제 흐름 부하
AI_RATE=12 FAULT=hang ./load-test/run.sh openai-fault my-run   # OpenAI 장애 주입
./load-test/db-fault.sh my-db-run                          # MySQL 중단
./load-test/error-matrix.sh                                # 에러 응답 표
python3 load-test/phase-collect.py load-test/results/<run>.meta
```

Grafana: http://localhost:3000 (대시보드 "MeetingMate / Backend Overview")
