import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Flex, Form, Input, Modal, Skeleton, Typography } from 'antd';
import { useState } from 'react';

import { onlyDigits, PIN_LENGTH, PIN_RULES } from '@/domains/payment/paymentPin';
import { paymentPinQueries } from '@/domains/payment/queries';

import { type PaymentPinFormMode, PaymentPinFormModal } from './PaymentPinFormModal';

interface PaymentPinPayModalProps {
  open: boolean;
  // 결제할 카드와 금액 안내
  summary: string;
  paying: boolean;
  // 결제 요청이 결제 비밀번호 오류(PIN_*)로 끝나면 onPinError로 서버 메시지를 돌려준다. 틀린 경우 남은 횟수가 들어 있다
  onPay: (pin: string, onPinError: (message: string) => void) => void;
  onCancel: () => void;
}

// 주문서의 등록 카드 결제 확인. 결제 비밀번호를 받아 결제한다.
// 설정하지 않았거나 잠겼으면 이 자리에서 설정·재설정하고, 새로 정한 비밀번호로 바로 결제한다.
export function PaymentPinPayModal({
  open,
  summary,
  paying,
  onPay,
  onCancel,
}: PaymentPinPayModalProps) {
  const statusQuery = useQuery({ ...paymentPinQueries.status(), enabled: open });
  const [form] = Form.useForm<{ pin: string }>();
  const [formOpen, setFormOpen] = useState(false);
  const [formMode, setFormMode] = useState<PaymentPinFormMode>('set');
  const status = statusQuery.data;
  const canEnter = status?.isSet === true && !status.isLocked;

  const openForm = (mode: PaymentPinFormMode) => {
    setFormMode(mode);
    setFormOpen(true);
  };

  // 틀렸으면 칸을 비우고 서버 메시지를 칸 오류로 둔다. 다시 입력하면 입력 검사가 오류를 바꾼다
  const showPinError = (message: string) =>
    form.setFields([{ name: 'pin', value: '', errors: [message] }]);
  const pay = (pin: string) => onPay(pin, showPinError);

  const renderBody = () => {
    if (statusQuery.isPending) return <Skeleton active paragraph={{ rows: 2 }} />;
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
    if (!statusQuery.data.isSet) {
      return (
        <Flex vertical gap="middle" align="flex-start">
          <Typography.Text>
            등록 카드로 결제하려면 결제 비밀번호를 먼저 설정해 주세요. 설정하면 바로 결제해요.
          </Typography.Text>
          <Button type="primary" onClick={() => openForm('set')}>
            결제 비밀번호 설정
          </Button>
        </Flex>
      );
    }
    if (statusQuery.data.isLocked) {
      return (
        <Flex vertical gap="middle" align="flex-start">
          <Alert
            type="error"
            showIcon
            title="결제 비밀번호를 5번 틀려 잠겼어요."
            description="로그인 비밀번호로 확인한 뒤 다시 설정하면 바로 결제해요."
          />
          <Button type="primary" onClick={() => openForm('reset')}>
            결제 비밀번호 재설정
          </Button>
        </Flex>
      );
    }
    return (
      <>
        <Typography.Paragraph>{summary}</Typography.Paragraph>
        <Form.Item name="pin" label="결제 비밀번호" rules={PIN_RULES} normalize={onlyDigits}>
          <Input.Password
            inputMode="numeric"
            maxLength={PIN_LENGTH}
            autoComplete="one-time-code"
            disabled={paying}
          />
        </Form.Item>
        <Button type="link" onClick={() => openForm('reset')}>
          비밀번호를 잊었어요
        </Button>
      </>
    );
  };

  return (
    <>
      <Modal
        open={open}
        title="등록 카드로 결제"
        okText="결제"
        cancelText="닫기"
        okButtonProps={{ htmlType: 'submit' }}
        confirmLoading={paying}
        // 결제 중에는 닫지 못하게 한다. 닫아도 결제는 진행되고 결과를 보여 줄 곳이 없다
        cancelButtonProps={{ disabled: paying }}
        closable={!paying}
        mask={{ closable: !paying }}
        keyboard={!paying}
        footer={canEnter ? undefined : (_, { CancelBtn }) => <CancelBtn />}
        onCancel={onCancel}
        destroyOnHidden
        modalRender={(dom) => (
          <Form
            form={form}
            layout="vertical"
            preserve={false}
            clearOnDestroy
            onFinish={({ pin }: { pin: string }) => pay(pin)}
          >
            {dom}
          </Form>
        )}
      >
        {renderBody()}
      </Modal>
      <PaymentPinFormModal
        open={formOpen}
        mode={formMode}
        onClose={() => setFormOpen(false)}
        onDone={pay}
      />
    </>
  );
}
