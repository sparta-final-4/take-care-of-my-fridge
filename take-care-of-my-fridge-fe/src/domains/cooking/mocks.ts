import type {
  CookingSortType,
  CookingTabType,
  MyCookedRecipeItem,
  ThisMonthCookingSummary,
} from './api';

export const MOCK_MY_COOKED_RECIPES: MyCookedRecipeItem[] = [
  {
    recipeId: 1,
    recipeName: '불닭 참치 두부조림',
    imageUrl: '',
    isAiRecommended: true,
    cookCount: 3,
    lastCookedAt: '2026-10-12',
    review: {
      id: 101,
      rating: 5,
      content: '설탕 빼고 간장으로 바꾸니 드디어 성공! 이제 안 망해요.',
      updatedAt: '2026-10-12',
    },
    history: [
      { id: 1, date: '2026-10-12', result: 'SUCCESS', memo: '간장 반 스푼으로 바꿈' },
      { id: 2, date: '2026-10-08', result: 'FAILED', memo: '너무 달았음' },
      { id: 3, date: '2026-10-01', result: 'FAILED', memo: '두부가 다 부서짐' },
    ],
  },
  {
    recipeId: 2,
    recipeName: '참치 두부 덮밥',
    imageUrl: '',
    isAiRecommended: true,
    cookCount: 1,
    lastCookedAt: '2026-10-10',
    review: {
      id: 102,
      rating: 5,
      content: '간단하고 맛있게 한 끼 해결했어요.',
      updatedAt: '2026-10-10',
    },
    history: [{ id: 4, date: '2026-10-10', result: 'SUCCESS', memo: '계란 후라이 추가함' }],
  },
  {
    recipeId: 3,
    recipeName: '두부 크림 파스타',
    imageUrl: '',
    isAiRecommended: false,
    cookCount: 2,
    lastCookedAt: '2026-10-05',
    review: {
      id: 103,
      rating: 4,
      content: '고소하고 담백해서 가족들이 좋아하네요.',
      updatedAt: '2026-10-05',
    },
    history: [
      { id: 5, date: '2026-10-05', result: 'SUCCESS', memo: '우유 약간 더 넣음' },
      { id: 6, date: '2026-09-28', result: 'SUCCESS', memo: '후추 많이 뿌림' },
    ],
  },
  {
    recipeId: 4,
    recipeName: '계란말이',
    imageUrl: '',
    isAiRecommended: false,
    cookCount: 5,
    lastCookedAt: '2026-10-03',
    review: null,
    history: [
      { id: 7, date: '2026-10-03', result: 'SUCCESS', memo: '파 듬뿍' },
      { id: 8, date: '2026-09-25', result: 'SUCCESS', memo: '당근 추가' },
      { id: 9, date: '2026-09-18', result: 'FAILED', memo: '불 조절 실패' },
      { id: 10, date: '2026-09-10', result: 'SUCCESS', memo: '치즈 넣음' },
      { id: 11, date: '2026-09-02', result: 'SUCCESS', memo: '기본 레시피' },
    ],
  },
];

/**
 * 탭 및 정렬 조건에 따라 레시피 목록을 필터링 및 정렬한다.
 */
export function filterAndSortMyRecipes(
  recipes: MyCookedRecipeItem[],
  tab: CookingTabType,
  sort: CookingSortType,
): MyCookedRecipeItem[] {
  let filtered = [...recipes];

  if (tab === 'UNREVIEWED') {
    filtered = filtered.filter((r) => r.review === null);
  } else if (tab === 'REVIEWED') {
    filtered = filtered.filter((r) => r.review !== null);
  }

  if (sort === 'MOST_COOKED') {
    filtered.sort(
      (a, b) => b.cookCount - a.cookCount || b.lastCookedAt.localeCompare(a.lastCookedAt),
    );
  } else {
    // LATEST
    filtered.sort(
      (a, b) => b.lastCookedAt.localeCompare(a.lastCookedAt) || b.cookCount - a.cookCount,
    );
  }

  return filtered;
}

/**
 * 탭별 레시피 개수를 계산한다.
 */
export function countRecipesByTab(recipes: MyCookedRecipeItem[]): Record<CookingTabType, number> {
  const total = recipes.length;
  const unreviewed = recipes.filter((r) => r.review === null).length;
  const reviewed = recipes.filter((r) => r.review !== null).length;

  return {
    ALL: total,
    UNREVIEWED: unreviewed,
    REVIEWED: reviewed,
  };
}

/**
 * 이번 달 요리 통계를 계산한다.
 */
export function calculateThisMonthSummary(recipes: MyCookedRecipeItem[]): ThisMonthCookingSummary {
  const totalCooks = recipes.reduce((sum, r) => sum + r.cookCount, 0);

  // 가장 많이 만든 레시피
  const mostCooked = [...recipes].sort((a, b) => b.cookCount - a.cookCount)[0];

  return {
    completedCookCount: totalCooks,
    consumedIngredientCount: 18, // 가짜 데이터 (소진한 재료 수)
    mostCookedRecipeName: mostCooked ? mostCooked.recipeName : '-',
    mostCookedRecipeCount: mostCooked ? mostCooked.cookCount : 0,
  };
}
