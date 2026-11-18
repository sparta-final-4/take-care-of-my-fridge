# Jev 판단 연결 설계 (app.chat.jev.mode)

상태: **설계 (코드 변경 없음)** · 작성일: 2026-11-17 · 브랜치: `feat/chat-jev-shadow`

지금 Jev는 그림자 모드(`app.chat.jev.enabled`)로만 돈다. 결과는 비교 로그에만 남고 상담에 쓰지 않는다.
이 문서는 설정을 `app.chat.jev.mode`(`off` / `shadow` / `on`, 기본 `off`)로 바꾸고, `on`이면 Jev 판단으로 상담 의도를 정하는 방법을 정한다.

근거 자료:
- Jev 평가: [jev-v4 summary](eval-results/jev-v4/) (122문장 × 3회). 요청 종류·1등 분류·맛·의도 일치율, confidence 구간
- OpenAI 평가와 보정 하나씩 끔: [eval-results](eval-results/) `all-on`(120/121), `all-off`(104), `off-*`
- 넓은 요청 요리 이름: [broad-request-llm-check.md](broad-request-llm-check.md) 프롬프트 v2 (맞는 레시피 88%)
- 벡터 검색 확인: [vector-broad-request-check.md](vector-broad-request-check.md) (넓은 요청에 쓸 수 없음)

> 지시서가 가리킨 `docs/testing/chat-intent-corrections-eval.md`는 없다. 보정별 효과 숫자는 이 브랜치에서 돌린 `eval-results/off-*`(2026-11-16, 121문장)로 적었다.

## 1. 목표 흐름

```
메시지
 ├─ Jev 1차 (의도·묻는 말·냉장고 범위) ─┐        ← 둘을 함께 시작한다 (지금 그림자 모드와 같은 스레드 구조)
 └─ OpenAI 의도 분석 (꺼낸 값만 씀) ────┤
                                        ▼
 Jev 의도 confidence < 0.9 또는 Jev 실패 → OpenAI 결과 + 기존 보정 (지금 흐름 그대로)
 Jev 의도가 추천·다시 추천이면 Jev 2차 (요청 종류, 1등 분류, 맛, 밀키트, 요리 이름을 말했나)
   ├ 특정 요리 (DISH)    → OpenAI dishText → 기존 이름 검색 → 못 찾으면 비슷한 요리(SimilarDishFinder, 기존)
   ├ 특정 분류 (CATEGORY) → Jev 1등 분류로 분류 조건
   ├ 넓은 요청 (BROAD)    → 넓은 요청 프롬프트로 요리 이름 5개(캐시) → 키워드 검색
   └ 없음 (NONE)          → 냉장고 기반 (기존)
 그 밖의 의도 → Jev 의도 + OpenAI가 꺼낸 값(냉장고·사기 이름, 레시피 자세히 대상 등)
```

- 지연: Jev 1·2차 합계는 중앙값 355ms·95% 417ms(jev-v4)이고 OpenAI 의도 분석(1~2초)과 함께 돈다. 넓은 요청 요리 이름 호출도 Jev 2차가 끝난 직후 시작하면 OpenAI를 기다리는 동안 끝난다(캐시면 0). 그래서 `on`의 응답 시간은 지금과 거의 같다.
- 비용: Jev 메시지당 입력 약 2,100토큰(약 $0.00008), 넓은 요청 요리 이름은 캐시가 없을 때만 gpt-4o-mini 한 번(약 300토큰).

## 2. 설정

| 값 | 동작 |
|---|---|
| `off` (기본) | Jev를 부르지 않는다. 지금 `enabled: false`와 같다 |
| `shadow` | 지금 `enabled: true`와 같다. 결과는 비교 로그에만 |
| `on` | Jev 결과로 의도·조건을 정한다. 비교 로그는 계속 남긴다(되돌린 경우 포함) |

- `ChatJevProperties(boolean enabled)` → `ChatJevProperties(Mode mode)`, `enum Mode { OFF, SHADOW, ON }`. 환경 변수 `CHAT_JEV_MODE`(기본 `off`).
- 기존 `CHAT_JEV_ENABLED`는 한 번에 바꾼다(배포 설정에서 `CHAT_JEV_ENABLED=true` → `CHAT_JEV_MODE=shadow`). 둘 다 읽는 호환 코드는 두지 않는다.

## 3. OpenAI 의도 분석: on에서 쓰는 칸과 버리는 칸

| 칸 (`IntentResult`) | on에서 | 이유 |
|---|---|---|
| `intent` | **버림** (Jev 되돌림일 때만 씀) | Jev 의도 116/120, 보정 전 OpenAI 117/120과 비슷하고 보정 없이 나온다 |
| `wantedTastes` · `avoidTastes` | **버림** | Jev 원함 19/21·피함 16/17 (보정 전 OpenAI 17·17) |
| `moodCategories` | **버림** | 특정 분류는 Jev 1등 분류 12/12. 넓은 요청은 분류 대신 요리 이름 |
| `keywords` | **버림** | 특징·넓은 요청은 넓은 요청 요리 이름으로, 요리 이름은 dishText로 |
| `dishText` | **씀** | Jev는 이름을 꺼내지 못한다(Box는 이미지 전용, jev-api-notes h) |
| `preferenceText` | **씀** | 요청에 못 맞췄을 때 안내 문구, 넓은 요청 캐시 키 |
| `includeIngredients` · `excludeIngredients` · `useSelectedOnly` | **씀** | 재료 이름·조건 |
| `maxMinutes` | **씀** | 시간 |
| `targetIndex` · `targetName` · `detailQuestion` | **씀** | 레시피 자세히 대상 |
| `removed` | **씀** | 다시 추천에서 지울 조건 |
| `fridgeScope` | **씀** | Jev `fridge_scope`는 아직 평가하지 않았다(평가 세트 scope 칸을 Jev 평가에 넣은 뒤 다시 정한다) |

**프롬프트는 이번에 그대로 두고 칸만 무시한다.** `on`에서 Jev confidence가 낮거나 Jev가 실패하면 OpenAI 결과로 되돌려야 해서, 의도·맛·분류를 뽑는 프롬프트(`chat-intent.txt`, 첫 질문 1,751토큰)가 계속 필요하다. 되돌림 비율이 낮다는 것을 운영 로그로 확인한 뒤 줄인다(8절 2단계).

## 4. ChatIntentResolution 만들기

새 클래스: **`domain/chat/intent/JevIntentResolver`** (`@Component`). `IntentCorrector`와 같은 폴더, 같은 역할(AI 결과 → 상담이 쓰는 결과)의 Jev판이다.

```java
// ChatIntentFacade.decide()에서 mode == ON이고 Jev 결과를 쓸 수 있을 때
Optional<IntentResult> resolveWithJev(CorrectionInput input, JevIntentJudgement jev, IntentResult openAi)
```

1. 의도 = `jev.first().intent()` (서비스 규칙 적용 값: 직전 추천이 없는 REFINE·RECIPE_DETAIL → RECOMMEND, 이미 `JevIntentClassifier.serviceIntent`).
2. OpenAI 결과에서 3절의 "씀" 칸만 남긴 `IntentResult`를 만든다. 의도·맛·분류·키워드는 Jev 쪽 값으로 채운다.
   - 맛: Jev 원함·피함(Member.Taste 이름)
   - 분류: 요청 종류가 특정 분류이고 1등 분류가 있을 때만 `[1등 분류]`, 아니면 빈 목록
   - 키워드: 넓은 요청이면 넓은 요청 요리 이름(5절), 특정 요리면 `[dishText]`, 그 밖이면 빈 목록
3. 그 결과를 지금 `ChatIntentFacade.decide()`의 `switch`(RECOMMEND → `toRecipeQuery`, REFINE → `refine`(직전 조건 합치기), RECIPE_DETAIL → `recipeDetail`, FRIDGE·SHOP → `lookupOf` …)에 그대로 넣는다. **결과 모양(`ChatIntentResolution`)과 그 뒤 흐름은 바꾸지 않는다.**
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

아래 중 하나면 그 메시지는 **지금 흐름(OpenAI 결과 + IntentCorrector 전체)**으로 정한다. 비교 로그에는 되돌린 이유를 남긴다.

1. Jev 1차 실패(키 없음, 시간 초과 3초, HTTP 오류, 답 형식 오류, 실행기 자리 없음)
2. Jev 의도 confidence < **0.9**
3. 의도가 추천·다시 추천인데 Jev 2차가 실패

jev-v4로 다시 확인한 0.9 기준 (의도를 기대한 회차, 단어 차단 제외):

| 기준 | 쓰는 회차 | 그중 정답 | 되돌리는 회차 | 되돌리는 회차의 Jev 정답 |
|---|---|---|---|---|
| ≥ 0.95 | 58.3% (208) | 98.6% | 41.7% | |
| **≥ 0.90** | **72.0% (257)** | **98.8%** | **28.0% (100)** | 91.0% (91) |
| ≥ 0.80 | 86.3% | 98.1% | 13.7% | |
| ≥ 0.70 | 90.2% | 98.1% | 9.8% | |

- 0.9 이상에서 틀린 회차는 3번이다(P01 "당근 사고 싶어" 0.96 → 추천). 0.8로 내려도 정답률은 98.1%로 비슷하지만 더 많이 쓰는 이득이 크지 않아 처음에는 0.9로 시작하고 운영 비교 로그로 다시 본다.
- **요청 종류 confidence로는 되돌리지 않는다.** jev-v4에서 0.7 미만 구간도 83.6%가 맞았고(종류 전체 63/68), 되돌릴 OpenAI 쪽에 요청 종류가 없다. 경계 규칙(5절)만 둔다.
- 설정: `app.chat.jev.min-intent-confidence: 0.9`.

## 7. on에서 IntentCorrector 단계

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
- `on`에서 되돌린 메시지(6절)는 모든 단계를 지금처럼 거친다.

## 8. 평가

### 방법
- `ChatIntentEvalManualTest`에 모드를 준다: `OPENAI_INTENT_EVAL_JEV_MODE=on`(기본 off). on이면 `TYPESAFE_API_KEY`도 필요하고, 같은 평가 세트를 `ChatIntentFacade.resolve`(Jev 연결 포함)로 돌린다. 넓은 요청 요리 이름 생성기도 실제 OpenAI로 부른다(캐시 없음).
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

### on 전환 후 지울 수 있는 코드 (2단계)

**전제: 되돌림(6절)을 OpenAI 결과 + 보정 전체로 하는 동안은 지울 수 없다.** 운영 비교 로그로 되돌림 비율과 되돌린 메시지의 정확도를 본 뒤, 되돌림을 "보정 없는 OpenAI 결과" 또는 "Jev 낮은 confidence 그대로"로 바꾸기로 정하면 아래를 지운다.

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

1. **되돌림 방식**: `on`에서 Jev confidence가 낮을 때 OpenAI 결과 + 보정 전체로 되돌린다(이 문서의 안). 이러면 보정 코드를 바로 지울 수 없다. 처음부터 "보정 없는 OpenAI"로 되돌리면 지울 수 있지만, 되돌린 메시지(약 28%)에서 정확도가 떨어진다(all-off 104/121).
2. **의도 confidence 기준**: 0.9로 시작(쓰는 회차 72%, 정답 98.8%). 0.8이면 86%를 쓰고 98.1%.
3. **넓은 요청 캐시 키**: OpenAI `preferenceText`(없으면 메시지). 같은 "술안주"라도 "술안주 추천해줘"·"술안주 뭐 있어"가 같은 키가 되게 할지.
4. **넓은 요청 프롬프트 v2의 남은 문제**: 분류 이름(김치·튀김·샐러드)·묶음 말(스테이크·파스타·치킨)·설명형 이름이 여전히 나온다(v2 26/180). 그대로 연결하고 검색(SPLIT 빼기, 0건 이름 버리기)으로 막을지, 프롬프트를 더 고친 뒤 연결할지.
5. **요청 종류 경계**: 특정 분류 + 1등 분류 없음 → 밀키트면 없음, 아니면 넓은 요청(5절). 특정 요리 + dishText 없음 → 넓은 요청.
6. **밀키트 판단**: 지금처럼 단어로(Jev `mealkit_wanted`는 평가 전이라 쓰지 않음).
7. **냉장고 범위**: OpenAI `fridgeScope` + 서버 보충을 그대로 쓴다. Jev `fridge_scope`는 평가에 넣은 뒤 정한다.
8. **설정 이름 변경**: `CHAT_JEV_ENABLED` → `CHAT_JEV_MODE`를 한 번에 바꾼다(호환 코드 없음). 배포 설정 변경이 같이 필요하다.
9. **지금 남은 Jev 의도 오답**: P01 "당근 사고 싶어"(추천 0.96, 0.9 기준을 넘어 되돌리지 않음), F11·F12·F14(냉장고 → 일반·장보기). on에서 그대로 틀린다. 문구를 더 고칠지, P01처럼 confidence가 높은 오답을 받아들일지.
