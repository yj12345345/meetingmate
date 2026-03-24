# MeetingMate Frontend

MeetingMate Frontend는 React + TypeScript + Vite 기반의 모임 장소 추천 UI입니다.
현재 코드 기준으로 인증, 키워드 추천 결과 탐색, 선택 장소 기반 코스 재추천, 마이페이지 기능이 구현되어 있습니다.

## 1. 핵심 화면/기능

- 홈 (`/home`, `/`)
- 로그인/회원가입 (`/login`, `/signup`)
  - 이메일 로그인
  - 카카오 OAuth 로그인
  - (옵션) 구글 OAuth 로그인
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
- TypeScript
- Vite
- React Router
- Axios
- Tailwind CSS
- Framer Motion

## 3. 실행 방법

```bash
cd meetingmate_front/meetingmate-frontend
npm install
npm run dev
```

기본 개발 서버:
- [http://localhost:5173](http://localhost:5173)

빌드:

```bash
npm run build
npm run preview
```

## 4. 백엔드 연동 포인트

현재 API 클라이언트 기본 URL은 `src/api/http.ts`에 하드코딩되어 있습니다.

- `baseURL`: `http://54.206.113.147`

OAuth 진입 URL도 `src/pages/LoginPage.tsx`에서 운영 도메인으로 하드코딩되어 있습니다.

- Kakao: `https://meetingmate.duckdns.org/oauth2/authorization/kakao`
- Google: `https://meetingmate.duckdns.org/oauth2/authorization/google`

로컬 개발 환경에 맞추려면 위 URL들을 개발용 주소로 변경해야 합니다.

## 5. 환경 변수

- `VITE_ENABLE_GOOGLE_LOGIN=true`
  - 로그인 화면에서 Google 버튼 노출
- `VITE_KAKAO_MAP_APP_KEY`
  - 추천 결과 화면의 카카오맵 SDK 로딩에 사용

## 6. 라우트 보호

`/select`, `/result`, `/recommend`, `/selected-route`, `/mypage`는 `ProtectedRoute`로 보호됩니다.
비로그인 상태면 `/login`으로 리다이렉트하고, 로그인 후 원래 경로로 복귀합니다.

## 7. 현재 코드 상태 참고

- 백엔드의 그룹/가능시간/장소투표 API 클라이언트(`src/api/group.ts`, `src/api/place.ts`)는 존재합니다.
- 다만 현재 주요 UI 플로우는 인증/추천/마이페이지 중심으로 연결되어 있습니다.
