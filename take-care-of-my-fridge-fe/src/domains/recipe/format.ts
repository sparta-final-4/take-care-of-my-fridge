import type { RecipeListItemResponse } from '@/common/api/clients';

// 백엔드는 cuisineType, category가 값 하나와 정확히 같은지만 비교한다.
// 칩 값은 실제 레시피 데이터(식약처 MFDS, 농식품부 MAFRA)에 있는 값만 쓴다.
// 두 출처의 분류 체계가 달라 "국"은 "국&찌개" 같은 비슷한 분류를 함께 찾지 못한다(백엔드 분류 통합 후 묶는다).
export const CUISINE_OPTIONS = ['한식', '서양', '중국', '일본'] as const;
export const CATEGORY_OPTIONS = ['밥', '국', '반찬', '만두/면류', '후식'] as const;

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
