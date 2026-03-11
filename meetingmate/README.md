# MeetingMate Backend

MeetingMate는 모임 참여자들이 일정과 장소를 빠르게 조율할 수 있도록 돕는 백엔드 API 서버입니다.  
JWT 기반 인증과 Kakao OAuth2 로그인을 지원하며, 모임 생성/참여, 가능 시간 등록, 장소 후보 등록/투표, AI 기반 장소 추천 기능을 제공합니다.

## Project Overview

- 목표:
  - 모임 일정과 장소 결정을 한 번에 처리할 수 있는 REST API 제공
- 핵심 문제:
  - 단체 모임에서 일정 조율과 장소 합의가 분리되어 있어 결정 비용이 큼
- 해결 방식:
  - 인증된 사용자가 모임을 만들고 초대 코드로 참여
  - 참여자별 가능 시간 등록
  - 장소 후보 등록 및 투표
  - 조건 기반 장소 추천 API 제공

## Tech Stack

- Language:
  - Java 21
- Framework:
  - Spring Boot 4.0.0
  - Spring Security
  - Spring Data JPA
  - Spring Validation
- Authentication:
  - JWT
  - OAuth2 Client (Kakao Login)
- Database:
  - MySQL
  - H2 (test runtime)
- API / Docs:
  - REST API
  - springdoc-openapi (Swagger UI)
- Test:
  - JUnit 5
  - Mockito
  - Spring Boot Test

## Core Features

- 로컬 회원가입 / 로그인
- Kakao OAuth2 로그인 후 JWT 발급
- 모임 생성
- 초대 코드 기반 모임 참여
- 내 모임 목록 조회
- 모임 상세 조회
- 참여 가능 시간 등록 / 조회
- 장소 후보 등록
- 장소 후보 투표
- 모임 장소 확정
- AI 기반 장소 추천

## Authentication Flow

### 1. Local Login

1. 클라이언트가 `/auth/local/signup`으로 회원가입 요청
2. 클라이언트가 `/auth/local/login`으로 이메일/비밀번호 로그인
3. 서버가 JWT access token 발급
4. 클라이언트는 이후 `/api/**` 요청 시 `Authorization: Bearer {token}` 헤더 포함
5. `JwtAuthenticationFilter`가 토큰 검증 후 `UserPrincipal`을 Security Context에 설정

### 2. Kakao OAuth2 Login

1. 클라이언트가 `/oauth2/authorization/kakao`로 로그인 시작
2. Kakao 인증 완료 후 Spring Security OAuth2 콜백 처리
3. `CustomOAuth2UserService`가 사용자 조회 또는 신규 생성
4. `OAuth2SuccessHandler`가 JWT를 발급
5. 설정된 프론트엔드 redirect URL로 `?token=` 쿼리와 함께 리다이렉트

## API Response Format

모든 성공/실패 응답은 공통 래퍼를 사용합니다.

```json
{
  "success": true,
  "code": "GROUP_CREATED",
  "message": "모임이 생성되었습니다.",
  "data": {
    "id": 1,
    "name": "백엔드 스터디",
    "inviteCode": "A1B2C3"
  }
}
```

실패 예시:

```json
{
  "success": false,
  "code": "PLACE_ALREADY_VOTED",
  "message": "이미 투표한 장소입니다.",
  "data": null
}
```

## ERD (Text)

### user

- id (PK)
- provider (LOCAL / KAKAO)
- oauthId
- email
- password
- nickname

### meeting_group

- id (PK)
- name
- description
- inviteCode (unique)
- ownerId
- createdAt
- confirmedPlaceId

### group_members

- id (PK)
- group_id (FK -> meeting_group.id)
- userId
- joinedAt

### availability

- id (PK)
- group_id
- userId

### availability_slot

- id (PK)
- availability_id (FK)
- date
- startTime
- endTime

### place_candidate

- id (PK)
- groupId
- name
- category
- lat
- lng
- createdBy

### place_vote

- id (PK)
- placeId
- userId
- unique(placeId, userId)

## Main API Examples

### 1. Local Signup

- Request

```http
POST /auth/local/signup
Content-Type: application/json
```

```json
{
  "email": "user@example.com",
  "password": "password123",
  "nickname": "meetingmate"
}
```

- Response

```json
{
  "success": true,
  "code": "LOCAL_SIGNUP_SUCCESS",
  "message": "회원가입이 완료되었습니다.",
  "data": null
}
```

### 2. Create Group

- Request

```http
POST /api/groups
Authorization: Bearer {token}
Content-Type: application/json
```

```json
{
  "name": "백엔드 스터디",
  "description": "매주 월요일 저녁 정기 모임"
}
```

- Response

```json
{
  "success": true,
  "code": "GROUP_CREATED",
  "message": "모임이 생성되었습니다.",
  "data": {
    "id": 1,
    "name": "백엔드 스터디",
    "inviteCode": "A1B2C3"
  }
}
```

### 3. Join Group

- Request

```http
POST /api/groups/join
Authorization: Bearer {token}
Content-Type: application/json
```

```json
{
  "inviteCode": "A1B2C3"
}
```

### 4. Vote Place

- Request

```http
POST /api/groups/1/places/3/vote
Authorization: Bearer {token}
```

- Response

```json
{
  "success": true,
  "code": "PLACE_VOTED",
  "message": "장소 투표가 완료되었습니다.",
  "data": null
}
```

### 5. Get Recommendations

- Request

```http
POST /api/recommendations
Authorization: Bearer {token}
Content-Type: application/json
```

```json
{
  "meetingType": "친구모임",
  "category": "CAFE",
  "preferences": ["조용한", "대화하기 좋은"]
}
```

## Validation Rules

- 모임 이름: 필수, 최대 50자
- 모임 설명: 최대 255자
- 초대 코드: 필수, 정확히 6자
- 이메일: 필수, 이메일 형식
- 비밀번호: 필수, 8자 이상 100자 이하
- 닉네임: 필수, 2자 이상 30자 이하
- 장소 이름: 필수, 최대 100자
- 장소 카테고리: 필수, 최대 30자
- 위도/경도: 필수
- 추천 선호 조건: 최소 1개, 최대 10개

## Exception Strategy

`@RestControllerAdvice` 기반 전역 예외 처리로 응답 형식을 통일했습니다.

- 비즈니스 예외:
  - `GROUP_NOT_FOUND`
  - `GROUP_ACCESS_DENIED`
  - `PLACE_NOT_FOUND`
  - `PLACE_NOT_IN_GROUP`
  - `PLACE_ALREADY_VOTED`
- 검증 예외:
  - `MethodArgumentNotValidException` -> `VALIDATION_ERROR`
- 인증 / 인가 예외:
  - `AuthenticationException` -> `UNAUTHORIZED`
  - `AccessDeniedException` -> `ACCESS_DENIED`
- 기타:
  - `IllegalArgumentException` -> `BAD_REQUEST`
  - `IllegalStateException` -> `ILLEGAL_STATE`
  - 미처리 예외 -> `INTERNAL_SERVER_ERROR`

## Test Coverage

현재 포함된 핵심 테스트:

- `RecommendationControllerTest`
  - 추천 API 응답 래퍼 검증
- `RecommendationServiceTest`
  - OpenAI 클라이언트 mock 기반 추천 서비스 검증
- `GroupServiceTest`
  - 모임 생성 성공
  - 중복 모임 참여 차단
- `PlaceServiceTest`
  - 다른 그룹 장소 투표 차단
  - 중복 투표 차단

## How To Run

### 1. Required Environment Variables

```bash
export JWT_SECRET=your-jwt-secret-with-32-bytes-minimum
export DB_URL=jdbc:mysql://localhost:3306/meetingmate?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
export DB_USERNAME=your-db-user
export DB_PASSWORD=your-db-password
export KAKAO_CLIENT_ID=your-kakao-client-id
export KAKAO_CLIENT_SECRET=your-kakao-client-secret
export OPENAI_API_KEY=your-openai-api-key
export APP_FRONTEND_REDIRECT_URL=http://localhost:5173/oauth/callback
```

### 2. Run Application

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

### 3. Run Tests

```bash
./gradlew test
```

### 4. Swagger

애플리케이션 실행 후 아래 경로에서 API 문서를 확인할 수 있습니다.

- [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

## Configuration Profiles

- `application.yaml`
  - 공통 설정 및 환경변수 placeholder
- `application-local.yaml`
  - 로컬 개발 설정
- `application-prod.yaml`
  - 운영 배포 설정
- `application-test.yaml`
  - 테스트 전용 설정 (H2, dummy OAuth2 값)

## Deployment Plan

현재는 미배포 상태이며, 다음 순서로 배포할 계획입니다.

1. MySQL 운영 DB 준비
2. 환경변수 기반 시크릿 주입
3. Spring Boot JAR 빌드
4. EC2 또는 Render/Railway에 애플리케이션 배포
5. Nginx 또는 플랫폼 기본 라우팅으로 HTTPS 연결
6. 운영 프로필(`prod`)로 실행
7. Swagger 접근 제한 또는 비활성화

배포 예시 명령:

```bash
./gradlew clean bootJar
java -jar build/libs/meetingmate-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

## Current Improvement Backlog

- `User` 테이블명 예약어 이슈 정리
- `PlaceService.createPlace`, `getPlaces`에도 그룹 멤버 검증 적용
- Controller validation 실패 케이스 테스트 추가
- README용 실제 ERD 이미지 및 아키텍처 다이어그램 추가

## Summary

MeetingMate Backend는 인증, 모임 관리, 일정 조율, 장소 후보 선정, 투표, 추천 기능을 하나의 흐름으로 제공하는 REST API 서버입니다.  
현재 포트폴리오 제출 가능한 수준의 구조 정리, 공통 응답 포맷, 전역 예외 처리, 입력 검증, 핵심 서비스 테스트를 갖춘 상태입니다.
