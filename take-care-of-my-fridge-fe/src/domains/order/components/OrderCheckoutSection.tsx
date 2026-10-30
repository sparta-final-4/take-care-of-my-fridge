import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Divider, Flex, Modal, Result, Skeleton, Tag, Typography } from 'antd';
import { useNavigate } from 'react-router';

import { ROUTES } from '@/app/paths';
import { getErrorCode, getErrorMessage } from '@/common/api/errors';
import { useAuthStore } from '@/common/auth/authStore';
import { formatPrice } from '@/domains/cart/format';
import { cartQueries } from '@/domains/cart/queries';
import { useCancelOnLeave } from '@/domains/order/hooks/useCancelOnLeave';
import { orderQueries } from '@/domains/order/queries';
import { confirmPayment, preparePayment } from '@/domains/payment/api';
import { openPaymentWindow, type PaymentCustomer } from '@/domains/payment/portone';

import { OrderItemRow } from './OrderItemRow';
import styles from './OrderCheckoutSection.module.scss';

interface OrderCheckoutSectionProps {
  orderId: number;
}

// 결제 대기 주문만 그린다. 결제 완료·취소·없는 주문은 서버가 오류로 알려 주고 안내로 바꾼다.
export function OrderCheckoutSection({ orderId }: OrderCheckoutSectionProps) {
  const navigate = useNavigate();
  const validOrderId = Number.isSafeInteger(orderId) && orderId > 0;
  const {
    data: order,
    isPending,
    isError,
    isSuccess,
    error,
    refetch,
  } = useQuery({ ...orderQueries.checkout(orderId), enabled: validOrderId });
  // 결제 대기 주문을 보여 주는 동안만 이동을 붙잡는다.
  // 다시 조회하다 실패하면 data는 이전 값이 남으므로 data가 아니라 성공 상태로 판단한다(취소된 주문 안내의 버튼이 막히지 않게).
  const { isLeaving, isConfirmOpen, isCancelling, askCancel, confirm, close, allowLeave } =
    useCancelOnLeave(orderId, isSuccess);
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const member = useAuthStore((state) => state.member);

  // 결제 준비 후 PortOne 결제창을 띄우고, 결제창이 성공으로 끝나면 서버가 PG 결제를 확인한 뒤에만 성공으로 본다.
  // 결제창 실패·취소는 결제창 응답 그대로 돌려준다. 서버가 거절하면(만료·금액 불일치·결제할 수 없는 주문 등)
  // 오류로 끝나 서버 메시지를 띄운다. 지워진 주문(ORDER_001)만 따로 처리한다.
  const payMutation = useMutation({
    mutationFn: async (customer: PaymentCustomer) => {
      const payment = await preparePayment(orderId);
      const response = await openPaymentWindow(payment, customer);
      if (!response || response.code) return response;
      await confirmPayment(orderId, payment.portonePaymentId);
      return response;
    },
    meta: { skipGlobalError: true },
  });
  // 결제사에 보낼 연락처는 회원 가입 때 받은 휴대폰 번호를 쓴다.
  const handlePay = () => {
    if (!member?.phone) {
      message.warning('회원 정보에 휴대폰 번호가 없어 결제할 수 없습니다.');
      return;
    }
    payMutation.mutate(
      { fullName: member.nickname ?? '', phoneNumber: member.phone, email: member.email ?? '' },
      {
        onSuccess: (response) => {
          if (!response) return;
          if (response.code) {
            message.warning(response.message || '결제를 완료하지 못했습니다.');
            return;
          }
          message.success('결제 성공!');
          // 서버가 결제한 상품을 장바구니에서 지웠으므로 헤더 개수와 장바구니를 다시 불러온다
          void queryClient.invalidateQueries({ queryKey: cartQueries.all });
          // 결제를 마치고 떠나는 이동은 붙잡지 않는다. 붙잡으면 나가기 확인에서 방금 결제한 주문이 취소된다.
          allowLeave();
          navigate(ROUTES.home, { replace: true });
        },
        // 생성 5분이 지나 서버가 지운 주문이면 남은 주문이 없으므로 붙잡지 않고 장바구니로 보낸다
        onError: (error) => {
          if (getErrorCode(error) !== 'ORDER_001') {
            message.error(getErrorMessage(error));
            return;
          }
          message.info('주문 시간이 지났습니다. 다시 주문해 주세요.');
          allowLeave();
          navigate(ROUTES.cart);
        },
      },
    );
  };

  const goToCart = () => navigate(ROUTES.cart);
  const cartButton = (
    <Button type="primary" onClick={goToCart}>
      장바구니로 가기
    </Button>
  );
  const notFound = <Result status="404" title="주문을 찾을 수 없습니다." extra={cartButton} />;

  if (!validOrderId) return notFound;
  if (isPending) return <Skeleton active paragraph={{ rows: 6 }} />;
  if (isError) {
    const code = getErrorCode(error);
    if (code === 'ORDER_001') return notFound;
    if (code === 'ORDER_007') {
      return (
        <Result
          status="info"
          title="이미 결제가 완료된 주문입니다."
          subTitle="주문 내역에서 확인해 주세요."
          extra={
            <Button type="primary" onClick={() => navigate(ROUTES.mypageOrders)}>
              주문 내역 보기
            </Button>
          }
        />
      );
    }
    if (code === 'ORDER_008') {
      return (
        <Result
          status="warning"
          title="취소된 주문입니다."
          subTitle="장바구니에서 다시 주문해 주세요."
          extra={cartButton}
        />
      );
    }
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
      <Flex align="center" justify="space-between" wrap gap="small" className={styles.header}>
        <Flex align="center" gap="small">
          <Typography.Title level={2} className={styles.title}>
            주문 확인
          </Typography.Title>
          <Tag>결제 대기</Tag>
        </Flex>
        <Typography.Text>
          주문번호 <Typography.Text strong>{order.orderNumber}</Typography.Text>
        </Typography.Text>
      </Flex>
      <div className={styles.layout}>
        <section className={styles.list} aria-label="주문 상품">
          {order.items?.map((item) => (
            <OrderItemRow key={item.productId} item={item} />
          ))}
        </section>
        <section className={styles.summary} aria-label="결제할 금액">
          <Typography.Text strong className={styles.summaryTitle}>
            결제할 금액
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
            <Typography.Text strong>결제할 금액</Typography.Text>
            <Typography.Text strong className={styles.total}>
              {formatPrice(order.payAmount ?? 0)}
            </Typography.Text>
          </Flex>
          <Button
            type="primary"
            size="large"
            block
            loading={payMutation.isPending}
            onClick={handlePay}
            className={styles.pay}
          >
            결제하기
          </Button>
          <Button size="large" block onClick={askCancel} className={styles.cancel}>
            취소하기
          </Button>
        </section>
      </div>
      <Modal
        open={isConfirmOpen}
        title={isLeaving ? '나가면 주문이 취소됩니다.' : '주문을 취소할까요?'}
        okText={isLeaving ? '나가기' : '주문 취소'}
        cancelText="머무르기"
        okButtonProps={{ danger: true }}
        confirmLoading={isCancelling}
        onOk={confirm}
        onCancel={close}
      >
        장바구니는 그대로 남아 있어 다시 주문할 수 있습니다.
      </Modal>
    </div>
  );
}
