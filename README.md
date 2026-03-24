# MeetingMate (Full Stack)

모임 장소 결정을 빠르게 도와주는 서비스입니다.  
Spring Boot 백엔드 + React 프론트엔드로 구성되어 있으며, 인증/추천/모임 관리 기능을 통합 제공합니다.

## 구성

- `meetingmate/`: Backend (Spring Boot, MySQL, JWT, OAuth2, OpenAI)
- `meetingmate_front/meetingmate-frontend/`: Frontend (React, TypeScript, Vite)
- `docker-compose.yml`: MySQL + Backend + Frontend + Nginx 통합 실행

## 통합 기능 (현재 코드 기준)

- 인증
  - 로컬 회원가입/로그인 (`/api/auth/signup`, `/api/auth/login`)
  - Kakao/Google OAuth2 로그인 + JWT 발급
  - 보호 라우트 및 로그인 후 원래 경로 복귀
- 사용자
  - 내 정보 조회, 닉네임 수정, 회원 탈퇴
- AI 추천
  - 기본 장소 추천 (`POST /api/recommendations`)
  - 키워드 기반 카테고리/코스 추천 (`GET /api/recommend`)
  - 선택 장소 기반 코스 재추천 (`POST /api/recommend/selected-routes`)
  - AI 실패 시 DB/Fallback 추천
- 모임 관리
  - 모임 생성, 초대코드 참여, 내 모임 목록/상세 조회
  - 가능 시간 등록/조회(30분 단위 검증)
  - 장소 후보 등록/조회, 투표(중복 방지), 모임장 장소 확정
- 프론트 UX
  - 추천 결과 캐시(localStorage), 새로고침, 공유
  - 카카오맵 SDK 기반 장소 검증/지도 표시

## 빠른 시작

### 1) Docker로 전체 실행

```bash
docker compose up --build
```

- Nginx: `http://localhost`
- Backend: `http://localhost:8080`
- MySQL: `localhost:3306`

### 2) 로컬 개발 실행 (분리 실행)

Backend:

```bash
cd meetingmate
./gradlew bootRun --args='--spring.profiles.active=local'
```

Frontend:

```bash
cd meetingmate_front/meetingmate-frontend
npm install
npm run dev
```

## 주요 환경 변수

- Backend
  - `JWT_SECRET`
  - `APP_FRONTEND_REDIRECT_URL`
  - `MEETINGMATE_OPENAI_API_KEY`
  - `OPENAI_MODEL`
- Frontend
  - `VITE_ENABLE_GOOGLE_LOGIN`
  - `VITE_KAKAO_MAP_APP_KEY`

## 참고

- Swagger: `http://localhost:8080/swagger-ui.html`
- 프론트의 API baseURL/OAuth URL 일부는 운영 주소로 하드코딩되어 있어, 로컬 개발 시 수정이 필요합니다.
- 세부 문서는 각 모듈 README 참고:
  - `meetingmate/README.md`
  - `meetingmate_front/meetingmate-frontend/README.md`
