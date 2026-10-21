import { useMutation, useQuery } from '@tanstack/react-query';
import {
  App,
  Button,
  Divider,
  Flex,
  Form,
  Input,
  Result,
  Skeleton,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import { useNavigate } from 'react-router';

import { ROUTES } from '@/app/paths';
import { getErrorCode } from '@/common/api/errors';
import { useAuthStore } from '@/common/auth/authStore';
import { formatPrice } from '@/domains/cart/format';
import { orderQueries } from '@/domains/order/queries';
import { preparePayment } from '@/domains/payment/api';
import { openPaymentWindow, type PaymentCustomer } from '@/domains/payment/portone';

import { OrderItemRow } from './OrderItemRow';
import styles from './OrderCheckoutSection.module.scss';

interface OrderCheckoutSectionProps {
  orderId: number;
}

interface PayFormValues {
  phoneNumber: string;
}

// 휴대폰 번호. 하이픈은 있어도 되고 결제사에는 숫자만 보낸다.
const PHONE_PATTERN = /^01\d-?\d{3,4}-?\d{4}$/;

// 결제 대기 주문만 그린다. 결제 완료·취소·없는 주문은 서버가 오류로 알려 주고 안내로 바꾼다.
export function OrderCheckoutSection({ orderId }: OrderCheckoutSectionProps) {
  const navigate = useNavigate();
  const validOrderId = Number.isSafeInteger(orderId) && orderId > 0;
  const {
    data: order,
    isPending,
    isError,
    error,
    refetch,
  } = useQuery({ ...orderQueries.checkout(orderId), enabled: validOrderId });
  const { message } = App.useApp();
  const member = useAuthStore((state) => state.member);

  // 결제 준비 후 PortOne 결제창을 띄운다.
  const payMutation = useMutation({
    mutationFn: async (customer: PaymentCustomer) =>
      openPaymentWindow(await preparePayment(orderId), customer),
  });
  const handlePay = ({ phoneNumber }: PayFormValues) =>
    payMutation.mutate(
      {
        fullName: member?.nickname ?? '',
        phoneNumber: phoneNumber.replaceAll('-', ''),
        email: member?.email ?? '',
      },
      {
        onSuccess: (response) => {
          if (!response) return;
          if (response.code) {
            message.warning(response.message || '결제를 완료하지 못했습니다.');
            return;
          }
          message.success('결제 성공!');
          navigate(ROUTES.home, { replace: true });
        },
      },
    );

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
          <Form<PayFormValues>
            layout="vertical"
            requiredMark={false}
            onFinish={handlePay}
            className={styles.pay}
          >
            <Form.Item
              label="연락처"
              name="phoneNumber"
              extra="결제 확인을 위해 결제사에만 전달됩니다."
              rules={[
                { required: true, message: '연락처를 입력해 주세요.' },
                { pattern: PHONE_PATTERN, message: '휴대폰 번호를 정확히 입력해 주세요.' },
              ]}
            >
              <Input inputMode="tel" autoComplete="tel" placeholder="01012345678" />
            </Form.Item>
            <Button
              type="primary"
              htmlType="submit"
              size="large"
              block
              loading={payMutation.isPending}
            >
              결제하기
            </Button>
          </Form>
          {/* 주문 취소 API가 붙으면 활성화한다. */}
          <Tooltip title="주문 취소 기능은 준비 중입니다.">
            <Button size="large" block disabled className={styles.cancel}>
              취소하기
            </Button>
          </Tooltip>
        </section>
      </div>
    </div>
  );
}
