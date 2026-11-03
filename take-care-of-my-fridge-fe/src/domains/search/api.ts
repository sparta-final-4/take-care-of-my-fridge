import {
  searchApi,
  type PageResponseProductListItemResponse,
  type PageResponseRecipeListItemResponse,
  type ProductListRequest,
  type RecipeListRequest,
  type SearchFridgeMatchRequest,
  type SearchFridgeMatchResponse,
  type SearchClickRequest,
  type SearchRecipeClickRequest,
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

// 통합 검색 결과에서 레시피를 눌러 상세로 갈 때 남기는 클릭 기록(검색 품질 확인용). 비로그인도 기록한다.
export async function recordSearchRecipeClick(request: SearchRecipeClickRequest): Promise<void> {
  await unwrap(searchApi.recordRecipeClick({ searchRecipeClickRequest: request }));
}

// 상단 검색창 자동완성. 검색어로 시작하는 단어가 이름에 있는 레시피 이름을 최대 8개 돌려준다.
export async function suggestSearchKeywords(
  keyword: string,
  signal?: AbortSignal,
): Promise<string[]> {
  return unwrap(searchApi.suggest({ request: { keyword } }, { signal }));
}

// 통합 검색 결과를 로그인 회원의 냉장고와 비교한다(레시피별 보유 재료 수, 검색어가 든 냉장고 재료). 로그인 회원만.
export async function matchSearchFridge(
  request: SearchFridgeMatchRequest,
  signal?: AbortSignal,
): Promise<SearchFridgeMatchResponse> {
  return unwrap(searchApi.matchFridge({ request }, { signal }));
}
