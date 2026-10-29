import { Flex, Tag, Typography } from 'antd';

import type { SaleResponse } from '@/common/api/clients';
import { SaleCountdown } from '@/domains/sale/components/SaleCountdown';
import { SALE_TYPE_COLORS, SALE_TYPE_LABELS } from '@/domains/sale/format';

import styles from './SaleInfo.module.scss';

interface SaleInfoProps {
  sale: SaleResponse;
}

// 할인 구분, 남은 시간, 번개 드롭 남은 수량. 상품 상세와 장바구니가 가격 아래에 둔다.
export function SaleInfo({ sale }: SaleInfoProps) {
  return (
    <Flex vertical gap={2}>
      <Flex align="center" gap="small" wrap>
        {sale.type && (
          <Tag color={SALE_TYPE_COLORS[sale.type]} className={styles.tag}>
            {SALE_TYPE_LABELS[sale.type]}
          </Tag>
        )}
        {sale.endsAt && <SaleCountdown endsAt={sale.endsAt} />}
      </Flex>
      {sale.type === 'FLASH_DROP' && (
        <Typography.Text>
          남은 수량 {sale.remainingQuantity} / {sale.limitedQuantity}개
        </Typography.Text>
      )}
    </Flex>
  );
}
