# 부하 테스트: 외부 AI API 대기 중 DB 커넥션 점유(OSIV)로 인한 풀 고갈

- 날짜: 2026-11-09
- 환경: 로컬 docker-compose (운영과 같은 `prod` 프로필, 백엔드 CPU 2 / 메모리 768MB 제한, MySQL 8.0 / 512MB)
- 도구: k6 1.3.0, Prometheus + Grafana, MySQL slow query log / `performance_schema` / `EXPLAIN ANALYZE`
- 서비스 흐름: 개인 사용자가 조건(지역·모임 유형·분위기·일정)을 입력한다 → AI가 장소를 추천한다 → 카카오맵으로 확인한다 → 마음에 드는 장소를 골라 AI 코스 추천을 받는다

> **이후 코드 변경 (2026-11-10)** — 이 문서는 측정 당시 코드 기준이다.
> - 추천 API가 OpenAI 호출 전에 하던 DB 조회(`place_candidate` 키워드 검색)는 `ad0008f`에서 제거됐다. JWT 필터의 사용자 조회도 `2e7fdef`에서 없어졌다. **그래서 현재 추천 경로는 DB 커넥션을 잡지 않고, 이 문서의 병목은 같은 조건으로는 재현되지 않는다.** `open-in-view: false`는 "외부 호출 전에 DB를 조회하는 코드"가 다시 생길 때를 막는 장치로 유지한다.
> - 부록 B의 N+1 수정 코드(`PlaceService.getPlaces`, `PlaceVoteRepository.countGroupedByPlaceIds`)와 테스트는 미사용 모임/장소 API와 함께 `ad0008f`에서 삭제됐다. 인덱스는 엔티티에 그대로 남아 있다.

## 1. 문제

사용자 60명이 실제 화면 흐름(로그인 → 조건 입력 추천 → 결과 확인 → 선택 장소 코스 추천 → 마이페이지)을 반복하자,
**AI와 관계없는 API까지 느려졌다.**

- `GET /api/user/me`는 PK 조회 한 번인데 p95가 **1.4초**였다(평소 11ms).
- `POST /api/auth/login`의 p95는 **991ms**였다(평소 약 105ms. 대부분 bcrypt 계산 시간).

CPU에는 여유가 있었고, 에러는 0건이었다.

## 2. 가설

Spring Boot 기본값인 **OSIV(`spring.jpa.open-in-view=true`)** 가 원인이라고 봤다.

OSIV가 켜져 있으면, 요청 중 처음 쿼리를 실행할 때 얻은 DB 커넥션을 **응답을 다 보낼 때까지** 반납하지 않는다.
`GET /api/recommend`는 DB 조회를 먼저 하고 OpenAI 응답을 기다린다(중앙값 약 3.4초).
그래서 추천 요청 하나가 커넥션 하나를 3초 넘게 쥐고 있게 된다.
HikariCP 기본 풀은 10개뿐이라, AI 요청이 동시에 10개 이상 몰리면 로그인이나 마이페이지 같은 다른 API가 커넥션을 기다리게 된다.

## 3. 실험 방법

- **시나리오:** [`load-test/recommend-flow.js`](../../load-test/recommend-flow.js). 프론트엔드 라우트(`/select` → `/result` → `/selected-route`, MyPage)를 그대로 따랐다.
  - 세션의 30%만 다시 로그인한다. 나머지는 토큰이 유효한 재방문자로 가정했다.
  - 결과 화면에서는 프론트와 똑같이 **카테고리마다 한 곳씩** 골라 코스 추천을 요청한다.
  - 단계 사이에는 생각하는 시간(2~7초)을 넣었다.
  - 사용자 수는 0 → 20 → 60명으로 늘려 2분 유지하고 줄인다. 한 번에 약 4분 반 걸린다.
- **로그인:** OAuth는 자동화할 수 없어서, 앱에 이미 있는 로컬 계정 API로 실제 JWT를 받았다. 테스트 전용 인증 코드는 운영에 추가하지 않았다.
- **OpenAI 대체:** 비용 원칙에 따라 WireMock mock을 썼다. 응답 형식은 실제와 같고, 지연은 lognormal 분포(중앙값 3초, σ=0.4)로 실제 LLM API의 긴 꼬리 지연을 흉내 냈다.
- **변수 통제:**
  - 코드는 그대로 두고, 환경변수 `SPRING_JPA_OPEN_IN_VIEW`만 `true`/`false`로 바꿔 같은 시나리오를 두 번 돌렸다.
  - DB는 운영과 비슷하게 빈 상태에서 시작했다.
  - 실행할 때마다 컨테이너의 환경변수를 확인해서, OSIV가 실제로 반영됐는지 검증했다.
- **수집:** k6 요약과, 실행 구간의 Prometheus 서버 지표([`collect.py`](../../load-test/collect.py))를 함께 남겼다.

## 4. 결과

| 지표 (60 VU, 실제 흐름) | OSIV on (개선 전) | OSIV off (개선 후) |
|---|---|---|
| AI 외 API p95 (k6, 클라이언트 측) | **1.11 s** | **121 ms** |
| `GET /api/user/me` 서버 p95 | 1,432 ms | **25 ms** |
| `POST /api/auth/login` 서버 p95 | 991 ms | **132 ms** |
| Hikari active (max) / pending (max) | 10 (풀 전체) / **12** | 1 / **0** |
| 커넥션 평균 대기 시간 | 179 ms | 0.1 ms |
| 커넥션 평균 점유 시간 | **821 ms** | **2.2 ms** |
| `GET /api/recommend` 서버 p50 / p95 | 3,404 / 6,670 ms | 3,370 / 6,299 ms |
| `POST /api/recommend/selected-routes` p50 / p95 | 3,375 / 6,514 ms | 3,343 / 6,724 ms |
| 완료한 사용자 흐름 (4분 반) | 528 | 529 |
| 에러율 | 0% | 0% |

### 해석 (과장하지 않기)

- OSIV를 끈다고 **AI 추천 자체가 빨라지지는 않는다.** AI 요청은 OpenAI(mock) 응답 시간에 묶여 있어서 전후가 같다. 처리량도 같다. 사용자의 생각 시간과 AI 대기가 전체 시간의 대부분을 차지하기 때문이다.
- 개선된 것은 **AI 대기가 다른 API로 번지는 현상**이다. 커넥션 점유 시간이 821ms에서 2.2ms로 줄면서, 로그인과 마이페이지가 AI 트래픽과 상관없이 평소 속도를 되찾았다.
- 지금은 60명에 pending 12 정도지만, AI 동시 요청이 많아질수록 이 차이는 더 커진다. 풀(10개)보다 동시 AI 요청이 많아지는 순간부터, AI와 상관없는 모든 요청이 줄을 서기 때문이다.

## 5. 개선

`application.yaml`에 `spring.jpa.open-in-view: false`를 넣었다.

끄기 전에 지연 로딩이 깨질 곳이 있는지 확인했다.
LAZY 연관관계는 모두 서비스의 `@Transactional` 안에서만 접근하고, 컨트롤러는 DTO와 ID만 다룬다.
그래서 `LazyInitializationException`이 생길 곳이 없다는 것을 확인했고, 기존 테스트도 모두 통과했다.

**커넥션 풀 크기를 늘리지 않은 이유:**
풀을 늘리면 증상은 줄지만 "외부 API를 기다리면서 커넥션을 쥐고 있는" 구조는 그대로 남는다. 동시 사용자가 늘면 같은 문제가 다시 생긴다.
게다가 운영 MySQL은 같은 소형 EC2에서 돌고 있어서, 커넥션 하나하나가 메모리를 쓴다.
원인인 점유 시간을 없애는 것이 맞다고 판단했다.

## 6. 그래프 (Grafana, 로컬 스택 실행 중일 때 열림)

| 실행 | 링크 |
|---|---|
| OSIV on | http://localhost:3000/d/meetingmate-backend?from=1794216441000&to=1794216766000 |
| OSIV off | http://localhost:3000/d/meetingmate-backend?from=1794216723000&to=1794217047000 |

캡처 추천 패널: "Connections (HikariCP)"(pending과 active 차이), "p95 latency by endpoint"(user/me와 login).
원본 수치는 `load-test/results/*recommend-flow-osiv-*`에 있다.

## 7. 다음 과제 (2단계로 넘김)

이 서비스의 실제 병목은 **OpenAI 호출 자체**다.

- 중앙값 3.4초, p95 6~7초가 걸린다.
- `WebClient.block()`에 **타임아웃이 없다.** OpenAI가 멈추면 Tomcat 스레드도 무한정 기다린다.

타임아웃, 서킷 브레이커, 폴백(이미 있는 FALLBACK 코스)을 장애 주입으로 검증할 예정이다.

## 8. 배운 점

- **느린 API와 느린 원인은 다른 곳에 있을 수 있다.** `user/me`와 로그인 코드에는 문제가 없었다. 원인은 같은 커넥션 풀을 쓰는 AI 추천 API였다. 엔드포인트별 지연만 봐서는 찾을 수 없었고, 커넥션 풀 지표(pending, 점유 시간)를 함께 봐야 보였다.
- **트랜잭션 범위와 "커넥션 점유 범위"는 다르다.** 외부 API를 부르는 서비스라면 OSIV를 끄거나, 외부 호출을 커넥션 밖으로 빼야 한다.
- **부하 시나리오는 실제 서비스 흐름에서 시작해야 한다.** 처음에는 백엔드 API 목록을 보고 시나리오를 짰다. 그래서 프론트에서 쓰지 않는 모임 기능을 넣었고, 그 결과 병목을 과장한 수치(아래 부록 A)가 나왔다. 프론트엔드 라우트를 확인한 뒤 시나리오를 다시 만들어 측정했다.
- **측정 대상이 실제로 배포됐는지부터 확인해야 한다.** Docker 빌드가 네트워크 타임아웃으로 실패했는데 이전 컨테이너가 그대로 떠 있어서, 옛 버전을 측정한 적이 있다. 이후 로컬 스택은 호스트에서 빌드한 jar를 마운트하도록 바꿨다(`load-test/redeploy.sh`). 그리고 실행할 때마다 설정이 반영됐는지(환경변수, 커넥션 점유 시간) 확인했다.
- **요청 ID가 디버깅 시간을 줄였다.** 새 시나리오의 첫 실행에서 코스 추천이 전부 400을 받았다. k6가 보낸 `X-Request-Id`로 서버 로그를 바로 찾았고, 같은 카테고리 장소를 고른 스크립트 버그라는 것을 확인했다.
- **작은 인스턴스에서는 대량 DELETE 하나로도 MySQL이 죽는다.** 테스트 데이터 50만 행 이상을 트랜잭션 하나로 지우다가, 메모리 512MB로 제한한 MySQL 컨테이너가 **OOM으로 종료**됐다. 운영 EC2도 소형 인스턴스에서 MySQL을 함께 돌리므로, 대량 정리 작업은 배치로 나눠야 한다(예: `DELETE ... LIMIT 5000` 반복).

---

## 부록 A. 첫 번째 시나리오(모임 기능 포함)에서의 측정 — 참고용

처음 시나리오([`user-flow.js`](../../load-test/user-flow.js), 지금은 대체됨)는 프론트에서 쓰지 않는 모임/장소 API를 탐색 흐름에 넣었다.
그래서 AI 외 요청의 비중이 실제보다 컸고, OSIV 효과도 과장돼 보였다(AI 외 API p95 4.06s → 25ms).
**포트폴리오 수치로는 본문(4장)을 쓴다.**

## 부록 B. 부가 발견 (현재 미사용 기능 / 데이터 확장 가정)

- **N+1:** `GET /api/groups/{id}/places`가 장소마다 투표 수 `COUNT`를 따로 실행했다. 장소 20개면 SQL이 22개였다. `IN + GROUP BY` 한 번으로 바꿔 3개로 줄였다. 다만 **프론트에서 쓰지 않는 기능**이라 실사용 효과는 없다.
- **인덱스:** 합성 데이터(사용자 20만 등)에서 `EXPLAIN ANALYZE`로 풀 스캔을 확인하고 인덱스 4개를 추가했다. 이 중 **로그인 경로의 `user(email, provider)`, `user(provider_id, provider)`는 실제 서비스에 해당한다**(20만 행 기준 47ms → 1ms). 모임/장소 인덱스는 미사용 기능용이다.
- **철회한 가설:** 추천 API의 키워드 검색(`upper(name) LIKE '%키워드%'`)이 50만 행 기준 245ms로 병목처럼 보였다. 하지만 검색 대상 `place_candidate`는 미사용 모임 기능으로만 채워지는 테이블이다. 운영에서는 거의 비어 있으므로, 합성 데이터가 만든 인위적인 병목이라 판단해 철회했다.
- **보안:** 미사용 모임 API에 IDOR가 있다(장소 목록 조회 시 멤버인지 확인하지 않음). 그런데도 운영에 노출돼 있다. 2단계에서 비활성화나 삭제를 검토한다.

## 부록 C. 재현 방법

```bash
cp .env.observability.example .env.observability   # 값 채우기
(cd backend && ./gradlew bootJar -x test)
docker compose -f docker-compose.observability.yml --env-file .env.observability up -d

# 개선 전 (OSIV on)
SPRING_JPA_OPEN_IN_VIEW=true docker compose -f docker-compose.observability.yml --env-file .env.observability up -d --force-recreate backend
./load-test/run.sh recommend-flow osiv-on
# 개선 후 (OSIV off, 기본값)
docker compose -f docker-compose.observability.yml --env-file .env.observability up -d --force-recreate backend
./load-test/run.sh recommend-flow osiv-off

python3 load-test/collect.py load-test/results/<실행>.meta
```
