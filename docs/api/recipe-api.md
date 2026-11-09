# 레시피 API 명세

상태: 구현 완료 · 공통 규칙: [API 계약](api-spec.md#공통-규칙) · 자세한 동작: [레시피 설계](../architecture/domains/recipe.md)

공통 응답·오류 코드·페이지·날짜 형식은 [공통 규칙](api-spec.md#공통-규칙)을 따릅니다. 이 문서의 경로는 `/api`를 뺀 값이고, 에러 표에는 공통 오류(401 `A003`, 403 `A004`, 400 `C001`)를 빼고 이 API만의 오류를 적습니다.

## 목차

- [레시피 목록](#레시피-목록)
- [레시피 분류 값](#레시피-분류-값)
- [레시피 상세](#레시피-상세)
- [레시피 등록](#레시피-등록)
- [레시피 수정](#레시피-수정)
- [레시피 요청 DTO](#레시피-요청-dto)
- [레시피 응답 DTO](#레시피-응답-dto)

## 레시피 목록
`GET /recipes` · 공개

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| sort (query) | `LATEST`·`RELEVANCE`·`NAME_ASC`·`DIFFICULTY_ASC` | N | 기본 `LATEST`(최신순). `RELEVANCE`는 이 API에서 `LATEST`와 같음 |
| keyword (query) | string | N | 50자 이하. 레시피 이름에 들어 있는지 봄 |
| cuisineType (query) | string | N | 20자 이하. 값이 같은 것만 |
| category (query) | string | N | 50자 이하. 값이 같은 것만 |
| maxCookTimeMin (query) | int | N | 1 이상. 이 값 이하 |
| maxDifficulty (query) | int | N | 1~10. 이 값 이하 |
| page (query) | int | N | 기본 1, 1 이상 |
| size (query) | int | N | 기본 20, 1~100 |

응답: `PageResponse<RecipeListItemResponse>`

주의 사항
- MySQL로만 조회합니다. 관련도 순 검색은 [레시피 검색](search-api.md#레시피-검색)을 씁니다.

## 레시피 분류 값
`GET /recipes/classifications` · 공개

요청 값 없음

응답: `RecipeClassificationResponse`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| cuisineTypes | string[] | N | 음식 종류. 가나다순 |
| categories | string[] | N | 요리 분류. 가나다순 |

주의 사항
- 레시피 데이터에 실제로 있는 값만 담습니다. 목록 필터 칩에 씁니다.

## 레시피 상세
`GET /recipes/{recipeId}` · 공개

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| recipeId (path) | long | Y | 레시피 ID |

응답: `RecipeDetailResponse`

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | RECIPE_001 | 없는 레시피 |

## 레시피 등록
`POST /admin/recipes` · ADMIN

요청: `RecipeCreateRequest`

응답: `RecipeDetailResponse` (201)

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | INGREDIENT_002 | 재료 사전에 없는 `ingredientId` |

주의 사항
- 재료 순서(`seq`)와 단계 번호(`stepNo`)는 목록 순서대로 1부터 매깁니다.

## 레시피 수정
`PATCH /admin/recipes/{recipeId}` · ADMIN

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| recipeId (path) | long | Y | 레시피 ID |

요청: `RecipeUpdateRequest`

응답: `RecipeDetailResponse`

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | RECIPE_001 | 없는 레시피 |
| 404 | INGREDIENT_002 | 재료 사전에 없는 `ingredientId` |

주의 사항
- 보낸 칸만 바꾸고 null인 칸은 그대로 둡니다. 선택 텍스트 칸은 빈 문자열을 보내면 비웁니다.
- `ingredients`·`steps`를 보내면 목록 전체를 바꾸고 번호를 1부터 다시 매깁니다.

## 레시피 요청 DTO

`RecipeCreateRequest`

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| name | string | Y | 200자 이하 |
| summary | string | N | 2,000자 이하 |
| imageUrl | string | N | 500자 이하, `http://`·`https://`로 시작 |
| cuisineType | string | N | 음식 종류, 20자 이하 |
| category | string | N | 요리 분류, 50자 이하 |
| cookMethod | string | N | 조리 방법, 20자 이하 |
| mainIngredientCategory | string | N | 주재료 분류, 30자 이하 |
| cookTimeMin | int | N | 조리 시간(분), 1 이상 |
| servings | int | N | 분량(인분), 1 이상 |
| difficulty | int | Y | 1~10 |
| tags | string | N | 255자 이하 |
| tip | string | N | 2,000자 이하 |
| nutrition | `RecipeNutritionRequest` | N | 1인분 기준 영양 정보 |
| ingredients | `RecipeIngredientRequest[]` | Y | 1~100개 |
| steps | `RecipeStepRequest[]` | Y | 1~50개 |

`RecipeUpdateRequest`: 칸은 `RecipeCreateRequest`와 같고 모두 선택입니다. `name`은 1자 이상, `ingredients`는 보낼 때 1~100개, `steps`는 보낼 때 1~50개입니다. `imageUrl`은 빈 문자열도 받습니다.

`RecipeNutritionRequest` (모두 선택, 0 이상, 정수 6자리·소수 2자리)

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| weightG | number | N | 중량(g) |
| calories | number | N | 열량(kcal) |
| carbsG | number | N | 탄수화물(g) |
| proteinG | number | N | 단백질(g) |
| fatG | number | N | 지방(g) |
| sodiumMg | number | N | 나트륨(mg) |

`RecipeIngredientRequest`

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| ingredientId | long | Y | 표준 재료 ID |
| displayName | string | Y | 화면에 보일 재료명, 100자 이하 |
| amount | string | N | 용량 글(예: "1큰술"), 100자 이하 |
| quantity | number | N | 0보다 큼, 정수 8자리·소수 2자리 |
| role | string | N | 재료 역할, 20자 이하 |
| groupName | string | N | 재료 그룹명(예: 양념장), 50자 이하 |

`RecipeStepRequest`

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| description | string | Y | 2,000자 이하 |
| imageUrl | string | N | 500자 이하, `http://`·`https://`로 시작. 빈 값은 null |
| tip | string | N | 2,000자 이하 |

## 레시피 응답 DTO

`RecipeListItemResponse`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| recipeId | long | N | 레시피 ID |
| name | string | N | 레시피 이름 |
| summary | string | Y | 요약 |
| imageUrl | string | Y | 대표 사진 |
| cuisineType | string | Y | 음식 종류 |
| category | string | Y | 요리 분류 |
| cookTimeMin | int | Y | 조리 시간(분) |
| servings | int | Y | 분량(인분) |
| difficulty | int | N | 난이도 1~10 |
| calories | number | Y | 1인분 열량 |
| lastStepImageUrl | string | Y | 마지막 조리 단계 사진. 카드에 마우스를 올렸을 때 씀 |

`RecipeDetailResponse`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| recipeId | long | N | 레시피 ID |
| name | string | N | 레시피 이름 |
| summary | string | Y | 요약 |
| imageUrl | string | Y | 대표 사진 |
| cuisineType | string | Y | 음식 종류 |
| category | string | Y | 요리 분류 |
| cookMethod | string | Y | 조리 방법 |
| cookTimeMin | int | Y | 조리 시간(분) |
| servings | int | Y | 분량(인분) |
| difficulty | int | N | 난이도 1~10 |
| tags | string | Y | 태그 |
| tip | string | Y | 팁 |
| tipSummary | string | Y | 후기 요약 |
| nutrition | `Nutrition` | N | 1인분 영양 정보. 각 칸(weightG, calories, carbsG, proteinG, fatG, sodiumMg)은 null일 수 있음 |
| ingredients | `IngredientItem[]` | N | `seq` 순서 |
| ingredients[].seq | int | N | 재료 순서 |
| ingredients[].ingredientId | long | N | 표준 재료 ID |
| ingredients[].displayName | string | Y | 재료명 |
| ingredients[].amount | string | Y | 용량 글 |
| ingredients[].role | string | Y | 재료 역할 |
| ingredients[].groupName | string | Y | 재료 그룹명 |
| ingredients[].quantity | number | Y | 수량 |
| ingredients[].basicSeasoning | boolean | N | 기본 양념 |
| steps | `StepItem[]` | N | `stepNo` 순서 |
| steps[].stepNo | int | N | 단계 번호 |
| steps[].description | string | N | 설명 |
| steps[].imageUrl | string | Y | 단계 사진 |
| steps[].tip | string | Y | 단계별 팁 |
