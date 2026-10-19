# WORK-FE-ADMIN-MEMBERS: 관리자 회원 관리

상태: 검증 대기 · 작성일: 2026-10-19 · 담당: 프론트엔드

## 목표와 완료 기준
- A-10에서 회원 검색·역할 필터·페이지 이동을 제공한다.
- ACTIVE USER만 확인 후 관리자로 승격하고 최신 목록을 다시 조회한다.

## 근거와 범위
- `docs/requirements/screens.md` A-10
- 백엔드 `AdminMemberController`, `MemberListRequest`, `MemberSummaryResponse`, `PageResponse`

## 구현 결과
- `/admin/members`를 ADMIN 전용 경로로 추가했다.
- 승격 일시·담당자를 표시하고 비활성·관리자 계정의 승격 버튼은 비활성화했다.
- 관리자 로그인 기본 이동과 헤더 진입점을 연결했다.

## 검증
- `pnpm lint`, `pnpm typecheck`, `pnpm build`, `pnpm test`: 통과.
- 인증이 필요한 화면의 브라우저 표시·승격 API 왕복: 백엔드 서버가 없어 미검증.
- `pnpm format:check`: 기존 파일 줄바꿈 차이로 실패.
