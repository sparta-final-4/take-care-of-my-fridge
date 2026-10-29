import {
  searchApi,
  type PageResponseProductListItemResponse,
  type PageResponseRecipeListItemResponse,
  type ProductListRequest,
  type RecipeListRequest,
  type SearchClickRequest,
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

// 통합 검색 결과에서 상품을 눌러 상세로 갈 때 남기는 클릭 기록(할인 분석의 검색 클릭 수). 비로그인도 기록한다.
export async function recordSearchClick(request: SearchClickRequest): Promise<void> {
  await unwrap(searchApi.recordClick({ searchClickRequest: request }));
}
