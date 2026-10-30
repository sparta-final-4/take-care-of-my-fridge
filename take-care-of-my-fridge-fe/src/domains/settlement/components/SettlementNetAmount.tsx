import { Typography } from 'antd';

import { formatWon } from '../format';

interface SettlementNetAmountProps {
  value?: number;
}

// 순매출은 환불이 더 많으면 음수가 될 수 있어 빨간색으로 보여 준다
export function SettlementNetAmount({ value }: SettlementNetAmountProps) {
  return (
    <Typography.Text type={(value ?? 0) < 0 ? 'danger' : undefined}>
      {formatWon(value)}
    </Typography.Text>
  );
}
