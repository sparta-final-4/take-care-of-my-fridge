# WORK: 의도 판단 서버 보정을 설정으로 끌 수 있게

상태: 진행 (A·E 평가 반영, 다음 평가 대기) · 작성일: 2026-11-10 · 담당: cookiez

## 목표와 완료 기준
- 사용자 문제: 의도 판단 뒤 서버 보정이 늘었고, 단어 목록에 없는 표현("맵고", "화끈한", "빼줘")에서 AI가 맞게 낸 값을 서버가 틀리게 바꾼다.
- 확인 가능한 완료 조건:
  - 보정마다 설정(`app.chat.corrections`)으로 끌 수 있다. 기본은 모두 켜져 있어 지금 동작과 같다.
  - 평가 테스트(`ChatIntentEvalManualTest`)를 보정을 끈 채로 돌릴 수 있고, 결과 요약 맨 위에 끈 설정이 찍힌다.
  - 평가 세트에 "맵고·화끈한·얼얼한" 문장이 있다.
  - A~E 평가 결과를 비교해 뺄 보정을 정한다 (다음 작업).

## 근거와 범위
- 변경할 영역: 백엔드 `domain/chat`(ChatIntentFacade, 새 설정 ChatCorrectionProperties), `application.yml`, 의도 평가 테스트·평가 세트.
- 다른 도메인 영향: 없음. 프런트·API 변경 없음.
- 끄지 않는 것: 안전 단어 차단(SafetyWordChecker), 알레르기 빼기(AllergyExclusionFinder). 위험해서 목록에만 적는다.

## 보정 목록

줄 번호는 `take-care-of-my-fridge-be/src/main/java/.../domain/chat/facade/ChatIntentFacade.java` 기준(이 작업 전).

| 보정 | 위치 | 무엇을 바꾸나 | 붙은 때 | 끄는 설정 |
|---|---|---|---|---|
| 맛 보정 `correctTastes` | :417 | 맛 단어로 원하는 맛·피할 맛을 다시 정해 AI 값을 덮어씀 | 2026-11-05 #585 | `taste-words` |
| 새 추천 맛 정리 `keepSpokenTastes` | :468 | 새 추천에서 이번 말에 맛 단어가 없는 맛을 지움 ("맵고"는 목록에 없어 SPICY가 지워짐) | 2026-11-06 #598 | `spoken-tastes-only` |
| 피할 맛만 말하면 맛 지우기 취소 `keepOtherTastes` | :448 | "매운 건 빼줘"에서 AI가 넣은 removed: TASTE를 뺌 | 2026-11-09 #629 | `keep-other-tastes` |
| 다시 추천 맛 부딪힘 `followSpokenTastes` | :583 | 다시 추천에서 직전 맛과 이번 말 맛이 부딪히면 이번 말(단어)을 따름 | 2026-11-07 #608 | `refine-spoken-tastes` |
| 분류 단어 보정 `correctCategories` (묶음 말 포함) | :499 | 분류 이름·묶음 말이 있으면 그 분류로 덮어쓰고, 직전 키워드·요리 이름을 빼며 removed에 KEYWORD·CATEGORY를 더함 | 2026-11-06 #596 (묶음 말 #598) | `category-words` |
| 요리 이름 서버 계산 `withDishFromKeywords` | IntentResult.java:209 | 첫 키워드가 말에 그대로 있을 때만 요리 이름을 채움 (AI는 주지 않는 값) | 2026-11-09 #629 | `dish-from-keywords` |
| 이름만 말하기 `nameOnly` | :356 | 15자 이하·묻는 말 없음 + 밀키트·분류·재료·레시피 이름이면 GENERAL → RECOMMEND | 2026-11-06 #598 | `name-only` |
| 밀키트 달라는 말 `mealkitRequested` | :329 | 밀키트 단어 + 달라는 말이면 GENERAL → 추천 | 2026-11-06 #598 | `mealkit-request` |
| OFF_TOPIC 되살리기 `reviveOffTopic` | :209 | 음식 말·분류·재료·레시피 이름이 있으면 OFF_TOPIC → GENERAL/RECOMMEND | 2026-11-07 #607 | `off-topic-revive` |
| 냉장고·사기 단어 `applyFridgeShopWords` | :231 | 냉장고·사기 단어로 FRIDGE·SHOP으로 바꾸거나, 요리 말·묻는 말이면 RECOMMEND·GENERAL로 되돌림 | 2026-11-09 #624 | `fridge-shop-words` |
| 묻는 말 `AskWordChecker` | 위 보정 안 + AI 실패 시 :150 | 혼자 AI 값을 바꾸지 않음. 위 보정의 조건과 AI 실패 시 추천·일반 판단 | — | 따로 없음 |
| 냉장고·사기 범위·이름 채우기 `lookupOf` | :259 | AI 값이 ALL이거나 비었을 때만 단어로 채움 (덮어쓰지 않음) | 2026-11-09 #624 | 따로 없음 |
| 안전 단어 차단 `SafetyWordChecker` | ChatStreamService.java:106 | AI 앞에서 자해·금지어 차단 | — | 끄지 않음 |
| 알레르기 빼기 `AllergyExclusionFinder` | :109 | 상담에서 말한 알레르기 재료를 뺄 재료에 더함 | — | 끄지 않음 |

## 계획
| 단계 | 변경 | 검증 |
|---|---|---|
| 1 | `ChatCorrectionProperties`(기본 모두 true), `application.yml` `app.chat.corrections`, 보정마다 설정 확인 | `./gradlew test` |
| 2 | 평가 테스트: `OPENAI_INTENT_EVAL_OFF`로 끌 보정, `OPENAI_INTENT_EVAL_LABEL`로 파일 이름, 요약 맨 위에 끈 설정 | `EvalCorrectionSwitchesTest` |
| 3 | 평가 세트 T11~T14 추가 | 평가 실행 (사용자가 실제 키로) |
| 4 | A·E 결과로 고칠 보정 결정 (아래 "평가 결과와 결정") | `./gradlew clean test` |

## 평가 명령

`take-care-of-my-fridge-be` 폴더에서 실행합니다. 키는 `OPENAI_INTENT_EVAL_KEY`로 넘깁니다(`OPENAI_API_KEY`는 build.gradle이 가짜 키로 덮어씀). 결과는 `build/reports/chat-intent-eval/eval-{시각}-{이름}-summary.txt`(·`-runs.csv`·`-cases.csv`)에 남고, 요약 맨 위 줄에 끈 설정이 찍힙니다.

| 경우 | 끄는 설정 (`OPENAI_INTENT_EVAL_OFF`) |
|---|---|
| A | 없음 (모두 켜짐) |
| B | `taste-words,spoken-tastes-only` |
| B2 | `taste-words,spoken-tastes-only,keep-other-tastes,refine-spoken-tastes` |
| C | `category-words` |
| D | `name-only,mealkit-request,off-topic-revive,fridge-shop-words` |
| E | `taste-words,spoken-tastes-only,keep-other-tastes,refine-spoken-tastes,category-words,name-only,mealkit-request,off-topic-revive,fridge-shop-words` (요리 이름 `dish-from-keywords`만 켬) |

Git Bash:
```bash
export OPENAI_INTENT_EVAL=true OPENAI_INTENT_EVAL_KEY=sk-...
OPENAI_INTENT_EVAL_LABEL=A ./gradlew cleanTest test --tests '*ChatIntentEvalManualTest' -i
OPENAI_INTENT_EVAL_LABEL=B OPENAI_INTENT_EVAL_OFF=taste-words,spoken-tastes-only ./gradlew cleanTest test --tests '*ChatIntentEvalManualTest' -i
OPENAI_INTENT_EVAL_LABEL=B2 OPENAI_INTENT_EVAL_OFF=taste-words,spoken-tastes-only,keep-other-tastes,refine-spoken-tastes ./gradlew cleanTest test --tests '*ChatIntentEvalManualTest' -i
OPENAI_INTENT_EVAL_LABEL=C OPENAI_INTENT_EVAL_OFF=category-words ./gradlew cleanTest test --tests '*ChatIntentEvalManualTest' -i
OPENAI_INTENT_EVAL_LABEL=D OPENAI_INTENT_EVAL_OFF=name-only,mealkit-request,off-topic-revive,fridge-shop-words ./gradlew cleanTest test --tests '*ChatIntentEvalManualTest' -i
OPENAI_INTENT_EVAL_LABEL=E OPENAI_INTENT_EVAL_OFF=taste-words,spoken-tastes-only,keep-other-tastes,refine-spoken-tastes,category-words,name-only,mealkit-request,off-topic-revive,fridge-shop-words ./gradlew cleanTest test --tests '*ChatIntentEvalManualTest' -i
```

PowerShell (환경 변수가 창에 남으므로 매번 `OPENAI_INTENT_EVAL_OFF`를 다시 넣습니다. `''`이면 지워져 모두 켜짐):
```powershell
$env:OPENAI_INTENT_EVAL='true'; $env:OPENAI_INTENT_EVAL_KEY='sk-...'
$env:OPENAI_INTENT_EVAL_LABEL='A';  $env:OPENAI_INTENT_EVAL_OFF='';  .\gradlew.bat cleanTest test --tests '*ChatIntentEvalManualTest' -i
$env:OPENAI_INTENT_EVAL_LABEL='B';  $env:OPENAI_INTENT_EVAL_OFF='taste-words,spoken-tastes-only';  .\gradlew.bat cleanTest test --tests '*ChatIntentEvalManualTest' -i
$env:OPENAI_INTENT_EVAL_LABEL='B2'; $env:OPENAI_INTENT_EVAL_OFF='taste-words,spoken-tastes-only,keep-other-tastes,refine-spoken-tastes';  .\gradlew.bat cleanTest test --tests '*ChatIntentEvalManualTest' -i
$env:OPENAI_INTENT_EVAL_LABEL='C';  $env:OPENAI_INTENT_EVAL_OFF='category-words';  .\gradlew.bat cleanTest test --tests '*ChatIntentEvalManualTest' -i
$env:OPENAI_INTENT_EVAL_LABEL='D';  $env:OPENAI_INTENT_EVAL_OFF='name-only,mealkit-request,off-topic-revive,fridge-shop-words';  .\gradlew.bat cleanTest test --tests '*ChatIntentEvalManualTest' -i
$env:OPENAI_INTENT_EVAL_LABEL='E';  $env:OPENAI_INTENT_EVAL_OFF='taste-words,spoken-tastes-only,keep-other-tastes,refine-spoken-tastes,category-words,name-only,mealkit-request,off-topic-revive,fridge-shop-words';  .\gradlew.bat cleanTest test --tests '*ChatIntentEvalManualTest' -i
```

## 평가 결과와 결정 (2026-11-10)

gpt-4o-mini, 107문장 × 3번 다수결. 결과 파일: `build/reports/chat-intent-eval/eval-20261110-150334-A-*`, `eval-20261110-151424-E-*`.

| 경우 | 끈 설정 | 전체 일치 | 추천 | 맛 | 이름만 | 일반 | 냉장고 | 후속 |
|---|---|---|---|---|---|---|---|---|
| A | 없음 (모두 켜짐) | 101 / 107 (94.4%) | 15 / 17 | 10 / 14 | 5 / 5 | 13 / 13 | 14 / 14 | 18 / 18 |
| E | 요리 이름 말고 모두 | 92 / 107 (86.0%) | 11 / 17 | 10 / 14 | 4 / 5 | 12 / 13 | 13 / 14 | 16 / 18 |

상관없음·거절·자해·냉장고아님·사기·사기아님은 A·E 모두 전부 일치.

- A에서만 틀림: T11~T14(AI는 SPICY를 맞게 줬는데 새 추천 맛 정리가 지움), R14
- E에서만 틀림: 분류 9개(R07·R12·R15·R16·T02·T11·N04·U09·U16, 분류 단어 보정이 채워 주던 것), R17·G08·F11(의도 보정), T06(맛 보정이 채워 주던 MILD)
- 둘 다 틀림: R10 "간단한 한 끼 추천해줘" (알려진 차이)

결정:
- 보정은 빼지 않는다. AI만(E)이 A보다 9문장 적게 맞아, 분류 단어·의도 보정·맛 보정은 아직 필요하다.
- 새 추천 맛 정리(`keepSpokenTastes`): 이번 말에 맛 단어가 없다는 이유로 지우지 않고, 직전 조건(Redis 문맥)에 있던 맛이 이번 말에 없을 때만 지운다. 직전 조건이 없으면 AI 값 그대로. → T11~T14
- 맛 보정(`correctTastes`)은 그대로 둔다 (T06 MILD를 채워 줌).
- 분류 단어에 "스프"를 수프 별칭으로 추가. → T12 분류
- R14 원인: 보정이 찌개를 지운 것이 아니다. A 세 번 모두 AI 원래 값이 분류 없이 키워드 "찌개"였고(E 세 번은 AI가 분류 찌개를 줌),
  분류 단어 보정은 `CategoryWordChecker.java:37` 요청하는 말(REQUEST_WORDS)에 "끓이고"가 없어 찌개를 채우지 못했다. "끓"을 추가.
- 설정 스위치(`app.chat.corrections.*`)는 다음 평가에도 쓰도록 남긴다.

## AI 활용과 개발자 판단
- AI에 맡긴 작업: 보정 위치·도입 시점 조사(git log -S), 설정·평가 스위치 구현, 평가 문장 추가.
- 개발자 판단: B를 B(맛 보정 2개)와 B2(맛 보정 4개)로 나눔, E는 요리 이름만 켬, 안전 단어·알레르기는 끄지 않음, T11~T14 기대 값 확정.

## 구현 결과
- `ChatCorrectionProperties`(새): `app.chat.corrections.*`, 기본 모두 true (`@DefaultValue("true")`), `ALL_ON`.
- `ChatIntentFacade`: 보정 호출마다 설정 확인. 끄면 그 보정을 건너뛰고 AI 값을 그대로 쓴다. 켰을 때 순서·동작은 그대로.
- `application.yml`: `app.chat.corrections` 10개 모두 true.
- 테스트: `EvalCorrectionSwitches`(평가용 이름 → 설정), `EvalCorrectionSwitchesTest`, `ChatIntentFacadeTest$Corrections`(끄면 보정이 빠지는지 4개).
- 평가 테스트: `OPENAI_INTENT_EVAL_OFF`·`OPENAI_INTENT_EVAL_LABEL`, 요약 맨 위에 끈 설정.
- 평가 세트: T11 "맵고 따뜻한 국물 요리", T12 "맵고 따뜻한 스프", T13 "화끈한 거 먹고 싶어", T14 "얼얼한 거".

## 검증
| 명령·시나리오 | 실행 환경·조건 | 통과/실패/미실행 | 근거 |
|---|---|---|---|
| `./gradlew test` (스위치 추가 커밋) | 로컬 | 통과 (2276개 중 1개 실패: PopularRecipeServiceCacheIntegrationTest, 이 작업과 무관·단독 실행 통과) | build/test-results |
| A 평가 (모두 켬) | 실제 OpenAI 키, 사용자 실행 | 101 / 107 | eval-20261110-150334-A-summary.txt |
| E 평가 (요리 이름만 켬) | 실제 OpenAI 키, 사용자 실행 | 92 / 107 | eval-20261110-151424-E-summary.txt |
| B·B2·C·D 평가 | 실제 OpenAI 키 | 미실행 | — |
| `./gradlew clean test` (평가 반영 수정 뒤) | 로컬 | 2281개 중 1개 실패(PopularRecipeServiceCacheIntegrationTest, 아래 참고), 2개 건너뜀 | build/test-results |
| PopularRecipeServiceCacheIntegrationTest 비교 | 로컬 | 이 브랜치 전체 실행 2번 모두 실패(TooManyActualInvocations: 순위 집계 2번), 단독·cooking·recipe 묶음 실행은 통과. origin/dev 전체 실행 1번은 통과 | 원인 미확인. 이 브랜치는 cooking·캐시 코드를 바꾸지 않음. 전체 실행 순서·공유 Redis에 따라 순위 캐시가 비는 것으로 추정 |
| 수정 뒤 A 다시 평가 (T11~T14·R14 확인) | 실제 OpenAI 키 | 미실행 | — |

## 연결과 남은 작업
- 실제 커밋: `refactor: 의도 판단 서버 보정을 설정으로 끌 수 있게`, `test: 평가 세트 추가`,
  `fix: 새 추천에서는 앞 대화에서 따라온 맛만 빼기`, `fix: "스프"도 수프 분류로`, `fix: "찌개 끓이고 싶어"도 찌개 분류로`, `docs: 평가 결과 A·E와 결정`
- 남은 작업: 수정 뒤 A를 다시 돌려 T11~T14·R14가 맞는지 확인. R10은 알려진 차이로 둠.
