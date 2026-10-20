import { App, Button, Flex, Form, InputNumber, Modal, Typography } from 'antd';

import type { FridgeItemResponse } from '../api';
import { formatQuantity, withObjectParticle } from '../format';
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

  const handleFillAll = () => form.setFieldValue('quantity', item.quantity);

  // 남은 양 전부면 null로 보낸다. 남은 양보다 커도 막지 않고 보내면 서버가 전부 사용으로 처리한다.
  const handleFinish = ({ quantity }: ConsumeFormValues) => {
    takeOutMutation.mutate(
      {
        fridgeId: item.id,
        request: { action: 'CONSUMED', quantity: quantity === item.quantity ? null : quantity },
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
      title={`${withObjectParticle(item.rawName)} 얼마나 먹었나요?`}
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
        <Typography.Paragraph type="secondary">
          남은 양: {formatQuantity(item)}
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
                min={0}
                step={0.5}
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
