# WORK-FE-SIGNUP: 회원가입

상태: 검증 대기 · 작성일: 2026-10-19 · 담당: 프론트엔드

## 목표와 완료 기준
- U-02 가입 폼에서 이메일·닉네임 중복 확인, 비밀번호 확인, 맛 선택을 처리한다.
- 가입 성공 시 U-03 완료 화면으로 이동하고 자동 로그인하지 않는다.

## 근거와 범위
- `docs/requirements/screens.md` U-02·U-03
- 백엔드 `SignUpRequest`, `SignUpResponse`, 이메일·닉네임 중복 확인 DTO

## 구현 결과
- `/signup`, `/signup/complete` 경로와 API 함수를 추가했다.
- 입력 변경 시 중복 확인을 무효화하고 가입 직전에 다시 확인 상태를 검사한다.
- 서버의 가입 시 중복 재검증 실패는 오류 안내로 표시한다.

## 검증
- `pnpm lint`, `pnpm typecheck`, `pnpm build`, `pnpm test`: 통과.
- 브라우저 375/768/1440px 화면과 빈 폼 검증 확인.
- 실제 가입·중복 확인 HTTP 응답: 백엔드 서버가 없어 미검증.
- `pnpm format:check`: 기존 파일 줄바꿈 차이로 실패.
