# 상담 레시피 검색 기준 기록 (고치기 전)

상태: 초안 (고치기 전 현재 동작 기록) · 작성일: 2026-11-13

AI 상담의 요리 이름 추천("나가사키 짬뽕", "김피탕" 등)을 고치기 전에, 지금 코드가 어떻게 동작하는지 남깁니다. 기능 코드(src/main)는 바꾸지 않았고 테스트·문서만 더했습니다. 1단계 수정 뒤 이 표와 비교합니다.

- 근거 코드: `RecipeCandidateCollector.searchKeywords`, `RecipeCandidateFacade.supplementKeywords`, `RecipeSearchService.search`
- 관련 문서: [레시피 검색 벡터 폴백 평가](recipe-vector-fallback-evaluation.md)

## 기대값(정답)과 현재 결과

의도 판단(AI + 서버 보정)은 실제 OpenAI 키가 있어야 돌릴 수 있어 이번에는 측정하지 못했습니다(아래 "다시 측정하는 법"). 검색 칸은 로컬 ES에서 잰 값입니다.

| id | 문장 | 기대값 (정답) | 의도 판단 현재 결과 | 레시피 검색 현재 결과 |
|---|---|---|---|---|
| N09 | 나가사키 짬뽕 | RECOMMEND, 요리 이름 나가사키 짬뽕 | 미측정 | "나가사키 짬뽕" 글자 0건 → 벡터. 키워드가 ["나가사키 짬뽕", "짬뽕"]이면 "짬뽕"은 검색하지 않음 (버그 1) |
| N10 | 김피탕 | RECOMMEND, 요리 이름 김피탕 | 미측정 | 글자 0건 (김/피/탕으로 쪼개짐) → 벡터 |
| R06 | 김피탕 같은 요리 추천해줘 | RECOMMEND, 요리 이름 김피탕 | 재생 기준값(기록된 AI 결과 3번): 요리 이름 **비어 있음**, 키워드 김피탕/탕수육/깐풍기 | 키워드가 하나도 안 맞으면 분류·냉장고 점수 후보로 카드가 채워짐 (버그 2) |
| G14 | 김피탕이 뭐야? | GENERAL | 미측정 | 해당 없음 |
| T17 | 매운 거 말고 짬뽕 같은 거 | RECOMMEND, 피할 맛 SPICY, 요리 이름 짬뽕 | 미측정 | "짬뽕" 글자 1건(짬뽕해물탕밥). 피할 맛 SPICY면 ES 후보 검색에서 매운 레시피가 빠진다 |
| T18 | 매운 거 말고 김치찌개 | RECOMMEND, 피할 맛 SPICY, 요리 이름 김치찌개 | 미측정 | "김치찌개" 글자 19건. 콕 집은 요리(NAME)는 피할 맛이어도 남긴다 (`keepsNamedDish_whenAvoidingItsTaste`) |

R06 칸은 `chat-intent-replay/expected.csv`의 R06 세 줄입니다. 기록된 AI 원래 결과(`inputs.csv`)의 요리 이름이 비어 있고, 서버 보정도 채우지 않습니다.

## 재현 테스트 (현재 동작 고정)

`RecipeCandidateFacadeTest`에 ES 응답을 목으로 둔 테스트 2개를 더했습니다. 이름 끝 `_현재동작`은 지금의 잘못된 동작을 고정한다는 뜻이고, 1단계에서 기대값을 뒤집습니다.

| 테스트 | 고정한 현재 동작 | 1단계 뒤 기대 |
|---|---|---|
| `skipsLaterKeyword_whenFirstKeywordFallsBackToVector_현재동작` | 키워드 ["나가사키 짬뽕", "짬뽕"]에서 첫 키워드가 글자 0건 → 벡터(나가사키부대찌개)를 쓰면 "나머지 1개 생략"으로 "짬뽕"을 검색하지 않아 짬뽕해물탕밥이 후보에 없음 | "짬뽕"을 글자로 찾아 후보에 넣음 |
| `fillsCardsWithCategoryAndFridgeHits_whenNoKeywordMatches_현재동작` | "김피탕"이 글자·벡터 모두 0건이어도 분류(튀김)·냉장고 점수로 모은 후보로 카드 3장이 채워지고 `keywordsNotFound`만 true | 맞는 요리가 없음을 먼저 알리고 상관없는 카드로 채우지 않음 (정책은 1단계에서 정함) |

첫 테스트는 ES 후보 검색에 짬뽕 레시피가 없다고 가정합니다. 로컬 색인에서는 필터가 없으면 후보 검색의 키워드 조건("짬뽕", cross_fields AND)으로 짬뽕해물탕밥이 걸려 늘린 이름(EXPANDED)으로 맞으므로 벡터 보충 자체가 일어나지 않습니다. 이 버그는 후보 검색에서 그 레시피가 빠졌을 때(피할 맛·시간 조건, 100건 밖)와 DB 경로(`collectFromDb`도 같은 `searchKeywords`를 씀)에서 드러납니다.

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
| 짬뽕 | 짬뽕 | 1 (짬뽕해물탕밥) |
| 나가사키 | 나가사키 | 2 (이름 기준: 나가사키부대찌개, 아귀나가사키조림) |
| 김치찌개 | 김치, 찌개 | 19 |

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

## 남은 결정

- 키워드가 하나도 맞지 않을 때 카드를 아예 비울지, 안내 뒤 비슷한 요리를 보일지 (버그 2의 1단계 기대값)
- 벡터 검색 한 번 제한을 유지하면서 뒤 키워드의 글자 검색은 계속할지 (버그 1)
