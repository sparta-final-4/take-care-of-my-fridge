import { useQuery } from '@tanstack/react-query';
import { Skeleton, Typography } from 'antd';

import { SectionCard } from '@/common/components/SectionCard';
import { cookingQueries } from '@/domains/cooking/queries';

import styles from './RecipeCookingStats.module.scss';

interface RecipeCookingStatsProps {
  recipeId: number;
}

export function RecipeCookingStats({ recipeId }: RecipeCookingStatsProps) {
  const { data, isPending } = useQuery(cookingQueries.stats(recipeId));

  const totalCount = data?.totalCount ?? 0;
  const failedCount = data?.failedCount ?? 0;

  const renderContent = () => {
    if (isPending) {
      return <Skeleton active paragraph={{ rows: 1 }} title={false} />;
    }

    if (totalCount === 0) {
      return (
        <Typography.Text type="secondary" className={styles.text}>
          아직 요리 기록이 없어요
        </Typography.Text>
      );
    }

    if (totalCount < 5) {
      return (
        <Typography.Text className={styles.text}>
          {totalCount}번 중 {failedCount}번 실패했어요
        </Typography.Text>
      );
    }

    const failRate = Math.round((failedCount / totalCount) * 100);
    return (
      <Typography.Text className={styles.text}>
        {totalCount}번 중 {failedCount}번 실패했어요 ({failRate}%)
      </Typography.Text>
    );
  };

  return <SectionCard title="요리 통계">{renderContent()}</SectionCard>;
}
