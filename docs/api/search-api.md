# 통합검색 API 명세

상태: 구현 완료 · 공통 규칙: [API 계약](api-spec.md#공통-규칙) · 자세한 동작: [통합검색 설계](../architecture/domains/search.md)

공통 응답·오류 코드·페이지·날짜 형식은 [공통 규칙](api-spec.md#공통-규칙)을 따릅니다. 이 문서의 경로는 `/api`를 뺀 값이고, 에러 표에는 공통 오류(401 `A003`, 400 `C001`)를 빼고 이 API만의 오류를 적습니다. 냉장고 비교만 로그인이 필요하고 나머지는 공개입니다. 로그인했으면 회원 ID를 기록·집계에 씁니다.

## 목차

- [레시피 검색](#레시피-검색)
- [상품 검색](#상품-검색)
- [검색어 자동완성](#검색어-자동완성)
- [인기 검색어](#인기-검색어)
- [냉장고 비교](#냉장고-비교)
- [상품 클릭 기록](#상품-클릭-기록)
- [레시피 클릭 기록](#레시피-클릭-기록)

## 레시피 검색
`GET /search/recipes` · 공개

요청: [레시피 목록](recipe-api.md#레시피-목록)과 같음

응답: `PageResponse<RecipeListItemResponse>` ([레시피 응답 DTO](recipe-api.md#레시피-응답-dto))

주의 사항
- 검색어가 있으면 ES로 찾고(`RELEVANCE`는 점수 순), 키워드 결과가 0건이면 의미가 가까운 레시피를 벡터 검색으로 다시 찾습니다.
- ES가 실패하면 오류 없이 MySQL 조회(레시피 목록과 같은 결과)로 대신합니다.

## 상품 검색
`GET /search/products` · 공개

요청: [상품 목록](product-api.md#상품-목록)과 같음

응답: `PageResponse<ProductListItemResponse>` ([상품 응답 DTO](product-api.md#상품-응답-dto))

주의 사항
- 검색어가 있으면 ES로 찾고, ES가 실패하면 오류 없이 MySQL 조회로 대신합니다. 판매 중지 상품은 빠집니다.

## 검색어 자동완성
`GET /search/suggestions` · 공개

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| keyword (query) | string | Y | 50자 이하 |

응답: `List<String>` (레시피 이름, 최대 8개)

주의 사항
- 이름이 검색어로 시작하는 레시피가 먼저 오고, 같은 이름은 한 번만 나옵니다.

## 인기 검색어
`GET /search/popular` · 공개

요청 값 없음

응답: `PopularKeywordsResponse`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| keywords | string[] | N | 최근 7일 많이 검색된 순, 최대 10개. 없으면 빈 목록 |

주의 사항
- 로그인 회원의 레시피 검색만 셉니다. 서버가 5분 캐시하고 Redis 장애면 빈 목록입니다.

## 냉장고 비교
`GET /search/fridge-matches` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| recipeIds (query) | long[] | Y | 검색 결과 한 페이지의 레시피 ID, 1~20개 |
| keyword (query) | string | N | 50자 이하 |

응답: `SearchFridgeMatchResponse`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| recipes | `RecipeMatch[]` | N | 요청 순서대로 |
| recipes[].recipeId | long | N | 레시피 ID |
| recipes[].ownedCount | int | N | 그중 냉장고에 있는 재료 수 |
| recipes[].ingredientCount | int | N | 비교한 재료 수(기본 양념 등 제외) |
| keywordItems | string[] | N | 검색어가 든 내 냉장고 재료의 등록 이름. 검색어가 없으면 빈 목록 |

주의 사항
- 재료는 표준 재료 ID로 비교하고, 소비기한이 지난 냉장고 재료는 세지 않습니다.

## 상품 클릭 기록
`POST /search/clicks` · 공개

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| productId | long | Y | 클릭한 상품, 1 이상 |
| keyword | string | Y | 검색어, 100자 이하 |

응답: 없음(`ApiResponse.ok()`)

주의 사항
- 반복 클릭은 걸러서 저장하며, 걸러져도 200입니다. 할인 후보 분석과 메인 "지금 뜨는 할인"이 이 기록을 읽습니다.

## 레시피 클릭 기록
`POST /search/recipe-clicks` · 공개

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| recipeId | long | Y | 클릭한 레시피, 1 이상 |
| keyword | string | Y | 검색어, 100자 이하 |

응답: 없음(`ApiResponse.ok()`)
