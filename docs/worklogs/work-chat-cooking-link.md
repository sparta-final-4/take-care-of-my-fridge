# 요리 완료로 AI 상담 연결·종료

상태: 검증 대기 · 작성일: 2026-11-09 · 담당: zcookiez

브랜치 `feat/chat-cooking-link`(dev `7d9cd1cf`에서 만듦)에서 한 작업이다. 커밋은 로컬에만 있고 push·PR은 하지 않았다.

## 목표와 완료 기준
- 사용자 문제: 요리 완료와 상담을 잇는 코드(`chatSessionId`)는 있었지만 프론트가 값을 넘기지 않아 한 번도 동작하지 않았다. 모든 `cooking_logs.chat_session_id`가 null이었다.
  - 넘기더라도 남의 상담·끝난 상담이면 404·409로 요리 완료 자체가 실패했다.
  - 상담 종료가 요리 트랜잭션 안에 있어, 종료가 실패하면 요리 기록·재료 차감까지 되돌려졌다.
- 확인 가능한 완료 조건:
  - 진행 중 상담에서 추천한 레시피로 요리 완료하면, 프론트가 상담 ID를 넘기지 않아도 `chat_session_id`가 연결되고 커밋 뒤 상담이 COMPLETED가 되며 신호가 나간다.
  - 상담을 잇지 못하거나 종료가 실패해도 요리 완료는 성공한다.
  - 끝난 채팅창에 "○○ 요리 완료로 상담이 끝났어요"가 보인다.

## 근거와 범위
- 변경 영역: cooking(요리 완료 퍼사드·요청·서비스·이벤트), chat(상담 찾기·종료·단건 조회), 프론트 chat(끝난 상담 문구)
- DB 변경 없음
- 정한 것(사용자):
  1. 30분이 지났지만 아직 ACTIVE인 상담은 잇지 않는다 (목록에서 이미 만료로 보임)
  2. 레시피 카드(`chat_recommendations.recipe_id`)로 추천한 것만 잇는다. 밀키트 카드의 연결 레시피(`mealkit_products.recipe_id`)는 보지 않는다
  3. `chatSessionId` 칸은 남긴다. 남의 상담·끝난 상담·만료면 잇지 않고 요리 완료는 성공한다. 없으면 서버가 찾는다. 있고 유효하면 추천 여부는 보지 않는다
  4. 끝난 화면 문구는 상담 응답에 요리 정보를 붙여서 보여 준다 (`cooking_logs.chat_session_id`로 조회)
  5. 요리 결과가 실패(FAILED)여도 상담을 끝낸다

## 계획
| 단계 | 변경 | 검증 |
|---|---|---|
| 1 | 상담 찾기(트랜잭션 밖, 읽기만, 오류 나면 null), 요청 값 404·409 없앰 | `ChatSessionServiceTest`, `CookingFacadeTest` |
| 2 | 상담 종료를 커밋 뒤 이벤트로 분리, 실패는 로그만 | `CookingCompletedEventTransactionTest`(MySQL) |
| 3 | 상담 단건 조회에 `cookedRecipe` | `ChatSessionQueryFacadeTest`, `ChatSessionControllerTest` |
| 4 | 프론트 끝난 상담 문구, 클라이언트 재생성 | `format.test.ts`, lint·typecheck·build |

## AI 활용과 개발자 판단
- AI에 맡긴 작업: 지금 흐름 조사(이미 있던 연결·종료 코드, 프론트 미전달), 설계안, 구현과 테스트
- 채택한 내용과 이유
  - 상담 찾기는 `NOT_SUPPORTED`로 요리 트랜잭션 밖에서 읽는다. 안에서 읽다가 저장소 예외가 나면, 퍼사드가 잡아도 바깥 트랜잭션이 롤백 전용으로 표시돼 커밋이 실패하기 때문이다.
  - 종료(`completeSessionFromCooking`)는 `REQUIRES_NEW`로 한다. AFTER_COMMIT 리스너에서 `REQUIRED`로 부르면 이미 끝난 트랜잭션에 참여해 저장되지 않는다.
  - 리스너에서 예외를 잡는다. AFTER_COMMIT 콜백의 예외는 요청까지 올라가, 커밋된 요리 완료가 500으로 보이기 때문이다.
  - 끝난 문구는 응답에 레시피 이름을 붙인다. `end_reason` 컬럼을 두지 않아 DB 변경이 없다.
- 보류한 것: 상담 내역 목록(`ChatSessionSummaryResponse`)에 요리 정보 붙이기. 아래 "남은 작업" 참고

## 구현 결과
- 백엔드
  - `ChatSessionService.findCookingSessionId(memberId, requestedSessionId, recipeId)`
    - 요청 값이 있으면 내 진행 중 상담이고 30분이 지나지 않았을 때만
    - 없으면 진행 중 상담(30분 안)에서 이 레시피를 레시피 카드로 추천했을 때
    - 조회는 상담 1번 + 추천 1번(`ChatRecommendationRepository.existsRecipeInSession`)
  - `validateAndGetSession` 삭제 (요리 완료에서만 쓰였고, 404·409를 던지던 곳)
  - `completeSessionFromCooking`: 내 상담이고 ACTIVE일 때만 끝내며 예외를 던지지 않는다. `REQUIRES_NEW`
  - `CookingFacade`: 상담 찾기 → `saveCookingLog(memberId, request, chatSessionId)` → 재료 차감 → `CookingCompletedEvent` 발행
  - `CookingCompleteRequest.toEntity(memberId, chatSessionId)`: 요청 값이 아니라 서버가 확인한 상담 ID를 저장한다
  - `CookingCompletedEventListener`(chat/event): AFTER_COMMIT으로 상담을 끝내고, 실패하면 로그만 남긴다. 신호는 기존 `ChatSessionChangedEvent` 그대로
  - `ChatSessionResponse.cookedRecipe(recipeId, name)`. `ChatSessionQueryFacade.getSession`이 COMPLETED 상담만 `CookingLogRepository.findFirstByMemberIdAndChatSessionIdOrderByIdAsc`와 레시피 이름으로 채운다. GET `/chat-sessions/{id}`만 채우고 시작·종료 API는 null
- 프론트
  - `getSession` API, `chatQueries.endedSessionQuery`
  - `ChatRoomSection`: 진행 중이던 상담이 COMPLETED가 되면(신호 또는 직접 종료) 단건 조회를 한 번 받아 상태 배너를 `formatEndedLabel`로 보여 준다. 긴 이름은 말줄임 처리
  - 프론트에서 `chatSessionId`를 넘기는 곳은 그대로 없다 (서버가 찾음)
- 문서: `docs/api/api-spec.md` 요리 완료 처리 순서·오류, 상담 단건 조회 행, `ChatSessionResponse`

## 검증
| 명령·시나리오 | 실행 환경·조건 | 통과/실패/미실행 | 근거 |
|---|---|---|---|
| `./gradlew clean test` | 로컬, Testcontainers MySQL | 통과 | 2,248개, 실패 0, 건너뜀 1 |
| 추천한 레시피 → 연결, 커밋 뒤 COMPLETED·신호 1번 | `CookingCompletedEventTransactionTest` | 통과 | 커밋 전에는 ACTIVE·신호 없음 |
| 추천 안 한 레시피·밀키트 상품 ID·30분 지난 상담 → 연결 안 함 | 같은 테스트(실제 쿼리), `ChatSessionServiceTest` | 통과 | |
| 요청 값이 남의 상담·끝난 상담·만료 → 요리 완료 성공, 연결 안 함 | `ChatSessionServiceTest`, `CookingFacadeTest` | 통과 | 404·409 없음 |
| 상담 종료가 실패해도 요리 기록 남음, 예외가 요청으로 안 번짐 | `CookingCompletedEventTransactionTest`(종료 메서드가 예외를 던지게 함) | 통과 | |
| 요리 중 상담이 만료되면 만료 그대로, 요리 기록 남음 | 같은 테스트 | 통과 | |
| FAILED 결과도 상담 끝남 | `CookingFacadeTest`, `CookingCompletedEventTransactionTest` | 통과 | |
| 상담 응답 요리 정보 | `ChatSessionQueryFacadeTest`, `ChatSessionControllerTest` | 통과 | 직접 끝냄·레시피 삭제·진행 중은 null |
| `pnpm lint`·`typecheck`·`build`·`test` | 로컬 | 통과 | 프론트 242개 |
| `pnpm format:check` | 로컬 | 실패 | 이번에 바꾸지 않은 23개 파일(줄바꿈)만 걸림. 바꾼 파일은 통과 |
| 실제 요리 완료 → 열린 채팅창 문구 | dev 브라우저 | 미실행 | |
| 375 / 768 / 1440px 배너 | 브라우저 | 미실행 | |

## 연결과 남은 작업
- 커밋: `5d0a276a` feat(be) 자동 연결, `000d6a37` refactor(be) 커밋 뒤 이벤트, `a99600bc` feat(be) 상담 응답 요리 정보, `50de0c06` feat(fe) 끝난 상담 문구, 이 문서 커밋
- openapi 스펙(`openapi/fridge-api.json`)은 백엔드를 띄우지 않고 `ChatSessionResponse.cookedRecipe`·`CookedRecipe`만 손으로 더한 뒤 `pnpm oag:generate:offline`으로 만들었다. 백엔드를 띄워 `pnpm oag:generate`로 한 번 맞춰 봐야 한다
- 상담 내역 목록: 끝난 상담 오른쪽 칸(`ChatSessionConversation`)은 "종료" 태그와 "지난 상담은 읽기만 할 수 있어요"만 보이고 끝난 이유 문구가 없다. 목록에서도 요리 완료를 보이려면 `ChatSessionSummaryResponse`에 같은 정보를 붙여야 한다(상담 id 묶음으로 요리 기록 1번 + 레시피 이름은 기존 조회에 합침). 이번 범위에서는 하지 않았다
- `cooking_logs.chat_session_id`에는 인덱스가 없다. 단건 조회는 `member_id`도 같이 걸지만, 요리 기록이 많아지면 `(member_id, chat_session_id)` 인덱스를 검토한다
- 상담 종료와 만료가 정말 같은 순간에 겹치면 엔티티를 읽은 뒤 쓰는 사이라 COMPLETED가 덮어쓸 수 있다(버전 칸 없음). 요리 완료로 끝난 쪽이 남는 것이라 그대로 둔다
