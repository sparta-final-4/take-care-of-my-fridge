import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Divider, Flex, Modal, Radio, Result, Skeleton, Tag, Typography } from 'antd';
import { useState } from 'react';
import { generatePath, useNavigate } from 'react-router';

import { ROUTES } from '@/app/paths';
import { getErrorCode, getErrorMessage } from '@/common/api/errors';
import { useAuthStore } from '@/common/auth/authStore';
import { formatPrice } from '@/domains/cart/format';
import { cartQueries } from '@/domains/cart/queries';
import { useCancelOnLeave } from '@/domains/order/hooks/useCancelOnLeave';
import { orderQueries } from '@/domains/order/queries';
import { confirmPayment, payWithCard, preparePayment } from '@/domains/payment/api';
import { cardName, maskedCardNumber } from '@/domains/payment/cardFormat';
import { CardPicker } from '@/domains/payment/components/CardPicker';
import { useCardRegistration } from '@/domains/payment/hooks/useCardRegistration';
import { PG_PROVIDER_OPTIONS, type PgProvider } from '@/domains/payment/pgProvider';
import { openPaymentWindow, type PaymentCustomer } from '@/domains/payment/portone';
import { billingKeyQueries } from '@/domains/payment/queries';

import { OrderItemRow } from './OrderItemRow';
import styles from './OrderCheckoutSection.module.scss';

interface OrderCheckoutSectionProps {
  orderId: number;
}

// 등록 카드(결제창 없이 결제) 또는 결제대행사(결제창)
type PayMethod = { kind: 'card'; cardId: number } | { kind: 'pg'; pgProvider: PgProvider };

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
  const cardsQuery = useQuery(billingKeyQueries.list());
  const cards = cardsQuery.data ?? [];
  // 고른 결제 수단. 아직 고르지 않았으면 대표 카드가 선택돼 있다. 고른 카드가 삭제됐으면 다시 대표 카드로 돌아간다
  const [chosenMethod, setChosenMethod] = useState<PayMethod>();
  const defaultCard = cards.find((card) => card.isDefault) ?? cards[0];
  const chosenCardExists =
    chosenMethod?.kind !== 'card' || cards.some((card) => card.cardId === chosenMethod.cardId);
  const method: PayMethod | undefined =
    chosenMethod && chosenCardExists
      ? chosenMethod
      : defaultCard?.cardId !== undefined
        ? { kind: 'card', cardId: defaultCard.cardId }
        : undefined;
  const selectedCard =
    method?.kind === 'card' ? cards.find((card) => card.cardId === method.cardId) : undefined;
  const [cardConfirmOpen, setCardConfirmOpen] = useState(false);
  // 카드 등록이 끝나면 화면에 머물고 새 카드를 고른다. 모바일은 이 화면으로 돌아와 등록을 마무리한다
  const { register, registering } = useCardRegistration(
    generatePath(ROUTES.orderCheckout, { orderId: String(orderId) }),
    (card) => card.cardId !== undefined && setChosenMethod({ kind: 'card', cardId: card.cardId }),
  );

  // 결제 준비 후 PortOne 결제창을 띄우고, 결제창이 성공으로 끝나면 서버가 PG 결제를 확인한 뒤에만 성공으로 본다.
  // 결제창 실패·취소는 결제창 응답 그대로 돌려준다. 서버가 거절하면(만료·금액 불일치·결제할 수 없는 주문 등)
  // 오류로 끝나 서버 메시지를 띄운다. 지워진 주문(ORDER_001)만 따로 처리한다.
  const payMutation = useMutation({
    mutationFn: async ({
      provider,
      customer,
    }: {
      provider: PgProvider;
      customer: PaymentCustomer;
    }) => {
      const payment = await preparePayment(orderId, provider);
      const response = await openPaymentWindow(payment, customer);
      if (!response || response.code) return response;
      await confirmPayment(orderId, payment.portonePaymentId);
      return response;
    },
    meta: { skipGlobalError: true },
  });
  // 등록 카드 결제는 결제창이 없고 서버가 PG에 청구한 뒤 결제 확정과 같은 규칙으로 승인한다
  const cardPayMutation = useMutation({
    mutationFn: (cardId: number) => payWithCard(orderId, cardId),
    meta: { skipGlobalError: true },
    // 다른 탭에서 삭제한 카드처럼 쓸 수 없는 카드가 선택된 채 남지 않게 카드 목록을 다시 불러온다
    onError: () => queryClient.invalidateQueries({ queryKey: billingKeyQueries.all }),
  });
  const paying = payMutation.isPending || cardPayMutation.isPending;

  const finishPaid = () => {
    message.success('결제 성공!');
    // 서버가 결제한 상품을 장바구니에서 지웠으므로 헤더 개수와 장바구니를 다시 불러온다
    void queryClient.invalidateQueries({ queryKey: cartQueries.all });
    // 결제를 마치고 떠나는 이동은 붙잡지 않는다. 붙잡으면 나가기 확인에서 방금 결제한 주문이 취소된다.
    allowLeave();
    navigate(ROUTES.home, { replace: true });
  };
  // 생성 5분이 지나 서버가 지운 주문이면 남은 주문이 없으므로 붙잡지 않고 장바구니로 보낸다
  const handlePayError = (error: unknown) => {
    const code = getErrorCode(error);
    if (code === 'ORDER_001') {
      message.info('주문 시간이 지났습니다. 다시 주문해 주세요.');
      allowLeave();
      navigate(ROUTES.cart);
      return;
    }
    // 카드 결제 결과를 아직 모르면 결제 대기로 남아 서버가 마무리한다. 같은 주문을 다시 결제하지 않게 주문 내역으로 보낸다
    if (code === 'PAYMENT_011') {
      message.info(getErrorMessage(error));
      allowLeave();
      navigate(ROUTES.mypageOrders, { replace: true });
      return;
    }
    message.error(getErrorMessage(error));
  };

  const handlePayWithCard = () => {
    if (method?.kind !== 'card') return;
    cardPayMutation.mutate(method.cardId, {
      onSuccess: finishPaid,
      onError: handlePayError,
      onSettled: () => setCardConfirmOpen(false),
    });
  };

  // 결제사에 보낼 연락처는 회원 가입 때 받은 휴대폰 번호를 쓴다.
  const handlePay = () => {
    if (!method) return;
    // 등록 카드는 결제창이 없어 결제 전에 한 번 확인한다
    if (method.kind === 'card') {
      setCardConfirmOpen(true);
      return;
    }
    if (!member?.phone) {
      message.warning('회원 정보에 휴대폰 번호가 없어 결제할 수 없습니다.');
      return;
    }
    payMutation.mutate(
      {
        provider: method.pgProvider,
        customer: {
          fullName: member.nickname ?? '',
          phoneNumber: member.phone,
          email: member.email ?? '',
        },
      },
      {
        onSuccess: (response) => {
          if (!response) return;
          if (response.code) {
            message.warning(response.message || '결제를 완료하지 못했습니다.');
            return;
          }
          finishPaid();
        },
        onError: handlePayError,
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
          <Typography.Text strong id="payment-method-title" className={styles.methodTitle}>
            결제 수단
          </Typography.Text>
          <Typography.Text type="secondary" className={styles.methodGroupTitle}>
            등록 카드
          </Typography.Text>
          {cardsQuery.isPending ? (
            <Skeleton.Button active block className={styles.cardsStatus} />
          ) : cardsQuery.isError ? (
            <Flex align="center" gap="small" className={styles.cardsStatus}>
              <Typography.Text type="secondary">등록 카드를 불러오지 못했어요.</Typography.Text>
              <Button size="small" onClick={() => cardsQuery.refetch()}>
                다시 시도
              </Button>
            </Flex>
          ) : (
            <CardPicker
              cards={cards}
              selectedCardId={method?.kind === 'card' ? method.cardId : undefined}
              disabled={paying}
              registering={registering}
              onSelect={(cardId) => setChosenMethod({ kind: 'card', cardId })}
              onRegister={register}
            />
          )}
          <Typography.Text type="secondary" className={styles.methodGroupTitle}>
            다른 결제 수단
          </Typography.Text>
          <Radio.Group
            aria-labelledby="payment-method-title"
            value={method?.kind === 'pg' ? method.pgProvider : undefined}
            onChange={(event) =>
              setChosenMethod({ kind: 'pg', pgProvider: event.target.value as PgProvider })
            }
            disabled={paying}
            className={styles.methods}
          >
            {PG_PROVIDER_OPTIONS.map((option) => (
              <Radio.Button key={option.value} value={option.value} className={styles.method}>
                {option.label}
              </Radio.Button>
            ))}
          </Radio.Group>
          <Button
            type="primary"
            size="large"
            block
            disabled={!method}
            loading={paying}
            onClick={handlePay}
            className={styles.pay}
          >
            결제하기
          </Button>
          <Button
            size="large"
            block
            onClick={askCancel}
            disabled={paying}
            className={styles.cancel}
          >
            취소하기
          </Button>
        </section>
      </div>
      <Modal
        open={cardConfirmOpen}
        title="등록 카드로 결제할까요?"
        okText="결제"
        cancelText="닫기"
        confirmLoading={cardPayMutation.isPending}
        onOk={handlePayWithCard}
        onCancel={() => setCardConfirmOpen(false)}
      >
        {selectedCard
          ? `${cardName(selectedCard)} ${maskedCardNumber(selectedCard)}로 ${formatPrice(order.payAmount ?? 0)}을 결제해요.`
          : '고른 카드로 결제해요.'}
      </Modal>
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
