import { useQuery } from '@tanstack/react-query';
import { Alert } from 'antd';

import { cookingQueries } from '@/domains/cooking/queries';

interface RecipeFailureNoticeProps {
  recipeId: number;
}

// 다른 회원의 요리 기록 중 실패가 있으면 알린다. 실패 메모는 공개하지 않고 수만 보여 준다.
export function RecipeFailureNotice({ recipeId }: RecipeFailureNoticeProps) {
  const { data: stats } = useQuery(cookingQueries.stats(recipeId));
  const failedCount = stats?.failedCount ?? 0;
  if (failedCount === 0) return null;

  return (
    <Alert
      type="warning"
      showIcon
      title={`실패 기록이 ${failedCount}번 있어요`}
      description={`전체 ${stats?.totalCount ?? 0}번 중 ${stats?.successCount ?? 0}번 성공했어요. 조리 순서와 팁을 한 번 더 확인해 주세요.`}
    />
  );
}
