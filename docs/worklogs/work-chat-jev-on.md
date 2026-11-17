# WORK-CHAT-JEV-ON: 상담 의도 Jev on 모드 연결

상태: 정리 단계 완료 (Jev가 유일한 의도 판단, 아래 "정리 단계") · 작성일: 2026-11-17 · 담당: 상담(chat)

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
1. d2: REFINE을 ChatIntent와 OpenAI 프롬프트에서 없애고 추천의 이어짐 항목으로 바꾸기
2. 그다음: `MealkitWordChecker`·`TasteWordChecker`·`FridgeShopWordChecker` 나머지·`TemperatureWordChecker.wanted`를 Jev 질문으로 옮기기
3. F11 "슬슬 처리해야 할 거 있어?" 냉장고 의도
4. 김피탕 같은 줄임말 별칭 데이터 (비슷한 요리 AI가 모르는 요리)
5. dev DB에서 끝에서 끝(카드) 확인
