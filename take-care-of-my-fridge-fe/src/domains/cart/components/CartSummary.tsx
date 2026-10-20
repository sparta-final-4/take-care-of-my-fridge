import { Button, Divider, Flex, Tooltip, Typography } from 'antd';

import type { CartItemResponse } from '@/domains/cart/api';
import { formatPrice } from '@/domains/cart/format';
import styles from './CartSummary.module.scss';

interface CartSummaryProps {
  checkedItems: CartItemResponse[];
}

// 금액은 지금 가격으로 계산한다. 가격은 주문할 때 확정된다.
export function CartSummary({ checkedItems }: CartSummaryProps) {
  const listPriceAmount = checkedItems.reduce(
    (sum, item) => sum + item.listPrice * item.quantity,
    0,
  );
  const totalAmount = checkedItems.reduce((sum, item) => sum + item.unitPrice * item.quantity, 0);
  const discountAmount = listPriceAmount - totalAmount;

  return (
    <section className={styles.card} aria-label="결제 예정 금액">
      <Typography.Text strong className={styles.title}>
        결제 예정 금액
      </Typography.Text>
      <Flex vertical gap="small" className={styles.rows}>
        <Flex justify="space-between">
          <Typography.Text type="secondary">상품 금액</Typography.Text>
          <Typography.Text className={styles.amount}>
            {formatPrice(listPriceAmount)}
          </Typography.Text>
        </Flex>
        <Flex justify="space-between">
          <Typography.Text type="secondary">할인 금액</Typography.Text>
          <Typography.Text className={styles.amount}>
            {discountAmount > 0 ? `-${formatPrice(discountAmount)}` : formatPrice(0)}
          </Typography.Text>
        </Flex>
      </Flex>
      <Divider className={styles.divider} />
      <Flex justify="space-between" align="baseline">
        <Typography.Text strong>합계</Typography.Text>
        <Typography.Text strong className={styles.total}>
          {formatPrice(totalAmount)}
        </Typography.Text>
      </Flex>
      {/* 주문 API가 붙으면 활성화한다. */}
      <Tooltip title="주문 기능은 준비 중입니다.">
        <Button type="primary" size="large" block disabled className={styles.order}>
          주문하기 ({checkedItems.length}개)
        </Button>
      </Tooltip>
    </section>
  );
}
