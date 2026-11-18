# WORK-CHAT-SIMILAR-DISHES: 상담 요리 이름 추천에 비슷한 요리 넣기 (2단계)

상태: 검증 대기 · 작성일: 2026-11-16 · 담당: 미기재

## 목표와 완료 기준
- 사용자 문제:
  - "김피탕"과 "김피탕 같은 요리 추천해줘"가 다른 결과를 낸다. 비슷한 요리를 늘릴지("같은"이 있을 때만)를 AI가 정하고, R06은 AI가 요리 이름을 비워 서버 보정도 채우지 않았다.
  - 요리 이름을 말한 추천에서 키워드로 맞은 후보가 일부라도 있으면 남는 자리를 냉장고·분류 후보로 채운다 ("나가사키짬뽕 레시피 추천해줘"에 냉장고 기반 요리가 섞임).
- 확인 가능한 완료 조건:
  - 같은 AI 결과(요리 이름·비슷한 요리)면 두 말의 추천 조건(RecipeQuery)이 같다.
  - "나가사키짬뽕" + 비슷한 요리 "짬뽕" → 짬뽕해물탕밥과 나가사키 조각 레시피만 후보, 냉장고·분류 후보 없음.
  - 요리 이름을 말하지 않은 추천은 지금처럼 3장을 채운다.
  - API 요청·응답 형식 변경 없음.

## 근거와 범위
- 요구사항·화면 ID·설계 문서: [상담 레시피 검색 기준 기록](../testing/chat-recipe-search-baseline.md), [1단계 작업 기록](work-chat-dish-name-search.md)
- 변경할 영역: 백엔드 chat(의도 결과·보정·의도 분기·후보 모으기·답 분기·Redis 캐시), 프롬프트(chat-intent.txt, chat-general.txt), 단어 설정(chat-words.yml ask.question), global/config/ai/ChatClientConfig(의도 판단 출력 토큰)
- 다른 도메인 영향·기존 승인 범위: search 도메인 코드 변경 없음(기존 `searchKeywordOnly`·`analyzeTokens` 재사용). API·DB·프론트 변경 없음.
- 미정 정책·가정:
  - 추천 시도(AI 일반 답 + 요리 이름 + 묻는 말 없음)는 스위치 `nameOnly`에 묶었다(이름만 말하기와 같은 계열).
  - "김피탕이 뭐야?"가 묻는 말로 잡히지 않아(기존 묻는 말 목록에 "뭐야" 없음) "뭐야, 뭐예요, 뭐에요, 뭔데"를 묻는 말에 더했다.
  - 요리 이름 보정(첫 키워드)은 밀키트 말과 맛 단어로 꾸민 여러 낱말("칼칼한 국물")은 요리 이름으로 보지 않는다(재생 T02·N01·F16에서 잘못 채워지는 것을 보고 더함).
  - 요리 이름을 말한 추천은 피할 맛 레시피로 뒤를 채우지 않는다(1~2장 그대로).

## 계획
| 단계 | 변경 | 검증 |
|---|---|---|
| A1 | 구조화 출력 확인 (이미 strict json_schema) | `OpenAiChatIntentExtractorTest.sendsStrictSchema_withIntentEnumAndSimilarDishes` |
| A2 | `IntentResult.similarDishes`, `searchKeywords` 합치기 | `IntentResultTest`, `ChatIntentFacadeTest$DishSearch` |
| A3 | chat-intent.txt 정리 | `ChatIntentPromptTest` |
| A4 | 요리 이름 보정(첫 키워드) | `IntentCorrectorTest`, `DishSearch.fillsDishFromFirstKeyword…`, `keepsDishEmpty…` |
| A5 | `SimilarDishStore` (Redis 캐시) | `SimilarDishStoreTest` |
| B6 | 추천 시도(`tryDish`, `Source.DISH_TRY`), 0장이면 일반 답 | `DishSearch.triesRecommend…`, `keepsGeneral_whenAskingAboutDish`, `ChatStreamFacadeTest.sendsGeneralReply_whenDishTryFindsNoCards` |
| C7 | 이름 조각 조건(NAME 없음) | `RecipeCandidateFacadeTest.searchesDishFragments_whenOnlySimilarDishMatches`, `skipsDishFragments_whenDishFoundByNameWithSimilarDishes` |
| C8 | 요리 이름을 말한 추천은 키워드로 찾은 후보만 | `returnsOnlyMatchedCandidate_whenDishAsked`, `fillsWithFridgeCandidates_whenNoDishAsked` |
| D | 평가 세트·기준 기록·작업 기록 | 문서 |

## AI 활용과 개발자 판단
- AI에 맡긴 작업·핵심 지시: 위 계획 전체(2단계). 검색 흐름의 1단계 동작은 유지, API 형식 유지, 새 라이브러리 금지, 재생 기준값 다시 기록 금지, 커밋 금지.
- 제안 중 채택한 내용과 이유:
  - 구조화 출력은 이미 `AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT`으로 적용되어 있었다. Spring AI 2.0.1 `OpenAiChatModel`은 strict를 따로 정하지 않으면 `json_schema`를 strict=true로 보내고, 스키마는 `IntentResult`(enum `ChatIntent`)에서 만든다. 코드 변경 없이 테스트로 고정했다.
  - 키워드 합치기는 `IntentResult.searchKeywords()`에 두어 "같은 AI 결과 → 같은 키워드"를 한 곳에서 보장했다.
  - 요리 이름을 말한 추천의 후보 거르기는 정렬 전(`rankableInfos`)에 했다. 정렬 뒤 상위 10개에서 거르면 키워드 후보가 냉장고 후보에 밀려 잘릴 수 있어서다. 그래서 1단계의 `onlyVectorHits`는 필요 없어져 지웠다(못 찾음 표시는 그대로).
  - 이름 조각만 찾는 단계(`supplementDishFragments`)를 벡터 보충과 따로 두었다. 키워드 보충을 할 때는 지금처럼 그 안에서 조각까지 찾고, 하지 않을 때만 조각 단계를 돈다.
  - 레시피 검색 보충에서 이미 모은 레시피(냉장고로 걸림)도 찾았으면 키워드 결과로 표시하게 했다(`foundPool`). 요리 이름을 말한 추천은 키워드 표시가 없으면 카드에서 빠지기 때문이다.
- 수정·보류한 제안과 이유:
  - 첫 키워드 요리 이름 보정을 지시대로만 하면 재생 T02("칼칼한 국물"), N01("국물 밀키트"), F16("밀키트")이 요리 이름이 되어, 밀키트 말·맛으로 꾸민 특징을 뺐다.
  - nameOnly의 레시피 이름(suggestNames) 분기는 지우지 않고 추천 시도(`tryDish`)로 옮겼다. AI가 일반 답 + 요리 이름 빈칸으로 낼 때의 안전망으로 남기되, 출처를 DISH_TRY로 해 0장이면 일반 답이 되게 했다. 레시피 이름 찾기 실패는 이제 예외를 내지 않고 못 찾은 것으로 본다.
  - 프롬프트 길이 테스트(3,300자 아래)를 지키려고 예시를 줄였다(3,280자).
  - chat-intent-followup.txt는 비슷한 요리·요리 이름 규칙이 겹치지 않아 바꾸지 않았다.
  - chat-recommend.txt는 이미 "후보 수만큼 [n]", "후보 수와 같게 말한다"고 해서 바꾸지 않았다. 대신 chat-general.txt의 추천 기준 설명(맞는 게 없으면 냉장고 요리 세 개)을 요리 이름을 말했을 때와 나눠 고쳤다.
- 개발자가 확인한 근거: 로컬 ES `_search`·`_analyze` 결과, 테스트 결과(아래), 재생 테스트에서 달라진 줄.

## 구현 결과
- `chat/dto/IntentResult`: `similarDishes` 칸(canonical에서 dishText 뒤), 예전 16칸 생성자, `withSimilarDishes`, `searchKeywords()`, sanitize(최대 5개).
- `chat/dto/ChatIntentResolution`: `Source.DISH_TRY`.
- `chat/config/ChatProperties`: `similarDishesTtl`(없으면 7일). application.yml `app.chat.similar-dishes-ttl: 7d`.
- `chat/service/SimilarDishStore`(신규): `chat:similar-dishes:{요리 이름 공백 제거·소문자}`, StringRedisTemplate + JSON, 적중이면 캐시 값, 아니면 AI 값 저장(빈 목록 제외), 장애면 WARN 후 AI 값.
- `chat/intent/IntentCorrector`: `checkDishAndKeywords`에 첫 키워드 요리 이름 보정(`keywordAsDish`)과 비슷한 요리의 분류 이름 빼기, `nameOnly`에서 레시피 이름 분기를 빼고 `tryDish` 추가, `Corrected.dishTry`.
- `chat/facade/ChatIntentFacade`: 추천 조건에 `searchKeywords()`와 캐시 값 사용, 추천 시도면 `Source.DISH_TRY`.
- `chat/facade/ChatStreamFacade`: DISH_TRY인데 카드 0장이면 일반 답.
- `chat/facade/RecipeCandidateFacade`: 이름 조각 단계(`supplementDishFragments`), 요리 이름을 말한 추천은 키워드 후보만 정렬(`rankableInfos`), 피할 맛 채우기 안 함, `onlyVectorHits` 제거.
- `chat/facade/RecipeCandidateCollector`: `searchDishFragmentsOnly`, `foundPool`(이미 모은 레시피도 키워드 표시).
- `chat/recommend/KeywordMatchClassifier`: 주석만.
- `global/config/ai/ChatClientConfig`: 의도 판단 출력 토큰 200 → 300 (비슷한 요리 5개가 더해져 잘리지 않게).
- 프롬프트: chat-intent.txt(similarDishes 규칙, keywords 역할, dishText 규칙, 사례 지시 삭제), chat-general.txt(추천 기준 1번).
- 단어: chat-words.yml `ask.question`에 뭐야·뭐예요·뭐에요·뭔데.
- 테스트: 위 계획 표의 테스트 추가·수정. 기존 테스트 중 바뀐 것: `IntentCorrectorTest.keepsAiDish_onlyWhenInMessage`(AI가 비우면 첫 키워드로 채움), `ChatIntentPromptTest`(사례 지시 삭제 확인으로 바꿈), `RecipeCandidateFacadeTest.keepsNamedDish_whenAvoidingItsTaste`(다른 찌개를 비슷한 요리 키워드로), `countsOnlyEsMarkedKeywordHits`(냉장고로만 걸린 후보가 빠짐).
- 평가 세트: `similarDishes: true` 기대 칸(R06, N09, N10, G14, T17, T18), N11 추가. 결과 CSV에 `aiSimilarDishes` 칸. 재생 테스트는 이 칸이 없는 예전 기록을 빈 목록으로 읽는다.
- 계획과 달라진 점: 위 "수정·보류한 제안". API·DB 변경 없음.

## 검증
| 명령·시나리오 | 실행 환경·조건 | 통과/실패/미실행 | 근거 |
|---|---|---|---|
| `./gradlew clean compileJava compileTestJava` | 로컬 Windows | 통과 | 실행 로그 |
| `./gradlew test --tests '*.domain.chat.*'` | 로컬 Windows, Docker | 실패 1건 (861건, 건너뜀 1): `ChatIntentReplayTest` (아래) | build/test-results |
| `ChatIntentReplayTest` | 위와 같음 | 실패 — 기준값과 6줄 다름 (R06 1~3, T08 1~3: 요리 이름 칸이 빈칸 → 김피탕). 의도한 변경(요리 이름 보정)이고 기준값은 다시 기록하지 않음 | 테스트 출력 |
| `./gradlew test` (전체) | 로컬 Windows, Docker | 실패 1건 (2,418건, 건너뜀 2): `ChatIntentReplayTest`만 (위와 같은 6줄). `PopularRecipeServiceCacheIntegrationTest`는 이번에 통과 | build/test-results |
| 로컬 ES 검색 재현 (나가사키짬뽕+짬뽕, 김피탕+탕수육·깐풍기, 김치찌개) | 로컬 ES 9.2.0, recipes 1,693건 | 통과 (기준 기록 문서 "2단계 후" 표) | `_search`·`_analyze` 응답 |
| 실제 AI 의도 평가(similarDishes 채움 여부) | OpenAI 키 없음 | 미실행 | 없음 |
| 카드 1~2장 실제 답 글 | 앱·OpenAI 미실행 | 미실행 | 없음 |

## 연결과 남은 작업
- 실제 커밋·PR: 커밋 전 (브랜치 `refactor/chat-intent-output`).
- 테스트·화면 증거: 위 검증 표.
- 관련 결정·문제 기록: [기준 기록 문서](../testing/chat-recipe-search-baseline.md)의 "남은 결정".
- 미검증·결정 대기 항목:
  - 재생 기준값(`expected.csv`) R06·T08 6줄을 다시 기록할지 결정 필요 (`CHAT_INTENT_REPLAY_RECORD=true ./gradlew test --tests '*ChatIntentReplayTest'`).
  - 실제 AI가 similarDishes를 3~5개 채우는지, 출력이 300토큰 안에 드는지 평가 필요.
  - 추천 시도가 잡담("어제 김치찌개 먹었는데")까지 추천으로 바꾸는지 운영 로그로 확인 필요.

## 평가 뒤 수정 (2단계 이어서, 2026-11-16)

### 평가 결과와 조사
- 실제 AI 평가(gpt-4o-mini, 121문장 × 3번, `build/reports/chat-intent-eval/eval-20261116-120851-*`): 116/121.
- R06 "김피탕 같은 요리 추천해줘" AI 원본 3회 모두: intent RECOMMEND(맞음), dishText "", keywords [], moodCategories [], similarDishes [김치찌개, 부대찌개, 순두부찌개, 김치전골, 김치찜]. 의도는 맞았고 요리 이름·키워드를 모두 놓쳤으며, 비슷한 요리는 프롬프트 예시(김치찌개)를 그대로 베꼈다.
- N09 "나가사키 짬뽕" 3회 모두: intent RECOMMEND, dishText "나가사키 짬뽕", keywords [], similarDishes [] ("반드시 3~5개"를 지키지 않음).
- N10 "김피탕"도 3회 모두 similarDishes가 김치찌개 예시 그대로였다 (평가 기대에는 비슷한 요리 내용이 없어 통과로 잡혔다).

### 바꾼 것
- 비슷한 요리를 의도 판단에서 빼고 별도 호출로: `IntentResult.similarDishes` 삭제(스키마·프롬프트), `ai/SimilarDishGenerator`·`OpenAiSimilarDishGenerator`·`StubSimilarDishGenerator`, `dto/SimilarDishesResult`, `prompts/chat-similar-dishes.txt`, `ChatClientConfig.chatSimilarDishChatClient`(gpt-4o-mini, temperature 0, 출력 100토큰, 타임아웃 5초, CostAdvisor), `facade/SimilarDishFinder`(캐시 → AI → 정리·저장, 실패하면 빈 목록), `SimilarDishStore`는 find/save로 나눔.
- 호출 조건: ES 경로이고 요리 이름이 있고, 첫 후보 검색에서 요리 이름 그대로 맞은 레시피(NAME)가 없을 때만 (`RecipeCandidateFacade.addSimilarDishes`). 받은 비슷한 요리는 키워드 뒤에 더하고 글자로 찾는다(`RecipeCandidateCollector.searchSimilarDishes`). 그 뒤 키워드 보충(벡터·조각) 또는 이름 조각 단계는 그대로.
- 의도 판단 출력 토큰은 300 → 200으로 되돌림 (비슷한 요리 칸이 빠짐).
- R06: `IntentCorrector.likeDish`("○○ 같은 요리·거·것·음식·메뉴"의 ○○, 분류·묶음·밀키트·가리키는 말 제외)를 첫 키워드 보정 뒤에 둠. chat-intent.txt dishText에 일반 예시("○○ 같은 요리 추천해줘" → dishText ○○, 처음 듣는 이름이어도 그대로)와 닭볶음탕 예시, 비슷한 요리 규칙·예시 삭제 (3,280 → 2,919자).
- 평가 세트: `similarDishes` 기대 칸 삭제, R06은 `dish: 김피탕`만. 평가 테스트의 `similarDishes` 기대 칸·`aiSimilarDishes` 칸은 되돌림. `SimilarDishEvalManualTest` 추가.
- 예전 설계(AI가 요리 이름 키워드를 늘림)와 같은 성격의 기대값은 다른 케이스에 없음. 남아 있는 `keywordsAny`(R04, R05, T16)는 특징 → 요리 이름 키워드라 지금 설계에서도 유효해 두었다.

### 추천 한 번당 AI 호출
- 의도 판단 1 + 비슷한 요리 0~1 + 추천 답 1 = 최대 3번 (모드 칩 추천은 의도 판단 없음, 카드 0장·밀키트만 답은 추천 답 AI 없음). 비슷한 요리는 캐시가 있거나 이름 그대로 찾히면 0번.
- 타임아웃은 5초다. 실패 때 OpenAI 클라이언트가 다시 시도하는지(횟수)는 확인하지 않았다. 다시 시도하면 최악 지연은 5초의 몇 배가 될 수 있다(미검증).
- 하루 상담 횟수(`ChatDailyLimitService.checkAndIncrement`)는 메시지 하나에 한 번 세며 AI 호출 수와 상관없다. 비슷한 요리 호출은 횟수를 쓰지 않는다.

### 검증 (평가 뒤 수정)
| 명령·시나리오 | 실행 환경·조건 | 통과/실패/미실행 | 근거 |
|---|---|---|---|
| `./gradlew test --tests '*.domain.chat.*'` | 로컬 Windows, Docker | 실패 1건 (868건, 건너뜀 2): `ChatIntentReplayTest` — 앞과 같은 R06·T08 6줄(요리 이름 빈칸 → 김피탕), 새로 달라진 줄 없음 | build/test-results |
| `./gradlew clean compileJava compileTestJava` | 로컬 Windows | 통과 | 실행 로그 |
| `./gradlew test` (전체) | 로컬 Windows, Docker | 실패 1건 (2,425건, 건너뜀 3): `ChatIntentReplayTest`만 (위와 같은 6줄) | build/test-results |
| 실제 AI 평가(의도·비슷한 요리) 다시 돌리기 | OpenAI 키 없음 | 미실행 | 없음 |
