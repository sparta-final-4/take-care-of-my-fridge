import { App, Button, Flex, Form, InputNumber, Modal, Typography } from 'antd';

import type { FridgeItemResponse } from '../api';
import { formatQuantity, getStep, withObjectParticle } from '../format';
import { useTakeOutFridgeItem } from '../queries';
import styles from './FridgeItemConsumeModal.module.scss';

// 입력칸 하나뿐이라 기본 너비(520px)보다 좁게 띄운다. 좁은 화면에서는 antd가 화면 안으로 줄인다.
const MODAL_WIDTH = 400;

interface ConsumeFormValues {
  quantity: number;
}

interface FridgeItemConsumeModalProps {
  item: FridgeItemResponse;
  onClose: () => void;
}

export function FridgeItemConsumeModal({ item, onClose }: FridgeItemConsumeModalProps) {
  const { message } = App.useApp();
  const [form] = Form.useForm<ConsumeFormValues>();
  const takeOutMutation = useTakeOutFridgeItem();
  // 밀키트는 개수 단위라 1개씩만 뺀다(서버도 소수면 FRIDGE_005로 거절).
  const isMealkit = item.ingredientType === 'MEALKIT';

  const handleFillAll = () => form.setFieldValue('quantity', item.quantity);

  // 입력칸은 max로 가진 양을 넘지 못하게 하지만(+ 버튼 비활성화, 벗어나거나 Enter 시 가진 양으로 맞춤) 조용히 바뀌므로,
  // 맞추기 전에 직접 입력한 글자를 읽어 넘었으면 안내한다.
  const warnIfOverRemaining = (text: string) => {
    const typed = Number(text);
    if (item.quantity !== undefined && Number.isFinite(typed) && typed > item.quantity) {
      void message.warning(`가진 양(${formatQuantity(item)})보다 많이 뺄 수 없어요`);
    }
  };

  // 남은 양 전부면 quantity를 빼고 보낸다(서버가 전부 사용으로 처리).
  const handleFinish = ({ quantity }: ConsumeFormValues) => {
    takeOutMutation.mutate(
      {
        fridgeId: item.id ?? 0,
        request:
          quantity === item.quantity ? { action: 'CONSUMED' } : { action: 'CONSUMED', quantity },
      },
      {
        onSuccess: (result) => {
          void message.success(
            result.removed
              ? '냉장고에서 뺐어요'
              : `남은 양: ${formatQuantity({ quantity: result.remainingQuantity, baseUnit: item.baseUnit })}`,
          );
          onClose();
        },
      },
    );
  };

  return (
    <Modal
      open
      width={MODAL_WIDTH}
      centered
      title={`${withObjectParticle(item.rawName ?? '')} 얼마나 먹었나요?`}
      okText="먹었어요"
      cancelText="취소"
      confirmLoading={takeOutMutation.isPending}
      okButtonProps={{ loading: takeOutMutation.isPending, disabled: takeOutMutation.isPending }}
      cancelButtonProps={{ disabled: takeOutMutation.isPending }}
      onOk={form.submit}
      onCancel={onClose}
    >
      <Form<ConsumeFormValues>
        form={form}
        layout="vertical"
        requiredMark={false}
        initialValues={{ quantity: item.quantity }}
        onFinish={handleFinish}
      >
        {/* 아래 입력칸 초기값(실제 남은 양)과 숫자가 같도록 반올림하지 않는다. */}
        <Typography.Paragraph type="secondary">
          남은 양: {item.quantity}
          {item.baseUnit ?? ''}
        </Typography.Paragraph>
        <Form.Item label="먹은 양">
          <Flex gap="small">
            <Form.Item
              name="quantity"
              noStyle
              rules={[
                { required: true, message: '먹은 양을 입력해 주세요.' },
                {
                  validator: (_, value?: number) =>
                    value === undefined || value > 0
                      ? Promise.resolve()
                      : Promise.reject(new Error('먹은 양은 0보다 커야 합니다.')),
                },
              ]}
            >
              <InputNumber
                min={isMealkit ? 1 : 0}
                max={item.quantity}
                step={isMealkit ? 1 : getStep(item.baseUnit)}
                precision={isMealkit ? 0 : undefined}
                onBlur={(event) => warnIfOverRemaining(event.target.value)}
                onPressEnter={(event) => warnIfOverRemaining(event.currentTarget.value)}
                suffix={item.baseUnit || undefined}
                aria-label="먹은 양"
                className={styles.quantityInput}
              />
            </Form.Item>
            <Button onClick={handleFillAll}>전부</Button>
          </Flex>
        </Form.Item>
      </Form>
    </Modal>
  );
}
