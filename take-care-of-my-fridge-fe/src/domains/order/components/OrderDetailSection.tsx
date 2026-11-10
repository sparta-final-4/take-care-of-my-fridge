import { RightOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Divider, Flex, Modal, Result, Skeleton, Tag, Typography } from 'antd';
import dayjs from 'dayjs';
import { useState } from 'react';
import { generatePath, useNavigate } from 'react-router';

import { ROUTES } from '@/app/paths';
import { getErrorCode } from '@/common/api/errors';
import { formatPrice } from '@/domains/cart/format';
import { confirmOrder } from '@/domains/order/api';
import { ORDER_STATUS_COLORS, ORDER_STATUS_LABELS } from '@/domains/order/format';
import { orderQueries } from '@/domains/order/queries';
import {
  getOrderItemRefundState,
  groupRefundedItems,
  isFullyRefunded,
  type OrderItemRefundState,
  sumRefundAmountByStatus,
} from '@/domains/refund/format';
import { refundQueries } from '@/domains/refund/queries';

import { OrderItemHeader, OrderItemRow } from './OrderItemRow';
import styles from './OrderDetailSection.module.scss';

interface OrderDetailSectionProps {
  orderId: number;
}

// 주문할 때 저장한 상품·금액을 보여 준다. 환불 내역은 환불 상세 화면이 보여 준다.
// 결제 완료·구매 확정·전액 환불 주문은 환불 금액을 더하고, 환불이 있으면 환불 상세로 가는 버튼을 둔다.
// 구매 확정·환불 카드는 결제 완료·구매 확정·전액 환불 주문에 보인다. 구매 확정·전부 환불된 주문은 버튼을 누르면 안 되는 이유를 알린다.
// 결제 완료 주문의 환불 신청은 환불 신청 화면으로 이동하고, 환불할 수 있는 수량이 없으면 안내만 한다.
export function OrderDetailSection({ orderId }: OrderDetailSectionProps) {
  const navigate = useNavigate();
  const validOrderId = Number.isSafeInteger(orderId) && orderId > 0;
  const {
    data: order,
    isPending,
    isError,
    error,
    refetch,
  } = useQuery({ ...orderQueries.detail(orderId), enabled: validOrderId });
  // 환불 조회 실패는 화면을 막지 않는다. 환불 표시 없이 주문만 보여 준다.
  const hasRefundSection =
    order?.status === 'PAID' || order?.status === 'CONFIRMED' || order?.status === 'REFUNDED';
  const { data: refundHistory = [] } = useQuery({
    ...refundQueries.history(orderId),
    enabled: hasRefundSection,
  });
  // 환불 가능 수량은 결제 완료 주문만 조회된다. 구매 확정·전액 환불 주문은 환불 내역으로 태그를 정한다.
  const { data: refundableItems } = useQuery({
    ...refundQueries.refundableItems(orderId),
    enabled: order?.status === 'PAID',
  });
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [confirmAsked, setConfirmAsked] = useState(false);
  // 실패(이미 상태가 바뀐 주문 등)는 전역 알림이 서버 메시지로 알린다.
  // 성공·실패 모두 상세와 목록을 다시 불러, 화면 상태를 서버와 맞춘다.
  const confirmMutation = useMutation({
    mutationFn: () => confirmOrder(orderId),
    onSuccess: () => message.success('구매 확정되었습니다.'),
    onSettled: () => {
      setConfirmAsked(false);
      return queryClient.invalidateQueries({ queryKey: orderQueries.all });
    },
  });

  const listButton = (
    <Button className={styles.button} onClick={() => navigate(ROUTES.mypageOrders)}>
      목록으로
    </Button>
  );
  const notFound = <Result status="404" title="주문을 찾을 수 없습니다." extra={listButton} />;

  if (!validOrderId) return notFound;
  if (isPending) return <Skeleton active paragraph={{ rows: 6 }} />;
  if (isError) {
    if (getErrorCode(error) === 'ORDER_001') return notFound;
    return (
      <Result
        status="error"
        title="주문을 불러오지 못했습니다."
        extra={<Button onClick={() => refetch()}>다시 시도</Button>}
      />
    );
  }

  const isConfirmed = order.status === 'CONFIRMED';
  const isRefunded = order.status === 'REFUNDED';
  // 환불 내역에는 주문 상품 번호만 있고 상세 상품에는 없어, 같은 주문 안에서 겹치지 않는 상품 이름으로 짝을 짓는다.
  const refundedQuantityByName = new Map(
    groupRefundedItems(refundHistory).map((item) => [item.productName, item.quantity]),
  );
  const refundedAmount = sumRefundAmountByStatus(refundHistory, 'COMPLETED');
  // 환불 상세 화면은 실패한 환불을 보여 주지 않아, 실패 말고 다른 환불이 있을 때만 환불 금액과 상세 버튼을 둔다.
  const hasVisibleRefund = refundHistory.some((refund) => refund.status !== 'FAILED');
  // 환불 가능 상품을 아직 못 읽었으면 막지 않고, 환불 신청 화면이 다시 확인한다
  const nothingToRefund =
    refundableItems !== undefined &&
    refundableItems.every((item) => (item.refundableQuantity ?? 0) === 0);
  const handleRefundClick = () => {
    if (isRefunded) {
      message.info('전부 환불된 주문입니다.');
      return;
    }
    if (nothingToRefund) {
      message.info('환불할 수 있는 상품이 없습니다.');
      return;
    }
    navigate(generatePath(ROUTES.mypageOrderRefund, { orderId: String(orderId) }));
  };
  const handleConfirmClick = () => {
    if (isConfirmed) {
      message.info('이미 구매 확정된 주문입니다.');
      return;
    }
    if (isRefunded) {
      message.info('전부 환불된 주문입니다.');
      return;
    }
    // 환불 정보를 아직 못 읽었으면 막지 않고 서버가 다시 확인한다
    if (refundableItems !== undefined && isFullyRefunded(refundableItems, refundHistory)) {
      message.info('전부 환불된 주문은 구매 확정할 수 없습니다.');
      return;
    }
    setConfirmAsked(true);
  };

  return (
    <div className={styles.page}>
      <Flex align="center" justify="space-between" wrap gap="middle" className={styles.card}>
        <Flex vertical gap="small" className={styles.heading}>
          <Flex align="center" gap="small">
            <Typography.Title level={3} className={styles.title}>
              주문 상세
            </Typography.Title>
            {order.status && (
              <Tag color={ORDER_STATUS_COLORS[order.status]} className={styles.tag}>
                {ORDER_STATUS_LABELS[order.status]}
              </Tag>
            )}
          </Flex>
          <Typography.Text type="secondary" className={styles.meta}>
            <span className={styles.nowrap}>주문번호 {order.orderNumber}</span>
            {order.orderedAt && (
              <span className={styles.nowrap}>
                {` · ${dayjs(order.orderedAt).format('YYYY-MM-DD HH:mm')}`}
              </span>
            )}
          </Typography.Text>
          {order.confirmedAt && (
            <Typography.Text type="secondary" className={styles.meta}>
              구매 확정일 {dayjs(order.confirmedAt).format('YYYY-MM-DD (ddd)')}
            </Typography.Text>
          )}
        </Flex>
        {listButton}
      </Flex>
      <section className={`${styles.card} ${styles.list}`} aria-label="주문 상품">
        <OrderItemHeader />
        {order.items?.map((item) => {
          // 환불 정보를 못 읽었으면 태그 없이 보인다. 상세 상품에는 주문 상품 번호가 없어 상품 번호로 짝을 짓는다.
          const refundable = refundableItems?.find((other) => other.productId === item.productId);
          const refundState =
            order.status === 'PAID'
              ? refundable && getOrderItemRefundState(refundable)
              : toRefundState(
                  refundedQuantityByName.get(item.productName ?? '') ?? 0,
                  item.quantity ?? 0,
                );
          return (
            <OrderItemRow
              key={item.productId}
              item={item}
              linkToProduct
              refundState={refundState}
            />
          );
        })}
      </section>
      <div className={styles.bottom}>
        <section className={styles.card} aria-label="결제 금액">
          <Typography.Text strong className={styles.cardTitle}>
            결제 금액
          </Typography.Text>
          <Flex vertical gap="small" className={styles.rows}>
            <Flex justify="space-between">
              <Typography.Text type="secondary">상품 금액</Typography.Text>
              <Typography.Text className={styles.amount}>
                {formatPrice(order.listPriceAmount ?? 0)}
              </Typography.Text>
            </Flex>
            <Flex justify="space-between">
              <Typography.Text type="secondary">할인 금액</Typography.Text>
              <Typography.Text className={styles.amount}>
                {order.discountAmount ? `-${formatPrice(order.discountAmount)}` : formatPrice(0)}
              </Typography.Text>
            </Flex>
            <Flex justify="space-between">
              <Typography.Text>결제 금액</Typography.Text>
              <Typography.Text className={styles.amount}>
                {formatPrice(order.payAmount ?? 0)}
              </Typography.Text>
            </Flex>
            {hasVisibleRefund && (
              <>
                <Flex justify="space-between">
                  <Typography.Text type="secondary">환불 금액</Typography.Text>
                  <Typography.Text className={styles.amount}>
                    {refundedAmount ? `-${formatPrice(refundedAmount)}` : formatPrice(0)}
                  </Typography.Text>
                </Flex>
                <Flex justify="flex-end">
                  <Button
                    onClick={() =>
                      navigate(
                        generatePath(ROUTES.mypageOrderRefunds, { orderId: String(orderId) }),
                      )
                    }
                    className={styles.button}
                  >
                    환불 상세 보기
                    <RightOutlined />
                  </Button>
                </Flex>
              </>
            )}
          </Flex>
          <Divider className={styles.divider} />
          <Flex justify="space-between" align="baseline">
            <Typography.Text strong>최종 결제 금액</Typography.Text>
            <Typography.Text strong className={styles.total}>
              {formatPrice((order.payAmount ?? 0) - refundedAmount)}
            </Typography.Text>
          </Flex>
        </section>
        {(order.status === 'PAID' || isConfirmed || isRefunded) && (
          <section className={styles.card} aria-label="구매 확정·환불">
            <Typography.Text strong className={styles.cardTitle}>
              구매 확정·환불
            </Typography.Text>
            <Typography.Paragraph type="secondary" className={styles.notice}>
              구매 확정 후에는 환불할 수 없습니다.
            </Typography.Paragraph>
            <Flex vertical gap="small">
              <Button
                type="primary"
                size="large"
                block
                onClick={handleConfirmClick}
                className={styles.button}
              >
                구매 확정
              </Button>
              {isConfirmed ? (
                <Button
                  size="large"
                  block
                  onClick={() => message.warning('구매 확정된 주문은 환불이 불가합니다.')}
                  className={styles.button}
                >
                  환불 신청
                </Button>
              ) : (
                <Button size="large" block onClick={handleRefundClick} className={styles.button}>
                  환불 신청
                </Button>
              )}
            </Flex>
          </section>
        )}
      </div>
      <Modal
        open={confirmAsked}
        title="구매 확정할까요?"
        okText="구매 확정"
        cancelText="닫기"
        confirmLoading={confirmMutation.isPending}
        onOk={() => confirmMutation.mutate()}
        onCancel={() => setConfirmAsked(false)}
      >
        구매 확정 후에는 환불할 수 없습니다.
      </Modal>
    </div>
  );
}

// 주문 상품의 환불 수량으로 환불 태그를 정한다.
function toRefundState(refundedQuantity: number, orderedQuantity: number): OrderItemRefundState {
  if (refundedQuantity === 0) return 'none';
  return refundedQuantity < orderedQuantity ? 'partial' : 'full';
}
