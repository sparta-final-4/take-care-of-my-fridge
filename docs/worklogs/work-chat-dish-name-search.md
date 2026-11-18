# WORK-CHAT-DISH-NAME-SEARCH: 상담 요리 이름 추천 검색 바로잡기 (0·1단계)

상태: 검증 대기 · 작성일: 2026-11-13 · 담당: 미기재

## 목표와 완료 기준
- 사용자 문제: AI 상담에서 요리 이름("나가사키 짬뽕", "김피탕")을 말하면 그 요리와 상관없는 레시피(분류·냉장고 점수로만 걸린 요리, 글자 일부로 걸린 요리)가 카드로 나온다.
- 확인 가능한 완료 조건:
  - 0단계: 고치기 전 동작을 테스트·문서로 고정한다 (기능 코드 변경 없음).
  - 1단계: 벡터를 쓴 뒤에도 나머지 키워드를 글자로 찾는다. 요리 이름이 0건이면 분석기 조각(두 글자 이상)으로 보충한다. 그래도 맞은 게 없으면 분류·냉장고 후보로 카드를 채우지 않는다.
  - 의도 판단 재생 테스트(ChatIntentReplayTest)가 기록을 바꾸지 않고 통과한다.

## 근거와 범위
- 요구사항·화면 ID·설계 문서: [상담 레시피 검색 기준 기록](../testing/chat-recipe-search-baseline.md), [레시피 검색 벡터 폴백 평가](../testing/recipe-vector-fallback-evaluation.md)
- 변경할 영역: 백엔드 chat(후보 모으기·정렬 전 분류·카드 이유), search(글자만 검색, 분석기 호출 메서드 추가)
- 다른 도메인 영향·기존 승인 범위: 레시피 목록 API(`RecipeSearchService.search`) 동작은 그대로다(내부에서 새 `searchKeywordOnly`를 부르도록 옮겼을 뿐). 프롬프트(chat-intent.txt, chat-recommend.txt), ChatIntentFacade 분기, IntentCorrector는 바꾸지 않았다. API·DB·프론트 변경 없음.
- 미정 정책·가정:
  - "요리 이름을 말함" = 요리 이름(keywordText)이 있거나, 원한 표현(preferenceText) 없이 키워드만 있음. 특징만 말한 추천("시원한 국물" → 냉국 키워드)은 지금처럼 분류·냉장고 후보로 채운다.
  - 못 찾았을 때 벡터 후보는 남긴다 (오타 "김치찌게" → 김치찌개 구제가 벡터 폴백의 주 용도라서). 빼는 것은 키워드 일치가 없는 후보와 글자 일부로만 걸린(SPLIT) 후보다.
  - 카드 0장 답은 이미 있는 정해진 답(`ChatStreamFacade.NO_RECIPE_REPLY`)을 그대로 쓴다.

## 계획
| 단계 | 변경 | 검증 |
|---|---|---|
| 0 | 평가 세트 케이스 추가, `_현재동작` 재현 테스트 2개, 기준 기록 문서 | `./gradlew test` |
| 1-1 | `searchKeywords`의 break 제거, 벡터 뒤 키워드는 `searchKeywordOnly` | `callsVectorOnce…`, `searchesEachKeywordOnce…` |
| 1-2 | 보충 단계에서 요리 이름 조각 검색, `KeywordMatch.Type.FRAGMENT` | 조각 테스트 5개, 분류기·카드 이유 테스트 |
| 1-3 | 요리 이름이 맞은 후보가 없으면 벡터 후보만 남김 (없으면 0장) | `returnsNoCandidates…`, `keepsOnlyVectorHits…`, `keepsFridgeCandidates…` |
| 1-4 | `_현재동작` 테스트를 고친 동작으로 바꿈, 기준 기록에 1단계 칸 | 문서, 로컬 ES 재현 |

## AI 활용과 개발자 판단
- AI에 맡긴 작업·핵심 지시: 0단계(현재 동작 기록, 기능 코드 변경 금지), 1단계(검색 단계만 수정, 프롬프트·의도 판단·목록 API 변경 금지, 커밋 금지).
- 제안 중 채택한 내용과 이유:
  - 조각 표시는 EXPANDED를 재사용하지 않고 새 종류 FRAGMENT를 뒀다. EXPANDED의 keyword는 "AI가 늘린 키워드"라 정렬(`RecommendationPicker.keywordOrder`)·카드 이유가 그 전제로 쓰여 있어서, 의미가 다른 조각을 섞지 않았다.
  - 요리 이름이 키워드에 없으면(AI가 요리 이름을 키워드에 안 넣은 경우) 조각으로 나누기 전에 원래 이름을 글자로 한 번 찾는다. 찾히면 나누지 않는다.
- 수정·보류한 제안과 이유:
  - "못 찾으면 카드를 비운다"를 문자 그대로 하면 벡터 결과까지 사라져 오타 구제가 깨져서, 벡터 후보는 남기도록 좁혔다.
  - 기존 DB 경로 매운맛 테스트들은 요리 이름 없이 "튀김"/"고기" 키워드만 넣어 새 규칙에 걸렸다. 규칙을 약하게 하지 않고, 테스트 조건을 실제 흐름(특징 "바삭한" + AI가 늘린 키워드)에 맞게 바꿨다.
- 개발자가 확인한 근거: 로컬 ES `_analyze`(korean_search) 결과, 재생 테스트 통과, 아래 검증 표.

## 구현 결과
- `search/service/RecipeSearchService`: `searchKeywordOnly`(글자 검색만, 벡터 없음), `analyzeTokens`(레시피 인덱스 `korean_search` 분석) 추가. `search`는 `searchKeywordOnly`를 부르도록 옮김(동작 같음).
- `chat/facade/RecipeCandidateCollector`: 벡터 뒤에도 글자 검색 계속, 로그 `(글자만)`. `searchKeywordsOnce`에서 요리 이름 조각 검색(`searchDishFragments`), 분석 실패 시 공백 대체. `CandidatePool`에 `fragments` 필드.
- `chat/facade/RecipeCandidateFacade`: 분류에 조각 전달, 보충 로그에 조각, `dishNotFound`/`onlyVectorHits`로 못 찾은 경우 처리.
- `chat/dto/KeywordMatch`: `FRAGMENT("이름 조각")` 추가, `strong()`에 포함.
- `chat/recommend/KeywordMatchClassifier`: 조각을 받는 `classify` 오버로드 (이름 > 늘린 이름 > 재료 > 조각 > 쪼개짐/벡터).
- `chat/reply/RecommendationReasonWriter`: 조각 카드 이유 "{요리 이름}과 이름이 비슷한 요리예요" (요리 이름 없으면 "{조각} 요리예요").
- 테스트: `RecipeCandidateFacadeTest`(재현 테스트 2개 전환, 조각·못 찾음 테스트 추가, 기존 테스트 3개 기대값 조정·매운맛 테스트 조건 보정), `KeywordMatchClassifierTest`, `RecommendationReasonWriterTest`, `RecipeSearchServiceTest`.
- 문서: [상담 레시피 검색 기준 기록](../testing/chat-recipe-search-baseline.md)에 1단계 칸, 0단계에서 `chat-intent-eval.yml`에 N09·N10·G14·T17·T18.
- 카드 0장 답 문구 (1단계 후속): 요리 이름을 말했는데 0장이면 이유를 `RecipeCandidates.emptyReason`에 담아 서버 글(`NoRecipeReplyWriter`, `RecommendationReply.fixedReply`)로 보낸다. 못 찾음 "김피탕 레시피는 아직 없어요. …", 뺄 재료 "새우볶음밥에는 새우가 들어 있어서 추천에서 뺐어요. …"(재료를 모르면 "못 드시는 재료"), 그 밖은 기존 `NO_RECIPE_REPLY`. 이유를 정하는 확인 검색(요리 이름 글자 검색)이 실패하면 이유를 담지 않아 `NO_RECIPE_REPLY`가 나간다(검색 장애를 "레시피가 없다"고 말하지 않게). 뺄 재료 조회만 실패하면 "못 드시는 재료" 문구를 유지한다.
- 계획과 달라진 점: 못 찾았을 때 "카드 비움" 대신 "벡터 후보만 남김(없으면 비움)". API·DB 변경 없음.

## 검증
| 명령·시나리오 | 실행 환경·조건 | 통과/실패/미실행 | 근거 |
|---|---|---|---|
| `./gradlew test --tests '*.domain.chat.*' --tests '*.domain.search.*'` | 로컬 Windows, Docker | 통과 (1,000건, 건너뜀 2: 수동 테스트) | build/test-results |
| `ChatIntentReplayTest` | 위와 같음 | 통과 (기준값 다시 기록하지 않음) | 위와 같음 |
| `./gradlew clean compileJava compileTestJava` | 로컬 Windows | 통과 | 실행 로그 |
| `./gradlew test` (전체) | 로컬 Windows, Docker | 실패 1건 (2,381건 중, 건너뜀 2: 수동 테스트). 실패는 cooking 도메인 `PopularRecipeServiceCacheIntegrationTest` 하나로 이번 변경과 무관하고, 혼자 다시 돌리면 통과 (Redis 캐시 상태에 따른 간헐 실패로 보임) | build/test-results |
| `./gradlew test` (전체, 0장 답 문구 후속 뒤) | 로컬 Windows, Docker | 실패 1건 (2,389건 중, 건너뜀 2). 같은 `PopularRecipeServiceCacheIntegrationTest`가 또 실패, 단독 재실행은 2건 모두 통과 | build/test-results |
| 로컬 ES 검색 재현 (나가사키 짬뽕, 나가사키짬뽕, 김피탕, 김치찌개) | 로컬 ES 9.2.0, recipes 1,693건 | 통과 (기준 기록 문서 표) | `_analyze`·`_search` 응답 |
| 카드까지 낸 실제 상담 결과 | MySQL·앱 미실행 | 미실행 | 없음 |
| 의도 판단 평가(실제 AI) | OpenAI 키 없음 | 미실행 | 없음 |

## 연결과 남은 작업
- 실제 커밋·PR: 0단계 `9759c71b test(chat): 상담 레시피 검색 버그 재현 테스트와 기준선 기록`. 1단계는 커밋 전.
- 테스트·화면 증거: 위 검증 표.
- 관련 결정(도메인 문서 변경 이력)·문제 기록: 기준 기록 문서의 "남은 결정".
- 미검증·결정 대기 항목:
  - "나가사키" 같은 조각이 상관 적은 레시피(부대찌개, 조림)도 데려옴.
  - 콕 집은 요리가 알레르기·시간 조건으로 빠지면 다른 요리로 채우지 않고 "찾지 못했어요"가 나감.
  - 실제 AI·앱 전체에서의 결과 미측정.
