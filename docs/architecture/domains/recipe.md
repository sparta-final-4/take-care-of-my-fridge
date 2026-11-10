# 레시피 설계

상태: 구현 완료 · API: [레시피 API 명세](../../api/recipe-api.md)

## 목적과 책임
- 해결할 사용자 문제: 만들 수 있는 요리를 찾아보고, 재료·조리 단계·영양 정보를 본다.
- 이 도메인이 소유하는 데이터: 레시피(`recipes`), 레시피 재료(`recipe_ingredients`), 조리 단계(`recipe_steps`), 레시피 맛(`recipe_tastes`).
- 다른 도메인에 맡기는 책임: 표준 재료 사전과 기본 양념 판단(재료), 검색어·관련도 검색(통합검색), 요리 기록·통계(요리 기록), 후기와 후기 요약 생성(후기).

## 동작 규칙
| 기능 | 규칙 | 근거 |
|---|---|---|
| 목록 조회 | MySQL로만 조회합니다. `keyword`는 이름에 들어 있는지, `cuisineType`·`category`는 같은 값인지 보고, 최댓값 조건은 그 값을 포함합니다. 빈 조건은 걸지 않습니다 | `RecipeRepositoryImpl.findRecipesByCondition` |
| 정렬 | `LATEST`는 ID 내림차순이고, 다른 정렬도 같으면 ID 내림차순입니다. MySQL에는 관련도 점수가 없어 `RELEVANCE`는 `LATEST`와 같습니다 | `RecipeRepositoryImpl` |
| 마지막 단계 사진 | 목록 한 페이지의 마지막 조리 단계 사진을 한 번에 읽어 `lastStepImageUrl`에 담습니다 | `RecipeService.findLastStepImages` |
| 분류 값 | 레시피에 실제로 있는 음식 종류·요리 분류를 가나다순으로 돌려줍니다. 캐시(`RECIPE_CLASSIFICATIONS`)하고 등록·수정 때 비웁니다 | `RecipeService.getClassifications` |
| 기본 양념 표시 | 상세의 재료마다 기본 양념인지(`basicSeasoning`)를 재료 도메인 기준으로 붙입니다. 요리 완료 차감·냉장고 비교와 같은 기준입니다 | `RecipeService.toDetailResponse` |
| 등록 | 레시피·재료·조리 단계를 한 트랜잭션에서 저장합니다. 재료 순서와 단계 번호는 목록 순서대로 1부터 매깁니다. 출처는 `ADMIN`이고, `(source, source_id)`가 유니크라 원본 코드(20자)를 UUID로 만듭니다 | `RecipeService.createRecipe` |
| 수정 | 기본 정보는 보낸 칸만 바꾸고(null은 유지, 선택 텍스트의 빈 문자열은 비움), 영양 정보도 보낸 항목만 바꿉니다. 재료·조리 단계는 보냈을 때만 지우고 다시 넣습니다 | `RecipeUpdateRequest.applyTo`, `RecipeService.updateRecipe` |
| 재료 확인 | `recipe_ingredients.ingredient_id`는 FK 없이 ID로만 참조하므로, 저장 전에 모든 재료 ID가 사전에 있는지 확인하고 하나라도 없으면 404 `INGREDIENT_002`입니다 | `RecipeService.validateIngredientsExist` |
| 검색 색인 | 등록·수정 뒤 `RecipeChangedEvent`를 내고, 통합검색이 그 레시피를 ES에 다시 넣습니다 | `RecipeService`, `RecipeIndexEventListener` |
| 후기 요약 | `tip_summary`는 후기 도메인이 요약을 만들 때 `updateTipSummary`로 바꿉니다. 이 API로는 바꾸지 않습니다 | `RecipeService.updateTipSummary` |
| 삭제 | 레시피 삭제 API는 없습니다 | `AdminRecipeController` |
