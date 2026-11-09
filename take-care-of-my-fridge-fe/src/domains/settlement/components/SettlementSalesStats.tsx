import { Card, Col, Row, Statistic, theme } from 'antd';

import { useScreens } from '@/common/hooks/useScreens';

import type { SalesAmountResponse } from '../api';
import { formatCount, formatWon } from '../format';

function salesStats(sales?: SalesAmountResponse) {
  return [
    { title: '결제금액', value: formatWon(sales?.paidAmount) },
    { title: '환불금액', value: formatWon(sales?.refundedAmount) },
    { title: '순매출', value: formatWon(sales?.netAmount), negative: (sales?.netAmount ?? 0) < 0 },
    { title: '결제 건수', value: formatCount(sales?.paidCount) },
    { title: '환불 건수', value: formatCount(sales?.refundedCount) },
  ];
}

interface SettlementSalesStatsProps {
  sales?: SalesAmountResponse;
  bordered?: boolean;
}

// 다섯 칸을 모바일 2열, 태블릿 3열, 데스크톱 한 줄로 놓는다
export function SettlementSalesStats({ sales, bordered }: SettlementSalesStatsProps) {
  // 한 줄 카드 수: 휴대폰 2 / md 3 / xl 5.
  const screens = useScreens();
  const statColProps = screens.xl ? { flex: '20%' } : { span: screens.md ? 8 : 12 };
  const { token } = theme.useToken();
  return (
    <Row gutter={[token.marginSM, token.marginSM]}>
      {salesStats(sales).map((stat) => {
        const statistic = (
          <Statistic
            title={stat.title}
            value={stat.value}
            styles={stat.negative ? { content: { color: token.colorError } } : undefined}
          />
        );
        return (
          <Col key={stat.title} {...statColProps}>
            {bordered ? <Card size="small">{statistic}</Card> : statistic}
          </Col>
        );
      })}
    </Row>
  );
}
