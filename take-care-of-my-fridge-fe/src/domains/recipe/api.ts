import {
  recipeApi,
  type PageResponseRecipeListItemResponse,
  type RecipeDetailResponse,
  type RecipeListRequest,
} from '@/common/api/clients';
import { unwrap } from '@/common/api/unwrap';

export async function getRecipes(
  request: RecipeListRequest,
  signal?: AbortSignal,
): Promise<PageResponseRecipeListItemResponse> {
  return unwrap(recipeApi.getRecipes({ request }, { signal }));
}

export async function getRecipe(
  recipeId: number,
  signal?: AbortSignal,
): Promise<RecipeDetailResponse> {
  return unwrap(recipeApi.getRecipe({ recipeId }, { signal }));
}
