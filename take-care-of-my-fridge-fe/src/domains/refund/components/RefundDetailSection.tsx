import { useQuery } from '@tanstack/react-query';
import { Button, Divider, Empty, Flex, Result, Skeleton, Tag, Typography } from 'antd';
import dayjs from 'dayjs';
import { generatePath, useNavigate } from 'react-router';

import { ROUTES } from '@/app/paths';
import { getErrorCode } from '@/common/api/errors';
import { formatPrice } from '@/domains/cart/format';
import { OrderItemHeader, OrderItemRow } from '@/domains/order/components/OrderItemRow';
import { orderQueries } from '@/domains/order/queries';
import {
  getOverallRefundStatus,
  groupRefundedItems,
  REFUND_REASON_LABELS,
  REFUND_STATUS_LABELS,
  sumRefundAmountByStatus,
} from '@/domains/refund/format';
import { refundQueries } from '@/domains/refund/queries';

import styles from './RefundDetailSection.module.scss';

interface RefundDetailSectionProps {
  orderId: number;
}

// 한 주문의 환불을 모아 보여 준다. 주문번호·결제 금액은 주문 상세에서, 환불 상품·내역은 환불 내역에서 가져온다.
// 환불 금액은 완료된 환불만 더한다. 진행 중인 환불은 따로 보여 준다.
// 실패한 환불은 돈이 움직이지 않았으므로 보여 주지 않는다. 실패만 있으면 환불 내역이 없는 것으로 본다.
export function RefundDetailSection({ orderId }: RefundDetailSectionProps) {
  const navigate = useNavigate();
  const validOrderId = Number.isSafeInteger(orderId) && orderId > 0;
  const orderQuery = useQuery({ ...orderQueries.detail(orderId), enabled: validOrderId });
  const historyQuery = useQuery({ ...refundQueries.history(orderId), enabled: validOrderId });

  const detailPath = generatePath(ROUTES.mypageOrderDetail, { orderId: String(orderId) });
  const backButton = (
    <Button className={styles.button} onClick={() => navigate(detailPath)}>
      주문 상세로
    </Button>
  );
  const notFound = (
    <Result
      status="404"
      title="주문을 찾을 수 없습니다."
      extra={<Button onClick={() => navigate(ROUTES.mypageOrders)}>주문 내역으로</Button>}
    />
  );

  if (!validOrderId) return notFound;
  if (orderQuery.isPending || historyQuery.isPending) {
    return <Skeleton active paragraph={{ rows: 6 }} />;
  }
  if (orderQuery.isError || historyQuery.isError) {
    if (getErrorCode(orderQuery.error) === 'ORDER_001') return notFound;
    return (
      <Result
        status="error"
        title="환불 내역을 불러오지 못했습니다."
        extra={
          <Button onClick={() => Promise.all([orderQuery.refetch(), historyQuery.refetch()])}>
            다시 시도
          </Button>
        }
      />
    );
  }

  const order = orderQuery.data;
  const history = historyQuery.data.filter((refund) => refund.status !== 'FAILED');
  const overallStatus = getOverallRefundStatus(history);
  const refundedItems = groupRefundedItems(history);
  const payAmount = order.payAmount ?? 0;
  const completedAmount = sumRefundAmountByStatus(history, 'COMPLETED');
  const requestedAmount = sumRefundAmountByStatus(history, 'REQUESTED');

  return (
    <div className={styles.page}>
      <Flex align="center" justify="space-between" wrap gap="middle" className={styles.card}>
        <Flex vertical gap="small" className={styles.heading}>
          <Flex align="center" gap="small">
            <Typography.Title level={3} className={styles.title}>
              환불 상세
            </Typography.Title>
            {overallStatus && (
              <Tag
                color={overallStatus === 'REQUESTED' ? 'processing' : undefined}
                className={styles.tag}
              >
                {REFUND_STATUS_LABELS[overallStatus]}
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
        </Flex>
        {backButton}
      </Flex>
      {history.length === 0 ? (
        <Empty description="환불 내역이 없습니다." className={styles.card} />
      ) : (
        <>
          {refundedItems.length > 0 && (
            <section className={`${styles.card} ${styles.list}`} aria-label="환불 상품">
              <OrderItemHeader />
              {refundedItems.map((item) => (
                <OrderItemRow
                  key={item.orderItemId}
                  item={{
                    productName: item.productName,
                    productImageUrl: item.productImageUrl,
                    quantity: item.quantity,
                    unitPrice: item.unitPrice,
                    lineAmount: item.amount,
                    // 특가·번개 드롭은 환불할 수 없어 환불 상품은 늘 일반 상품이다. 환불 내역 응답에 판매 유형이 없어 고정한다
                    saleType: 'NONE',
                  }}
                />
              ))}
            </section>
          )}
          <div className={styles.bottom}>
            <section className={styles.card} aria-label="결제·환불 금액">
              <Typography.Text strong className={styles.cardTitle}>
                결제·환불 금액
              </Typography.Text>
              <Flex vertical gap="small" className={styles.rows}>
                <AmountRow label="상품 금액" amount={formatPrice(order.listPriceAmount ?? 0)} />
                <AmountRow
                  label="할인 금액"
                  amount={
                    order.discountAmount ? `-${formatPrice(order.discountAmount)}` : formatPrice(0)
                  }
                />
                <AmountRow label="결제 금액" amount={formatPrice(payAmount)} />
                <AmountRow
                  label="환불 금액"
                  amount={completedAmount ? `-${formatPrice(completedAmount)}` : formatPrice(0)}
                />
                {requestedAmount > 0 && (
                  <AmountRow label="환불 진행 중 금액" amount={formatPrice(requestedAmount)} />
                )}
              </Flex>
              <Divider className={styles.divider} />
              <Flex justify="space-between" align="baseline">
                <Typography.Text strong>최종 결제 금액</Typography.Text>
                <Typography.Text strong className={styles.total}>
                  {formatPrice(Math.max(0, payAmount - completedAmount))}
                </Typography.Text>
              </Flex>
              <Typography.Paragraph type="secondary" className={styles.notice}>
                환불 금액은 결제한 수단으로 돌려드려요.
              </Typography.Paragraph>
            </section>
            <section className={styles.card} aria-label="환불 내역">
              <Typography.Text strong className={styles.cardTitle}>
                환불 내역
              </Typography.Text>
              <Flex vertical gap="small" className={styles.rows}>
                {history.flatMap((refund) =>
                  (refund.items ?? []).map((item) => {
                    const name = item.productName ?? '';
                    const at = refund.refundedAt ?? refund.requestedAt;
                    return (
                      <div
                        key={`${refund.refundId}-${item.orderItemId}`}
                        className={styles.history}
                      >
                        <div className={styles.thumb}>
                          {item.productImageUrl ? (
                            <img src={item.productImageUrl} alt={name} className={styles.image} />
                          ) : (
                            <span className={styles.imagePlaceholder} aria-hidden="true" />
                          )}
                        </div>
                        <Flex vertical className={styles.historyInfo}>
                          <Typography.Text strong title={name} className={styles.name}>
                            {name} × {item.quantity ?? 0}
                          </Typography.Text>
                          {at && (
                            <Typography.Text type="secondary" className={styles.meta}>
                              {dayjs(at).format('YYYY-MM-DD HH:mm')}
                            </Typography.Text>
                          )}
                          {refund.reasonType && (
                            <Typography.Text type="secondary">
                              사유: {REFUND_REASON_LABELS[refund.reasonType]}
                            </Typography.Text>
                          )}
                        </Flex>
                        <Flex vertical align="flex-end" gap="small" className={styles.historyAside}>
                          <Typography.Text strong className={styles.amount}>
                            {formatPrice(item.amount ?? 0)}
                          </Typography.Text>
                          {refund.status && (
                            <Tag
                              color={refund.status === 'FAILED' ? 'error' : undefined}
                              className={styles.tag}
                            >
                              {REFUND_STATUS_LABELS[refund.status]}
                            </Tag>
                          )}
                        </Flex>
                      </div>
                    );
                  }),
                )}
              </Flex>
            </section>
          </div>
        </>
      )}
    </div>
  );
}

interface AmountRowProps {
  label: string;
  amount: string;
}

function AmountRow({ label, amount }: AmountRowProps) {
  return (
    <Flex justify="space-between" gap="middle">
      <Typography.Text type="secondary">{label}</Typography.Text>
      <Typography.Text className={styles.amount}>{amount}</Typography.Text>
    </Flex>
  );
}
