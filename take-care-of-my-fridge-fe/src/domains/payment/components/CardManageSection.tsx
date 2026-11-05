import { CreditCardOutlined, PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App,
  Button,
  Divider,
  Empty,
  Flex,
  Popconfirm,
  Result,
  Skeleton,
  Tag,
  Typography,
} from 'antd';
import dayjs from 'dayjs';

import { ROUTES } from '@/app/paths';
import type { BillingKeyResponse } from '@/common/api/clients';
import { SectionCard } from '@/common/components/SectionCard';
import { deleteCard, setDefaultCard } from '@/domains/payment/api';
import {
  CARD_TYPE_LABELS,
  cardName,
  MAX_CARDS,
  maskedCardNumber,
} from '@/domains/payment/cardFormat';
import { useCardRegistration } from '@/domains/payment/hooks/useCardRegistration';
import { billingKeyQueries } from '@/domains/payment/queries';

import { PaymentPinSetting } from './PaymentPinSetting';
import styles from './CardManageSection.module.scss';

// 등록 카드 목록과 카드 등록·대표 카드 지정·삭제, 결제 비밀번호 설정. 카드 번호는 결제대행사 창에서만 입력하고 우리는 빌링키만 넘긴다.
export function CardManageSection() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const cardsQuery = useQuery(billingKeyQueries.list());
  const { register, registering } = useCardRegistration(ROUTES.mypageCards);

  const refreshCards = () => queryClient.invalidateQueries({ queryKey: billingKeyQueries.all });
  const defaultMutation = useMutation({ mutationFn: setDefaultCard, onSettled: refreshCards });
  const deleteMutation = useMutation({ mutationFn: deleteCard, onSettled: refreshCards });

  const cards = cardsQuery.data ?? [];
  const limitReached = cards.length >= MAX_CARDS;

  const handleSetDefault = (card: BillingKeyResponse) =>
    defaultMutation.mutate(card.cardId ?? 0, {
      onSuccess: () => message.success('대표 카드로 지정했어요.'),
    });
  const handleDelete = (card: BillingKeyResponse) =>
    deleteMutation.mutate(card.cardId ?? 0, {
      onSuccess: () => message.success('카드를 삭제했어요.'),
    });

  const registerButton = (
    <Button
      type="primary"
      icon={<PlusOutlined />}
      onClick={register}
      loading={registering}
      disabled={limitReached}
      className={styles.registerButton}
    >
      카드 등록
    </Button>
  );

  return (
    <SectionCard
      title="카드 관리"
      bracket={false}
      titleVariant="page"
      extra={cardsQuery.isSuccess && cards.length > 0 ? registerButton : undefined}
    >
      {cardsQuery.isPending ? (
        <Skeleton active paragraph={{ rows: 4 }} />
      ) : cardsQuery.isError ? (
        <Result
          status="error"
          title="등록한 카드를 불러오지 못했습니다."
          extra={<Button onClick={() => cardsQuery.refetch()}>다시 시도</Button>}
        />
      ) : cards.length === 0 ? (
        <Empty description="등록한 카드가 없어요. 카드를 등록하면 결제창 없이 바로 결제할 수 있어요.">
          {registerButton}
        </Empty>
      ) : (
        <Flex vertical gap="middle">
          <ul className={styles.list}>
            {cards.map((card) => (
              <li key={card.cardId} className={styles.card}>
                <CreditCardOutlined className={styles.icon} aria-hidden="true" />
                <Flex vertical className={styles.info}>
                  <Flex align="center" gap="small" wrap>
                    <Typography.Text
                      strong
                      ellipsis={{ tooltip: cardName(card) }}
                      className={styles.name}
                    >
                      {cardName(card)}
                    </Typography.Text>
                    {card.isDefault && <Tag color="blue">대표</Tag>}
                    {card.cardType && <Tag>{CARD_TYPE_LABELS[card.cardType]}</Tag>}
                  </Flex>
                  <Typography.Text type="secondary" className={styles.meta}>
                    {[
                      maskedCardNumber(card),
                      card.createdAt && `${dayjs(card.createdAt).format('YYYY-MM-DD')} 등록`,
                    ]
                      .filter(Boolean)
                      .join(' · ')}
                  </Typography.Text>
                </Flex>
                <Flex gap="small" wrap className={styles.actions}>
                  {!card.isDefault && (
                    <Button
                      onClick={() => handleSetDefault(card)}
                      loading={
                        defaultMutation.isPending && defaultMutation.variables === card.cardId
                      }
                    >
                      대표 카드로
                    </Button>
                  )}
                  <Popconfirm
                    title="카드를 삭제할까요?"
                    description="삭제한 카드는 결제에 쓸 수 없어요."
                    okText="삭제"
                    cancelText="취소"
                    okButtonProps={{ danger: true }}
                    onConfirm={() => handleDelete(card)}
                  >
                    <Button
                      danger
                      loading={deleteMutation.isPending && deleteMutation.variables === card.cardId}
                    >
                      삭제
                    </Button>
                  </Popconfirm>
                </Flex>
              </li>
            ))}
          </ul>
          {limitReached && (
            <Typography.Text type="secondary">
              카드는 최대 {MAX_CARDS}장까지 등록할 수 있어요.
            </Typography.Text>
          )}
        </Flex>
      )}
      <Divider />
      <PaymentPinSetting />
    </SectionCard>
  );
}
