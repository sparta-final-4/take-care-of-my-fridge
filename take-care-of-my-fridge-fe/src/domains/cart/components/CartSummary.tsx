import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Button, Divider, Flex, Typography } from 'antd';
import { useRef } from 'react';
import { generatePath, useNavigate } from 'react-router';

import { ROUTES } from '@/app/paths';
import { getErrorCode, isOutcomeUnknown } from '@/common/api/errors';
import type { CartItemResponse } from '@/domains/cart/api';
import { formatPrice } from '@/domains/cart/format';
import { cartQueries } from '@/domains/cart/queries';
import { createOrder } from '@/domains/order/api';
import styles from './CartSummary.module.scss';

interface CartSummaryProps {
  checkedItems: CartItemResponse[];
}

// 금액은 지금 가격으로 계산한다. 가격은 주문할 때 확정된다.
export function CartSummary({ checkedItems }: CartSummaryProps) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const orderMutation = useMutation({
    mutationFn: ({
      items,
      idempotencyKey,
    }: {
      items: CartItemResponse[];
      idempotencyKey: string;
    }) =>
      createOrder(
        items.map(({ cartItemId, unitPrice, saleType }) => ({
          cartItemId,
          unitPrice,
          // 할인이 없으면 null이지만 생성 타입은 생략으로 받는다. 서버는 생략을 null로 읽는다.
          saleType: saleType ?? undefined,
        })),
        idempotencyKey,
      ),
  });
  // 주문 시도 하나에 멱등 키 하나. 응답을 받기 전에 다시 누르면 같은 키를 보내 주문이 둘 생기지 않게 한다.
  const attemptRef = useRef<{ request: string; idempotencyKey: string } | null>(null);

  const listPriceAmount = checkedItems.reduce(
    (sum, item) => sum + item.listPrice * item.quantity,
    0,
  );
  const totalAmount = checkedItems.reduce((sum, item) => sum + item.unitPrice * item.quantity, 0);
  const discountAmount = listPriceAmount - totalAmount;

  const handleOrder = () => {
    // 체크한 항목, 수량, 가격이 바뀌면 다른 주문이므로 새 키를 만든다. 같은 키로 보내면 서버가 거절한다.
    const request = checkedItems
      .map((item) => `${item.cartItemId}:${item.quantity}:${item.unitPrice}:${item.saleType}`)
      .join(',');
    if (attemptRef.current?.request !== request) {
      attemptRef.current = { request, idempotencyKey: crypto.randomUUID() };
    }
    const { idempotencyKey } = attemptRef.current;
    orderMutation.mutate(
      { items: checkedItems, idempotencyKey },
      {
        onSuccess: ({ orderId }) => {
          attemptRef.current = null;
          if (orderId === undefined) return;
          navigate(generatePath(ROUTES.orderCheckout, { orderId: String(orderId) }));
        },
        // 서버가 답한 실패는 끝난 시도다. 응답이 없거나 5xx면 주문이 만들어졌을 수 있어 키를 남긴다.
        // 가격이 바뀌었으면(실패 알림은 서버 메시지) 장바구니를 다시 읽어 새 가격을 보여 준다. 다시 눌러야 주문된다.
        onError: (error) => {
          if (!isOutcomeUnknown(error)) attemptRef.current = null;
          if (getErrorCode(error) === 'ORDER_010') {
            void queryClient.invalidateQueries({ queryKey: cartQueries.all });
          }
        },
      },
    );
  };

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
      <Button
        type="primary"
        size="large"
        block
        disabled={checkedItems.length === 0}
        loading={orderMutation.isPending}
        onClick={handleOrder}
        className={styles.order}
      >
        주문하기 ({checkedItems.length}개)
      </Button>
    </section>
  );
}
