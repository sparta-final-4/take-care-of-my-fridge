import { useQuery } from '@tanstack/react-query';

import type { IngredientItem } from '@/common/api/clients';
import { useAuthStore } from '@/common/auth/authStore';
import { fridgeQueries } from '@/domains/fridge/queries';

// 레시피 재료를 내 냉장고와 ingredientId로 맞춰 본다. 로그인하지 않았으면 냉장고를 조회하지 않는다.
// 같은 재료가 냉장고에 여러 줄 있으면 가장 임박한 D-day를 쓴다.
export function useRecipeFridgeMatch(ingredients: IngredientItem[]) {
  const authenticated = useAuthStore((state) => state.status) === 'authenticated';
  const { data: fridgeItems, isSuccess } = useQuery({
    ...fridgeQueries.items('EXPIRATION'),
    enabled: authenticated,
  });

  const dDayByIngredient = new Map<number, number>();
  fridgeItems?.forEach((item) => {
    if (item.ingredientId === null) return;
    const prev = dDayByIngredient.get(item.ingredientId);
    if (prev === undefined || item.dDay < prev) dDayByIngredient.set(item.ingredientId, item.dDay);
  });

  // 같은 재료가 레시피에 두 번 나올 수 있어(양념 그룹 등) 재료 ID로 한 번만 센다.
  const ingredientIds = [
    ...new Set(
      ingredients.map((item) => item.ingredientId).filter((id): id is number => id !== undefined),
    ),
  ];
  const heldIds = ingredientIds.filter((id) => dDayByIngredient.has(id));

  return {
    // 냉장고를 확인했을 때만 보유 여부를 보여 준다.
    checked: authenticated && isSuccess,
    dDayOf: (ingredientId: number | undefined) =>
      ingredientId === undefined ? undefined : dDayByIngredient.get(ingredientId),
    heldIds,
    totalCount: ingredientIds.length,
    missingCount: ingredientIds.length - heldIds.length,
  };
}

export type RecipeFridgeMatch = ReturnType<typeof useRecipeFridgeMatch>;
