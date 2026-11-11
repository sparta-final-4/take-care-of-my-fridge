# 상담 도메인 패키지 정리: facade/service를 기능 단위 하위 폴더로

상태: 완료 · 작성일: 2026-11-11 · 담당: zcookiez

브랜치 `refactor/chat-packages`(origin/dev 91c1c96a 위)에서 한 작업이다. 커밋은 로컬에만 있고 push·PR은 하지 않았다.
앞선 기록: [AI 상담 의도 보정 정리](work-chat-intent-corrector.md).

## 목표와 완료 기준
- 사용자 문제
  - `domain/chat/facade`에 Facade가 아닌 클래스(단어 확인·고르기·문장 만들기)가 23개 섞여 있었다.
  - `domain/chat/service`에 AI 호출·답 문장 다듬기·저장이 섞여 있었다.
- 완료 조건
  - 파일 이동만 하고 동작은 그대로 둔다. 이동 커밋에는 package·import·접근 제한자 외 변경이 없다.
  - 폴더마다 커밋 하나, 커밋마다 컴파일과 chat 테스트 통과.
  - 마지막에 `./gradlew clean compileJava compileTestJava test`와 재생 테스트(`ChatIntentReplayTest`) 통과.

## 근거와 범위
- 규칙: [백엔드 CLAUDE.md](../../take-care-of-my-fridge-be/CLAUDE.md) 3장. 기존 선례는 payment/port, notification/push, chat/push, admin/demo, 여러 도메인의 scheduler, search/config처럼 기술 역할 폴더였고, 기능 단위 폴더는 이번이 처음이라 3장에 한 줄을 보탰다.
- 변경 영역: `domain/chat/{facade,service}` → `domain/chat/{intent,safety,recommend,reply,ai}`, 같은 구조의 테스트 폴더.
- 다른 도메인 영향: `domain/search/service/RecipeDocumentReader`의 `SpicyRecipeDetector` import 한 줄(사용자 승인). `cooking`의 `ChatSessionService` 사용은 service/에 남아 그대로.
- 이번에 하지 않은 것: `config/`의 Properties 16개 정리(별도 작업), 주석 `{@link}` 정리, `IntentCorrectorTest` 분리, `ChatStreamService`→facade 의존 방향.

## 계획
| 단계 | 변경 | 검증 |
|---|---|---|
| 1 | intent/: IntentCorrector, Ask·Category·FridgeShop·Mealkit·Taste·TemperatureWordChecker | compile + chat 테스트 |
| 2 | safety/: SafetyWordChecker, HealthWordChecker, AllergyWordChecker, AllergyExclusionFinder | compile + chat 테스트 |
| 3 | recommend/: RecipeCandidateConditions, RecipeCandidateScorer, KeywordMatchClassifier, PickRule, RecommendationPicker, MealkitPicker, ShoppingProductPicker, SpicyRecipeDetector | compile + chat 테스트 |
| 4 | reply/: RecommendationPromptBuilder, RecipeDetailPromptBuilder, RecommendationReasonWriter, FridgeReplyWriter, RecommendationTextSplitter, LinkFilter, OmissionNoteFilter | compile + chat 테스트 |
| 5 | ai/: ChatIntentExtractor, OpenAiChatIntentExtractor, StubChatIntentExtractor, ChatReplyGenerator, OpenAiChatReplyGenerator, StubChatReplyGenerator, ChatReplyErrorTranslator | compile + chat 테스트 |
| 6 | CLAUDE.md 3장 한 줄, 이 기록 | clean compile + 전체 테스트 + 재생 테스트 |

facade/에 남은 것: ChatIntentFacade, ChatRecommendationFacade, ChatFridgeShopFacade, RecipeCandidateFacade, ChatContextFacade, ChatMessageQueryFacade, ChatSessionQueryFacade.
service/에 남은 것: ChatSessionService, ChatMessageService, ChatStreamService, ChatContextStore, ChatDailyLimitService, ChatStreamLockService.
테스트는 대상 클래스를 따라 옮겼다. 대상 클래스가 없는 테스트(ChatIntentReplayTest, ChatIntentEvalManualTest, EvalCorrectionSwitches, 프롬프트 파일 테스트 5개)는 제자리에 뒀다.

## AI 활용과 개발자 판단
- AI에 맡긴 작업: 이동 전 조사(클래스별 행선지, 다른 도메인 import, package-private 사용처, 스프링 설정·문자열 경로 영향), `git mv` 이동과 import 보정, 커밋별 테스트.
- 채택: 제안 구조에 TasteWordChecker(intent)와 SpicyRecipeDetector(recommend)를 보탰다. reply/는 prompt/와 나누지 않았다(세 클래스가 package-private `dDayLabel`을 같이 써서).
- 수정한 판단: 조사 단계에서는 public으로 바꿀 것이 2개(MealkitPicker.compact, IntentCorrector.withoutRecipeWords)라고 봤으나, 조사 스크립트의 정규식 오류로 IntentCorrector의 package-private 메서드를 놓쳤다. 실제로는 컴파일러가 요구한 아래 목록대로 열었다.
- 개발자가 확인할 것: 아래 public 전환 목록이 받아들일 만한지. 특히 `IntentCorrector`의 메서드 4개는 facade/`ChatIntentFacadeTest`만 쓴다. `IntentCorrectorTest`를 intent/로 분리하면 다시 package-private으로 되돌릴 수 있다.

## 구현 결과
- 이동 파일: main 33개 + test 29개(이름 변경), import·접근 제한자만 바뀐 파일 15개. 커밋 5개(ec4fd633, 32b4d785, 190e389c, bfb9b6e7, 530e4e36), 77 files changed, +160/−109 (대부분 package·import 줄).
- package·import 외에 바뀐 것(패키지가 갈라져 public으로 연 것)

| 커밋 | 대상 | 쓰는 쪽 |
|---|---|---|
| intent | `IntentCorrector.ingredientNames` | facade/ChatIntentFacade |
| intent | `IntentCorrector.withoutRecipeWords`, `categoriesOf`, `correctTastes`, `correctCategories` | facade/ChatIntentFacadeTest |
| intent | 테스트 고정값 `AskWordCheckerTest`·`CategoryWordCheckerTest`·`FridgeShopWordCheckerTest`·`MealkitWordCheckerTest`·`TasteWordCheckerTest`·`TemperatureWordCheckerTest` 클래스와 `WORDS`·`GROUPS` | facade 테스트 |
| safety | 테스트 고정값 `AllergyWordCheckerTest` 클래스 | facade/ChatRecommendationFacadeTest |
| recommend | `MealkitPicker.compact` | facade/RecipeCandidateFacade |
| recommend | 테스트 고정값 `ChatIntentFacadeTest` 클래스와 `TASTE_OPPOSITES` | recommend/PickRuleTest |
| reply | `FridgeReplyWriter.SEASONING_SHOP_HINT` | facade/ChatFridgeShopFacadeTest |
| ai | 없음 | |

- 다른 도메인: `search/service/RecipeDocumentReader` import 한 줄(recommend 커밋).
- 문서: 백엔드 CLAUDE.md 3장에 "도메인이 커져 facade/service에 역할이 다른 클래스가 많이 섞이면, 도메인 안에 기능 단위 하위 폴더를 둘 수 있다" 한 줄.
- 스프링 설정 영향 없음: `@ConfigurationPropertiesScan`은 루트 패키지, 테스트의 `@Import`·`@SpringBootTest(classes=)`는 클래스 참조, 재생·평가 테스트는 리소스 이름만 쓴다.

API·DB·프론트 변경 없음.

## 검증
| 명령·시나리오 | 실행 환경·조건 | 결과 | 근거 |
|---|---|---|---|
| 커밋마다 `./gradlew compileJava compileTestJava` + `test --tests "…domain.chat.*"` | 로컬, Docker(Testcontainers) | 5회 모두 통과 | chat 794개, 실패 0, 건너뜀 1(수동 평가 테스트) |
| `./gradlew clean compileJava compileTestJava test` (1회차) | 로컬 | 실패 1 | 2,316개 중 `cooking/PopularRecipeServiceCacheIntegrationTest` 1개 실패(Redis 순위 캐시가 두 번 집계). chat과 무관한 파일이며 단독 실행은 통과 |
| `./gradlew test` (2회차, 같은 소스) | 로컬 | 통과 | 2,316개, 실패 0, 건너뜀 2 |
| 재생 테스트 `ChatIntentReplayTest` | 전체 테스트에 포함 | 통과 | 1개, 실패 0 |

참고: 경로가 길어진 테스트(`recommend/RecipeCandidateScorerTest`의 중첩 클래스 4개)는 Gradle이 결과 XML 파일 이름을 줄여 저장한다(`TEST-o-9EFL45AC96EVE.example…`). 파일 이름으로 세면 빠진 것처럼 보이지만 실행·통과했다.

## 연결과 남은 작업
- 실제 커밋: 로컬 `refactor/chat-packages` 6개(push 전).
- 나중에 할 것
  - `config/`의 Properties 16개 정리(별도 PR).
  - `config/`·`dto/` 주석의 `{@link}`가 옮긴 클래스를 import 없이 가리켜 javadoc 링크가 끊긴다(컴파일 영향 없음). config 정리 때 같이 손본다.
  - `IntentCorrector` 테스트를 `ChatIntentFacadeTest`에서 `intent/IntentCorrectorTest`로 분리하고, 위 메서드 4개를 다시 package-private으로.
  - `ChatStreamService`가 Facade 4개를 부르는 service→facade 의존은 이번 범위 밖.
  - `cooking/PopularRecipeServiceCacheIntegrationTest`가 전체 실행에서 간헐적으로 실패하는지 지켜본다.
