# WORK-FE-AUTH-SESSION: 쿠키 인증 세션

상태: 검증 대기 · 작성일: 2026-10-19 · 담당: 프론트엔드

## 목표와 완료 기준
- 앱 시작 시 내 정보를 조회하고 필요하면 토큰을 재발급한다.
- 보호 API 401에서 재발급 후 한 번 재시도하고, 로그아웃과 30분 미사용 종료를 제공한다.

## 근거와 범위
- 백엔드 `AuthController`, `CsrfController`, `MemberController`, `SecurityConfig`
- 프론트엔드 `CLAUDE.md` 8.3~8.4
- 쿠키는 브라우저가 관리하며 CSRF 헤더는 `/api/auth/csrf` 응답에서 가져온다.

## 구현 결과
- 세션 상태, 시작 시 내 정보 조회, 인증 경로 보호 컴포넌트를 추가했다.
- 재발급·로그아웃과 보호 API의 401 재시도를 연결했다.
- 헤더에 로그인 회원의 로그아웃 동작을 추가했다.

## 검증
- `pnpm lint`, `pnpm typecheck`, `pnpm build`, `pnpm test`: 통과.
- 실제 쿠키·CSRF 왕복: 백엔드 서버가 없어 미검증.
- `pnpm format:check`: 기존 파일 줄바꿈 차이로 실패.

## 연결과 남은 작업
- 계정·관리자 화면에서 `RequireAuth`를 적용한다.
- 실제 백엔드와 재발급, 로그아웃, 만료 시나리오를 통합 확인한다.
