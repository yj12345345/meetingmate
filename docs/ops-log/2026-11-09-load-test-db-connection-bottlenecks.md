# 부하 테스트: DB 커넥션 풀 고갈(OSIV) · N+1 · 인덱스 누락

- 날짜: 2026-11-09
- 환경: 로컬 docker-compose (운영과 같은 `prod` 프로필, 백엔드 CPU 2 / 메모리 768MB 제한, MySQL 8.0 / 512MB)
- 도구: k6 1.3.0, Prometheus + Grafana, MySQL slow query log / `performance_schema` / `EXPLAIN ANALYZE`
- 관련 커밋: OSIV 비활성화, `PlaceService.getPlaces` N+1 제거, 엔티티 인덱스 추가

## 1. 문제

사용자 60명이 "내 정보 → 내 모임 → 모임 상세 → 장소 후보 → AI 추천" 흐름을 반복하자,
**DB 조회 한 번뿐인 `GET /api/user/me`의 p95가 5.5초**까지 늘어났다. 1명일 때는 같은 API가 20ms 안팎이었다.
CPU 사용률은 58%로 여유가 있었고, 에러는 0건이었다. 서버가 실패하지는 않고 "그냥 느린" 상태였다.

## 2. 가설

1. **OSIV(Open Session In View) 때문에 커넥션이 오래 점유된다.**
   Spring Boot 기본값은 `spring.jpa.open-in-view=true`다.
   이 상태에서는 요청 중 처음 쿼리를 실행할 때 얻은 DB 커넥션을 **응답을 다 보낼 때까지** 반납하지 않는다.
   `/api/recommend`는 DB 조회(`findTop5ByName...`)를 먼저 하고 그다음 OpenAI를 호출한다(약 3초).
   그래서 추천 요청 하나가 커넥션을 약 3초씩 쥐고 있게 된다.
   HikariCP 기본 풀 크기는 10개뿐이라, 추천 요청 10개만 동시에 들어와도 다른 모든 API가 커넥션을 기다린다.
2. 장소 목록 API에 **N+1 쿼리**가 있다. 장소마다 `COUNT(*)`를 한 번씩 실행한다.
3. 데이터가 늘면 `group_id`, `user_id`, `email`에 인덱스가 없어서 **풀 테이블 스캔**이 생긴다.

## 3. 실험 방법

- **시나리오:** [`load-test/user-flow.js`](../../load-test/user-flow.js). 사용자 0 → 20 → 60명으로 늘린 뒤 2분 유지하고 줄이는 4분짜리 흐름이다. 각 단계 사이에 실제 사람처럼 생각하는 시간(think time)을 넣었다.
- **로그인:** OAuth는 자동화할 수 없어서, 앱에 이미 있는 로컬 계정 API(`/api/auth/signup`, `/login`)로 실제 JWT를 받았다. 테스트 전용 인증 코드는 운영 코드에 추가하지 않았다.
- **OpenAI 대체:** 비용 원칙에 따라 실제 API 대신 WireMock mock을 썼다. 응답 형식은 실제와 같고, 지연은 lognormal 분포(중앙값 3초, σ=0.4)로 실제 LLM API의 긴 꼬리 지연을 흉내 냈다.
- **수집:** 실행 구간의 서버 지표는 [`load-test/collect.py`](../../load-test/collect.py)로 Prometheus에서 뽑았다. 요청 하나당 SQL 수는 [`count-queries.sh`](../../load-test/count-queries.sh)로 `performance_schema`에서 셌다.
- **변수 통제:** 한 번에 하나만 바꾸고 다시 쟀다(baseline → OSIV off → 데이터 확장 → 인덱스).

## 4. 결과와 개선

### 4-1. OSIV off

| 지표 (60 VU, 4분) | Before (baseline) | After (OSIV off) |
|---|---|---|
| 일반 API p95 (k6, 클라이언트 측) | **4.06 s** | **25 ms** |
| `GET /api/user/me` 서버 p95 | 5,463 ms | 17 ms |
| `GET /api/recommend` p95 | 9.53 s | 6.27 s (mock 자체 p95 ≈ 5.8 s) |
| Hikari active (max) / pending (max) | 10 / **39** | 1 / **0** |
| 커넥션 평균 대기 시간 | 591 ms | 0 ms |
| 커넥션 평균 점유 시간 | 293 ms | **1 ms** |
| 4분간 완료한 사용자 흐름 | 620 | **1,002 (+62%)** |
| 에러율 | 0% | 0% |

**개선 내용:** `application.yaml`에 `spring.jpa.open-in-view: false`를 넣었다.
끄기 전에 지연 로딩이 깨질 곳이 있는지 확인했다.
LAZY 연관관계(`Availability`, `AvailabilitySlot`)는 모두 서비스의 `@Transactional` 안에서만 접근하고, 컨트롤러는 DTO와 ID만 다룬다.
그래서 `LazyInitializationException`이 날 곳이 없다는 것을 확인했고, 기존 테스트도 모두 통과했다.

> **풀 크기를 늘리지 않은 이유:** 풀을 30으로 늘리면 일단 증상은 줄어든다.
> 하지만 "외부 API를 기다리는 동안 커넥션을 쥐고 있는" 구조는 그대로라서, 동시 사용자가 늘면 같은 문제가 다시 생긴다.
> 게다가 운영 MySQL은 같은 소형 EC2에서 돌고 있어서, 커넥션 하나하나가 메모리를 쓴다.
> 원인(점유 시간)을 없애는 것이 맞다고 판단했다.

### 4-2. N+1 쿼리 (`GET /api/groups/{id}/places`)

| | Before | After |
|---|---|---|
| 장소 20개인 모임 조회 시 SQL 수 | **22** (사용자 1 + 장소 1 + 장소별 COUNT 20) | **3** (장소 수와 무관) |

**개선 내용:** `PlaceVoteRepository.countGroupedByPlaceIds`(`... WHERE place_id IN (...) GROUP BY place_id`)로 투표 수를 한 번에 가져온다.
투표가 없는 장소는 0으로 채운다.
H2에서 실제 JPQL을 실행하는 통합 테스트(`PlaceServiceVoteCountIntegrationTest`)로, 고치기 전과 같은 결과가 나오는 것을 확인했다.

### 4-3. 인덱스 누락 (데이터 확장 시나리오)

실제 운영 데이터는 아직 적다. 그래서 **서비스가 성장했다고 가정한 합성 데이터**를 넣었다
([`seed-scale.sql`](../../load-test/seed-scale.sql): 사용자 20만, 모임 5만, 장소 후보 50만, 멤버십 20만).

| 쿼리 (`EXPLAIN ANALYZE`) | Before | After |
|---|---|---|
| `place_candidate WHERE group_id=?` | Table scan 50만 행, **103 ms** | Index lookup 20행, **0.5 ms** |
| `group_members WHERE user_id=?` | Table scan 20만 행, 27 ms | Index lookup, 0.7 ms |
| `user WHERE email=? AND provider=?` (로그인) | Table scan 20만 행, 47 ms | Index lookup, 1.0 ms |

| 부하 테스트 (데이터 확장 상태) | 인덱스 없음 | 인덱스 추가 |
|---|---|---|
| `GET /places` 서버 p50 / p95 | 144 / 410 ms | **7 / 32 ms** |
| `GET /api/groups/me` 서버 p50 / p95 | 49 / 135 ms | 8 / 66 ms |

**개선 내용:** 엔티티에 `@Table(indexes = ...)`를 선언했다.
`idx_place_candidate_group_id`, `idx_group_members_user_id`, `idx_user_email_provider`, `idx_user_provider_id_provider` 4개다.
운영의 `ddl-auto: update`가 다음 배포 때 이 인덱스를 자동으로 만든다(로컬에서 확인함).

### 4-4. 남은 병목 (다음 작업)

인덱스를 넣은 뒤에도 모든 API의 **p99가 약 900ms**였고, Hikari pending이 16까지 올랐다.
slow query log에 잡힌 1,914건 중 1,908건이 추천 API의 키워드 검색이었다.

```sql
select ... from place_candidate where upper(name) like upper('%홍대%') order by id desc limit 5
-- EXPLAIN ANALYZE: 50만 행 전체 스캔, 245 ms (부하 중 중앙값 304 ms, 최대 2.4 s)
```

앞에 `%`가 붙는 LIKE는 B-tree 인덱스를 쓸 수 없다. 남은 선택지는 MySQL FULLTEXT(ngram) 인덱스, 검색 범위 축소, 캐시다.
덤으로, 이 쿼리는 **다른 모임의 장소 후보까지** 검색 대상에 넣는다. 데이터 노출 측면에서도 검토가 필요하다.

## 5. 그래프 (Grafana, 로컬 스택 실행 중일 때 열림)

| 실행 | 링크 |
|---|---|
| baseline | http://localhost:3000/d/meetingmate-backend?from=1794211203000&to=1794211524000 |
| OSIV off | http://localhost:3000/d/meetingmate-backend?from=1794211861000&to=1794212180000 |
| 데이터 확장, 인덱스 없음 | http://localhost:3000/d/meetingmate-backend?from=1794212238000&to=1794212560000 |
| 데이터 확장, 인덱스 추가 | http://localhost:3000/d/meetingmate-backend?from=1794212543000&to=1794212870000 |

캡처 추천 패널: "Latency p50/p95/p99", "Connections (HikariCP)". baseline과 OSIV off를 나란히 놓으면 pending 그래프 차이가 가장 잘 보인다.
원본 수치는 `load-test/results/`에 있다(k6 요약 JSON/TXT, 서버 지표 `.server.md`).

## 6. 배운 점

- **"느린 API"와 "느린 원인"은 다른 곳에 있을 수 있다.** `user/me` 자체에는 문제가 없었다. 원인은 같은 풀을 쓰는 추천 API였다. 엔드포인트별 지연만 봐서는 찾을 수 없었고, 커넥션 풀 지표(pending, 점유 시간)를 함께 봐야 보였다.
- **OSIV는 외부 API 호출과 함께 쓰면 위험하다.** 트랜잭션 범위뿐 아니라 "커넥션 점유 범위"를 설계해야 한다.
- **측정 대상이 실제로 배포됐는지부터 확인해야 한다.** 두 번째 측정 때 Docker 빌드가 네트워크 타임아웃으로 실패했는데, 이전 컨테이너가 그대로 떠 있어서 결과가 baseline과 똑같았다. 시작 로그의 OSIV 경고를 보고 알아챘다. 이후 로컬 스택은 호스트에서 빌드한 jar를 마운트하는 방식으로 바꿨고(`load-test/redeploy.sh`), 재배포 후 반영 여부를 확인하는 단계를 넣었다. 덕분에 baseline이 재현된다는 것도 확인했다(두 번 모두 pending 39~42, `user/me` p95 5.5~6.0초).
- **인덱스 효과는 데이터 규모가 있어야 보인다.** 행이 몇백 개일 때는 차이가 없었다. 그래서 가정한 데이터 규모를 결과와 함께 적어야 한다.

## 부록: 재현 방법

```bash
cp .env.observability.example .env.observability   # 값 채우기
(cd backend && ./gradlew bootJar -x test)
docker compose -f docker-compose.observability.yml --env-file .env.observability up -d
./load-test/run.sh user-flow baseline
python3 load-test/collect.py load-test/results/<실행>.meta
```
