import { Card, Empty, Typography } from 'antd';

import type { SalesAmountResponse, SettlementPeriodResponse, SettlementUnit } from '../api';
import { SettlementChart } from './SettlementChart';
import { SettlementSalesStats } from './SettlementSalesStats';

import styles from './SettlementSummarySection.module.scss';

interface SettlementSummarySectionProps {
  unit: SettlementUnit;
  summary?: SalesAmountResponse;
  periods: SettlementPeriodResponse[];
}

// 요약 탭의 조회 기간 부분: 기간 합계와 기간별 차트
export function SettlementSummarySection({
  unit,
  summary,
  periods,
}: SettlementSummarySectionProps) {
  const hasSales = periods.some((period) => period.paidAmount || period.refundedAmount);

  return (
    <>
      <div className={styles.block}>
        <Typography.Title level={5}>조회 기간 합계</Typography.Title>
        <SettlementSalesStats sales={summary} bordered />
      </div>

      <Card size="small">
        {hasSales ? (
          <SettlementChart unit={unit} periods={periods} />
        ) : (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="이 기간에는 매출이 없어요" />
        )}
      </Card>
    </>
  );
}
