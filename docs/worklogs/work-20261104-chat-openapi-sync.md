# WORK-20261104: 채팅 요청 OpenAPI 생성물 동기화

상태: 검증 대기 · 작성일: 2026-11-04 · 담당: OpenAPI 생성물

## 목표와 완료 기준
- dev에 병합된 채팅 요청 필드를 커밋된 OpenAPI 스펙과 생성 클라이언트에 반영한다.
- 이후 백엔드 PR의 OpenAPI drift 검사가 동일한 누락으로 실패하지 않는다.

## 근거와 범위
- 할인 분석 PR #561·#562의 drift 로그에서 `fridge-api.json` 19줄, `chat-message-create-request.ts` 11줄 차이를 확인했다.
- #558의 `ChatMessageCreateRequest`에 추가된 mode, selectedIngredientIds, useSelectedOnly만 반영한다.
- 채팅 백엔드 동작과 할인 분석 코드는 변경하지 않는다.

## 구현 결과
- OpenAPI 스펙에 새 필드를 추가하고 프로젝트 오프라인 생성기로 클라이언트를 다시 생성했다.

## 검증
| 명령·시나리오 | 결과 |
|---|---|
| OpenAPI JSON 파싱 | 통과 |
| `pnpm oag:generate:offline` | 통과 |
| `pnpm lint`, `pnpm format:check`, `pnpm typecheck`, `pnpm build` | 통과 |
| `pnpm test` | 28개 파일, 169개 테스트 통과 |
| GitHub OpenAPI drift | PR CI에서 확인 예정 |

## 연결과 남은 작업
- PR: `chore/openapi-chat-intent-sync` → `dev`
