# WORK-FE-ACCOUNT: 내 계정

상태: 검증 대기 · 작성일: 2026-10-19 · 담당: 프론트엔드

## 목표와 완료 기준
- U-04 계정 화면에서 현재 회원 정보를 조회한다.
- 닉네임 중복 확인, 맛 선호, 비밀번호를 변경한다.

## 근거와 범위
- `docs/requirements/screens.md` U-04
- 백엔드 `MemberController` 및 `ChangeNicknameRequest`, `ChangeTastesRequest`, `ChangePasswordRequest`

## 구현 결과
- `/mypage/account`를 인증 경로로 추가하고 헤더 동선을 연결했다.
- 수정 요청에 CSRF 헤더를 포함하고 성공 후 내 정보를 다시 조회한다.
- 닉네임 확인은 값 변경 시 무효화한다.

## 검증
- `pnpm lint`, `pnpm typecheck`, `pnpm build`, `pnpm test`: 통과.
- 인증이 필요한 화면의 실제 브라우저 표시·API 왕복: 백엔드 서버가 없어 미검증.
- `pnpm format:check`: 기존 파일 줄바꿈 차이로 실패.
