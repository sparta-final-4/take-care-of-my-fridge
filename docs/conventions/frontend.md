# 프론트엔드 개발 규칙

상태: 기준본 안내 · 최초 정리: 2026-10-14 · 기준본으로 옮김: 2026-11-09

프론트 규칙의 기준본은 [take-care-of-my-fridge-fe/CLAUDE.md](../../take-care-of-my-fridge-fe/CLAUDE.md)입니다. 반응형·접근성, API 호출(경로 변수 `encodeURIComponent`, 조회 취소 `signal`), 에러 처리, 인증, 검증 명령이 모두 거기 있습니다. 이 문서에는 규칙을 복사하지 않고, 팀 결정만 둡니다. 규칙을 바꾸면 기준본만 고칩니다.

함께 적용: [공통 지침](../../AGENTS.md).

## 팀 결정
- 30분 동안 쓰지 않으면 로그아웃합니다([Q-01](../requirements/project-plan.md#결정-대기-목록)).
- API 클라이언트는 백엔드 `/v3/api-docs`로 생성하고, 명세보다 실제 컨트롤러·DTO·직렬화가 우선입니다(기준본 적용 메모, 8.1).

처음 공유된 규칙 원문(S1)은 git 기록에 있습니다(`git log -- docs/conventions/frontend.md`).
