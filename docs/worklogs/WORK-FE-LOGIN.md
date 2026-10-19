# WORK-FE-LOGIN: 로그인 화면과 API 연결

상태: 검증 대기 · 작성일: 2026-10-19 · 담당: 프론트엔드

## 목표와 완료 기준
- U-01 로그인 화면에서 이메일과 비밀번호를 검증하고 `/api/auth/login`에 제출한다.
- 성공 시 안전한 내부 경로로 이동하고, 실패 시 계정 상태 또는 인증 오류를 표시한다.

## 근거와 범위
- 화면: `docs/requirements/screens.md` U-01
- 계약: `AuthController`, `LoginRequest`, `MyInfoResponse`, `ApiResponse`
- 범위: 로그인 화면, 경로, 헤더 진입점, 공통 오류 응답 파싱

## 구현 결과
- 백엔드의 `{success,data,error}` 응답과 로그인 결과 타입을 적용했다.
- 마이페이지 진입점에서 로그인 화면으로 이동하도록 연결했다.

## 검증
- `pnpm lint`, `pnpm typecheck`, `pnpm build`, `pnpm test`: 통과.
- 브라우저 375/768/1440px: 화면 표시와 빈 폼 검증 확인.
- 실제 로그인 HTTP 응답: 백엔드 서버가 없어 미검증.
- `pnpm format:check`: 이번 변경과 무관한 기존 파일 40개의 줄바꿈 차이로 실패.

## 연결과 남은 작업
- 인증 유지·재발급·로그아웃은 후속 작업에서 구현한다.
- 실제 백엔드 실행 후 성공·실패 응답을 통합 확인한다.
