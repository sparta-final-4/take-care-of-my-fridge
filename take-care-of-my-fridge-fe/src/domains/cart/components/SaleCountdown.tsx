import { Typography } from 'antd';
import dayjs from 'dayjs';
import { useEffect, useState } from 'react';

import { serverNow } from '@/common/api/serverClock';
import { formatRemaining } from '@/domains/cart/format';

interface SaleCountdownProps {
  endsAt: string;
}

// 종료 시각까지 남은 시간을 1초마다 다시 그린다. 0이 되면 "할인 종료"로 바뀌고, 가격은 사용자가 새로고침하면 바뀐다.
// 현재 시각은 기기 시계가 아니라 서버 시각(응답의 Date 헤더 기준)이라 모든 사용자가 같은 시간을 본다.
export function SaleCountdown({ endsAt }: SaleCountdownProps) {
  const [now, setNow] = useState(() => serverNow());
  const remaining = dayjs(endsAt).valueOf() - now;
  const expired = remaining <= 0;

  useEffect(() => {
    if (expired) return;
    const timer = setInterval(() => setNow(serverNow()), 1000);
    return () => clearInterval(timer);
  }, [expired]);

  return (
    <Typography.Text type="secondary">
      {expired ? '할인 종료' : `남은 시간 ${formatRemaining(remaining)}`}
    </Typography.Text>
  );
}
