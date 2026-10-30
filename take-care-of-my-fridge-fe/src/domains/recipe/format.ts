import type { RecipeListItemResponse } from '@/common/api/clients';

// maxCookTimeMin은 그 값을 포함한다.
export const COOK_TIME_OPTIONS = [
  { key: '10', label: '10분 이내', maxCookTimeMin: 10 },
  { key: '20', label: '20분 이내', maxCookTimeMin: 20 },
  { key: '30', label: '30분 이내', maxCookTimeMin: 30 },
] as const;

export type CookTimeKey = (typeof COOK_TIME_OPTIONS)[number]['key'];

// 임박 재료(서버 status IMMINENT) 배지 색. 오늘·내일은 더 눈에 띄게 한다.
export function imminentTagColor(dDay: number) {
  return dDay <= 1 ? 'red' : 'orange';
}

// 카드·상세 상단에 쓰는 "15분 · 2인분 · 한식 반찬" 한 줄
export function formatRecipeMeta(
  recipe: Pick<RecipeListItemResponse, 'cookTimeMin' | 'servings' | 'cuisineType' | 'category'>,
) {
  return [
    recipe.cookTimeMin ? `${recipe.cookTimeMin}분` : undefined,
    recipe.servings ? `${recipe.servings}인분` : undefined,
    [recipe.cuisineType, recipe.category].filter(Boolean).join(' ') || undefined,
  ]
    .filter(Boolean)
    .join(' · ');
}
