import { useQuery } from '@tanstack/react-query';
import { Flex, Tag, Typography } from 'antd';
import dayjs from 'dayjs';

import type { RecipeDetailResponse } from '@/common/api/clients';
import { SectionCard } from '@/common/components/SectionCard';
import { useAuthStore } from '@/common/auth/authStore';
import { cookingQueries } from '@/domains/cooking/queries';
import { formatDDay } from '@/domains/fridge/format';
import type { RecipeFridgeMatch } from '@/domains/recipe/hooks/useRecipeFridgeMatch';

import styles from './RecipeCookPanel.module.scss';

// 재료 목록과 같은 기준(3일 이내)으로 임박 재료를 보여 준다.
const IMMINENT_DAYS = 3;

interface RecipeCookPanelProps {
  recipe: RecipeDetailResponse;
  fridgeMatch: RecipeFridgeMatch;
}

// 요리 완료 버튼과 팝업은 요리 기록 담당 작업에서 이 카드에 붙인다.
export function RecipeCookPanel({ recipe, fridgeMatch }: RecipeCookPanelProps) {
  const authenticated = useAuthStore((state) => state.status) === 'authenticated';
  const recipeId = recipe.recipeId ?? 0;
  const ingredients = recipe.ingredients ?? [];
  // 가장 최근 기록 1건과 전체 개수만 있으면 된다.
  const { data: logs } = useQuery({
    ...cookingQueries.logs({ recipeId, page: 1, size: 1 }),
    enabled: authenticated && recipeId > 0,
  });

  const { checked, dDayOf, heldIds, totalCount, missingCount } = fridgeMatch;
  // 같은 재료가 두 번 나오면 첫 줄만 쓴다.
  const heldIngredients = ingredients.filter(
    (item, index) =>
      item.ingredientId !== undefined &&
      heldIds.includes(item.ingredientId) &&
      ingredients.findIndex((other) => other.ingredientId === item.ingredientId) === index,
  );
  const imminent = heldIngredients.filter((item) => {
    const dDay = dDayOf(item.ingredientId);
    return dDay !== undefined && dDay <= IMMINENT_DAYS;
  });
  const cookCount = logs?.totalElements ?? 0;
  const lastCookedAt = logs?.content?.[0]?.createdAt;

  return (
    <SectionCard title="내 냉장고 재료">
      <Flex vertical gap="middle">
        {checked ? (
          <Typography.Text>
            {missingCount === 0
              ? `필요한 재료 ${totalCount}개가 모두 있어요.`
              : `필요한 재료 ${totalCount}개 중 ${heldIds.length}개 있어요.`}
          </Typography.Text>
        ) : (
          <Typography.Text type="secondary">
            로그인하면 내 냉장고 재료와 요리 기록을 함께 보여 줘요.
          </Typography.Text>
        )}
        {imminent.length > 0 && (
          <Flex gap="small" wrap>
            {imminent.map((item) => {
              const dDay = dDayOf(item.ingredientId) ?? 0;
              return (
                <Tag key={item.seq} color={dDay <= 1 ? 'red' : 'orange'} className={styles.tag}>
                  {item.displayName} {formatDDay(dDay)}
                </Tag>
              );
            })}
          </Flex>
        )}
        {authenticated && logs && (
          <Typography.Text>
            {cookCount > 0 ? (
              <>
                <Typography.Text strong>{cookCount}번 만들었어요</Typography.Text>
                {lastCookedAt && (
                  <Typography.Text type="secondary">
                    {' '}
                    · 마지막 {dayjs(lastCookedAt).format('YYYY-MM-DD')}
                  </Typography.Text>
                )}
              </>
            ) : (
              <Typography.Text type="secondary">아직 만든 적 없어요.</Typography.Text>
            )}
          </Typography.Text>
        )}
      </Flex>
    </SectionCard>
  );
}
