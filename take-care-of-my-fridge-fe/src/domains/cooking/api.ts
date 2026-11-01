import {
  cookingLogApi,
  cookingStatsApi,
  myRecipeApi,
  popularRecipeApi,
  type CookingCompleteRequest,
  type CookingCompleteResponse,
  type CookingLogResponse,
  type CookingLogSearchRequest,
  type CookingLogUpdateRequest,
  type CookingStatsResponse,
  type MyRecipeListResponse,
  type MyRecipeResponse,
  type MyRecipeSearchRequest,
  type MyRecipeSummaryResponse,
  type PageResponseCookingLogResponse,
  type PopularRecipeResponse,
} from '@/common/api/clients';
import { unwrap } from '@/common/api/unwrap';

export type CookingSortType = 'LATEST' | 'MOST_COOKED';
export type CookingTabType = 'ALL' | 'UNREVIEWED' | 'REVIEWED';

export type {
  CookingCompleteRequest,
  CookingCompleteResponse,
  CookingLogResponse,
  CookingLogSearchRequest,
  CookingLogUpdateRequest,
  CookingStatsResponse,
  MyRecipeListResponse,
  MyRecipeResponse,
  MyRecipeSummaryResponse,
  MyRecipeSearchRequest,
  PageResponseCookingLogResponse,
  PopularRecipeResponse,
};

/**
 * 로그인한 회원이 요리 완료한 레시피 목록과 요약 통계를 조회한다.
 */
export async function getMyRecipes(
  request: MyRecipeSearchRequest = {},
  signal?: AbortSignal,
): Promise<MyRecipeListResponse> {
  return unwrap(myRecipeApi.getMyRecipes({ request }, { signal }));
}

/**
 * 특정 레시피의 요리 통계(총 요리, 성공, 실패)를 조회한다.
 */
export async function getCookingStats(
  recipeId: number,
  signal?: AbortSignal,
): Promise<CookingStatsResponse> {
  return unwrap(cookingStatsApi.getCookingStats({ recipeId }, { signal }));
}

/**
 * 로그인한 회원 본인의 요리 기록을 조회한다. (recipeId별 필터링 가능)
 */
export async function getCookingLogs(
  request: CookingLogSearchRequest,
  signal?: AbortSignal,
): Promise<PageResponseCookingLogResponse> {
  return unwrap(cookingLogApi.getCookingLogs({ request }, { signal }));
}

/**
 * 요리 기록의 결과(성공/실패)와 메모를 수정한다.
 */
export async function updateCookingLog(
  cookingLogId: number,
  request: CookingLogUpdateRequest,
): Promise<CookingLogResponse> {
  return unwrap(
    cookingLogApi.updateCookingLog({
      cookingLogId,
      cookingLogUpdateRequest: request,
    }),
  );
}

/**
 * 요리 완료 기록을 생성하고 냉장고 재료를 차감한다.
 */
export async function completeCooking(
  request: CookingCompleteRequest,
): Promise<CookingCompleteResponse> {
  return unwrap(
    cookingLogApi.completeCooking({
      cookingCompleteRequest: request,
    }),
  );
}

/**
 * 최근 30일 요리 기록 기반 인기 레시피 목록을 조회한다 (기본 6건).
 */
export async function getPopularRecipes(
  size = 6,
  signal?: AbortSignal,
): Promise<PopularRecipeResponse[]> {
  return unwrap(popularRecipeApi.getPopularRecipes({ size }, { signal }));
}
