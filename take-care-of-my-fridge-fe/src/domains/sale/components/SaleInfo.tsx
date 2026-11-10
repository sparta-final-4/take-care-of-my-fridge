import { ClockCircleOutlined } from '@ant-design/icons';
import { Flex, Tag, Typography } from 'antd';

import type { SaleResponse } from '@/common/api/clients';
import { SaleCountdown } from '@/domains/sale/components/SaleCountdown';
import { SALE_TYPE_COLORS, SALE_TYPE_LABELS } from '@/domains/sale/format';

import styles from './SaleInfo.module.scss';

interface SaleInfoProps {
  sale: SaleResponse;
}

// 할인 구분, 남은 시간, 번개 드롭 남은 수량. 상품 상세와 장바구니가 가격 아래에 둔다.
// 태그와 남은 시간은 장바구니 칸 폭이 충분하면 한 줄에, 좁으면 두 줄에 둔다. 칸 폭으로만 정해서 같은 화면의 항목은 모두 같은 모양이다.
export function SaleInfo({ sale }: SaleInfoProps) {
  return (
    <Flex vertical align="flex-start" gap={2}>
      <div className={styles.head}>
        {sale.type && (
          <Tag color={SALE_TYPE_COLORS[sale.type]} className={styles.tag}>
            {SALE_TYPE_LABELS[sale.type]}
          </Tag>
        )}
        {/* 줄어드는 시간이 눈에 띄도록 할인율처럼 굵은 빨강으로 둔다. 상품 카드처럼 "남은 시간" 대신 시계 아이콘을 붙인다. */}
        {sale.endsAt && (
          <Typography.Text strong type="danger" className={styles.countdown}>
            <ClockCircleOutlined aria-label="남은 시간" />{' '}
            <SaleCountdown endsAt={sale.endsAt} compact />
          </Typography.Text>
        )}
      </div>
      {sale.type === 'FLASH_DROP' && (
        <Typography.Text>
          남은 수량 {sale.remainingQuantity} / {sale.limitedQuantity}개
        </Typography.Text>
      )}
    </Flex>
  );
}
