# 상담 레시피 검색 기준 기록

상태: 초안 (0단계 기준 기록 + 1단계 검색 수정 결과) · 작성일: 2026-11-13 · 갱신: 2026-11-13 (1단계)

AI 상담에서 요리 이름("나가사키 짬뽕", "김피탕" 등)을 말했는데 엉뚱한 레시피가 나오는 문제의 기록입니다. 0단계에서 고치기 전 동작을 남겼고(기능 코드 변경 없음), 1단계에서 검색 단계를 고친 뒤 결과를 "1단계 후" 칸에 더했습니다. 의도 판단(프롬프트, ChatIntentFacade, IntentCorrector)과 레시피 목록 API 동작은 바꾸지 않았습니다.

- 근거 코드: `RecipeCandidateCollector.searchKeywords`·`searchKeywordsOnce`, `RecipeCandidateFacade.supplementKeywords`·`dishNotFound`, `RecipeSearchService.searchKeywordOnly`·`analyzeTokens`
- 관련 문서: [레시피 검색 벡터 폴백 평가](recipe-vector-fallback-evaluation.md), [작업 기록](../worklogs/work-chat-dish-name-search.md)

## 1단계에서 바꾼 것

1. **벡터 뒤에도 글자 검색 계속**: 벡터 검색은 한 번의 추천에서 한 번만 쓰고, 그 뒤 키워드는 끊지 않고 글자로만 찾습니다. 로그는 `나머지 N개 생략` 대신 키워드마다 `짬뽕 1건(글자만)`으로 남깁니다.
2. **요리 이름 조각 보충**: 키워드로 제대로 맞은 레시피(`KeywordMatch.strong`)가 없을 때 도는 보충 단계에서만 합니다. 요리 이름(없으면 첫 키워드)이 글자로 0건이면 `korean_search` 토큰 중 두 글자 이상만 골라 글자로 찾습니다. 토큰이 1개뿐이거나 두 글자 이상 토큰이 없으면 하지 않고, `_analyze`가 실패하면 공백으로 나눈 단어로 대신합니다. 이렇게 찾은 레시피는 새 종류 `KeywordMatch.Type.FRAGMENT`("이름 조각")로 표시합니다. 카드 이유는 "나가사키 짬뽕과 이름이 비슷한 요리예요"이고, "찾으신 ○○"로 쓰지 않습니다.
3. **못 찾으면 카드를 채우지 않음**: 요리 이름을 말했는데(요리 이름이 있거나, 원한 표현 없이 키워드만 있음) 보충 뒤에도 제대로 맞은 후보가 없으면 분류·냉장고 점수로만 걸린 후보와 글자 일부로만 걸린(SPLIT) 후보를 뺍니다. 벡터로 찾은 후보(오타 "김치찌게" → 김치찌개)는 남깁니다. 남는 게 없으면 카드 0장 → 정해진 답 `ChatStreamFacade.NO_RECIPE_REPLY`가 나갑니다.

## 기대값(정답)과 결과

의도 판단(AI + 서버 보정)은 실제 OpenAI 키가 있어야 돌릴 수 있어 측정하지 못했습니다(아래 "다시 측정하는 법"). 검색 칸은 로컬 ES와 목 테스트로 확인한 값입니다.

| id | 문장 | 기대값 (정답) | 의도 판단 현재 결과 | 레시피 검색 0단계 (고치기 전) | 레시피 검색 1단계 후 |
|---|---|---|---|---|---|
| N09 | 나가사키 짬뽕 | RECOMMEND, 요리 이름 나가사키 짬뽕 | 미측정 | "나가사키 짬뽕" 글자 0건 → 벡터. 키워드가 ["나가사키 짬뽕", "짬뽕"]이면 "짬뽕"은 검색하지 않음 (버그 1) | 벡터 뒤에도 "짬뽕"을 글자로 찾음. 요리 이름 조각 나가사키·짬뽕으로 짬뽕해물탕밥·나가사키부대찌개·아귀나가사키조림이 후보(이름 조각). 안내 "나가사키 짬뽕 레시피는 없어서, 비슷한 …을 골랐어요" |
| N10 | 김피탕 | RECOMMEND, 요리 이름 김피탕 | 미측정 | 글자 0건 (김/피/탕으로 쪼개짐) → 벡터 | 조각 없음(모두 한 글자). 벡터 결과가 있으면 그것만, 없으면 카드 0장 + "찾지 못했어요" |
| R06 | 김피탕 같은 요리 추천해줘 | RECOMMEND, 요리 이름 김피탕 | 재생 기준값(기록된 AI 결과 3번): 요리 이름 **비어 있음**, 키워드 김피탕/탕수육/깐풍기 | 키워드가 하나도 안 맞으면 분류·냉장고 점수 후보로 카드가 채워짐 (버그 2) | 탕수육·깐풍기가 늘린 이름으로 맞으면 지금처럼 그 카드. 하나도 안 맞으면 분류·냉장고 후보로 채우지 않음 |
| G14 | 김피탕이 뭐야? | GENERAL | 미측정 | 해당 없음 | 해당 없음 (의도 판단 미변경) |
| T17 | 매운 거 말고 짬뽕 같은 거 | RECOMMEND, 피할 맛 SPICY, 요리 이름 짬뽕 | 미측정 | "짬뽕" 글자 1건(짬뽕해물탕밥). 피할 맛 SPICY면 ES 후보 검색에서 매운 레시피가 빠진다 | 변화 없음 ("짬뽕"이 글자로 찾혀 나누지 않음). 짬뽕해물탕밥은 이름(NAME)으로 맞아 피할 맛이어도 남는다 |
| T18 | 매운 거 말고 김치찌개 | RECOMMEND, 피할 맛 SPICY, 요리 이름 김치찌개 | 미측정 | "김치찌개" 글자 19건. 콕 집은 요리(NAME)는 피할 맛이어도 남긴다 (`keepsNamedDish_whenAvoidingItsTaste`) | 변화 없음 (원래 이름으로 찾혀 김치/찌개로 나누지 않음) |

R06 칸은 `chat-intent-replay/expected.csv`의 R06 세 줄입니다. 기록된 AI 원래 결과(`inputs.csv`)의 요리 이름이 비어 있고, 서버 보정도 채우지 않습니다. 1단계 후에도 재생 테스트는 그대로 통과합니다(의도 판단 미변경).

## 로컬 ES로 넣어 본 검색 단계 결과 (1단계 후)

로컬 ES에 같은 조건(cross_fields AND, `korean_search`)으로 보충 단계의 검색을 그대로 재현한 값입니다(2026-11-13). 카드까지 낸 결과는 아닙니다. 카드는 MySQL(레시피 정보·회원 냉장고)이 있어야 해서 앱 전체를 띄우지 못해 측정하지 않았습니다.

| 요리 이름 | 원래 이름 글자 검색 | 분석기 토큰 | 조각 검색 | 보충 후보 |
|---|---|---|---|---|
| 나가사키 짬뽕 | 0건 | 나가사키 / 짬뽕 | 나가사키 2건, 짬뽕 1건 | 나가사키부대찌개, 아귀나가사키조림, 짬뽕해물탕밥 (이름 조각) |
| 나가사키짬뽕 | 0건 | 나가사키 / 짬뽕 | 나가사키 2건, 짬뽕 1건 | 위와 같음 |
| 김피탕 | 0건 | 김 / 피 / 탕 | 하지 않음 (두 글자 이상 토큰 없음) | 벡터 결과만 (없으면 카드 0장) |
| 김치찌개 | 19건 (김치찌개, 꽁치김치찌개, 참치김치찌개, …) | 나누지 않음 | 하지 않음 | 원래 이름 결과 |

"나가사키" 조각이 나가사키부대찌개·아귀나가사키조림도 데려옵니다. 둘 다 이름 조각으로 맞은 것으로 보고, 키워드 순서(조각은 맨 뒤)와 냉장고 점수로 정렬합니다.

## 재현·회귀 테스트

0단계의 `_현재동작` 테스트 2개를 고친 동작으로 바꾸고 이름에서 `_현재동작`을 뺐습니다.

| 0단계 테스트 | 1단계 테스트 | 1단계 기대 |
|---|---|---|
| `skipsLaterKeyword_whenFirstKeywordFallsBackToVector_현재동작` | `searchesLaterKeywordByText_whenFirstKeywordFallsBackToVector` | 첫 키워드가 벡터로 가도 "짬뽕"을 글자로 찾아 짬뽕해물탕밥(늘린 이름)이 후보에 들어감. "나가사키"는 조각으로 찾음 |
| `fillsCardsWithCategoryAndFridgeHits_whenNoKeywordMatches_현재동작` | `returnsNoCandidates_whenDishMatchesNothing` | 글자·벡터·조각 모두 0건이면 분류·냉장고 후보로 카드를 채우지 않음 (후보 0개, `keywordsNotFound` true) |

조각 보충 테스트: 띄어 쓴 이름(`searchesDishFragments_whenSpacedDishNotFound`), 붙여 쓴 이름(`…_whenJoinedDishNotFound`), 원래 이름으로 찾으면 나누지 않음(`skipsDishFragments_whenDishFoundByText`), 한 글자 토큰만(`…_whenAllTokensAreSingleCharacters`), 분석 실패 시 공백 대체(`splitsByWhitespace_whenAnalyzeFails`). 못 찾음 처리 테스트: 벡터 후보만 남김(`keepsOnlyVectorHits_whenDishMatchesNothingByText`), 특징만 말한 추천은 그대로(`keepsFridgeCandidates_whenOnlyFeatureAsked`).

## ES 분석기 확인

로컬 ES 9.2.0, `recipes` 별칭 → `recipes_20261107205437`, 1,693건 (2026-11-13 확인).

```http
GET recipes/_analyze
{ "analyzer": "korean_search", "text": "김피탕" }

GET recipes/_analyze
{ "analyzer": "korean_search", "text": "나가사키 짬뽕" }
```

| 입력 | korean_search 토큰 | 글자 검색 건수 (name^3·ingredients.text^2·summary, cross_fields AND) |
|---|---|---|
| 김피탕 | 김, 피, 탕 | 0 |
| 나가사키 짬뽕 | 나가사키, 짬뽕 | 0 |
| 나가사키짬뽕 | 나가사키, 짬뽕 | 0 |
| 짬뽕 | 짬뽕 | 1 (짬뽕해물탕밥) |
| 나가사키 | 나가사키 | 2 (나가사키부대찌개, 아귀나가사키조림) |
| 김치찌개 | 김치, 찌개 | 19 |
| 탕짜면 | 짜 | (토큰 1개라 조각 보충 대상 아님) |

Windows PowerShell 5.1에서 curl로 한글 본문을 보내면 UTF-8이 아니어서 400이 납니다. 본문을 UTF-8 바이트로 보내고 응답도 UTF-8로 읽어야 합니다.

```powershell
$body = [Text.Encoding]::UTF8.GetBytes('{"analyzer":"korean_search","text":"김피탕"}')
$res = Invoke-WebRequest -UseBasicParsing -Method Post -Uri 'http://localhost:9200/recipes/_analyze' `
  -ContentType 'application/json; charset=utf-8' -Body $body
[Text.Encoding]::UTF8.GetString($res.RawContentStream.ToArray())
```

## 회귀 테스트에 넣은 방식

- `chat-intent-eval.yml`: 기대값(정답)을 적는 곳이라 N09, N10, G14, T17, T18을 더했습니다. "김피탕 같은 요리 추천해줘"는 이미 R06(요리 이름 김피탕)이 있어 더하지 않았습니다. 이 평가는 `OPENAI_INTENT_EVAL=true`일 때만 도는 수동 테스트이고, 기대와 달라도 실패시키지 않고 일치율만 남기므로 CI에 영향이 없습니다.
- `chat-intent-replay`: 더하지 않았습니다. `inputs.csv`는 실제 AI가 낸 원래 결과를 기록한 것이고 `expected.csv`는 정답이 아니라 현재 서버 보정 결과의 스냅숏입니다(다른 줄이 하나라도 있으면 실패, 허용 실패 목록 없음). AI 키가 없어 원래 결과를 기록할 수 없고, 정답을 적으면 CI가 깨집니다.

## 다시 측정하는 법

take-care-of-my-fridge-be 폴더에서:

```bash
# 의도 판단 (실제 AI, 새 케이스만)
OPENAI_INTENT_EVAL=true OPENAI_INTENT_EVAL_KEY=sk-... OPENAI_INTENT_EVAL_ONLY=N09,N10,R06,G14,T17,T18 \
  ./gradlew cleanTest test --tests '*ChatIntentEvalManualTest' -i
```

결과 `build/reports/chat-intent-eval/eval-{시각}-runs.csv`의 AI 원래 값을 `chat-intent-replay/inputs.csv`에 옮기고 `CHAT_INTENT_REPLAY_RECORD=true`로 기준값을 기록하면 재생 테스트에도 넣을 수 있습니다.

운영 로그로 볼 것: `상담 추천 키워드 - … 검색: [… (벡터), … (글자만)]`, `상담 추천 요리 이름 조각 - … 토큰: …, 검색: …`, `상담 후보 요리 이름이 맞은 레시피가 없어 벡터 후보만 남김 - …`.

## 남은 결정

- (해결) 카드 0장 답: 요리 이름을 못 찾으면 "{요리 이름} 레시피는 아직 없어요. …", 뺄 재료 때문에 뺐으면 "{요리 이름}에는 {재료}이(가) 들어 있어서 추천에서 뺐어요. …"를 보냅니다(`NoRecipeReplyWriter`). 그 밖(시간 등 다른 조건)은 기존 `NO_RECIPE_REPLY` 그대로입니다.
- "나가사키" 같은 지명 조각이 부대찌개·조림까지 데려옵니다. 조각 중 마지막 토큰(요리 종류 말)만 쓸지는 실제 로그로 다시 봅니다.
- 콕 집은 요리가 알레르기·시간 조건으로 빠지면 다른 요리로 채우지 않고 "찾지 못했어요"가 나갑니다(`dropsNamedDish_whenItUsesExcludedIngredient`). 이게 맞는지 확인이 필요합니다.
