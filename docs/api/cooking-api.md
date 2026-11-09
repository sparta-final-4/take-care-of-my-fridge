# 요리 기록 API 명세

상태: 구현 완료 · 담당: 이현정 · 공통 규칙: [API 계약](api-spec.md#공통-규칙) · 자세한 동작: [요리 기록 설계](../architecture/domains/cooking.md)

공통 응답·오류 코드·페이지·날짜 형식은 [공통 규칙](api-spec.md#공통-규칙)을 따릅니다. 이 문서의 경로는 `/api`를 뺀 값이고, 에러 표에는 공통 오류(401 `A003`, 400 `C001`)를 빼고 이 API만의 오류를 적습니다.

## 목차

- [요리 완료](#요리-완료)
- [요리 기록 목록](#요리-기록-목록)
- [요리 결과 수정](#요리-결과-수정)
- [레시피 요리 통계](#레시피-요리-통계)
- [마이 레시피](#마이-레시피)
- [인기 레시피](#인기-레시피)
- [요리 기록 응답 DTO](#요리-기록-응답-dto)

## 요리 완료
`POST /cooking-logs` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| recipeId | long | Y | 레시피 ID |
| result | `SUCCESS`·`FAILED` | Y | 요리 결과 |
| memo | string | N | 50자 이하 |
| chatSessionId | long | N | 이을 상담. 비우면 서버가 찾음 |
| usedIngredients | `UsedIngredientRequest[]` | N | 30개 이하, 같은 재료 두 번 안 됨 |
| usedIngredients[].ingredientId | long | Y | 표준 재료 ID |
| usedIngredients[].quantity | number | N | 0보다 큼. null이거나 가진 양 이상이면 그 재료를 모두 차감 |

응답: `CookingCompleteResponse`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| cookingLogId | long | N | 요리 기록 ID |
| deductions | `IngredientDeductionResponse[]` | N | 실제로 차감한 재료만 |
| deductions[].ingredientId | long | N | 표준 재료 ID |
| deductions[].deductedQuantity | number | N | 차감한 양 |
| deductions[].remainingQuantity | number | N | 남은 양 |

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | RECIPE_001 | 없는 레시피 |

주의 사항
- 상담을 잇지 못해도(남의 상담·끝남·만료) 오류 없이 요리 완료를 진행하고, 이은 상담은 커밋 뒤 끝냅니다.
- 기본 양념·직접 입력·밀키트와 냉장고에 없는 재료는 차감하지 않습니다.

## 요리 기록 목록
`GET /cooking-logs` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| recipeId (query) | long | N | 비우면 전체 |
| from (query) | date | N | 이 날짜부터 |
| to (query) | date | N | 이 날짜 하루 끝까지 |
| page (query) | int | N | 기본 1, 1 이상 |
| size (query) | int | N | 기본 20, 1~100 |

응답: `PageResponse<CookingLogResponse>` (최신순)

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | C001 | `from`이 `to`보다 늦음 |

## 요리 결과 수정
`PATCH /cooking-logs/{cookingLogId}` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| cookingLogId (path) | long | Y | 요리 기록 ID |
| result | `SUCCESS`·`FAILED` | Y | |
| memo | string | N | 50자 이하 |

응답: `CookingLogResponse`

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | COOKING_001 | 없는 기록이거나 남의 기록 |

주의 사항
- 재료 차감은 되돌리지 않으므로 결과와 메모만 바꿉니다.

## 레시피 요리 통계
`GET /recipes/{recipeId}/cooking-stats` · 공개

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| recipeId (path) | long | Y | 레시피 ID |

응답: `CookingStatsResponse`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| recipeId | long | N | |
| totalCount | long | N | 기록이 없으면 0 |
| successCount | long | N | |
| failedCount | long | N | |

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | RECIPE_001 | 없는 레시피 |

## 마이 레시피
`GET /me/recipes` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| reviewStatus (query) | `ALL`·`WRITTEN`·`NOT_WRITTEN` | N | 기본 `ALL`. 후기 쓴·안 쓴 레시피 |
| sort (query) | `RECENT`·`MOST` | N | 기본 `RECENT`(최근 요리순). `MOST`는 많이 만든 순 |
| page (query) | int | N | 기본 1, 1 이상 |
| size (query) | int | N | 기본 10, 1~50 |

응답: `MyRecipeListResponse`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| summary | `MyRecipeSummaryResponse` | N | 필터와 상관없는 전체 요약 |
| summary.recipeCount | long | N | 요리한 레시피 수 |
| summary.totalCookCount | long | N | 총 요리 수 |
| summary.writtenCount | long | N | 후기 쓴 레시피 수 |
| summary.notWrittenCount | long | N | 후기 안 쓴 레시피 수 |
| summary.mostCookedRecipe | `{ recipeId, recipeName, cookCount }` | Y | 가장 많이 만든 레시피. 기록이 없으면 null |
| summary.consumedIngredientCount | long | N | 요리로 쓴 재료 종류 수 |
| recipes | `PageResponse<MyRecipeResponse>` | N | |
| recipes.content[].recipeId | long | N | |
| recipes.content[].recipeName | string | Y | |
| recipes.content[].recipeImageUrl | string | Y | |
| recipes.content[].cookCount | long | N | 이 레시피를 만든 수 |
| recipes.content[].lastCookedAt | date-time | N | |
| recipes.content[].fromChat | boolean | N | 상담에서 이어진 기록이 있는지 |
| recipes.content[].review | `{ id, rating, content, updatedAt }` | Y | 내 후기. 없으면 null |

## 인기 레시피
`GET /recipes/popular` · 공개

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| size (query) | int | N | 기본 6, 1~20 |

응답: `List<PopularRecipeResponse>` (최근 30일 요리한 회원 수 순)

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| recipeId | long | N | |
| recipeName | string | N | |
| imageUrl | string | Y | |
| tipSummary | string | Y | 후기 요약 |
| cookedMemberCount | long | N | 최근 30일 요리한 회원 수 |
| averageRating | number | Y | 후기가 없으면 null |
| reviewCount | long | N | |

주의 사항
- 순위는 24시간 캐시하고, 이름·이미지·요약·평점은 요청마다 읽습니다.

## 요리 기록 응답 DTO

`CookingLogResponse`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| id | long | N | 요리 기록 ID |
| recipeId | long | N | |
| result | `SUCCESS`·`FAILED` | N | |
| memo | string | Y | |
| fromChat | boolean | N | 상담에 이어진 기록인지 |
| createdAt | date-time | N | |
