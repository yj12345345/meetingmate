# MeetingMate (Full Stack)

조건(지역, 모임 유형, 분위기, 원하는 흐름)을 입력하면 AI가 실제 장소와 코스를 추천하고, 카카오맵으로 바로 확인할 수 있는 **개인용 장소 추천 서비스**입니다.
Spring Boot 백엔드와 React 프론트엔드로 구성되어 있습니다.

## 구성

- `backend/`: Spring Boot API 서버 (MySQL, JWT + refresh token, OAuth2, OpenAI)
- `frontend/`: React + TypeScript + Vite
- `nginx/default.conf`: 리버스 프록시 설정 예시 (아래 "참고" 참조)
- `docker-compose.observability.yml`: **로컬 관측/부하 테스트 스택** (백엔드 + MySQL + Prometheus + Grafana + OpenAI mock)
- `load-test/`: k6 시나리오, 장애 주입 스크립트, 측정 결과
- `docs/ops-log/`: 부하 테스트와 장애 주입 실험 기록
- `backend/ops-report/README.md`: 운영 분석 보고서 (무엇을 고쳤고 어떻게 계산했는지)

## 기능 (현재 코드 기준)

- **인증**
  - 로컬 회원가입/로그인 (`/api/auth/signup`, `/api/auth/login`)
  - 카카오/구글 OAuth2 로그인
  - access token(JWT, 15분) + refresh token(HttpOnly 쿠키, 14일, 회전과 재사용 탐지)
  - 토큰이 만료되면 프론트가 자동으로 갱신하고, 세션이 끝나면 로그인 후 원래 화면으로 돌아옴
- **사용자:** 내 정보 조회, 닉네임 수정, 회원 탈퇴
- **AI 추천**
  - 조건 기반 카테고리별 장소와 코스 추천 (`GET /api/recommend`)
  - 선택한 장소로 코스 재추천 (`POST /api/recommend/selected-routes`)
  - OpenAI가 실패하거나 느리면 기본 코스(`source: FALLBACK`)로 응답
- **프론트 UX:** 추천 결과 캐시(localStorage), 새로고침, 공유, 카카오맵 SDK 기반 장소 검증과 지도 표시
- **운영 안정성**
  - OpenAI 호출: 타임아웃, 벌크헤드, 서킷 브레이커, 재시도
  - MySQL 장애: 3초 안에 503 응답, AI 추천은 DB 없이도 동작(stateless 인증)
  - 요청 ID(`X-Request-Id`)를 로그에 남기고, Prometheus 메트릭을 제공

> 예전에 있던 모임 생성/초대, 가능 시간, 장소 투표 API는 프론트에서 쓰지 않아 삭제했습니다. 회원 탈퇴 시 남은 데이터를 지우기 위해 엔티티만 남아 있습니다.

## 실행

### 로컬 개발 (분리 실행)

Backend: MySQL이 `localhost:3306`에 있고 `meetingmate` DB가 있어야 합니다. 비밀값은 `backend/.env.local.properties`에 넣습니다(git 무시 대상, 형식은 `backend/.env.local.properties.example` 참고).

```bash
cd backend
./gradlew bootRun --args='--spring.profiles.active=local'
```

Frontend: Vite 개발 서버가 `/api`, `/oauth2`, `/login/oauth2`를 `http://localhost:8080`으로 프록시합니다(운영 Nginx와 같은 역할).

```bash
cd frontend
npm install
npm run dev
```

### 로컬 관측 / 부하 테스트 스택

```bash
cp .env.observability.example .env.observability   # 값 채우기
(cd backend && ./gradlew bootJar -x test)
docker compose -f docker-compose.observability.yml --env-file .env.observability up -d
```

- Backend `http://localhost:8080` (운영과 같은 `prod` 프로필), Grafana `http://localhost:3000`, Prometheus `http://localhost:9090`
- OpenAI는 WireMock mock(`localhost:8089`)으로 대체되어 실제 API 요금이 발생하지 않습니다.
- 부하 테스트와 장애 주입 방법은 `backend/ops-report/README.md`의 "재현 방법"을 참고하세요.

### 운영 배포 구조 (AWS EC2)

- Nginx(HTTPS, Let's Encrypt)가 프론트 정적 파일을 서빙하고 `/api`, `/oauth2`, `/login/oauth2`를 백엔드로 프록시
- 백엔드는 systemd 서비스(`meetingmate.service`)로 jar 실행, 환경변수는 `/etc/meetingmate/app.env`
- MySQL은 같은 EC2에서 실행

## 주요 환경 변수

- **Backend:** `JWT_SECRET`, `JWT_ACCESS_TOKEN_EXPIRY_MS`, `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET`, `MEETINGMATE_OPENAI_API_KEY`, `OPENAI_MODEL`, `APP_FRONTEND_REDIRECT_URL`, `SPRING_DATASOURCE_*`
  - 전체 목록은 `backend/README.md`에 있습니다.
- **Frontend:** `VITE_KAKAO_MAP_API_KEY`(카카오맵 JS SDK), `VITE_ENABLE_GOOGLE_LOGIN`(`true`면 구글 로그인 버튼 노출)

## 참고

- **Swagger:** `http://localhost:8080/swagger-ui.html`
- **루트의 `docker-compose.yml`은 예전 폴더 구조(`./meetingmate`, `./meetingmate_front/...`) 기준이라 지금은 그대로 동작하지 않습니다.** 로컬에서 전체를 띄울 때는 위의 관측 스택과 `npm run dev`를 사용하세요.
- **`nginx/default.conf`** 는 `location /api/ { proxy_pass http://127.0.0.1:8080/; }`처럼 `/api` 접두사를 떼고 넘깁니다. 그런데 백엔드 경로는 `/api/...`입니다. 실제 운영 서버의 설정과 다를 수 있으니 배포 전에 맞춰 확인하세요.
- 세부 문서:
  - `backend/README.md`
  - `frontend/README.md`
  - `backend/ops-report/README.md`
