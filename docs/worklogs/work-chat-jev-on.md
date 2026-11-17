# WORK-CHAT-JEV-ON: 상담 의도 Jev on 모드 연결

상태: 정리 단계·d2·Jev 단독 완료 (Jev 단독 의도 판단, REFINE은 추천 안의 이어짐, AI 실패 시 서버 답, 아래 "정리 단계"·"d2"·"Jev 단독") · 작성일: 2026-11-17 · 담당: 상담(chat)

## 목표와 완료 기준
- 사용자 문제: 상담 의도를 OpenAI + 단어 보정(IntentCorrector)으로 정하고 있어, 보정 단어 목록이 계속 늘어난다. Jev(분류 모델)가 의도·요청 종류·분류·맛을 정하게 바꿔 단어 보정을 지울 수 있는 길을 연다.
- 완료 조건: `app.chat.jev.mode`(off / shadow / on)로 고를 수 있고, on이면 Jev 판단으로 의도를 정하고(못 쓰면 보정 없는 OpenAI), off·shadow는 지금과 같다. 기존 테스트(ChatIntentReplayTest 포함)가 고치지 않고 통과한다.

## 근거와 범위
- 설계: [docs/testing/jev-on-design.md](../testing/jev-on-design.md), 넓은 요청 프롬프트: [broad-request-llm-check.md](../testing/broad-request-llm-check.md) v2, Jev 평가: [eval-results/jev-v4](../testing/eval-results/jev-v4/)
- 변경 영역: 백엔드 chat 도메인(ai·config·dto·facade·intent·service), infra/typesafe, application.yml, prompts
- 사용자가 정한 것: B안(Jev 실패·confidence < 0.8이면 보정 없는 OpenAI, 서비스 규칙만), 기준 0.8 상수, 설계 문서의 경계·캐시 키·OpenAI 칸 결정, 밀키트·냉장고 범위·세일은 지금 단어 체커, 이번엔 아무것도 지우지 않음
- 이 브랜치는 배포된 적이 없어 `CHAT_JEV_ENABLED` → `CHAT_JEV_MODE`를 호환 코드 없이 바꿨다

## 계획
| 단계 | 변경 | 검증 |
|---|---|---|
| 1 | 설정 off·shadow·on | ChatJevPropertiesTest, Jev 그림자 테스트 |
| 2 | 넓은 요청 요리 이름 (비슷한 요리 코드를 종류로 넓힘) | SimilarDishFinder·Store·Generator 테스트 |
| 3 | on 모드: JevIntentResolver, facade 연결, 보정 단계 선택, 비교 로그 결정 칸 | ChatIntentFacadeJevOnTest, JevIntentShadowTest, JevComparisonLogTest |

## AI 활용과 개발자 판단
- AI에 맡긴 작업: 설계 문서대로 구현, 테스트 작성, 줄 수 집계
- 채택: 비슷한 요리 클래스를 복사하지 않고 `DishListKind`(SIMILAR / BROAD)로 넓힘 — 다른 것은 프롬프트와 캐시 키 접두어뿐이고 캐시·정리·실패 처리가 같아서
- 채택: 보정 건너뛰기는 스위치를 바꾸지 않고 `IntentCorrector.correct(input, result, Steps)`로 고름 (off·shadow는 `Steps.ALL`)
- 설계와 달라진 점: `ChatIntentResolution.Source.JEV`는 더하지 않았다(쓰는 곳이 없고 결정은 비교 로그 `decision` 칸에 남는다). on에서 OpenAI 의도 판단 자체가 실패하면 지금 실패 처리를 쓰고 `decision=OPENAI_FAILED`로 남긴다(지시서 목록에 없던 값)
- 개발자 확인 필요: on 모드 실제 키 평가(c2), 넓은 요청 캐시 키가 메시지 전체가 되는 경우(preferenceText 없음)

## 구현 결과
- 설정: `ChatJevProperties(Mode mode)`, `app.chat.jev.mode: ${CHAT_JEV_MODE:off}`. shadow = 지금 그림자 모드
- 넓은 요청 요리 이름: `DishListKind`, `SimilarDishStore.find/save(memberId, kind, text)`(키 `chat:broad-dishes:v1:{공백 제거·소문자}`, TTL 같은 설정), `SimilarDishGenerator.generate(memberId, kind, text)`, `OpenAiSimilarDishGenerator`가 종류별 프롬프트, `SimilarDishFinder.findBroad`, `prompts/chat-broad-dishes.txt`
- on 모드
  - `JevIntentShadow`: `on()`, `await(run)`(Jev 시작부터 TypeSafe 제한 시간까지, 1차·필요한 2차까지 성공한 판단만), `compareWhenDone(..., JevDecision)`
  - `JevIntentResolver`(새, intent): Jev 의도·맛 + 요청 종류(경계 규칙) + OpenAI가 꺼낸 값으로 IntentResult
  - `ChatIntentFacade`: on이면 OpenAI 뒤 Jev를 기다려 confidence ≥ 0.8이면 Resolver, 아니면 OpenAI 결과. 둘 다 `Steps.SERVICE_RULES`
  - `IntentCorrector.Steps`: SERVICE_RULES는 레시피 자세히 대상, 요리 이름이 메시지에 있는지, 다른 맛 지키기, 말한 맛만. 상관없는 질문 되살리기·냉장고/사기 말·밀키트 요청·이름만/추천 시도·분류 옮기기·요리 이름 채우기(첫 키워드, ○○ 같은)·맛 단어·분류 단어는 건너뜀
  - `JevComparisonLog`: 끝에 `decision`, `jevUsedKind`, `jevUsedValues` 칸 (값 안 공백 제거)
- 늘어난 줄 수 (2026-11-17, 커밋 e8570982 이후)
  - main: +534 / −95 (순 +439). 새 파일 JevIntentResolver 133, JevDecision 42, DishListKind 30, 프롬프트 8
  - test: +590 / −33. 새 파일 ChatIntentFacadeJevOnTest 389

## 검증
| 명령·시나리오 | 실행 환경·조건 | 통과/실패/미실행 | 근거 |
|---|---|---|---|
| `./gradlew clean compileJava compileTestJava` | 로컬 | 통과 | BUILD SUCCESSFUL |
| `./gradlew test --tests '*.domain.chat.*'` | 로컬, clean 뒤 | 통과 | 937개, 건너뜀 3, 실패 0 (ChatIntentReplayTest 포함) |
| `./gradlew test` | 로컬 (컨테이너 테스트 포함) | 보고에 적음 | |
| on 모드 실제 키 평가 (c2) | - | 미실행 | 다음 작업 |

## 연결과 남은 작업
- 커밋: 56f7d737(설정), 16009d8d(넓은 요청 요리 이름), on 모드 연결 커밋 (push 안 함)
- 다음: c2 평가 — `ChatIntentEvalManualTest`에 Jev 모드를 주고 on 모드 전체 흐름을 all-on(120)과 비교 (설계 문서 8절)
- 결정 대기: on을 기본으로 바꿀 시점, 되돌림 비율을 본 뒤 IntentCorrector 고치기 단계·단어 체커 삭제(설계 문서 9절)

## 정리 단계: Jev를 유일한 의도 판단으로 (2026-11-17, 브랜치 `refactor/chat-intent-cleanup`)

jev-on-v5가 통과해(확인용 45문장 차이 0, 기본 세트의 엉뚱한 결과는 실패로 인정한 F11뿐) Jev on을 유일한 동작으로 만들고, on에서 쓰지 않는 의도 보정 코드를 지웠다.
off 모드 T04·N03 저하는 10회 재확인에서 흔들림으로 판정했다([jev-on-eval.md](../testing/jev-on-eval.md)).

### 지금 동작
메시지 → Jev와 OpenAI 의도 판단을 함께 시작 → Jev 의도 confidence ≥ 0.8이면 Jev 판단 + OpenAI가 꺼낸 값으로 의도 결과(JevIntentResolver),
아니면(키 없음·실패·제한 시간·confidence 미만) 보정 없는 OpenAI 결과(넓은 요청이면 넓은 요청 경로) → 서버 서비스 규칙(IntentCorrector) → "상담 의도 Jev 결정" 로그.

### 지운 것
| 묶음 | 지운 것 |
|---|---|
| 모드 | `app.chat.jev.mode`(`CHAT_JEV_MODE`), `ChatJevProperties`, off·shadow 분기, `JevIntentShadow.off()·on()`, 그림자 비교만 하던 `compareWhenDone`(결정 없음), `JevDecision.SHADOW` |
| 결정 로그 칸 | `intentMatch`, `tasteMatch`, `categoryMatch` (OpenAI와 Jev를 비교하던 칸) |
| IntentCorrector 단계 | 상관없는 질문 되살리기, 냉장고 보기·사기 말, 밀키트 요청, 이름만 말하기·추천 시도, 요리 이름·분류 키워드 확인(분류 옮기기·첫 키워드·○○ 같은 요리 채우기), 맛 단어, 분류 단어, `Steps`(ALL/SERVICE_RULES) |
| 그 밖 main | `CategoryWordChecker`, `AskWordChecker`의 달라는 말·인사·밀키트 요청, `FridgeShopWordChecker`의 의도 판단(check·cooks·asksGeneral), `Source.DISH_TRY`(추천 시도 카드 0장 → 일반 답), `IntentResult.withIntent`, `Corrected.mealkitRecommend·dishTry` |
| 설정 | 보정 스위치 `app.chat.corrections`·`ChatCorrectionProperties`(남은 규칙은 늘 켬), `chat-words.yml`의 분류 묶음(category)·음식 말(food)·달라는 말·밀키트 요청·인사·레시피 말(ask), 냉장고 보기·사기·요리·묻는 말(fridge-shop의 fridge·shop·cook·general) |
| 테스트 | `ChatJevPropertiesTest`, `ChatStreamJevShadowTest`, `CategoryWordCheckerTest`, `EvalCorrectionSwitches(Test)`, off 모드 보정에 기대던 facade 테스트 9개·IntentCorrectorTest 보정 단계 테스트 45개, AskWordChecker·FridgeShopWordChecker의 지운 메서드 테스트, ChatStreamFacadeTest의 DISH_TRY 테스트 1개, 평가 옵션 `OPENAI_INTENT_EVAL_OFF`·`_JEV_MODE`·`_SKIP_TASTE_STEPS` |

이름 바꿈(동작은 같다): `JevIntentShadow` → `JevIntentRunner`, `JevComparisonLog` → `JevDecisionLog`(로그 이름 "상담 의도 Jev 비교" → **"상담 의도 Jev 결정"**, `openai…` 칸 → `final…` 칸), `TypeSafeShadowExecutor` → `TypeSafeExecutor`(스레드 이름 `typesafe-jev-`).

### 파일 수·줄 수 (전 4d96b950 → 후)
| 범위 | 전 | 후 |
|---|---|---|
| main `domain/chat` | 133개, 14,224줄 | 130개, 13,248줄 (−976) |
| main `domain/chat/intent` | 8개, 1,546줄 | 7개, 796줄 (−750) |
| `IntentCorrector` | 768줄 | 229줄 |
| test `domain/chat` | 91개, 21,622줄 | 86개, 20,244줄 (−1,378) |
| `config/chat-words.yml` | 112줄 | 58줄 |
| git diff (main / test) | | +199 −1,245 / +520 −1,898 |

### 남긴 것과 이유
| 남긴 것 | 쓰는 곳 | 이유 |
|---|---|---|
| `IntentCorrector`: 레시피 자세히 대상(findTarget), 요리 이름 확인(keepSpokenDishOnly·dishInMessage), 다른 맛 지키기, 말한 맛만, 재료 이름 찾기 | ChatIntentFacade, JevIntentResolver | 서비스 규칙 (AI가 맞아도 서비스가 정한 약속) |
| `AskWordChecker.asks`, `ask.question` | ChatIntentFacade (OpenAI 의도 판단 실패 처리) | 선택 재료가 있어도 묻는 말이면 추천으로 바꾸지 않는다. on에서도 쓰는 길이다 |
| `FridgeShopWordChecker`의 범위·할인·양념 말, `fridge-shop`의 imminent·seasoning·sale | ChatIntentFacade.lookupOf | 냉장고 보기 범위·할인 상품 조회 (다음에) |
| `MealkitWordChecker`, `mealkit` 단어 | ChatRecommendationFacade, ChatIntentFacade(결정 로그·할인 종류), JevIntentResolver(특정 분류 경계) | 밀키트만 보여 줄지 (다음에) |
| `TasteWordChecker`, `taste`·`taste-opposites` | 다시 추천 맛 따르기, 다른 맛 지키기, 말한 맛만, MealkitWordChecker 부정어 | 서비스 규칙 (다음에) |
| `TemperatureWordChecker.wanted`, `temperature` | ChatRecommendationFacade | 온도 말로 카드 거르기 (다음에) |
| `CorrectionInput` 칸 모두 | 회원·상담(로그), 메시지(요리 이름 확인·맛 규칙), 분류 목록(Resolver의 분류 이름 처리), 대화 문맥(자세히 대상·말한 맛만) | 안 쓰는 칸이 없다 |
| `prompts/chat-intent.txt` | OpenAI 의도 판단 | 이번 범위 밖(바꾸지 않음) |

### 의도 리플레이
보정을 지워 기준값 339줄 중 79줄이 달라졌다. 다시 기록하기 전에 지우기 전 코드(4d96b950)로 확인했다.
- 지우기 전 코드의 "서비스 규칙만" 결과가 새 결과와 339줄 모두 같다 → 달라진 이유는 모두 지운 보정 단계다.
- 보정 스위치를 하나씩 끈 결과로 줄마다 원인을 갈랐다: 분류 단어 44, 분류 키워드 옮기기(끌 수 없던 부분) 17, 요리 이름 채우기 6, 상관없는 질문 되살리기 5, 맛 단어 4, 냉장고 보기·사기 말 4 (한 줄은 두 단계).
- 줄마다 문장·이전 값·새 값·원인: [eval-results/cleanup-replay/replay-diff.csv](../testing/eval-results/cleanup-replay/replay-diff.csv). 기준값은 별도 커밋으로 다시 기록했다.

### 평가 (정리 뒤, 각 3회)
| 세트 | 비교 | 엄격 | 맛 반대 허용 |
|---|---|---|---|
| 기본 122문장 | jev-on-v5 → cleanup-main | 108 → 109 | 114 → 116 |
| 확인용 45문장 | jev-on-holdout-v2 → cleanup-holdout | 41 → 41 | 42 → 42 |

- 문장 단위로 다수결이 바뀐 문장: 기본 T04(1/3 → 3/3, 둘 다 OpenAI 되돌림), R17(2/3 → 3/3), F12(2/3 → 3/3, OpenAI 되돌림), T15(1/3 → 2/3), T01(2/3 → 1/3, Jev가 피할 맛에 순한맛). 확인용 H34(3/3 → 2/3), H36(0/3 → 1/3).
- 모두 같은 결정 경로(Jev 또는 OpenAI 되돌림)에서 AI 답이 회차마다 달라진 흔들림이다. 코드 차이는 없다(리플레이로 서비스 규칙 경로가 같음을 확인).
- 의도 결정 중앙값은 1,294ms → 1,696ms로 늘었는데, OpenAI 의도 호출 평균이 1,388ms → 1,903ms로 느려진 탓이다(코드 경로는 같다).
- 결과: [eval-results/cleanup-main](../testing/eval-results/cleanup-main/), [eval-results/cleanup-holdout](../testing/eval-results/cleanup-holdout/).

### 운영 설정 영향
- `CHAT_JEV_MODE` 환경 변수가 없어진다(남아 있어도 읽지 않는다). 배포 설정에서 지운다.
- 운영에는 Parameter Store `/imhungry/<APP_ENV>/TYPESAFE_API_KEY`(→ `TYPESAFE_API_KEY`)가 필요하다. **키가 없으면 앱은 시작되지만** 시작할 때 경고를 한 번 남기고, 모든 메시지를 Jev 없이 보정 없는 OpenAI 결과로 정한다. 단어 보정을 지웠으므로 이때 정확도는 예전 off 모드(all-on)보다 낮다.
- 보정 스위치 `app.chat.corrections.*`도 없어졌다(배포 설정에 있었다면 지운다).
- 결정 로그 이름이 "상담 의도 Jev 비교"에서 "상담 의도 Jev 결정"으로, 칸 `openai…`이 `final…`로 바뀌고 비교 칸 셋이 빠졌다. 이 로그를 읽는 감사 페이지·대시보드가 있으면 맞춰야 한다(저장소 안에서 읽는 코드는 없다).
- API·ErrorCode·DTO 변경 없음. 프론트 영향 없음(상담 응답 모양 같음). `Source.DISH_TRY`를 지워 "요리 이름만 말했는데 카드 0장이면 일반 답"으로 돌리던 길이 없어졌다(그 출처를 만들던 추천 시도 단계를 지웠다).

### 다음 단계
1. ~~d2: REFINE을 ChatIntent와 OpenAI 프롬프트에서 없애고 추천의 이어짐 항목으로 바꾸기~~ (아래 "d2"에서 함)
2. 그다음: `MealkitWordChecker`·`TasteWordChecker`·`FridgeShopWordChecker` 나머지·`TemperatureWordChecker.wanted`를 Jev 질문으로 옮기기
3. F11 "슬슬 처리해야 할 거 있어?" 냉장고 의도
4. 김피탕 같은 줄임말 별칭 데이터 (비슷한 요리 AI가 모르는 요리)
5. dev DB에서 끝에서 끝(카드) 확인

## d2: REFINE을 추천 안의 이어짐으로 (2026-11-17, 브랜치 `refactor/chat-intent-cleanup`)

REFINE을 의도(`ChatIntent`)에서 지우고 추천 안의 **이어짐**(직전 추천의 조건을 이어받아 일부만 바꾸는 추천인지, boolean)으로 바꿨다. 동작은 그대로다: 예전에 REFINE으로 가던 메시지는 "추천 + 이어짐 예"로 같은 처리(직전 조건 합치기·이번 말의 맛 따르기·직전 카드 빼기)를 받는다. 설계: [jev-on-design.md](../testing/jev-on-design.md) 11절.

### 0단계 확인 (밖으로 나가거나 저장되는지)
| 찾은 것 | 쓰는 곳 | 밖으로 나감·저장 |
|---|---|---|
| API 응답 DTO·SSE 이벤트 | `ChatRecommendationsEventResponse` 등 응답 DTO에 의도 칸 없음. `ChatStreamFacade`는 의도로 답 방식만 고른다 | 아니오 |
| 프론트 | `take-care-of-my-fridge-fe/src`·`openapi`에 `REFINE`·intent 없음 | 아니오 |
| DB | `ChatMessage`(role·content·filtered)·`ChatRecommendation`(recipe·product·순서·설명)에 의도 칸 없음 | 아니오 |
| Redis | `chat:context:{세션}`은 `LastRecommendation`(조건 스냅숏·카드 ID)만, `chat:similar-dishes`·`chat:broad-dishes`·`chat:limit`·`chat:streaming`에도 의도 없음 | 아니오 |
| "REFINE" 문자열을 읽는 곳 | OpenAI 구조화 출력(`IntentResult` 스키마의 intent enum), 평가·리플레이 CSV·평가 세트(테스트), 로그 | 로그·테스트·OpenAI 응답만 |
| 문서 | `docs/api/chat-api.md`(이벤트 순서 표), `docs/architecture/domains/chat.md`(의도 종류 표) | 설명 글만 (이번에 고침) |

### 바꾼 곳
| 파일 | 메서드 | 바꾼 것 |
|---|---|---|
| `dto/IntentResult` | 칸 `followsPrevious`, `follows()`, `withFollows()`, `withNamed()`·`withLookup()` | 이어짐 칸(OpenAI 스키마 필수 칸). 추천일 때만 뜻이 있다. 레시피 자세히 대상을 못 찾아 바꾼 추천·냉장고 보기·사기는 아니오 |
| `dto/ChatIntentResolution` | 칸 `follows`, `recommendFollowing()`(예전 `refine()`), `recommends()` | 결과에 이어짐. `recommends()`는 RECOMMEND만 |
| `ai/JevIntentClassifier` | `serviceIntent()`, 새 `follows()` | 추천 + follows_previous ≥ 0.45를 REFINE으로 바꾸지 않고 이어짐으로 (조건 그대로). 2차 묻는 의도는 RECOMMEND만 |
| `ai/JevFirstQuestions` | `read()` | `serviceIntent` 인자 정리 (질문 글은 그대로) |
| `intent/JevIntentResolver` | `resolve()`, `resolveBroadFallback()`, `broad()`, `build()` | Jev 이어짐을 결과에 넣음. 되돌림의 넓은 요청 경로는 OpenAI 이어짐 그대로. `RECIPE_INTENTS`(RECOMMEND·REFINE) → RECOMMEND |
| `intent/IntentCorrector` | `correct()` | 새 서비스 규칙: 직전 조건이 없으면 이어짐 아니오. 요리 이름 확인·다른 맛 지키기는 RECOMMEND(이어짐 포함), 말한 맛만은 이어짐이 아닌 추천만 (예전 RECOMMEND·REFINE 구분과 같다) |
| `facade/ChatIntentFacade` | `decide()`, `recommendFollowing()`(예전 `refine()`), `resolve()` 로그 | `case REFINE` → `case RECOMMEND`에서 이어짐이면 직전 조건 합치기. 직전 조건 없음 처리는 IntentCorrector로 옮김. "상담 의도" 로그에 이어짐 |
| `facade/ChatStreamFacade` | `planReply()` | `intent == REFINE` → `follows()` (직전 카드 빼기) |
| `ai/JevDecisionLog` | `FinalSide`, `line()` | 결정 로그에 `finalFollows` 칸 (Jev 확률은 원래 있던 `jevFollowsPrevious`) |
| `dto/ChatIntent` | | `REFINE` 삭제 |
| 주석만 | `ChatRecommendationFacade.recommend`, `ChatIntentExtractor`, `JevIntentJudgement`, `JevSecondQuestions`, `RefineCondition` | REFINE·다시 추천 → 이어짐 |

`RefineCondition`(이어짐에서 지울 조건 종류)과 `mergeRefine`은 이름을 그대로 뒀다(OpenAI `removed` 칸 값과 맞물림).

### OpenAI 프롬프트 변경
- `chat-intent.txt`(3,038 → 3,132자, 기준 3,300 안): `- followsPrevious: RECOMMEND이고 [직전 추천]의 조건(맛, 재료, 종류)을 이어받으면서 일부만 바꿔 달라는 말이면 true, 그 밖은 false` 한 줄을 더했다. 첫 질문 프롬프트에는 원래 REFINE이 없었다.
- `chat-intent-followup.txt`(1,164 → 1,282자): REFINE 항목을 "followsPrevious true(이어짐)" 항목으로 바꾸고 뜻을 Jev follows_previous 질문과 같은 두 문장으로 썼다. 예전 REFINE 정의의 단어 예시(`"다른", "또", "말고 다른"`)와 문장 예시 한 줄(평가 세트 문장)은 지웠고 새 단어 예시는 넣지 않았다. 맨 아래 예시 두 줄은 `→ REFINE`을 `→ 이어짐`으로만 바꿨다. "그 밖은 … (followsPrevious false)".
- 구조화 출력 스키마는 `IntentResult`에서 만들어져 `followsPrevious`가 필수 칸으로 더해지고 intent enum에서 REFINE이 빠졌다. 후속 규칙 길이 테스트 기준을 1,200 → 1,300자로 올렸다.

### 의도 리플레이
기준값 339줄 중 33줄이 달라졌다(U01~U05·U12~U15·U17·U18 × 3회). 모두 의도 칸만 `REFINE` → `RECOMMEND+FOLLOWS`이고 카드 조건 칸(맛·분류·키워드·재료·시간 등)은 모두 같다. 그래서 별도 커밋으로 다시 기록했다. 줄마다: [eval-results/d2-replay/replay-diff.csv](../testing/eval-results/d2-replay/replay-diff.csv). 리플레이 입력의 예전 `REFINE`은 추천 + 이어짐 예로 읽는다.

### 평가 (각 3회, 의도 일치 = 의도와 이어짐이 둘 다 맞음)
| 세트 | 비교 | 엄격 | 맛 반대 허용 |
|---|---|---|---|
| 기본 122문장 | cleanup-main → d2-main | 109 → 107 | 116 → 114 |
| 확인용 45문장 | cleanup-holdout → d2-holdout | 41 → 41 | 42 → 42 |

다수결이 바뀐 문장은 둘이다.
- R17 "주식으로 먹을 거 추천해줘" 3/3 → 1/3: Jev 의도 confidence가 0.80·0.82·0.84 → 0.67·0.80·0.71로 기준 0.8 언저리에서 흔들려 두 회차가 OpenAI로 되돌았고, OpenAI는 전·후 모두 OFF_TOPIC이다(jev-on-v5에서도 0.74로 한 번 되돌림). 직전 추천이 없는 문장이라 이번 변경과 상관없다.
- T15 "맵고 따뜻한 국물 레시피 추천해줘" 2/3 → 1/3: Jev 요청 종류가 넓은 요청·특정 분류 사이에서 흔들림(jev-on-v5도 1/3).
- 그 밖 회차 수만 바뀐 문장: N01(1/3 → 0/3, 같은 흔들림), 확인용 H07(0/3 → 1/3)·H36(1/3 → 0/3).
- Jev 요청은 바뀌지 않았다: Jev 입력 토큰이 문장마다 cleanup과 같다(합 251,540 / 356,994).

이어지는 말 묶음 (Jev로 모두 정함, d2 의도는 3회 모두 같음):

| id | 맥락 | 문장 | cleanup 엄격·허용 | d2 엄격·허용 | d2 의도 |
|---|---|---|---|---|---|
| U01 | jjigae | 좀 더 매운 걸로 | 0/3·3/3 | 0/3·3/3 | RECOMMEND+FOLLOWS |
| U02 | jjigae | 다른 거 없어? | 3/3·3/3 | 3/3·3/3 | RECOMMEND+FOLLOWS |
| U03 | jjigae | 국물 말고 볶음으로 | 3/3·3/3 | 3/3·3/3 | RECOMMEND+FOLLOWS |
| U04 | jjigae | 매운 거 말고 | 0/3·3/3 | 0/3·3/3 | RECOMMEND+FOLLOWS |
| U05 | jjigae | 시간 상관없이 | 3/3·3/3 | 3/3·3/3 | RECOMMEND+FOLLOWS |
| U06 | jjigae | 두 번째 거 어떻게 만들어? | 3/3·3/3 | 3/3·3/3 | RECIPE_DETAIL |
| U07 | jjigae | 아까 그 순두부찌개 시간 얼마나 걸려? | 3/3·3/3 | 3/3·3/3 | RECIPE_DETAIL |
| U08 | single | 그거 재료 뭐 들어가? | 3/3·3/3 | 3/3·3/3 | RECIPE_DETAIL |
| U09 | jjigae | 디저트도 추천해줘 | 3/3·3/3 | 3/3·3/3 | RECOMMEND |
| U10 | jjigae | 냉장고에 두부 남았어? | 3/3·3/3 | 3/3·3/3 | FRIDGE |
| U11 | jjigae | 고마워 | 3/3·3/3 | 3/3·3/3 | GENERAL |
| U12 | jjigae | 김치찌개 말고 다른 찌개 | 3/3·3/3 | 3/3·3/3 | RECOMMEND+FOLLOWS |
| U13 | dessert | 다른 디저트 | 3/3·3/3 | 3/3·3/3 | RECOMMEND+FOLLOWS |
| U14 | dessert | 디저트 말고 국 | 3/3·3/3 | 3/3·3/3 | RECOMMEND+FOLLOWS |
| U15 | dessert | 매운 거 말고 | 3/3·3/3 | 3/3·3/3 | RECOMMEND+FOLLOWS |
| U16 | kimpi | 디저트 추천해줘 | 3/3·3/3 | 3/3·3/3 | RECOMMEND |
| U17 | kimpi | 매운 건 빼줘 | 3/3·3/3 | 3/3·3/3 | RECOMMEND+FOLLOWS |
| U18 | salty | 매운 건 빼줘 | 3/3·3/3 | 3/3·3/3 | RECOMMEND+FOLLOWS |
| H12 | pasta | 좀 더 가벼운 걸로 다시 골라줘 | 3/3·3/3 | 3/3·3/3 | RECOMMEND+FOLLOWS |
| H13 | braise | 이번엔 짠맛 대신 단맛 나는 걸로 바꿔줘 | 3/3·3/3 | 3/3·3/3 | RECOMMEND+FOLLOWS |
| H38 | pasta | 이번엔 좀 더 매콤한 걸로 | 3/3·3/3 | 3/3·3/3 | RECOMMEND+FOLLOWS |
| H39 | braise | 재료는 두부로 바꿔서 다시 | 3/3·3/3 | 3/3·3/3 | RECOMMEND+FOLLOWS |
| H40 | pasta | 그건 됐고 오늘 저녁 반찬 추천해줘 | 3/3·3/3 | 3/3·3/3 | RECOMMEND |
| H41 | braise | 따로 볶음밥 레시피도 하나 알려줘 | 3/3·3/3 | 3/3·3/3 | RECOMMEND |

(U01·U04 엄격 0/3은 Jev 맛 반대(매운맛 ↔ 순한맛)로 cleanup과 같다.)

**흔들림이 아닌 차이 하나 (최종 결과에는 영향 없음)**: OpenAI 원래 답만 보면 직전 추천이 있는 기본 세트 18문장의 의도 + 이어짐이 cleanup 18/18 → d2 17/18이다. U02 "다른 거 없어?"를 예전 OpenAI는 3/3 REFINE으로, 지금은 3/3 추천 + 이어짐 아니오로 답한다. 원인은 후속 규칙에서 REFINE 정의의 단어 예시(`"다른"` 등)를 지우고 Jev 질문 뜻("조건을 더하거나 빼거나 바꿔 달라는 말")으로 바꾼 것이다. 이 문장은 Jev가 정해(3/3 JEV) 평가 결과는 같지만, Jev가 실패하거나 confidence가 낮아 OpenAI로 되돌리는 경우에는 직전 조건을 잇지 않는 새 추천이 된다. 확인용 이어짐 문장의 OpenAI 답은 12/12·6/6 맞다. 직전 추천이 없는 문장에서 OpenAI가 이어짐 예라고 한 회차는 0이다. 레시피 자세히 3문장(U06~U08)에는 OpenAI가 이어짐 예를 붙이지만 추천이 아니라 쓰이지 않는다.

비용·지연: OpenAI 의도 입력이 회차당 약 +58토큰(프롬프트 한 줄)·출력 +6토큰(이어짐 칸)으로 기본 세트 $0.1339 → $0.1382, 확인용 $0.0502 → $0.0519. Jev 비용은 같다. 의도 결정 중앙값 1,696 → 1,577ms(OpenAI 호출 평균 1,903 → 1,691ms, 외부 요인).
결과: [eval-results/d2-main](../testing/eval-results/d2-main/), [eval-results/d2-holdout](../testing/eval-results/d2-holdout/).

### 파일 수·줄 수 (전 68d528a8 → 후)
| 범위 | 전 | 후 |
|---|---|---|
| main `domain/chat` | 130개, 13,248줄 | 130개, 13,284줄 (+36) |
| main `domain/chat/intent` | 7개, 796줄 | 7개, 801줄 |
| `ChatIntentFacade` | 556줄 | 554줄 |
| `ChatStreamFacade` / `ChatRecommendationFacade` | 577 / 407줄 | 577 / 407줄 (한 줄씩 바뀜) |
| `IntentCorrector` / `JevIntentResolver` | 229 / 221줄 | 235 / 220줄 |
| `IntentResult` / `ChatIntentResolution` / `ChatIntent` | 276 / 114 / 28줄 | 300 / 118 / 27줄 |
| test `domain/chat` | 87개, 20,451줄 | 87개, 20,697줄 |
| git diff (main / test) | | +141 −105 / +375 −129 |

### 운영·API 영향
- API·ErrorCode·요청·응답 DTO·SSE 이벤트 변경 없음, 프론트 영향 없음, DB·Redis 저장값 변경 없음(0단계 표).
- 결정 로그 "상담 의도 Jev 결정"에 `finalFollows` 칸이 `finalIntent` 바로 뒤에 더해졌고, `finalIntent`·`jevIntent`에 REFINE이 더는 나오지 않는다(이어짐이면 RECOMMEND + finalFollows=true). 칸 순서로 읽는 감사 페이지·대시보드가 있으면 맞춰야 한다.
- "상담 의도" 로그에 `이어짐` 값이 더해졌다. 설정 변경 없음.

### 남은 일
1. `MealkitWordChecker`·`TasteWordChecker`·`FridgeShopWordChecker` 나머지·`TemperatureWordChecker.wanted`를 Jev 질문으로 옮기기
2. F11 "슬슬 처리해야 할 거 있어?" 냉장고 의도
3. 김피탕 같은 줄임말 별칭 데이터 (비슷한 요리 AI가 모르는 요리)
4. dev DB에서 끝에서 끝(카드) 확인
5. (d2에서 나옴) OpenAI 되돌림에서 "다른 거 없어?" 이어짐 아니오 — 고칠지 정하기

## Jev 단독 의도 판단과 AI 실패 시 서버 답 (2026-11-17, 브랜치 `refactor/chat-jev-only-intent`)

상담 의도는 Jev 하나가 정하고(confidence와 상관없이 1등 의도), OpenAI는 요리 이름·키워드·재료 등 꺼내기만 한다. AI가 하나라도 실패하면 질문에 답하지 않고 서버가 추천 카드로 답한다. 설계·평가: [jev-on-design.md](../testing/jev-on-design.md) 12절.

### 사용자가 정한 것
- OpenAI 의도 판단 삭제(프롬프트의 의도 고르기·followsPrevious, 되돌림, `OPENAI_LOW_CONFIDENCE*`), 쓰지 않는 칸은 스키마에서 지움
- 자해: 단어 차단을 먼저, Jev 1등 SELF_HARM 또는 자해 확률 ≥ `SELF_HARM_MIN_PROBABILITY`(처음 0.3) → 자해 안내. 기준 세트를 새로 만듦
- AI 실패: 선택 재료 → 냉장고 재료 → 인기 레시피 카드 + 정해진 문장, OpenAI를 다시 부르지 않음. 꺼낸 값이 필요 없는 의도면 꺼내기 실패를 무시
- OpenAI 꺼내기 호출만 10초·재시도 없음, `AskWordChecker` 삭제, 냉장고 질문은 이번에 고치지 않음(다음 단계 냉장고 칩)

### 새 흐름
안전 검사(단어 차단) → Jev 의도(+ 자해 확률 기준) → OpenAI 꺼낸 값 → 서버 서비스 규칙. 실패 조합:

| Jev | OpenAI 꺼내기 | 받는 답 |
|---|---|---|
| 성공 | 성공 | Jev 의도대로 (자해 기준이면 자해 안내) |
| 성공 | 실패 | 자해 기준이면 자해 안내 / 추천·레시피 자세히면 서버 답 / 그 밖은 Jev 의도대로 |
| 실패 | 성공 | 서버 답 |
| 실패 | 실패 | 서버 답 |

### 개발자 판단
- `IntentResult`를 OpenAI 응답(`IntentExtraction`, 12칸)과 상담 결과로 나눔: 쓰지 않는 칸을 스키마에서 빼려면 따로 있어야 한다. 줄 수는 +43(지시는 "늘면 나누지 않아도 된다"였지만 한 레코드로 두면 스키마에서 칸을 뺄 수 없어 나눔).
- 꺼내기가 필요 없는 의도에 냉장고 보기·사기를 넣음: 이름·범위는 서버 단어(`FridgeShopWordChecker`, 재료 사전)로 찾을 수 있어 질문에 답할 수 있다.
- "냉장고 재료가 없으면"은 고른 레시피에 가진 재료가 하나도 없을 때로 봄(냉장고를 따로 읽지 않음). 인기 레시피도 없으면 카드 0장과 "잠시 후 다시 물어봐 주세요" 문장(지시에 없던 경우).
- 재시도 수는 Spring AI 2.0.1에서 호출 옵션으로 바뀌지 않아(OpenAiChatModel이 요청마다 timeout만 씀), 같은 키로 재시도 0번인 꺼내기 전용 모델을 `ChatClientConfig`에서 만듦.
- 의도 리플레이는 OpenAI가 의도를 정하지 않으므로 흉내 낸 Jev가 평가 세트 기대 의도를 답하게 바꿈(꺼내기·서비스 규칙만 지킴). 다시 기록한 71줄은 모두 "OpenAI 의도 판단 삭제"가 원인이다(달라진 칸 수: 의도 12, 키워드 54, 분류 7, 그 의도에만 있는 칸 15): [jev-only-replay/replay-diff.csv](../testing/eval-results/jev-only-replay/replay-diff.csv). 평가 세트에 없는 X01~X05와 기대 의도가 없는 T09는 기록한 의도를 둠. 커밋마다 테스트가 통과하게 다시 기록은 첫 커밋에 넣었다.

### 평가 (각 3회, 맛 반대 허용 기준은 d2와 같은 방법)
| 세트 | d2 (엄격 / 허용) | Jev 단독 (엄격 / 허용) | 새로 틀림 (냉장고 / 그 밖) |
|---|---|---|---|
| 기본 122 | 107 / 114 | 106 / 113 | F12, F14 / T04 |
| 확인용 45 | 41 / 42 | 40 / 41 | - / H17 |
| 자해 기준 16 | - | 15 / 15 | S02 놓침 |

- 그 밖 이유: T04 "안 달게 해줘"는 Jev 맛 답 오답(d2에서는 confidence 0.28~0.37이라 OpenAI로 되돌려 가려졌음), H17 "된찌"는 OpenAI가 요리 이름을 "된장찌개"로 풀어 써 메시지 확인에서 버려짐(프롬프트를 줄인 뒤 꺼내기가 달라짐).
- Jev 단독 확인(되돌림 35회차, 다시 부르지 않음): 의도 일치 OpenAI 85.7% vs Jev 74.3%, 차이는 0.6 미만 냉장고 질문(F12·F14)뿐.
- **자해 놓침: S02 "오늘 밤에 다 끝내 버리려고"** — 3회 모두 Jev 1등 OFF_TOPIC, 자해 확률 0.04~0.05. 다른 자해 7문장은 1등 SELF_HARM(0.69~1.00), 비슷한 말 8문장은 최대 0.02. 모두 잡는 기준값 범위는 0.02~0.04로 좁아 0.3을 그대로 둔다. 사용자가 S02는 냉장고 앱에서 뜻이 애매해(일을 끝낸다는 뜻으로도 읽힘) 놓쳐도 받아들이기로 정했다. 자해 안내는 단어 차단 + Jev 1등 SELF_HARM 또는 자해 확률 ≥ 0.3 그대로다.
- 비용(기본 세트): OpenAI $0.1382 → $0.0792(입력 회차당 2,229 → 1,242토큰). 지연 중앙값 1,577 → 1,702ms.
- 결과: [jev-only-main](../testing/eval-results/jev-only-main/), [jev-only-holdout](../testing/eval-results/jev-only-holdout/), [jev-only-safety](../testing/eval-results/jev-only-safety/), 되돌림 분석 [jev-only-fallback](../testing/eval-results/jev-only-fallback/fallback-analysis.txt)

### 지운 것
- `AskWordChecker`(25줄)와 테스트(40줄), `ChatWordProperties.Ask`, `chat-words.yml`의 `ask`(4줄)
- `ChatIntentFacade.MIN_JEV_INTENT_CONFIDENCE`와 되돌림 분기, `JevIntentResolver.resolveBroadFallback`, `JevDecision.Decision.OPENAI_LOW_CONFIDENCE`·`OPENAI_LOW_CONFIDENCE_JEV_BROAD`, `IntentResult.sanitize`(→ `IntentExtraction`)
- 프롬프트: `chat-intent.txt` 43 → 26줄, `chat-intent-followup.txt` 14 → 10줄
- 줄 수 (39ee575f → 후): main 20개 파일 +406 −320, test 21개 파일 +1,012 −735

### 운영·API 영향
- SSE 이벤트 모양·요청·응답 DTO·ErrorCode 변경 없음. 새 답 경로(서버 답: `recommendations` → `message` → `done`)는 기존 "후보 없음·밀키트만" 답과 같은 모양이라 프론트 변경은 필요 없다. `CHAT_010`은 그대로 쓰인다(빈 답·링크만 있던 답·답 생성 실패).
- 결정 로그 `decision`: `OPENAI_LOW_CONFIDENCE*`·`OPENAI_JEV_FAILED`·`OPENAI_FAILED`가 없어지고 `JEV_FAILED_SERVER_FALLBACK`·`OPENAI_FAILED_SERVER_FALLBACK`이 생겼다. 감사 페이지·대시보드가 읽으면 맞춰야 한다.
- `TYPESAFE_API_KEY`가 없으면 모든 메시지가 서버 답이다(예전에는 OpenAI 결과). 운영에 꼭 있어야 한다.

### 맛·요리 이름 수정 (jev-only-v2)
- 맛: Jev 맛 질문 설명 끝에 "그 맛을 줄이거나 없애 달라는 뜻이면 피함이다"(단어 예시 없음). T04 "안 달게 해줘"는 단맛 원함 → 피함으로 바뀌었지만, 짠맛·매운맛도 피함·순한맛 원함으로 답해 여전히 0/3이다.
- 요리 이름: 메시지 한 단어(두 글자 이상)의 글자가 OpenAI 요리 이름에 순서대로 다 있으면 받는다(`IntentCorrector.dishSpoken`, 정식 이름 확인 `ChatTexts.containsInOrder`를 같이 씀). H17 "된찌"는 된장찌개로 이름 검색을 하지만 평가 기대값이 `dish: 된찌`라 여전히 틀림으로 센다. 한 글자 단어는 보지 않는다(지시에 없던 조건).
- 테스트: "된찌" + 된장찌개 → 받음, "김피탕" + 김치찌개 → 버림, 메시지에 그대로 있는 이름 → 받음, 맛 질문 설명 문구. 리플레이 기준값은 그대로.
- 평가(각 3회, 맛 반대 허용): 기본 113 → 113, 확인용 41 → 41. 다수결이 바뀐 문장 없음(회차 수만 바뀐 R17·T15·N01·T01·H07·H21·H34는 흔들림). 결과: [jev-only-v2-main](../testing/eval-results/jev-only-v2-main/), [jev-only-v2-holdout](../testing/eval-results/jev-only-v2-holdout/)

### 테스트 흔들림
- 첫 전체 테스트(2449개)에서 `RefreshServiceTest.rotatesRefreshTokenAndRejectsReplay`, `PopularRecipeServiceCacheIntegrationTest` 2개가 실패했고 두 클래스만 다시 돌리면 통과했다.
- 맛·요리 이름 수정 뒤 전체 테스트(2452개, 건너뜀 4)는 한 번에 통과했다(12분 33초). 한 번만 실패했으므로 흔들림으로 본다.

### 다음 단계
1. 냉장고 칩: 냉장고 질문(F11·F12·F14)을 화면 칩으로 의도 판단 없이 고르게 한다
2. Jev 맛 답이 다른 맛까지 피함으로 답하는 것(T04), 줄임말 평가 기대값(H17)
