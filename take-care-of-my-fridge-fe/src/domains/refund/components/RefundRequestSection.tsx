import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App,
  Breadcrumb,
  Button,
  Checkbox,
  Divider,
  Flex,
  Form,
  Input,
  Modal,
  Radio,
  Result,
  Skeleton,
  Tag,
  Typography,
} from 'antd';
import dayjs from 'dayjs';
import { useState } from 'react';
import { generatePath, Link, useNavigate } from 'react-router';

import { ROUTES } from '@/app/paths';
import type { RefundableItemResponse, RefundCreateRequest } from '@/common/api/clients';
import { getErrorCode } from '@/common/api/errors';
import { formatPrice } from '@/domains/cart/format';
import { orderQueries } from '@/domains/order/queries';
import { requestRefund } from '@/domains/refund/api';
import {
  getRefundRowState,
  REFUND_REASON_OPTIONS,
  sumRefundedAmount,
} from '@/domains/refund/format';
import { refundQueries } from '@/domains/refund/queries';

import { RefundItemHeader, RefundItemRow } from './RefundItemRow';
import styles from './RefundRequestSection.module.scss';

interface RefundRequestSectionProps {
  orderId: number;
}

interface RefundReasonValues {
  reasonType: RefundCreateRequest['reasonType'];
  reasonDetail?: string;
}

const REASON_DETAIL_MAX_LENGTH = 500;

// 주문 상세(주문번호·결제 금액·정가)와 환불 가능 수량을 함께 불러 와 상품별로 고르게 한다.
// 두 응답은 productId로 맞춘다. 한 주문 안에서 같은 상품은 한 줄이다.
// 환불 가능 여부는 서버가 다시 확인하므로, 화면은 고를 수 없는 상품을 막기만 한다.
export function RefundRequestSection({ orderId }: RefundRequestSectionProps) {
  const navigate = useNavigate();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<RefundReasonValues>();
  // 주문 상품 ID → 환불 수량. 고르지 않은 상품은 키가 없다.
  const [quantities, setQuantities] = useState<Record<number, number>>({});
  const [confirmAsked, setConfirmAsked] = useState(false);

  const validOrderId = Number.isSafeInteger(orderId) && orderId > 0;
  const orderQuery = useQuery({ ...orderQueries.detail(orderId), enabled: validOrderId });
  const isPaid = orderQuery.data?.status === 'PAID';
  const itemsQuery = useQuery({
    ...refundQueries.refundableItems(orderId),
    enabled: validOrderId && isPaid,
  });
  // 실패(수량 초과 등)는 전역 알림이 서버 메시지로 알린다.
  // 성공·실패 모두 환불 가능 수량과 주문을 다시 불러 화면을 서버와 맞춘다.
  const refundMutation = useMutation({
    mutationFn: requestRefund,
    onSettled: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: refundQueries.all }),
        queryClient.invalidateQueries({ queryKey: orderQueries.all }),
      ]),
  });

  const orderIdParam = { orderId: String(orderId) };
  const detailPath = generatePath(ROUTES.mypageOrderDetail, orderIdParam);
  const backButton = (
    <Button size="large" block onClick={() => navigate(detailPath)} className={styles.button}>
      주문 상세로 돌아가기
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
  if (orderQuery.isPending) return <Skeleton active paragraph={{ rows: 6 }} />;
  if (orderQuery.isError) {
    if (getErrorCode(orderQuery.error) === 'ORDER_001') return notFound;
    return (
      <Result
        status="error"
        title="주문을 불러오지 못했습니다."
        extra={<Button onClick={() => orderQuery.refetch()}>다시 시도</Button>}
      />
    );
  }
  const order = orderQuery.data;
  if (!isPaid) {
    return (
      <Result
        status="warning"
        title="환불할 수 없는 주문입니다."
        subTitle={
          order.status === 'CONFIRMED'
            ? '구매 확정된 주문은 환불할 수 없습니다.'
            : '결제 완료된 주문만 환불할 수 있습니다.'
        }
        extra={<Button onClick={() => navigate(detailPath)}>주문 상세로 돌아가기</Button>}
      />
    );
  }
  if (itemsQuery.isPending) return <Skeleton active paragraph={{ rows: 6 }} />;
  if (itemsQuery.isError) {
    return (
      <Result
        status="error"
        title="환불할 수 있는 상품을 불러오지 못했습니다."
        extra={<Button onClick={() => itemsQuery.refetch()}>다시 시도</Button>}
      />
    );
  }

  const items = itemsQuery.data.filter((item) => item.orderItemId !== undefined);
  const listPrices = new Map(order.items?.map((item) => [item.productId, item.listPrice]));
  const refundableItems = items.filter((item) => getRefundRowState(item) === 'refundable');
  const blockedCount = items.filter((item) => getRefundRowState(item) === 'saleBlocked').length;
  const refundedCount = items.length - refundableItems.length - blockedCount;
  // 신청이 거절된 뒤 다시 읽은 남은 수량보다 많이 골라 둔 수량은 남은 수량으로 줄이고, 남은 수량이 없으면 선택에서 뺀다.
  const selectedQuantityOf = (item: RefundableItemResponse) => {
    const selected = quantities[item.orderItemId ?? 0];
    if (selected === undefined) return undefined;
    const quantity = Math.min(selected, item.refundableQuantity ?? 0);
    return quantity > 0 ? quantity : undefined;
  };
  const selectedItems = refundableItems.flatMap((item) => {
    const quantity = selectedQuantityOf(item);
    return quantity === undefined ? [] : [{ item, quantity }];
  });
  const allSelected =
    refundableItems.length > 0 &&
    refundableItems.every((item) => selectedQuantityOf(item) !== undefined);

  const payAmount = order.payAmount ?? 0;
  const refundedAmount = sumRefundedAmount(items);
  const refundAmount = selectedItems.reduce(
    (sum, { item, quantity }) => sum + (item.unitPrice ?? 0) * quantity,
    0,
  );
  const remainingAmount = Math.max(0, payAmount - refundedAmount - refundAmount);

  const handleQuantityChange = (orderItemId: number, quantity: number | undefined) =>
    setQuantities((current) => {
      const next = { ...current };
      if (quantity === undefined) delete next[orderItemId];
      else next[orderItemId] = quantity;
      return next;
    });
  // 전체 선택은 남은 수량을 모두 환불하도록 채운다.
  const handleSelectAll = (checked: boolean) =>
    setQuantities(
      checked
        ? Object.fromEntries(
            refundableItems.map((item) => [item.orderItemId ?? 0, item.refundableQuantity ?? 0]),
          )
        : {},
    );
  const handleFinish = () => {
    if (selectedItems.length === 0) {
      message.warning('환불할 상품을 선택해 주세요.');
      return;
    }
    setConfirmAsked(true);
  };
  const handleConfirm = () => {
    const { reasonType, reasonDetail } = form.getFieldsValue();
    refundMutation.mutate(
      {
        orderId,
        items: selectedItems.map(({ item, quantity }) => ({
          orderItemId: item.orderItemId ?? 0,
          quantity,
        })),
        reasonType,
        reasonDetail: reasonDetail?.trim() || undefined,
      },
      {
        onSuccess: (result) => {
          if (result.status === 'COMPLETED') {
            message.success('환불이 완료되었습니다.');
          } else {
            // 결제사 처리 결과를 아직 확정하지 못했다. 서버가 결제사와 맞춰 마무리하므로 접수만 알린다.
            message.info('환불을 접수했습니다. 결제사 처리가 끝나면 환불 내역에 반영됩니다.');
          }
          navigate(generatePath(ROUTES.mypageOrderRefunds, orderIdParam));
        },
        onError: () => setConfirmAsked(false),
      },
    );
  };

  return (
    <div className={styles.page}>
      <Breadcrumb
        items={[
          { title: <Link to={ROUTES.mypageOrders}>주문 내역</Link> },
          { title: <Link to={detailPath}>주문 상세</Link> },
          { title: '환불 신청' },
        ]}
      />
      <Form
        form={form}
        layout="vertical"
        requiredMark={false}
        onFinish={handleFinish}
        className={styles.page}
      >
        <section className={styles.card} aria-label="환불 상품">
          <Flex align="center" justify="space-between" wrap gap="small">
            <Typography.Title level={3} className={styles.title}>
              환불 신청
            </Typography.Title>
            <Typography.Text type="secondary" className={styles.meta}>
              <span className={styles.nowrap}>주문번호 {order.orderNumber}</span>
              {order.orderedAt && (
                <span className={styles.nowrap}>
                  {` · ${dayjs(order.orderedAt).format('YYYY-MM-DD HH:mm')}`}
                </span>
              )}
            </Typography.Text>
          </Flex>
          <Alert
            type="warning"
            showIcon
            title="번개 드롭·특가 상품과 구매 확정된 주문은 환불할 수 없어요."
            className={styles.alert}
          />
          <div className={styles.list}>
            <RefundItemHeader />
            {items.map((item) => (
              <RefundItemRow
                key={item.orderItemId}
                item={item}
                listPrice={listPrices.get(item.productId)}
                quantity={selectedQuantityOf(item)}
                onChange={(quantity) => handleQuantityChange(item.orderItemId ?? 0, quantity)}
              />
            ))}
          </div>
          <Flex
            align="center"
            justify="space-between"
            wrap
            gap="small"
            className={styles.listFooter}
          >
            <Checkbox
              checked={allSelected}
              indeterminate={!allSelected && selectedItems.length > 0}
              disabled={refundableItems.length === 0}
              onChange={(event) => handleSelectAll(event.target.checked)}
            >
              환불 가능한 상품 전체 선택
            </Checkbox>
            <Typography.Text type="secondary">
              환불 가능 {refundableItems.length}건 · 환불 불가 {blockedCount}건 · 환불 완료{' '}
              {refundedCount}건
            </Typography.Text>
          </Flex>
        </section>
        <div className={styles.bottom}>
          <section className={styles.card} aria-label="환불 사유">
            <Typography.Text strong className={styles.cardTitle}>
              환불 사유
            </Typography.Text>
            <Form.Item
              name="reasonType"
              rules={[{ required: true, message: '환불 사유를 선택해 주세요.' }]}
              className={styles.reasonItem}
            >
              <Radio.Group className={styles.reasons}>
                {REFUND_REASON_OPTIONS.map((option) => (
                  <Radio key={option.value} value={option.value} className={styles.reason}>
                    {option.label}
                  </Radio>
                ))}
              </Radio.Group>
            </Form.Item>
            <Form.Item name="reasonDetail" label="상세 사유 (선택)">
              <Input.TextArea
                rows={3}
                maxLength={REASON_DETAIL_MAX_LENGTH}
                showCount
                placeholder="환불 사유를 자세히 적어 주세요"
              />
            </Form.Item>
          </section>
          <Flex vertical gap="large">
            <section className={styles.card} aria-label="환불 금액">
              <Typography.Text strong className={styles.cardTitle}>
                환불 금액
              </Typography.Text>
              <Flex vertical gap="small" className={styles.rows}>
                <AmountRow label="상품 금액" amount={formatPrice(order.listPriceAmount ?? 0)} />
                <AmountRow
                  label="할인 금액"
                  amount={
                    order.discountAmount ? `-${formatPrice(order.discountAmount)}` : formatPrice(0)
                  }
                />
                <AmountRow label="결제 금액" amount={formatPrice(payAmount)} strong />
                <AmountRow
                  label="이미 환불된 금액"
                  amount={refundedAmount ? `-${formatPrice(refundedAmount)}` : formatPrice(0)}
                />
              </Flex>
              <Divider dashed className={styles.divider} />
              <Typography.Text type="secondary">
                이번 환불 · 선택 {selectedItems.length}건
              </Typography.Text>
              <Flex vertical gap="small" className={styles.rows}>
                {selectedItems.map(({ item, quantity }) => (
                  <AmountRow
                    key={item.orderItemId}
                    label={`${item.productName ?? ''} × ${quantity}`}
                    amount={formatPrice((item.unitPrice ?? 0) * quantity)}
                  />
                ))}
              </Flex>
              <Flex justify="space-between" align="baseline" className={styles.rows}>
                <Typography.Text strong>환불 예정 금액</Typography.Text>
                <Typography.Text strong className={styles.total}>
                  {formatPrice(refundAmount)}
                </Typography.Text>
              </Flex>
              <Divider dashed className={styles.divider} />
              <Flex vertical gap="small">
                <AmountRow label="환불 후 남는 결제 금액" amount={formatPrice(remainingAmount)} />
                <Flex justify="space-between" align="center">
                  <Typography.Text type="secondary">환불 후 상태</Typography.Text>
                  <Tag className={styles.tag}>
                    {remainingAmount > 0 ? '부분 환불' : '전체 환불'}
                  </Tag>
                </Flex>
              </Flex>
              <Typography.Paragraph type="secondary" className={styles.notice}>
                {blockedCount > 0 && '남는 금액에는 환불할 수 없는 번개 드롭·특가 상품이 있어요. '}
                환불 금액은 결제한 수단으로 돌려드려요.
              </Typography.Paragraph>
              <Flex vertical gap="small">
                <Button
                  type="primary"
                  size="large"
                  block
                  htmlType="submit"
                  disabled={selectedItems.length === 0}
                  className={styles.button}
                >
                  환불 신청 · {formatPrice(refundAmount)}
                </Button>
                {backButton}
              </Flex>
            </section>
            <section className={styles.card} aria-label="환불 안내">
              <Typography.Text strong className={styles.cardTitle}>
                환불 안내
              </Typography.Text>
              <ul className={styles.guide}>
                <li>결제 완료된 주문만 환불할 수 있어요.</li>
                <li>구매 확정된 주문은 환불할 수 없어요.</li>
                <li>번개 드롭·특가 상품은 환불되지 않아요.</li>
                <li>모두 환불된 상품은 다시 환불할 수 없어요.</li>
              </ul>
            </section>
          </Flex>
        </div>
      </Form>
      <Modal
        open={confirmAsked}
        title="환불을 신청할까요?"
        okText="환불 신청"
        cancelText="닫기"
        confirmLoading={refundMutation.isPending}
        onOk={handleConfirm}
        onCancel={() => setConfirmAsked(false)}
      >
        {formatPrice(refundAmount)}을 결제한 수단으로 돌려드려요. 신청한 환불은 취소할 수 없어요.
      </Modal>
    </div>
  );
}

interface AmountRowProps {
  label: string;
  amount: string;
  strong?: boolean;
}

function AmountRow({ label, amount, strong }: AmountRowProps) {
  return (
    <Flex justify="space-between" gap="middle">
      <Typography.Text type={strong ? undefined : 'secondary'} strong={strong}>
        {label}
      </Typography.Text>
      <Typography.Text strong={strong} className={styles.amount}>
        {amount}
      </Typography.Text>
    </Flex>
  );
}
