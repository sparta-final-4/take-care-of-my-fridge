import { useQuery } from '@tanstack/react-query';
import { Button, Divider, Flex, Result, Skeleton, Tag, Tooltip, Typography } from 'antd';
import dayjs from 'dayjs';
import { useNavigate } from 'react-router';

import { ROUTES } from '@/app/paths';
import { getErrorCode } from '@/common/api/errors';
import { formatPrice } from '@/domains/cart/format';
import { ORDER_STATUS_LABELS } from '@/domains/order/format';
import { orderQueries } from '@/domains/order/queries';

import { OrderItemHeader, OrderItemRow } from './OrderItemRow';
import styles from './OrderDetailSection.module.scss';

interface OrderDetailSectionProps {
  orderId: number;
}

// 주문할 때 저장한 상품·금액을 보여 준다. 환불 내역은 환불 상세 화면이 보여 준다.
// 구매 확정·환불 신청은 아직 API가 없어 결제 완료 주문에 비활성으로만 둔다.
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

  return (
    <div className={styles.page}>
      <Flex align="center" justify="space-between" wrap gap="middle" className={styles.card}>
        <Flex vertical gap="small" className={styles.heading}>
          <Flex align="center" gap="small">
            <Typography.Title level={3} className={styles.title}>
              주문 상세
            </Typography.Title>
            {order.status && (
              <Tag
                color={order.status === 'PAID' ? 'processing' : undefined}
                className={styles.tag}
              >
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
        {order.items?.map((item) => (
          <OrderItemRow key={item.productId} item={item} />
        ))}
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
          </Flex>
          <Divider className={styles.divider} />
          <Flex justify="space-between" align="baseline">
            <Typography.Text strong>결제 금액</Typography.Text>
            <Typography.Text strong className={styles.total}>
              {formatPrice(order.payAmount ?? 0)}
            </Typography.Text>
          </Flex>
        </section>
        {order.status === 'PAID' && (
          <section className={styles.card} aria-label="구매 확정·환불">
            <Typography.Text strong className={styles.cardTitle}>
              구매 확정·환불
            </Typography.Text>
            <Typography.Paragraph type="secondary" className={styles.notice}>
              구매 확정 후에는 환불할 수 없습니다.
            </Typography.Paragraph>
            <Flex vertical gap="small">
              <Tooltip title="구매 확정 기능은 준비 중입니다.">
                <Button type="primary" size="large" block disabled className={styles.button}>
                  구매 확정
                </Button>
              </Tooltip>
              <Tooltip title="환불 신청 기능은 준비 중입니다.">
                <Button size="large" block disabled className={styles.button}>
                  환불 신청
                </Button>
              </Tooltip>
            </Flex>
          </section>
        )}
      </div>
    </div>
  );
}
