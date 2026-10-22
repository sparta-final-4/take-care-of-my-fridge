import { axiosInstance } from '@/common/api/axiosInstance';
import {
  cookingLogApi,
  cookingStatsApi,
  type CookingLogSearchRequest,
  type CookingStatsResponse,
  type PageResponseCookingLogResponse,
} from '@/common/api/clients';
import type { ApiResponse } from '@/common/api/types';
import { unwrap } from '@/common/api/unwrap';

export type CookingSortType = 'LATEST' | 'MOST_COOKED';
export type CookingTabType = 'ALL' | 'UNREVIEWED' | 'REVIEWED';

export interface CookingHistoryItem {
  id: number;
  date: string; // 'YYYY-MM-DD'
  result: 'SUCCESS' | 'FAILED';
  memo: string;
}

export interface MyRecipeReviewItem {
  id: number;
  rating: number;
  content: string;
  updatedAt: string;
}

export interface MyCookedRecipeItem {
  recipeId: number;
  recipeName: string;
  imageUrl?: string;
  isAiRecommended: boolean;
  cookCount: number;
  lastCookedAt: string; // 'YYYY-MM-DD'
  review: MyRecipeReviewItem | null;
  history: CookingHistoryItem[];
}

export interface ThisMonthCookingSummary {
  completedCookCount: number;
  consumedIngredientCount: number;
  mostCookedRecipeName: string;
  mostCookedRecipeCount: number;
}

/**
 * 로그인한 회원의 요리 완료 레시피 목록을 조회한다.
 * TODO: 백엔드에 GET /api/me/recipes 가 구현되면 실제 API로 교체
 */
export async function getMyCookedRecipes(
  sort: CookingSortType = 'LATEST',
  signal?: AbortSignal,
): Promise<MyCookedRecipeItem[]> {
  const { data } = await axiosInstance.get<ApiResponse<MyCookedRecipeItem[]>>(
    `/api/me/recipes?sort=${sort}`,
    { signal },
  );
  return data.data;
}

// 로그인 없이 조회할 수 있다. 요리 기록이 없으면 모든 수가 0이다.
export async function getCookingStats(
  recipeId: number,
  signal?: AbortSignal,
): Promise<CookingStatsResponse> {
  return unwrap(cookingStatsApi.getCookingStats({ recipeId }, { signal }));
}

// 로그인한 회원 본인의 요리 기록만 최신순으로 온다.
export async function getCookingLogs(
  request: CookingLogSearchRequest,
  signal?: AbortSignal,
): Promise<PageResponseCookingLogResponse> {
  return unwrap(cookingLogApi.getCookingLogs({ request }, { signal }));
}
