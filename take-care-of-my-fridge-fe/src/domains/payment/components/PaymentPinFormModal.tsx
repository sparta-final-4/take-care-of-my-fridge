import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Alert, App, Form, Input, Modal, Typography } from 'antd';
import { useState } from 'react';

import {
  getErrorCode,
  getPaymentPinErrorField,
  getPaymentPinErrorMessage,
} from '@/common/api/errors';
import { changePin, resetPin, setPin } from '@/domains/payment/api';
import { CONFIRM_PIN_RULES, onlyDigits, PIN_LENGTH, PIN_RULES } from '@/domains/payment/paymentPin';
import { paymentPinQueries } from '@/domains/payment/queries';

export type PaymentPinFormMode = 'set' | 'change' | 'reset';

interface PaymentPinFormModalProps {
  open: boolean;
  // 닫히는 동안 제목·칸이 바뀌지 않게 닫을 때는 open만 바꾼다
  mode: PaymentPinFormMode;
  onClose: () => void;
  // 저장되면 새 비밀번호를 알린다(주문서는 이어서 그 비밀번호로 결제한다)
  onDone?: (newPin: string) => void;
}

interface FormValues {
  currentPin?: string;
  loginPassword?: string;
  newPin: string;
  confirmPin: string;
}

const MODE_TEXT: Record<PaymentPinFormMode, { title: string; done: string; guide: string }> = {
  set: {
    title: '결제 비밀번호 설정',
    done: '결제 비밀번호를 설정했어요.',
    guide: '등록 카드로 결제할 때 입력할 숫자 6자리예요. 같은 숫자나 연속된 숫자는 쓸 수 없어요.',
  },
  change: {
    title: '결제 비밀번호 변경',
    done: '결제 비밀번호를 바꿨어요.',
    guide: '현재 비밀번호를 틀리면 결제할 때처럼 틀린 횟수에 들어가요.',
  },
  reset: {
    title: '결제 비밀번호 재설정',
    done: '결제 비밀번호를 다시 설정했어요.',
    guide: '로그인 비밀번호로 본인인지 확인한 뒤 새로 정해요. 잠긴 비밀번호도 풀려요.',
  },
};

function submitPin(mode: PaymentPinFormMode, values: FormValues) {
  if (mode === 'set') return setPin(values.newPin);
  if (mode === 'change') {
    return changePin({ currentPin: values.currentPin ?? '', newPin: values.newPin });
  }
  return resetPin({ loginPassword: values.loginPassword ?? '', newPin: values.newPin });
}

const pinInput = (
  <Input.Password inputMode="numeric" maxLength={PIN_LENGTH} autoComplete="one-time-code" />
);

// 결제 비밀번호 설정·변경·재설정. 서버 오류는 해당 입력 칸에, 칸에 붙일 수 없는 오류는 위에 보여 준다
export function PaymentPinFormModal({ open, mode, onClose, onDone }: PaymentPinFormModalProps) {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<FormValues>();
  const [formError, setFormError] = useState<string>();

  const mutation = useMutation({
    mutationFn: (values: FormValues) => submitPin(mode, values),
    meta: { skipGlobalError: true },
    // 틀린 횟수·잠김이 바뀌었을 수 있으므로 실패해도 상태를 다시 받는다
    onSettled: () => queryClient.invalidateQueries({ queryKey: paymentPinQueries.all }),
  });

  const handleFinish = (values: FormValues) => {
    setFormError(undefined);
    mutation.mutate(values, {
      onSuccess: () => {
        message.success(MODE_TEXT[mode].done);
        onClose();
        onDone?.(values.newPin);
      },
      onError: (error) => {
        const errorMessage = getPaymentPinErrorMessage(error);
        // 다른 창에서 이미 설정했다. 상태를 다시 받았으니 폼을 닫고 안내만 한다
        if (getErrorCode(error) === 'PIN_005') {
          message.info(errorMessage);
          onClose();
          return;
        }
        const field = getPaymentPinErrorField(error);
        if (field && form.getFieldInstance(field)) {
          form.setFields([{ name: field, value: '', errors: [errorMessage] }]);
          // 새 비밀번호를 다시 정해야 하므로 확인 칸도 비운다
          if (field === 'newPin') form.setFieldValue('confirmPin', '');
          return;
        }
        setFormError(errorMessage);
      },
    });
  };

  return (
    <Modal
      open={open}
      title={MODE_TEXT[mode].title}
      okText="저장"
      cancelText="취소"
      okButtonProps={{ htmlType: 'submit' }}
      confirmLoading={mutation.isPending}
      // 저장 중에 닫으면 응답이 온 뒤 결제가 이어지거나 오류가 다음 열기에 남으므로 닫지 못하게 한다
      cancelButtonProps={{ disabled: mutation.isPending }}
      closable={!mutation.isPending}
      mask={{ closable: !mutation.isPending }}
      keyboard={!mutation.isPending}
      onCancel={onClose}
      afterClose={() => setFormError(undefined)}
      destroyOnHidden
      modalRender={(dom) => (
        <Form form={form} layout="vertical" preserve={false} clearOnDestroy onFinish={handleFinish}>
          {dom}
        </Form>
      )}
    >
      <Typography.Paragraph type="secondary">{MODE_TEXT[mode].guide}</Typography.Paragraph>
      {formError && <Alert type="error" showIcon title={formError} />}
      {mode === 'change' && (
        <Form.Item
          name="currentPin"
          label="현재 결제 비밀번호"
          rules={PIN_RULES}
          normalize={onlyDigits}
        >
          {pinInput}
        </Form.Item>
      )}
      {mode === 'reset' && (
        <Form.Item
          name="loginPassword"
          label="로그인 비밀번호"
          rules={[{ required: true, message: '로그인 비밀번호를 입력해 주세요.' }]}
        >
          <Input.Password autoComplete="current-password" />
        </Form.Item>
      )}
      <Form.Item
        name="newPin"
        label={mode === 'set' ? '결제 비밀번호' : '새 결제 비밀번호'}
        rules={PIN_RULES}
        normalize={onlyDigits}
      >
        {pinInput}
      </Form.Item>
      <Form.Item
        name="confirmPin"
        label="한 번 더 입력"
        dependencies={['newPin']}
        rules={CONFIRM_PIN_RULES}
        normalize={onlyDigits}
      >
        {pinInput}
      </Form.Item>
    </Modal>
  );
}
