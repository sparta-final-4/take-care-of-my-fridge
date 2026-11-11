# 통합검색 설계

상태: 구현 완료 · 담당: 강준모 · API: [통합검색 API 명세](../../api/search-api.md)

## 목적과 책임
- 해결할 사용자 문제: 검색어 하나로 레시피와 상품을 찾고, 찾은 레시피를 내 냉장고 재료로 얼마나 만들 수 있는지 본다.
- 이 도메인이 소유하는 데이터: 레시피·상품·재료 검색 색인(ES), 0건 검색어(`search_zero_result_histories`), 상품 클릭(`search_click_histories`), 레시피 클릭(`search_recipe_click_histories`), 인기 검색어 집계(Redis).
- 다른 도메인에 맡기는 책임: 레시피·상품 원본과 판매가(레시피·상품·할인 행사), 냉장고 재료(냉장고), 기본 양념 판단(재료).

## 동작 규칙
| 기능 | 규칙 | 근거 |
|---|---|---|
| 검색 엔진 선택 | 검색어가 없거나 `app.search.engine`(환경 변수 `SEARCH_ENGINE`, 기본 `elasticsearch`)이 `mysql`이면 레시피·상품 목록 조회와 같은 MySQL 조회를 씁니다 | `RecipeSearchFacade`, `ProductSearchFacade` |
| ES 결과 응답 | ES에서는 ID와 전체 건수만 받고, 그 ID 순서대로 DB에서 레시피·상품을 읽어 응답합니다. 가격·상태·할인은 DB 값이고, 색인이 늦어 섞여 온 판매 중지 상품은 이때 뺍니다 | `RecipeService.getRecipesByIds`, `ProductService.getProductsByIds` |
| ES 장애 | ES 검색·자동완성이 실패하면 오류 없이 MySQL 조회로 대신하고 경고 로그를 남깁니다 | `RecipeSearchFacade`, `ProductSearchFacade` |
| 레시피 키워드 검색 | 검색어의 단어가 이름(가중치 3)·재료(가중치 2)·요약 중 어딘가에 모두 있어야 합니다(cross_fields + AND). 최신순 등으로도 보여 주므로 OR로 넓히지 않습니다. 분류·조리 시간·난이도는 점수와 상관없는 필터로 겁니다 | `RecipeSearchService.keywordRequest` |
| 레시피 벡터 검색 | 키워드 결과가 0건이고 벡터 검색을 켰으면 검색어 임베딩으로 kNN 검색을 다시 합니다(같은 필터·정렬). 임베딩·ES가 실패하거나 호출 제한에 걸리면 0건입니다. 응답에는 벡터 결과인지 표시하지 않습니다 | `RecipeSearchService.vectorSearch` |
| 상품 키워드 검색 | 검색어의 단어가 이름(가중치 3)·설명 중 어딘가에 모두 있어야 합니다. 공개 상태·종류·가격 범위는 필터로 겁니다 | `ProductSearchService` |
| 자동완성 | 이름의 단어가 검색어로 시작하거나 이름에 검색어 단어(형태소)가 있는 레시피 이름을 최대 8개 고릅니다. 이름이 검색어로 시작하면 앞에 오고, 같은 이름은 한 번만 나옵니다. 벡터 검색은 하지 않습니다 | `RecipeSearchService.suggestRequest` |
| 검색 기록 | 검색어가 있고 필터 없는 첫 페이지일 때만 기록합니다. 0건이면 대상(`RECIPE`·`PRODUCT`)과 함께 0건 검색어로 남기고(사전 보강 근거), 레시피 검색 결과가 있으면 인기 검색어로 셉니다. 상품 검색은 인기 검색어로 세지 않습니다 | `RecipeSearchFacade.isPlainFirstPage` |
| 인기 검색어 | 날마다 Redis ZSET에 점수를 올리고, 조회 때 오늘 포함 7일(Asia/Seoul)을 합쳐 상위 10개를 돌려줍니다. 로그인 회원만, 같은 회원·검색어는 하루 한 번만 세고, 2번 이상 검색된 것만 보여 줍니다. 검색어는 공백 정리·영문 소문자로 맞추고 20자를 넘으면 세지 않습니다. 합친 결과는 5분 캐시하고, Redis 장애면 세지 않고 빈 목록입니다 | `SearchPopularKeywordService` |
| 냉장고 비교 | 레시피 재료와 냉장고 재료를 표준 재료 ID로 비교합니다(냉장고 "국산 흙대파"와 레시피 "대파"가 같은 재료). 기본 양념·직접 입력·밀키트 재료는 요리 완료 차감과 같은 기준으로 세지 않고, 소비기한이 지난 냉장고 재료는 비교하지 않습니다. `keywordItems`는 등록 이름에 검색어가 들었거나 표준 재료 이름이 검색어와 같은 재료입니다 | `SearchFridgeMatchFacade`, `FridgeRepository.findUsableIngredients` |
| 상품 클릭 거르기 | 할인 후보 분석과 메인 "지금 뜨는 할인"이 클릭 수를 읽으므로 반복 클릭을 거릅니다. 로그인은 같은 회원·상품·검색어를 10분에 한 번, 비로그인은 상품별 1분에 30건까지 저장합니다. Redis 장애면 거르지 않고 저장합니다. 상품이 있는지는 확인하지 않습니다 | `SearchClickHistoryService` |
| 레시피 클릭 | 거르지 않고 그대로 저장합니다. 레시피가 있는지는 확인하지 않습니다 | `SearchClickHistoryService.recordRecipe` |
| 색인 갱신 | 레시피·상품 변경 이벤트를 커밋 뒤 받아 그 문서만 다시 넣습니다. 실패하면 로그만 남깁니다. 상품은 매일 02시(Asia/Seoul)에 한 서버만 전체를 다시 색인합니다. 레시피·재료를 SQL로 직접 바꿨다면 alias를 지우고 재시작해 전체 색인합니다 | `RecipeIndexEventListener`, `ProductIndexEventListener`, `EsReindexScheduler`, `EsIndexInitializer` |
