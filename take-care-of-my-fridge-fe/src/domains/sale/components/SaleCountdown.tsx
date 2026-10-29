import dayjs from 'dayjs';
import { useEffect, useState } from 'react';

import { serverNow } from '@/common/api/serverClock';
import { formatRemaining } from '@/domains/sale/format';

interface SaleCountdownProps {
  endsAt: string;
  // 상품 카드 이미지 위처럼 좁은 곳에서는 "남은 시간" 없이 시간만 쓴다.
  compact?: boolean;
}

// 종료 시각까지 남은 시간을 1초마다 다시 그린다. 0이 되면 "할인 종료"로 바뀌고, 가격은 사용자가 새로고침하면 바뀐다.
// 현재 시각은 기기 시계가 아니라 서버 시각(응답의 Date 헤더 기준)이라 모든 사용자가 같은 시간을 본다.
export function SaleCountdown({ endsAt, compact = false }: SaleCountdownProps) {
  const [now, setNow] = useState(() => serverNow());
  const remaining = dayjs(endsAt).valueOf() - now;
  const expired = remaining <= 0;

  useEffect(() => {
    if (expired) return;
    const timer = setInterval(() => setNow(serverNow()), 1000);
    return () => clearInterval(timer);
  }, [expired]);

  return (
    // 글자색은 놓인 곳(상품 카드 이미지 위 띠, 상세, 장바구니)을 따른다.
    <span>
      {expired ? '할인 종료' : `${compact ? '' : '남은 시간 '}${formatRemaining(remaining)}`}
    </span>
  );
}
