# MeetingMate Frontend

MeetingMate Frontend는 React + TypeScript + Vite 기반의 장소 추천 UI입니다.
현재 코드 기준으로 인증(자동 토큰 갱신 포함), 조건 기반 추천 결과 탐색, 선택 장소 기반 코스 재추천, 마이페이지 기능이 구현되어 있습니다.

## 1. 핵심 화면/기능

- 홈 (`/home`, `/`)
- 로그인/회원가입 (`/login`, `/signup`)
  - 이메일 로그인
  - 카카오 OAuth 로그인
  - (옵션) 구글 OAuth 로그인
- OAuth 콜백 (`/oauth/callback`)
  - refresh token 쿠키를 access token으로 교환
- 추천 입력 (`/select`)
  - 모임 유형/지역/분위기/자유 요청 입력
- 추천 결과 (`/recommend`, `/result`)
  - 카테고리별 추천 장소 표시
  - 카카오맵 SDK 기반 장소 검증/지도 표시
  - 카테고리별 장소 선택
  - 추천 결과 캐시/새로고침/공유
- 선택 장소 코스 재추천 (`/selected-route`)
- 마이페이지 (`/mypage`)
  - 내 정보 조회
  - 닉네임 변경
  - 회원 탈퇴

## 2. 기술 스택

- React 19
- TypeScript 5.9
- Vite 7
- React Router 7
- Axios
- Tailwind CSS 3, React Bootstrap
- Framer Motion

## 3. 실행 방법

```bash
cd frontend
npm install
npm run dev
```

기본 개발 서버:
- [http://localhost:5173](http://localhost:5173)

개발 서버는 `/api`, `/oauth2`, `/login/oauth2` 요청을 백엔드(`http://localhost:8080`)로 프록시합니다(`vite.config.ts`). 운영 Nginx와 같은 역할입니다.
다른 백엔드를 쓰려면 `VITE_DEV_BACKEND` 환경변수로 주소를 바꿉니다.

빌드:

```bash
npm run build
npm run preview
```

## 4. 백엔드 연동 포인트

- **API 기본 URL:** `src/api/http.ts`의 `baseURL`은 `/`(같은 출처)입니다. 운영에서는 Nginx, 개발에서는 Vite 프록시가 백엔드로 전달합니다.
- **OAuth 진입:** `src/pages/LoginPage.tsx`가 `${window.location.origin}/oauth2/authorization/{kakao|google}`로 이동합니다. 하드코딩된 주소는 없습니다.
- **토큰 처리**
  - access token은 localStorage(`accessToken`)에 저장하고, 요청마다 `Authorization: Bearer` 헤더로 보냅니다.
  - refresh token은 백엔드가 심는 HttpOnly 쿠키라 JS에서 읽지 않습니다.
- **401 자동 처리 (`src/api/http.ts` 응답 인터셉터)**
  - API가 401을 받으면 `POST /api/auth/refresh`를 **한 번만**(동시 요청이 여러 개여도) 호출하고, 원래 요청을 새 토큰으로 다시 보냅니다.
  - refresh도 실패하면 토큰을 지우고, 현재 위치를 저장한 뒤 `/login`으로 이동합니다. 로그인하면 원래 위치로 돌아옵니다.
- **로그아웃:** `POST /api/auth/logout`으로 서버의 refresh token도 폐기합니다.

## 5. 환경 변수

- `VITE_ENABLE_GOOGLE_LOGIN=true`
  - 로그인 화면에 Google 버튼을 노출합니다.
- `VITE_KAKAO_MAP_API_KEY`
  - 추천 결과 화면의 카카오맵 JS SDK 로딩에 씁니다. `.env.local`에 두며 git 무시 대상입니다.
- `VITE_DEV_BACKEND` (개발 서버 전용, 선택)
  - Vite 프록시 대상 주소. 기본값은 `http://localhost:8080`입니다.

## 6. 라우트 보호

`/select`, `/result`, `/recommend`, `/selected-route`, `/mypage`는 `ProtectedRoute`로 보호됩니다.
localStorage에 access token이 없으면 `/login`으로 리다이렉트하고, 로그인 후 원래 경로로 돌아옵니다.
토큰이 있지만 만료된 경우에는 위의 401 자동 처리가 갱신이나 재로그인을 맡습니다.

## 7. 현재 코드 상태 참고

- 예전의 그룹/가능시간/장소투표 API 클라이언트(`group.ts`, `place.ts`)는 백엔드 API와 함께 삭제했습니다.
- `src/api/recommendation.ts`의 `recommendPlaces`(`POST /api/recommendations`)는 남아 있지만, 현재 어느 화면에서도 호출하지 않습니다.
