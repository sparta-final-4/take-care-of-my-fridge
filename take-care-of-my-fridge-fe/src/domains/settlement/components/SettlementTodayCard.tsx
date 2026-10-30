import { useQuery } from '@tanstack/react-query';
import { Button, Card, Spin, Typography } from 'antd';
import dayjs from 'dayjs';

import { getErrorMessage } from '@/common/api/errors';

import { settlementQueries } from '../queries';
import { SettlementSalesStats } from './SettlementSalesStats';

interface SettlementTodayCardProps {
  className?: string;
}

// 오늘 0시부터 지금까지의 매출. 조회 기간과 상관없어서 기간 조회와 따로 불러온다
export function SettlementTodayCard({ className }: SettlementTodayCardProps) {
  const todayQuery = useQuery(settlementQueries.today());

  return (
    <Card
      size="small"
      className={className}
      title={
        todayQuery.data
          ? `오늘 매출 (${dayjs(todayQuery.dataUpdatedAt).format('HH:mm')} 기준)`
          : '오늘 매출'
      }
      extra={
        <Button
          size="small"
          onClick={() => void todayQuery.refetch()}
          loading={todayQuery.isFetching}
        >
          새로고침
        </Button>
      }
    >
      {todayQuery.isError && !todayQuery.data ? (
        <Typography.Text type="danger">{getErrorMessage(todayQuery.error)}</Typography.Text>
      ) : (
        <Spin spinning={todayQuery.isPending}>
          <SettlementSalesStats sales={todayQuery.data?.sales} />
        </Spin>
      )}
    </Card>
  );
}
