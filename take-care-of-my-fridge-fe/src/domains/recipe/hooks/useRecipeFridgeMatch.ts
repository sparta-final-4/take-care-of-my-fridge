import { useQuery } from '@tanstack/react-query';

import type { IngredientItem } from '@/common/api/clients';
import { useAuthStore } from '@/common/auth/authStore';
import type { FridgeItemResponse } from '@/domains/fridge/api';
import { fridgeQueries } from '@/domains/fridge/queries';

type MatchedFridgeItem = Pick<FridgeItemResponse, 'dDay' | 'status'>;

// 레시피 재료를 내 냉장고와 ingredientId로 맞춰 본다. 로그인하지 않았으면 냉장고를 조회하지 않는다.
// 기한 지난 줄(EXPIRED)은 보유로 치지 않고, 남은 줄 중 가장 임박한 줄의 D-day·상태를 쓴다.
// 임박 여부는 서버가 계산한 status(IMMINENT)를 그대로 따른다.
export function useRecipeFridgeMatch(ingredients: IngredientItem[]) {
  const authenticated = useAuthStore((state) => state.status) === 'authenticated';
  const { data: fridgeItems, isSuccess } = useQuery({
    ...fridgeQueries.items('EXPIRATION'),
    enabled: authenticated,
  });

  const nearestByIngredient = new Map<number, MatchedFridgeItem>();
  fridgeItems?.forEach((item) => {
    if (item.ingredientId === null || item.status === 'EXPIRED') return;
    const prev = nearestByIngredient.get(item.ingredientId);
    if (prev === undefined || item.dDay < prev.dDay) {
      nearestByIngredient.set(item.ingredientId, { dDay: item.dDay, status: item.status });
    }
  });

  // 같은 재료가 레시피에 두 번 나올 수 있어(양념 그룹 등) 재료 ID로 한 번만 센다.
  const ingredientIds = [
    ...new Set(
      ingredients.map((item) => item.ingredientId).filter((id): id is number => id !== undefined),
    ),
  ];
  const heldIds = ingredientIds.filter((id) => nearestByIngredient.has(id));

  return {
    // 냉장고를 확인했을 때만 보유 여부를 보여 준다.
    checked: authenticated && isSuccess,
    fridgeItemOf: (ingredientId: number | undefined) =>
      ingredientId === undefined ? undefined : nearestByIngredient.get(ingredientId),
    heldIds,
    totalCount: ingredientIds.length,
    missingCount: ingredientIds.length - heldIds.length,
  };
}

export type RecipeFridgeMatch = ReturnType<typeof useRecipeFridgeMatch>;
