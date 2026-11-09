import { Card, Col, Empty, Row, theme, Typography } from 'antd';

import { useScreens } from '@/common/hooks/useScreens';

import type {
  SalesAmountResponse,
  SettlementMethodResponse,
  SettlementPeriodResponse,
  SettlementUnit,
} from '../api';
import { SettlementChart } from './SettlementChart';
import { SettlementMethodChart } from './SettlementMethodChart';
import { SettlementMethodTable } from './SettlementMethodTable';
import { SettlementRatioChart } from './SettlementRatioChart';
import { SettlementSalesStats } from './SettlementSalesStats';

import styles from './SettlementSummarySection.module.scss';

interface SettlementSummarySectionProps {
  unit: SettlementUnit;
  summary?: SalesAmountResponse;
  periods: SettlementPeriodResponse[];
  methods: SettlementMethodResponse[];
}

// 요약 탭의 조회 기간 부분: 기간 합계, 기간별 차트, 환불률·객단가 추이, 결제 수단별 합계와 비중.
// 결제·환불이 없으면 차트 대신 빈 상태를 보여 주고 결제 수단별 표는 감춘다
export function SettlementSummarySection({
  unit,
  summary,
  periods,
  methods,
}: SettlementSummarySectionProps) {
  const { token } = theme.useToken();
  // 휴대폰은 폭에 따라, 마우스를 쓰는 PC는 창을 줄여도 넓은 배치(useScreens).
  // 환불률·객단가는 md부터 2열, 결제 수단 표·도넛은 lg부터 한 줄
  const screens = useScreens();
  const ratioColSpan = screens.md ? 12 : 24;
  const methodColSpans = screens.lg ? { table: 14, chart: 10 } : { table: 24, chart: 24 };
  const gutter: [number, number] = [token.marginSM, token.marginSM];

  const hasSales = periods.some((period) => period.paidAmount || period.refundedAmount);
  const hasPaidMethod = methods.some((method) => (method.paidAmount ?? 0) > 0);

  return (
    <>
      <div className={styles.block}>
        <Typography.Title level={5}>조회 기간 합계</Typography.Title>
        <SettlementSalesStats sales={summary} bordered />
      </div>

      {!hasSales ? (
        <Card size="small" className={styles.block}>
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="이 기간에는 매출이 없어요" />
        </Card>
      ) : (
        <>
          <Card size="small" title="기간별 매출" className={styles.block}>
            <SettlementChart unit={unit} periods={periods} />
          </Card>

          <Row gutter={gutter} className={styles.block}>
            <Col span={ratioColSpan}>
              <Card size="small" title="환불률">
                <SettlementRatioChart metric="refundRate" unit={unit} periods={periods} />
              </Card>
            </Col>
            <Col span={ratioColSpan}>
              <Card size="small" title="객단가">
                <SettlementRatioChart metric="averagePaidAmount" unit={unit} periods={periods} />
              </Card>
            </Col>
          </Row>
        </>
      )}

      {methods.length > 0 && (
        <div className={styles.block}>
          <Typography.Title level={5}>결제 수단별</Typography.Title>
          <Row gutter={gutter}>
            <Col span={hasPaidMethod ? methodColSpans.table : 24}>
              <SettlementMethodTable methods={methods} />
            </Col>
            {hasPaidMethod && (
              <Col span={methodColSpans.chart}>
                <Card size="small" title="결제금액 비중">
                  <SettlementMethodChart methods={methods} />
                </Card>
              </Col>
            )}
          </Row>
        </div>
      )}
    </>
  );
}
