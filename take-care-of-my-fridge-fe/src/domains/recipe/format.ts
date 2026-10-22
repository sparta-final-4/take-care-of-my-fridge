import type { RecipeListItemResponse } from '@/common/api/clients';

// 백엔드 cuisineType·category는 값이 같은지 비교한다(Recipe.java 주석: 한식/서양/중국…, 밥/국/반찬…).
// 실제 레시피 데이터의 값과 다르면 이 목록만 맞추면 된다.
export const CUISINE_OPTIONS = ['한식', '서양', '중국', '일본'] as const;
export const CATEGORY_OPTIONS = ['밥', '국', '반찬', '면', '후식'] as const;

// maxCookTimeMin은 그 값을 포함한다.
export const COOK_TIME_OPTIONS = [
  { key: '10', label: '10분 이내', maxCookTimeMin: 10 },
  { key: '20', label: '20분 이내', maxCookTimeMin: 20 },
  { key: '30', label: '30분 이내', maxCookTimeMin: 30 },
] as const;

export type CookTimeKey = (typeof COOK_TIME_OPTIONS)[number]['key'];

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
