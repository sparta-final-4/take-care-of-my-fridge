import { useQueryClient } from '@tanstack/react-query';
import { Typography } from 'antd';
import dayjs from 'dayjs';
import { useEffect, useState } from 'react';

import { serverNow } from '@/common/api/serverClock';
import { formatRemaining } from '@/domains/cart/format';
import { cartQueries } from '@/domains/cart/queries';

interface SaleCountdownProps {
  endsAt: string;
}

// 종료 시각까지 남은 시간을 1초마다 다시 그린다. 0이 되면 장바구니를 다시 읽어 할인이 끝난 가격을 보여 준다.
// 현재 시각은 기기 시계가 아니라 서버 시각(응답의 Date 헤더 기준)이라 모든 사용자가 같은 시간을 본다.
export function SaleCountdown({ endsAt }: SaleCountdownProps) {
  const queryClient = useQueryClient();
  const [now, setNow] = useState(() => serverNow());
  const remaining = dayjs(endsAt).valueOf() - now;
  const expired = remaining <= 0;

  useEffect(() => {
    if (expired) {
      void queryClient.invalidateQueries({ queryKey: cartQueries.all });
      return;
    }
    const timer = setInterval(() => setNow(serverNow()), 1000);
    return () => clearInterval(timer);
  }, [expired, queryClient]);

  return (
    <Typography.Text type="secondary">
      {expired ? '할인 종료' : `남은 시간 ${formatRemaining(remaining)}`}
    </Typography.Text>
  );
}
