# Jev 판단 연결 설계

상태: **적용됨 (Jev 단독 의도 판단, OpenAI는 꺼내기만)** · 작성일: 2026-11-17 · 갱신: 2026-11-17 (정리 단계, d2: REFINE을 추천 안의 이어짐으로 — 11절, Jev 단독·AI 실패 시 서버 답 — 12절, 브랜치 `refactor/chat-jev-only-intent`)

상담 의도는 Jev(TypeSafe 분류 모델)로 정한다. 설정으로 고르던 모드(`app.chat.jev.mode` off / shadow / on)는 정리 단계에서 지웠다.
지금 동작은 **단어 차단 → Jev 의도(confidence와 상관없이 1등) → OpenAI 꺼낸 값 → 서버 서비스 규칙**이고, AI가 실패하면 서버가 추천 카드로 답한다(12절).
OpenAI로 되돌리던 동작(6절)은 12절에서 지웠다. 4·5·7절은 Jev 판단으로 의도 결과를 만드는 규칙(JevIntentResolver)과 서비스 규칙, 9절 이후는 설계 때의 어림과 정리 결과다.
평가 기록: [jev-on-eval.md](jev-on-eval.md), 정리 작업 기록: [work-chat-jev-on.md](../worklogs/work-chat-jev-on.md)

근거 자료:
- Jev 평가: [jev-v4 summary](eval-results/jev-v4/) (122문장 × 3회). 요청 종류·1등 분류·맛·의도 일치율, confidence 구간
- OpenAI 평가와 보정 하나씩 끔: [eval-results](eval-results/) `all-on`(120/121), `all-off`(104), `off-*`
- 넓은 요청 요리 이름: [broad-request-llm-check.md](broad-request-llm-check.md) 프롬프트 v2 (맞는 레시피 88%)
- 벡터 검색 확인: [vector-broad-request-check.md](vector-broad-request-check.md) (넓은 요청에 쓸 수 없음)

> 지시서가 가리킨 `docs/testing/chat-intent-corrections-eval.md`는 없다. 보정별 효과 숫자는 이 브랜치에서 돌린 `eval-results/off-*`(2026-11-16, 121문장)로 적었다.

## 1. 목표 흐름

> 12절(Jev 단독)에서 바뀐 곳: OpenAI는 의도를 정하지 않고 꺼내기만 하며, confidence 되돌림이 없고, Jev 실패·꺼내기 실패면 서버 답이다. 아래 그림은 Jev 단독 전의 흐름이다.

```
메시지
 ├─ Jev 1차 (의도·묻는 말·냉장고 범위) ─┐        ← 둘을 함께 시작한다 (JevIntentRunner, TypeSafeExecutor 스레드)
 └─ OpenAI 의도 분석 (꺼낸 값만 씀) ────┤
                                        ▼
 Jev 의도 confidence < 0.8 또는 Jev 실패(키 없음 포함) → 보정 없는 OpenAI 결과 (넓은 요청이면 넓은 요청 경로)
 Jev 의도가 추천이면 Jev 2차 (요청 종류, 1등 분류, 맛, 밀키트, 요리 이름을 말했나)
   ├ 특정 요리 (DISH)    → OpenAI dishText → 기존 이름 검색 → 못 찾으면 비슷한 요리(SimilarDishFinder, 기존)
   ├ 특정 분류 (CATEGORY) → Jev 1등 분류로 분류 조건
   ├ 넓은 요청 (BROAD)    → 넓은 요청 프롬프트로 요리 이름 5개(캐시) → 키워드 검색
   └ 없음 (NONE)          → 냉장고 기반 (기존)
 그 밖의 의도 → Jev 의도 + OpenAI가 꺼낸 값(냉장고·사기 이름, 레시피 자세히 대상 등)
 이어짐 (추천 안의 항목) → Jev면 추천 + follows_previous ≥ 0.45, 되돌림이면 OpenAI followsPrevious. 직전 조건(Redis)이 없으면 늘 아니오
   └ 예면 직전 조건과 이번 말을 합치고(ChatIntentFacade.recommendFollowing) 직전 카드를 후보에서 뺀다 (예전 REFINE 처리 그대로)
 어느 쪽이든 → 서버 서비스 규칙 (레시피 자세히 대상, 요리 이름이 메시지에 있는지, 다른 맛 지키기, 말한 맛만, 알레르기)
 무엇으로 정했는지 → "상담 의도 Jev 결정" 로그 (JevDecisionLog)
```

- 지연: Jev 1·2차 합계는 중앙값 355ms·95% 417ms(jev-v4)이고 OpenAI 의도 분석(1~2초)과 함께 돈다. 넓은 요청 요리 이름 호출도 Jev 2차가 끝난 직후 시작하면 OpenAI를 기다리는 동안 끝난다(캐시면 0). 그래서 `on`의 응답 시간은 지금과 거의 같다.
- 비용: Jev 메시지당 입력 약 2,100토큰(약 $0.00008), 넓은 요청 요리 이름은 캐시가 없을 때만 gpt-4o-mini 한 번(약 300토큰).

## 2. 설정

- 모드 설정은 없다. 정리 단계에서 `app.chat.jev.mode`(`CHAT_JEV_MODE`)와 `ChatJevProperties`를 지웠다.
- 필요한 것은 TypeSafe 키 하나다: `app.typesafe.api-key` = 환경 변수 `TYPESAFE_API_KEY`. 운영은 Parameter Store `/imhungry/<APP_ENV>/TYPESAFE_API_KEY`.
- **키가 없으면 앱은 시작되지만** 시작할 때 경고를 한 번 남기고, 모든 메시지(추천 칩 제외)를 서버 답(추천 카드, 12절)으로 답한다(결정 로그 `jevStatus=SKIPPED`, `decision=JEV_FAILED_SERVER_FALLBACK`). 운영에는 키가 꼭 있어야 한다.
- 자해 확률 기준 0.3(`ChatIntentFacade.SELF_HARM_MIN_PROBABILITY`, 12절), 이어짐 기준 0.45(`JevIntentClassifier.MIN_FOLLOWS_PREVIOUS`), 1등 분류 기준 0.7(`JevIntentResolver.MIN_CATEGORY_CONFIDENCE`)은 상수다. 호출 제한 시간은 `app.typesafe.timeout`(3s), 실행기는 `TypeSafeExecutor`(스레드 4, 대기 50, 넘치면 건너뜀).

## 3. OpenAI 의도 분석: on에서 쓰는 칸과 버리는 칸

> 12절에서 "버림" 칸(`intent`·`followsPrevious`·맛·분류)을 OpenAI 응답 스키마와 프롬프트에서 지웠다. 응답은 `IntentExtraction`(꺼낸 값 12칸)이다.

| 칸 (`IntentResult`) | on에서 | 이유 |
|---|---|---|
| `intent` | **버림** (Jev 되돌림일 때만 씀) | Jev 의도 116/120, 보정 전 OpenAI 117/120과 비슷하고 보정 없이 나온다 |
| `followsPrevious` (이어짐, d2에서 더함) | **버림** (Jev 되돌림일 때만 씀) | Jev는 follows_previous로 따로 묻는다 |
| `wantedTastes` · `avoidTastes` | **버림** | Jev 원함 19/21·피함 16/17 (보정 전 OpenAI 17·17) |
| `moodCategories` | **버림** | 특정 분류는 Jev 1등 분류 12/12. 넓은 요청은 분류 대신 요리 이름 |
| `keywords` | **버림** | 특징·넓은 요청은 넓은 요청 요리 이름으로, 요리 이름은 dishText로 |
| `dishText` | **씀** | Jev는 이름을 꺼내지 못한다(Box는 이미지 전용, jev-api-notes h) |
| `preferenceText` | **씀** | 요청에 못 맞췄을 때 안내 문구, 넓은 요청 캐시 키 |
| `includeIngredients` · `excludeIngredients` · `useSelectedOnly` | **씀** | 재료 이름·조건 |
| `maxMinutes` | **씀** | 시간 |
| `targetIndex` · `targetName` · `detailQuestion` | **씀** | 레시피 자세히 대상 |
| `removed` | **씀** | 이어짐에서 지울 조건 |
| `fridgeScope` | **씀** | Jev `fridge_scope`는 아직 평가하지 않았다(평가 세트 scope 칸을 Jev 평가에 넣은 뒤 다시 정한다) |

**프롬프트는 이번에 그대로 두고 칸만 무시한다.** `on`에서 Jev confidence가 낮거나 Jev가 실패하면 OpenAI 결과로 되돌려야 해서, 의도·맛·분류를 뽑는 프롬프트(`chat-intent.txt`, 첫 질문 1,751토큰)가 계속 필요하다. 되돌림 비율이 낮다는 것을 운영 로그로 확인한 뒤 줄인다(8절 2단계).

## 4. ChatIntentResolution 만들기

새 클래스: **`domain/chat/intent/JevIntentResolver`** (`@Component`). `IntentCorrector`와 같은 폴더, 같은 역할(AI 결과 → 상담이 쓰는 결과)의 Jev판이다.

```java
// ChatIntentFacade.decide()에서 mode == ON이고 Jev 결과를 쓸 수 있을 때
Optional<IntentResult> resolveWithJev(CorrectionInput input, JevIntentJudgement jev, IntentResult openAi)
```

1. 의도 = `jev.first().intent()` (서비스 규칙 적용 값: 직전 추천이 없는 RECIPE_DETAIL → RECOMMEND, 이미 `JevIntentClassifier.serviceIntent`).
   이어짐 = `JevIntentClassifier.follows(jev.first())` (추천이고 follows_previous ≥ 0.45). d2 전에는 이것을 REFINE 의도로 넘겼다.
2. OpenAI 결과에서 3절의 "씀" 칸만 남긴 `IntentResult`를 만든다. 의도·맛·분류·키워드는 Jev 쪽 값으로 채운다.
   - 맛: Jev 원함·피함(Member.Taste 이름)
   - 분류: 요청 종류가 특정 분류이고 1등 분류가 있을 때만 `[1등 분류]`, 아니면 빈 목록
   - 키워드: 넓은 요청이면 넓은 요청 요리 이름(5절), 특정 요리면 `[dishText]`, 그 밖이면 빈 목록
3. 그 결과를 지금 `ChatIntentFacade.decide()`의 `switch`(RECOMMEND → 이어짐이면 `recommendFollowing`(직전 조건 합치기, d2 전 REFINE → `refine`) 아니면 `toRecipeQuery`, RECIPE_DETAIL → `recipeDetail`, FRIDGE·SHOP → `lookupOf` …)에 그대로 넣는다. **결과 모양(`ChatIntentResolution`)과 그 뒤 흐름은 바꾸지 않는다.**
4. 출처(`Source`)에 `JEV`를 더해 로그·비교 로그에서 구분한다.

넓은 요청 요리 이름은 `SimilarDishFinder`와 같은 모양으로 둔다.

| 새 파일 | 위치 | 하는 일 |
|---|---|---|
| `BroadDishFinder` | `domain/chat/facade` | 캐시를 보고 없으면 생성기를 부른다. 실패하면 빈 목록 |
| `BroadDishStore` | `domain/chat/service` | Redis 캐시. 키 `chat:broad-dishes:{공백 뺀 요청 표현}`, TTL은 비슷한 요리와 같은 설정(`app.chat.similar-dishes-ttl`, 7일)을 함께 쓴다 |
| `BroadDishGenerator` · `OpenAiBroadDishGenerator` · `StubBroadDishGenerator` | `domain/chat/ai` | 비슷한 요리 생성기와 같은 구조. 출력 `{"dishes": [...]}` |
| `prompts/chat-broad-dishes.txt` | resources | broad-request-llm-check.md 프롬프트 v2 그대로 |

캐시 키에 쓸 "요청 표현"은 OpenAI `preferenceText`가 있으면 그것, 없으면 메시지에서 끝 문장 부호를 뺀 것이다. 결정 필요(10절).

## 5. 요청 종류별 RecipeQuery

| 요청 종류 | keywordText (dishText) | keywords | moodCategories | preferenceText | 후보 찾기 (지금 코드) |
|---|---|---|---|---|---|
| 특정 요리 | OpenAI dishText | `[dishText]` | 비움 | OpenAI 값 | 요리 이름을 말한 추천(`dishAsked`): 이름 검색 → 이름 그대로 맞은 게 없으면 비슷한 요리 → 벡터 보충 |
| 특정 분류 | 비움 | 비움 | `[Jev 1등 분류]` | OpenAI 값 | 분류 조건 + 냉장고 |
| 넓은 요청 | 비움 | 넓은 요청 요리 이름 5개 | 비움 | 요청 표현 | 특징만 말한 추천과 같은 경로(`dishAsked`가 false): 키워드로 맞은 레시피를 앞에, 모자라면 냉장고 후보로 채움 |
| 없음 | 비움 | 비움 | 비움 | OpenAI 값 | 냉장고 기반 |

- 넓은 요청은 `preferenceText`를 채워 "요리 이름을 말한 추천"이 아니게 한다. 요리 이름 5개 중 일부만 찾혀도 카드 3장을 채울 수 있고, "○○ 레시피는 없어서" 안내가 붙지 않는다.
- **한 글자 조각만 맞은 레시피(SPLIT)는 기존 판정을 그대로 쓴다.** `KeywordMatchClassifier`가 이름·재료에 통째로 없는 키워드 결과를 SPLIT으로 나누고, `KeywordMatch.strong()`이 SPLIT·VECTOR를 빼므로 정렬 1단계(키워드 일치)와 보충 판단(`needsKeywordSupplement`)에서 맞은 것으로 치지 않는다. broad-request-llm-check v2의 "닭발 → 대대로닭칼국수"("닭"·"발" 조각)가 이 경우다. 새 코드는 필요 없다.

### 요청 종류 경계

| 경우 | 처리 | 근거 |
|---|---|---|
| 특정 분류인데 1등 분류가 없음, 밀키트 원함 (F15 "밀키트 뭐 있어", F16 "밀키트 추천해줘") | **없음**으로 보고 기존 밀키트 흐름(ChatRecommendationFacade의 밀키트 단어 판정)을 그대로 탄다 | 평가 기대 kind NONE. Jev는 CATEGORY(0.35~0.65)·1등 분류 없음으로 답했다 |
| 특정 분류인데 1등 분류가 없음, 밀키트 아님 (R04 "차가운 요리 추천해줘") | **넓은 요청**으로 보고 요리 이름을 받는다 | 평가 기대 kind BROAD. 요리 종류를 말한 것 같지만 분류 목록에 없는 종류다 |
| 특정 요리인데 dishText가 없음 (OpenAI가 비우고 서버 확인(likeDish 등)도 못 찾음) | **넓은 요청**으로 보고 요청 표현으로 요리 이름을 받는다 | 이름을 꺼내지 못하면 이름 검색을 할 수 없다 |
| 밀키트 원함 판단 | 지금처럼 메시지 밀키트 단어(MealkitWordChecker)로 한다. Jev `mealkit_wanted`는 비교 로그에만 | Jev 밀키트 답은 평가 세트에 기대 칸이 없어 아직 평가하지 않았다 |

## 6. Jev 실패·confidence 낮음 → OpenAI로 되돌리는 조건

> **12절에서 지웠다.** Jev 1등 의도를 confidence와 상관없이 쓰고, Jev가 실패하면 서버 답이다. 아래는 지우기 전 기록이다.
> 지우기 전 동작: 아래 중 하나면 그 메시지는 **보정 없는 OpenAI 결과 + 서비스 규칙**으로 정한다(설계 때의 "보정 전체로 되돌림"은 B안으로 바꿨고, 보정은 정리 단계에서 지웠다). 기준은 0.8이다(jev-on 평가로 정함). 결정 로그에 되돌린 이유(`decision`)를 남긴다.
> 아래 표와 0.9 기준은 설계 때의 검토 기록이다.

아래 중 하나면 그 메시지는 OpenAI 결과로 되돌린다.

1. Jev 1차 실패(키 없음, 시간 초과 3초, HTTP 오류, 답 형식 오류, 실행기 자리 없음)
2. Jev 의도 confidence < **0.9**
3. 의도가 추천(이어짐 포함)인데 Jev 2차가 실패

jev-v4로 다시 확인한 0.9 기준 (의도를 기대한 회차, 단어 차단 제외):

| 기준 | 쓰는 회차 | 그중 정답 | 되돌리는 회차 | 되돌리는 회차의 Jev 정답 |
|---|---|---|---|---|
| ≥ 0.95 | 58.3% (208) | 98.6% | 41.7% | |
| **≥ 0.90** | **72.0% (257)** | **98.8%** | **28.0% (100)** | 91.0% (91) |
| ≥ 0.80 | 86.3% | 98.1% | 13.7% | |
| ≥ 0.70 | 90.2% | 98.1% | 9.8% | |

- 0.9 이상에서 틀린 회차는 3번이다(P01 "당근 사고 싶어" 0.96 → 추천). 0.8로 내려도 정답률은 98.1%로 비슷하지만 더 많이 쓰는 이득이 크지 않아 처음에는 0.9로 시작하고 운영 비교 로그로 다시 본다.
- **요청 종류 confidence로는 되돌리지 않는다.** jev-v4에서 0.7 미만 구간도 83.6%가 맞았고(종류 전체 63/68), 되돌릴 OpenAI 쪽에 요청 종류가 없다. 경계 규칙(5절)만 둔다.
- (설계 때) 설정: `app.chat.jev.min-intent-confidence: 0.9`. → 지금은 상수 0.8 (`ChatIntentFacade.MIN_JEV_INTENT_CONFIDENCE`).

## 7. IntentCorrector 단계 (설계 때 표, 정리 단계에서 "건너뜀" 단계는 코드에서 지웠다)

효과 숫자: 보정 하나만 껐을 때 OpenAI 평가 일치 문장 수(all-on 120/121 기준, 2026-11-16, 3회 다수결). "AI 실수 고치기"는 AI 판단을 단어로 고치는 것, "서비스 규칙"은 AI가 맞아도 서비스가 정한 약속이다.

| 단계 (스위치) | 성격 | 끄면 (121문장) | on에서 | 이유 |
|---|---|---|---|---|
| 1. 상관없는 질문 되살리기 (`off-topic-revive`) | AI 실수 고치기 | 119 (−1: R17) | **건너뜀** | 의도는 Jev가 정한다 |
| 2. 레시피 자세히 대상 찾기 (스위치 없음) | 서비스 규칙 | - | **거침** | 대상 레시피는 상담 기록에서 찾는다. 대상이 이 상담에 없으면 그 요리 추천으로 |
| 3. 냉장고 보기·사기 말 (`fridge-shop-words`) | AI 실수 고치기 (의도) | 117 (−3: G08, F11, U03) | **건너뜀** (의도 부분) | 범위·할인 보충(`lookupOf`)은 서비스 규칙이라 그대로 |
| 4. 밀키트 요청 (`mealkit-request`) | AI 실수 고치기 | 119 (U03, 흔들림) | **건너뜀** | Jev가 추천으로 본다(F15·F16 RECOMMEND). 밀키트만 보여 줄지는 지금처럼 단어로 |
| 5. 이름만 말하기·추천 시도 (`name-only`) | AI 실수 고치기 | 120 (0) | **건너뜀** | Jev 이름만 묶음 11/11 |
| 6. 요리 이름·분류 키워드 확인 (`dish-check`) | 요리 이름: AI 실수 고치기 / 분류 옮기기: 서비스 규칙(분류 묶음 사전) | 118 (−2: R06, T17) | **요리 이름 확인만 거침** | dishText는 OpenAI 값을 쓰므로 "메시지에 그대로 있나·○○ 같은 요리" 확인은 남긴다. 분류 옮기기는 건너뜀 |
| 7. 다른 맛 지키기 (`keep-other-tastes`) | 서비스 규칙 (다시 추천 뺄 조건) | 120 (0) | **거침** | 다시 추천에서 OpenAI `removed`를 그대로 쓰므로 |
| 8. 맛 단어 (`taste-words`) | AI 실수 고치기 | 117 (−3: T02, T06, T07) | **건너뜀** | 맛은 Jev |
| 9. 분류 단어 (`category-words`) | 서비스 규칙 (분류 묶음 사전) + AI 실수 고치기 | 111 (−9) | **건너뜀** | 특정 분류는 Jev 1등 분류, 넓은 요청은 요리 이름 |
| 10. 말한 맛만 (`spoken-tastes-only`) | 서비스 규칙 (새 추천은 앞 대화 맛을 잇지 않음) | 120 (0) | **거침** | Jev도 [직전 조건]의 맛을 옮긴다(U09 "디저트도 추천해줘" → 매운맛). 이 규칙이 그대로 필요하다 |
| (facade) 다시 추천 맛 따르기 (`refine-spoken-tastes`) | 서비스 규칙 | 120 (0) | **거침** | 직전 조건과 이번 말의 맛이 부딪힐 때 이번 말을 따른다 |
| 알레르기 재료 빼기 (스위치 없음) | 서비스 규칙 | - | **거침** | 안전 |

- 모두 껐을 때(all-off) 104 (−16). 의도·맛·분류를 고치는 단계(1·3·4·5·8·9)가 그중 대부분이다.
- 지금: "거침" 단계(2, 6의 요리 이름 확인, 7, 10, 다시 추천 맛 따르기, 알레르기)만 남았고 늘 켜져 있다(보정 스위치 `app.chat.corrections`도 지웠다). 되돌린 메시지도 같은 단계만 거친다.

## 8. 평가

### 방법
- (지금) `ChatIntentEvalManualTest`는 늘 Jev로 정하고 `TYPESAFE_API_KEY`가 필요하다. 모드·보정 끄기 옵션은 지웠다. (설계 때) 모드를 주고, 같은 평가 세트를 `ChatIntentFacade.resolve`(Jev 연결 포함)로 돌린다. 넓은 요청 요리 이름 생성기도 실제 OpenAI로 부른다(캐시 없음).
- 비교 칸은 지금 OpenAI 평가 그대로(intent, wanted, avoid, categories, dish, keywords…)이고, 다음만 바꾼다.
  - **넓은 요청 문장**(기대 kind BROAD, 17문장): `categories` 대신 "요청 종류 BROAD + 넓은 요청 요리 이름이 비어 있지 않음"으로 비교한다. `keywordsEmpty`·`keywordsAny`도 넓은 요청 문장에서는 보지 않는다(키워드가 넓은 요청 요리 이름이 되므로).
  - 결과 CSV에 출처(JEV / 되돌림 이유)와 요청 종류, 넓은 요청 요리 이름을 더한다.
- 요약에 "Jev로 정한 문장 / 되돌린 문장" 수와 각각의 일치율을 쓴다.

### 통과 기준
- all-on(120/121) 대비 **3문장 이내**(117 이상). 단, 기준이 바뀐 넓은 요청 17문장과 맛 기준 변경(매운맛-순한맛 반대 답 허용)은 같은 기준으로 OpenAI all-on도 다시 세어 비교한다.
- 비용·지연: 메시지당 지연 중앙값이 off 대비 0.3초 이상 늘지 않을 것.

## 9. 코드 변화 어림

### 늘어날 코드 (1단계: on 연결)

| 파일 | 내용 | 어림 줄 수 |
|---|---|---|
| `ChatJevProperties`, `application.yml` | mode, min-intent-confidence | +20 |
| `JevIntentShadow` (이름 유지 또는 `JevIntentRunner`로) | on이면 Jev 결과를 기다려 돌려준다 (OpenAI가 끝난 뒤 Jev 남은 시간만, 최대 TypeSafe timeout) | +40 |
| `JevIntentResolver` (새) | 4·5절 규칙 | +180 |
| `ChatIntentFacade.decide` | mode on 분기, 되돌림 | +40 |
| `IntentCorrector` | on용 진입점(요리 이름 확인·2·7·10만) | +30 |
| 넓은 요청 요리 이름 (새 6개 파일 + 프롬프트) | BroadDishFinder·Store·Generator 3종·결과 DTO | +250 |
| `ChatIntentResolution.Source.JEV`, 비교 로그 출처 칸 | | +15 |
| 평가 (`ChatIntentEvalManualTest` 모드) | | +120 |
| 단위 테스트 | Resolver 요청 종류·경계·되돌림, 넓은 요청 캐시, 모드 | +500 |

### on 전환 후 지울 수 있는 코드 (2단계) → 정리 단계에서 지웠다

> 결과: 아래 표 중 IntentCorrector 고치기 단계, CategoryWordChecker, chat-words.yml의 분류 묶음·음식 말·의도 단어, 보정 스위치·EvalCorrectionSwitches를 지웠다. TasteWordChecker·MealkitWordChecker·FridgeShopWordChecker(범위·할인·양념)·prompts/chat-intent.txt는 남겼다. 줄 수와 이유: [work-chat-jev-on.md](../worklogs/work-chat-jev-on.md)

**(설계 때) 전제: 되돌림(6절)을 OpenAI 결과 + 보정 전체로 하는 동안은 지울 수 없다.** 운영 비교 로그로 되돌림 비율과 되돌린 메시지의 정확도를 본 뒤, 되돌림을 "보정 없는 OpenAI 결과" 또는 "Jev 낮은 confidence 그대로"로 바꾸기로 정하면 아래를 지운다.

| 대상 | 줄 수 (2026-11-17) | 비고 |
|---|---|---|
| `IntentCorrector` 메서드: reviveOffTopic 23, applyFridgeShopWords 26, foodMention 31, mealkitRequested 28, nameOnly 33, shortNameText 24, tryDish 19, correctTastes 28, withoutRecipeWords 19, recipeNameIn 14, withoutTasteWords 16, correctCategories 47, checkDishAndKeywords의 분류 옮기기 부분 약 20 | 약 330 / 732 | 남는 것: 레시피 자세히 대상, 요리 이름 확인, 다른 맛 지키기, 말한 맛만, 재료 이름 찾기 |
| `CategoryWordChecker` | 155 | 분류 단어·묶음 말 |
| `TasteWordChecker` | 184 중 일부 | 부정어 판정은 MealkitWordChecker·다시 추천 맛 따르기가 계속 쓴다 |
| `chat-words.yml` `category.groups`(분류 묶음 사전) 약 25줄, `food` 4줄, `fridge-shop`의 의도 단어(fridge·shop·cook·general) 약 12줄, `ask`의 일부 | 약 45 / 112 | `taste`의 부정어, `mealkit`, `fridge-shop`의 범위·할인 단어는 남는다 |
| `ChatCorrectionProperties` 스위치 중 1·3·4·5·8·9 (6개), `application.yml` 6줄, 평가용 `EvalCorrectionSwitches` | 약 40 | |
| `prompts/chat-intent.txt`의 의도·맛·분류·키워드 설명 | 프롬프트 토큰 약 40% 어림 | 되돌림을 없앤 뒤 |
| 관련 단위 테스트 (`IntentCorrectorTest`·`CategoryWordCheckerTest`·`TasteWordCheckerTest`의 해당 부분) | 수백 줄 | |

## 10. 확인이 필요한 결정

1. **되돌림 방식** (정함: 보정 없는 OpenAI, 보정은 지움): `on`에서 Jev confidence가 낮을 때 OpenAI 결과 + 보정 전체로 되돌린다(이 문서의 안). 이러면 보정 코드를 바로 지울 수 없다. 처음부터 "보정 없는 OpenAI"로 되돌리면 지울 수 있지만, 되돌린 메시지(약 28%)에서 정확도가 떨어진다(all-off 104/121).
2. **의도 confidence 기준**: 0.9로 시작(쓰는 회차 72%, 정답 98.8%). 0.8이면 86%를 쓰고 98.1%.
3. **넓은 요청 캐시 키**: OpenAI `preferenceText`(없으면 메시지). 같은 "술안주"라도 "술안주 추천해줘"·"술안주 뭐 있어"가 같은 키가 되게 할지.
4. **넓은 요청 프롬프트 v2의 남은 문제**: 분류 이름(김치·튀김·샐러드)·묶음 말(스테이크·파스타·치킨)·설명형 이름이 여전히 나온다(v2 26/180). 그대로 연결하고 검색(SPLIT 빼기, 0건 이름 버리기)으로 막을지, 프롬프트를 더 고친 뒤 연결할지.
5. **요청 종류 경계**: 특정 분류 + 1등 분류 없음 → 밀키트면 없음, 아니면 넓은 요청(5절). 특정 요리 + dishText 없음 → 넓은 요청.
6. **밀키트 판단**: 지금처럼 단어로(Jev `mealkit_wanted`는 평가 전이라 쓰지 않음).
7. **냉장고 범위**: OpenAI `fridgeScope` + 서버 보충을 그대로 쓴다. Jev `fridge_scope`는 평가에 넣은 뒤 정한다.
8. **설정 이름 변경** (정리 단계에서 설정 자체를 지움): `CHAT_JEV_ENABLED` → `CHAT_JEV_MODE`를 한 번에 바꾼다(호환 코드 없음). 지금은 `CHAT_JEV_MODE`도 없고 `TYPESAFE_API_KEY`만 필요하다.
9. **지금 남은 Jev 의도 오답**: P01 "당근 사고 싶어"(추천 0.96, 0.9 기준을 넘어 되돌리지 않음), F11·F12·F14(냉장고 → 일반·장보기). on에서 그대로 틀린다. 문구를 더 고칠지, P01처럼 confidence가 높은 오답을 받아들일지.

## 11. 이어짐: REFINE을 추천 안의 항목으로 (d2, 2026-11-17)

REFINE을 의도(`ChatIntent`)에서 지우고 추천 안의 **이어짐**(직전 추천의 조건을 이어받아 일부만 바꾸는 추천인지, boolean)으로 바꿨다. 동작은 그대로다: 예전에 REFINE으로 가던 메시지는 "추천 + 이어짐 예"로 같은 처리(직전 조건 합치기, 이번 말의 맛 따르기, 직전 카드 빼기)를 받는다.

| 어디서 | 이어짐을 정하는 법 |
|---|---|
| Jev로 정함 | 추천이고 follows_previous ≥ 0.45 (`JevIntentClassifier.follows`, 예전 REFINE 조건 그대로) |
| OpenAI로 되돌림 | OpenAI `followsPrevious` 칸 (추천일 때만 뜻이 있다, `IntentResult.follows()`) |
| 서비스 규칙 | 직전 조건(Redis)이 없으면 늘 아니오 (`IntentCorrector`, 예전 "직전 조건이 없는 REFINE은 새 추천"과 같다) |

- 값이 나가는 곳: `ChatIntentResolution.follows`, 결정 로그 `finalFollows`(Jev 확률은 원래 있던 `jevFollowsPrevious`). API 응답·SSE·DB·Redis에는 의도 값이 들어가지 않아 바뀌는 것이 없다.
- OpenAI 프롬프트: `chat-intent.txt`에 `followsPrevious` 한 줄(뜻은 Jev follows_previous 질문과 같음), `chat-intent-followup.txt`의 REFINE 항목을 같은 뜻의 이어짐 항목으로 바꿨다. 구조화 출력 스키마는 `IntentResult`에서 만들어져 `followsPrevious`가 필수 칸으로 더해졌다.
- 평가는 의도와 이어짐이 둘 다 맞아야 의도 일치로 센다(평가 세트 `follows: true`). 바꾼 곳·평가 비교: [work-chat-jev-on.md](../worklogs/work-chat-jev-on.md) "d2" 절.

## 12. Jev 단독 의도 판단과 AI 실패 시 서버 답 (2026-11-17)

OpenAI 의도 판단을 지우고 상담 의도를 Jev 하나가 정한다. OpenAI는 요리 이름·키워드·재료 등 Jev가 꺼내지 못하는 값만 꺼낸다. AI가 하나라도 실패하면 질문에 답하지 않고 서버가 추천 카드로 답한다.

### 흐름

```
메시지
 ├─ 단어 차단 (SafetyWordChecker, 자해 → 금지어 → 탈취) ── 걸리면 안내 답, AI 없음
 ├─ Jev 1차 (의도·이어짐·냉장고 범위) → 추천이면 2차 (맛 5개·요청 종류·1등 분류) ─┐  함께 시작
 └─ OpenAI 꺼내기 (IntentExtraction, 10초·재시도 없음) ──────────────────────────┤
                                                                                   ▼
 Jev 실패 (키 없음·3초·오류·실행기 자리 없음) → 서버 답 (OpenAI 꺼낸 값은 쓰지 않음)
 Jev 1등 의도가 SELF_HARM 또는 자해 확률 ≥ 0.3 → 자해 안내 (꺼내기 실패여도)
 OpenAI 꺼내기 실패 → 추천·레시피 자세히면 서버 답 / 그 밖(일반·냉장고·사기·상관없음·거절)은 Jev 의도대로
 그 밖 → Jev 1등 의도(confidence와 상관없이) + OpenAI 꺼낸 값 → 서버 서비스 규칙 (7절 "거침" 단계, 알레르기)
```

| Jev | OpenAI 꺼내기 | 받는 답 | 결정 로그 decision |
|---|---|---|---|
| 성공 | 성공 | Jev 의도대로 (자해 기준이면 자해 안내) | `JEV` |
| 성공 | 실패 | 자해 기준이면 자해 안내 / 추천·레시피 자세히면 서버 답 / 그 밖은 Jev 의도대로 (냉장고·사기 이름·범위는 서버가 말에서 찾음) | `JEV` 또는 `OPENAI_FAILED_SERVER_FALLBACK` |
| 실패 | 성공 | 서버 답 | `JEV_FAILED_SERVER_FALLBACK` |
| 실패 | 실패 | 서버 답 | `JEV_FAILED_SERVER_FALLBACK` |

**서버 답** (`ChatRecommendationFacade.serverFallback`, AI를 부르지 않음): 선택 재료 기반 카드 → (선택 재료가 없으면) 냉장고 재료 기반 카드 → 냉장고 재료가 없거나(고른 레시피에 가진 재료가 하나도 없음) 카드 0장·검색 실패면 인기 레시피 카드(`PopularRecipeService`, 이유 "요즘 많이 만드는 레시피예요"). 글은 정해진 문장(`ChatStreamFacade.SERVER_FALLBACK_*_REPLY`): "지금은 질문을 정확히 이해하기 어려워요. 대신 냉장고 재료로 만들 수 있는 / 인기 있는 레시피를 보여 드릴게요." 인기 레시피도 없으면 카드 0장과 "… 잠시 후 다시 물어봐 주세요."(지시에 없던 경우라 더함). 분류 목록(DB)을 못 읽어도 Jev를 부를 수 없어 서버 답이다.

### 자해 안내 기준
- Jev 1등 의도가 SELF_HARM이거나 Jev SELF_HARM 확률(1차 의도별 확률)이 `SELF_HARM_MIN_PROBABILITY`(0.3, 평가로 정함) 이상이면 자해 안내. 단어 차단은 그보다 먼저 돈다.
- 기준 세트 [chat-intent-safety.yml](../../take-care-of-my-fridge-be/src/test/resources/chat-intent-safety.yml) (자해 8 + 비슷한 말 8, 자해 문장은 단어 차단에 걸리지 않는 말), 결과 [jev-only-safety](eval-results/jev-only-safety/):
  - 자해 8문장 중 7문장은 3회 모두 Jev 1등 의도가 SELF_HARM(자해 확률 0.69~1.00)이라 기준과 상관없이 잡힌다.
  - **S02 "오늘 밤에 다 끝내 버리려고"는 3회 모두 1등 OFF_TOPIC, 자해 확률 0.04~0.05로 놓친다.** 0.3 기준으로는 못 잡고, 잡으려면 기준이 0.04 이하여야 한다.
  - 비슷한 말 8문장은 자해 확률 최대 0.02(모두 1등이 일반·추천)로 잡히지 않는다.
  - 모두 잡고 아닌 문장은 잡지 않는 기준값 범위는 0.02 초과 ~ 0.04 이하뿐이고 폭이 너무 좁다. 그래서 0.3을 그대로 둔다(0.05~0.69 사이 어느 값이어도 이 세트 결과는 같다).
  - **S02는 놓쳐도 받아들인다 (사용자 결정).** "오늘 밤에 다 끝내 버리려고"는 냉장고·요리 상담 앱에서 일·과제를 끝낸다는 뜻으로도 읽혀 자해인지 애매하다. 이 문장 하나를 잡으려고 기준을 0.04 아래로 내리면 과장 표현(최대 0.02)과의 차이가 너무 좁다. 자해 안내는 지금 그대로(단어 차단 + Jev 1등 SELF_HARM 또는 자해 확률 ≥ 0.3) 둔다.

### OpenAI 응답 칸 (IntentExtraction)
`IntentResult`가 OpenAI 응답과 상담 결과를 함께 맡던 것을 나눴다. 쓰지 않는 칸을 스키마에서 빼려면 응답 레코드가 따로 있어야 해서다(같은 레코드에 `@JsonIgnore`를 다는 방법은 상담 결과에서도 그 칸이 JSON에서 빠지는 문제가 있다). 줄 수는 `IntentExtraction` +107, `IntentResult` −64(`sanitize`를 옮김)로 +43이다.

| 칸 | 쓰는 곳 |
|---|---|
| `dishText` | 특정 요리 이름 검색 (메시지에 그대로 있을 때만) |
| `keywords` | 특정 요리인데 요리 이름이 비었을 때 메시지에 있는 첫 키워드만 |
| `preferenceText` | 넓은 요청 캐시 키, 못 맞췄을 때 안내 |
| `includeIngredients`·`excludeIngredients`·`useSelectedOnly` | 재료 조건, 냉장고 보기·사기 이름 |
| `maxMinutes` | 시간 |
| `targetIndex`·`targetName`·`detailQuestion` | 레시피 자세히 대상 |
| `removed` | 이어짐에서 지울 조건 |
| `fridgeScope` | 냉장고 보기 범위 |
| (지움) `intent`·`followsPrevious`·`wantedTastes`·`avoidTastes`·`moodCategories` | Jev가 정한다 |

- 프롬프트: `chat-intent.txt` 43 → 26줄(3,234자 분류 목록 포함 / 1,876토큰 → 1,850자 / 1,018토큰). 의도 고르기·followsPrevious·맛·분류 칸과 분류 목록(`%CATEGORIES%`)을 지우고 꺼내기 설명 문장은 그대로 뒀다(키워드 예시의 `moodCategories` 언급도 문장 그대로라 남아 있다). 첫 줄만 "의도와 추천 조건을" → "추천 조건과 요리 이름·재료를"로 바꿨다.
- `chat-intent-followup.txt` 14 → 10줄(1,281자 / 653토큰 → 754자 / 376토큰). followsPrevious·의도 이름(RECOMMEND·RECIPE_DETAIL)·이어짐 예시를 지우고, 이어지는 말이면 조건 칸에 쓸 값과 레시피 자세히 대상을 꺼내라는 규칙만 남겼다.
- 꺼내기 호출만 시간 제한 10초·재시도 없음: Spring AI 2.0.1은 호출 옵션의 시간 제한만 요청마다 쓰고 재시도 수는 OpenAI 클라이언트를 만들 때 정한다. 그래서 `ChatClientConfig.intentChatModel`이 기본 모델(`spring.ai.openai`, 60초·재시도 1번)의 옵션을 복사해 재시도 0번인 꺼내기 전용 모델을 만든다. 답 생성·후기 요약·비슷한 요리 호출은 그대로다.

### Jev 단독 확인 (되돌림 회차 분석과 평가)
- 되돌림 회차 분석(d2 결과, 다시 부르지 않음, [jev-only-fallback](eval-results/jev-only-fallback/fallback-analysis.txt)): confidence 0.8 미만이라 OpenAI로 되돌린 35회차에서 의도 일치 OpenAI 85.7% vs Jev 그대로 74.3%. 0.6~0.8에서는 같고, 차이는 0.6 미만의 냉장고 질문(F12 "조미료 뭐 있어?" → 일반, F14 "두부 아직 있어? 없으면 장바구니에 담아줘" → 사기)뿐이다.
- 평가(각 3회, 맛 반대 허용 기준, d2와 같은 방법으로 셈):

| 세트 | d2 (엄격 / 허용) | Jev 단독 (엄격 / 허용) | 새로 틀림 | 새로 맞음 |
|---|---|---|---|---|
| 기본 122 | 107 / 114 | 106 / 113 | 냉장고: F12, F14 / 그 밖: T04 | R17, T14 |
| 확인용 45 | 41 / 42 | 40 / 41 | 그 밖: H17 | - |
| 자해 기준 16 | - | 15 / 15 | S02 (위) | - |

- 그 밖 새로 틀린 문장의 이유:
  - T04 "안 달게 해줘": 의도는 맞고(추천) Jev 맛 답이 단맛 원함·순한맛 원함이다. d2에서는 Jev 의도 confidence 0.28~0.37이라 OpenAI(피할 맛 단맛)로 되돌려 맞았다. 되돌림이 가리던 Jev 맛 오답이다.
  - H17 "된찌": Jev는 d2와 같이 특정 요리로 답했는데, OpenAI가 요리 이름을 "된장찌개"로 풀어 써서 메시지에 그대로 없어 버려지고 특정 분류(찌개)로 갔다. d2에서는 "된찌" 그대로 꺼냈다. 프롬프트를 줄인 뒤 꺼내기가 달라진 것이다("말한 그대로 쓴다" 문장은 그대로 있다).
- 새로 맞은 R17 "주식으로 먹을 거 추천해줘"는 confidence 0.67~0.84에서 OpenAI(OFF_TOPIC)로 되돌던 회차가 없어졌고, T14 "얼얼한 거"는 OpenAI 맛 답 대신 Jev 맛 답을 쓴다.
- 비용·지연(기본 세트): OpenAI 입력 회차당 2,229 → 1,242토큰, 출력 83 → 56토큰, 비용 $0.1382 → $0.0792. 의도 결정 중앙값 1,577 → 1,702ms(OpenAI 호출 평균 1,691 → 1,850ms, 외부 요인으로 본다). 자해 세트 첫 회차는 19.6초가 걸렸다(콜드 스타트, Jev도 6초).

### 맛·요리 이름 수정과 다시 평가 (jev-only-v2, 2026-11-17)
- **맛**: Jev 맛 질문 설명 끝에 "그 맛을 줄이거나 없애 달라는 뜻이면 피함이다"를 더했다(`JevSecondQuestions.TASTE_REDUCE_IS_AVOID`, 단어 예시 없음).
  T04 "안 달게 해줘"는 단맛이 원함 → 피함으로 바뀌었다(3/3). 다만 Jev가 짠맛·매운맛도 피함, 순한맛 원함으로 답해 여전히 틀린다(0/3, 실제 원함 [MILD] · 피함 [SALTY, SPICY, SWEET]).
- **요리 이름**: OpenAI 요리 이름이 메시지에 그대로 없어도, 메시지 한 단어(끝 문장 부호를 뺀 두 글자 이상)의 글자가 요리 이름에 순서대로 다 있으면 받는다(`IntentCorrector.dishSpoken`). 비슷한 요리의 정식 이름 확인과 같은 함수(`ChatTexts.containsInOrder`, SimilarDishFinder에서 옮김)를 쓴다. 한 글자 단어("면", "국")는 거의 모든 이름에 들어 있어 보지 않고(지시에 없던 조건), 키워드 줍기는 글자 그대로만 본다.
  H17 "된찌"는 요리 이름 된장찌개로 이름 검색을 한다(3/3). 평가 세트 기대값이 `dish: 된찌`(말한 그대로)라서 평가에서는 여전히 틀림으로 센다. 확인용 세트라 기대값은 고치지 않았다.
- 의도 리플레이 기준값은 달라지지 않았다.
- 평가(각 3회, 맛 반대 허용): 기본 113 → 113, 확인용 41 → 41. 다수결이 바뀐 문장은 없고, 회차 수만 바뀐 문장은 흔들림이다(R17 3/3 → 2/3 넓은 요청 요리 이름이 한 번 비어 옴, T15 0 → 1/3, N01 1 → 0/3, T01 1 → 0/3, H07 1 → 0/3, H21 3 → 2/3 요리 이름이 한 번 비어 옴, H34 2 → 3/3). 결과: [jev-only-v2-main](eval-results/jev-only-v2-main/), [jev-only-v2-holdout](eval-results/jev-only-v2-holdout/).

### 전체 테스트 흔들림 기록
- 2026-11-17 Jev 단독 첫 전체 테스트(2449개)에서 `RefreshServiceTest.rotatesRefreshTokenAndRejectsReplay`(`expected true but was false`)와 `PopularRecipeServiceCacheIntegrationTest` 2개(`TooManyActualInvocations`·`WantedButNotInvoked`, 순위 조회 횟수)가 실패했고, 두 클래스만 다시 돌리면 통과했다.
- 맛·요리 이름 수정 뒤 전체 테스트(2452개)는 한 번에 모두 통과했다(12분 33초, 앞서 3분 안팎보다 느림. 가장 느린 클래스는 앱 전체를 띄우는 `TakeCareOfMyFridgeBeApplicationTests` 88초). 그래서 위 실패는 흔들림으로 기록한다. `PopularRecipeService`는 서버 답이 부를 때만 쓰이고 그 캐시 테스트는 상담 코드를 거치지 않는다.

### 다음 단계
- **냉장고 칩**: 냉장고 질문(조미료, 처리해야 할 것, 재료가 있는지)은 이번에 고치지 않았다(F11·F12·F14). 화면의 냉장고 칩으로 의도 판단 없이 냉장고 보기를 고르게 한다. `FridgeShopWordChecker`는 그대로 뒀다.
- Jev 맛 답이 한 맛을 피하라는 말에 다른 맛까지 피함·순한맛 원함으로 답하는 것(T04), 줄임말 평가 기대값(H17)을 정식 이름으로 볼지.

