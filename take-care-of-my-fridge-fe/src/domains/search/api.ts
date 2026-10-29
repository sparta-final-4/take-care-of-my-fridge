import {
  searchApi,
  type PageResponseProductListItemResponse,
  type PageResponseRecipeListItemResponse,
  type ProductListRequest,
  type RecipeListRequest,
} from '@/common/api/clients';
import { unwrap } from '@/common/api/unwrap';

// 상단 통합 검색 전용. 레시피·상품 화면의 검색창은 각 도메인의 목록 API를 쓴다.
export async function searchRecipes(
  request: RecipeListRequest,
  signal?: AbortSignal,
): Promise<PageResponseRecipeListItemResponse> {
  return unwrap(searchApi.searchRecipes({ request }, { signal }));
}

export async function searchProducts(
  request: ProductListRequest,
  signal?: AbortSignal,
): Promise<PageResponseProductListItemResponse> {
  return unwrap(searchApi.searchProducts({ request }, { signal }));
}
