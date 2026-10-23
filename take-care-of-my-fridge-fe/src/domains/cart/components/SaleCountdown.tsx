import { useQueryClient } from '@tanstack/react-query';
import { Typography } from 'antd';
import dayjs from 'dayjs';
import { useEffect, useState } from 'react';

import { formatRemaining } from '@/domains/cart/format';
import { cartQueries } from '@/domains/cart/queries';

interface SaleCountdownProps {
  endsAt: string;
}

// 종료 시각까지 남은 시간을 1초마다 다시 그린다. 0이 되면 장바구니를 다시 읽어 할인이 끝난 가격을 보여 준다.
// 기기 시계가 틀리면 그만큼 일찍·늦게 끝나 보이지만, 실제 가격은 주문할 때 서버가 정한다.
export function SaleCountdown({ endsAt }: SaleCountdownProps) {
  const queryClient = useQueryClient();
  const [now, setNow] = useState(() => Date.now());
  const remaining = dayjs(endsAt).valueOf() - now;
  const expired = remaining <= 0;

  useEffect(() => {
    if (expired) {
      void queryClient.invalidateQueries({ queryKey: cartQueries.all });
      return;
    }
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [expired, queryClient]);

  return (
    <Typography.Text type="secondary">
      {expired ? '할인 종료' : `남은 시간 ${formatRemaining(remaining)}`}
    </Typography.Text>
  );
}
