import {
  recipeApi,
  searchApi,
  type PageResponseRecipeListItemResponse,
  type RecipeClassificationResponse,
  type RecipeDetailResponse,
  type RecipeListRequest,
} from '@/common/api/clients';
import { unwrap } from '@/common/api/unwrap';

// 검색어가 있으면 Elasticsearch 검색 API(형태소·오타 보정)로, 없으면 목록 API로 조회한다.
// 검색 API도 같은 조건·정렬·페이지를 받고, ES 장애 시 서버가 MySQL 조회로 대체한다.
export async function getRecipes(
  request: RecipeListRequest,
  signal?: AbortSignal,
): Promise<PageResponseRecipeListItemResponse> {
  if (request.keyword) return unwrap(searchApi.searchRecipes({ request }, { signal }));
  return unwrap(recipeApi.getRecipes({ request }, { signal }));
}

export async function getRecipeClassifications(
  signal?: AbortSignal,
): Promise<RecipeClassificationResponse> {
  return unwrap(recipeApi.getClassifications({ signal }));
}

export async function getRecipe(
  recipeId: number,
  signal?: AbortSignal,
): Promise<RecipeDetailResponse> {
  return unwrap(recipeApi.getRecipe({ recipeId }, { signal }));
}
