import { useQuery } from '@tanstack/react-query';
import { Button, Flex, Skeleton, Tag, Typography } from 'antd';
import { useState } from 'react';

import { paymentPinQueries } from '@/domains/payment/queries';

import { type PaymentPinFormMode, PaymentPinFormModal } from './PaymentPinFormModal';
import styles from './PaymentPinSetting.module.scss';

// 카드 관리 화면의 결제 비밀번호 상태와 설정·변경·재설정 버튼
export function PaymentPinSetting() {
  const statusQuery = useQuery(paymentPinQueries.status());
  const [formOpen, setFormOpen] = useState(false);
  const [formMode, setFormMode] = useState<PaymentPinFormMode>('set');

  const openForm = (mode: PaymentPinFormMode) => {
    setFormMode(mode);
    setFormOpen(true);
  };

  const renderStatus = () => {
    if (statusQuery.isPending) return <Skeleton.Input active size="small" />;
    if (statusQuery.isError) {
      return (
        <Flex align="center" gap="small" wrap>
          <Typography.Text type="secondary">
            결제 비밀번호 상태를 불러오지 못했어요.
          </Typography.Text>
          <Button size="small" onClick={() => statusQuery.refetch()}>
            다시 시도
          </Button>
        </Flex>
      );
    }
    const { isSet, isLocked } = statusQuery.data;
    if (!isSet) {
      return (
        <Typography.Text type="secondary">
          <Tag>미설정</Tag>등록 카드로 결제하려면 결제 비밀번호를 설정해 주세요.
        </Typography.Text>
      );
    }
    if (isLocked) {
      return (
        <Typography.Text type="secondary">
          <Tag color="red">잠김</Tag>5번 틀려 잠겼어요. 재설정하면 다시 쓸 수 있어요.
        </Typography.Text>
      );
    }
    return (
      <Typography.Text type="secondary">
        <Tag color="green">설정됨</Tag>등록 카드로 결제할 때 입력해요.
      </Typography.Text>
    );
  };

  const status = statusQuery.data;
  return (
    <div className={styles.setting}>
      <Flex vertical gap="small" className={styles.info}>
        <Typography.Text strong>결제 비밀번호</Typography.Text>
        {renderStatus()}
      </Flex>
      {status && (
        <Flex gap="small" wrap className={styles.actions}>
          {status.isSet ? (
            <>
              <Button onClick={() => openForm('change')} disabled={status.isLocked}>
                변경
              </Button>
              <Button onClick={() => openForm('reset')}>재설정</Button>
            </>
          ) : (
            <Button onClick={() => openForm('set')}>설정</Button>
          )}
        </Flex>
      )}
      <PaymentPinFormModal open={formOpen} mode={formMode} onClose={() => setFormOpen(false)} />
    </div>
  );
}
