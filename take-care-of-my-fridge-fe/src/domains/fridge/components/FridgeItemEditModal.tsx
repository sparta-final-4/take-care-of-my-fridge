import { App, DatePicker, Form, Input, InputNumber, Modal, Select } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';

import type { FridgeItemResponse, StorageType } from '../api';
import { STORAGE_LABELS } from '../format';
import { useUpdateFridgeItem } from '../queries';
import styles from './FridgeItemEditModal.module.scss';

// FridgeItemUpdateRequest.java의 @Size 제한
const MAX_NAME_LENGTH = 200;

const STORAGE_OPTIONS = (Object.keys(STORAGE_LABELS) as StorageType[]).map((value) => ({
  value,
  label: STORAGE_LABELS[value],
}));

interface EditFormValues {
  rawName: string;
  quantity: number;
  storage: StorageType;
  expirationDate: Dayjs;
}

interface FridgeItemEditModalProps {
  item: FridgeItemResponse;
  onClose: () => void;
}

export function FridgeItemEditModal({ item, onClose }: FridgeItemEditModalProps) {
  const { message } = App.useApp();
  const [form] = Form.useForm<EditFormValues>();
  const updateMutation = useUpdateFridgeItem();

  const handleFinish = (values: EditFormValues) => {
    updateMutation.mutate(
      {
        fridgeId: item.id,
        request: {
          rawName: values.rawName.trim(),
          quantity: values.quantity,
          storage: values.storage,
          expirationDate: values.expirationDate.format('YYYY-MM-DD'),
        },
      },
      {
        onSuccess: () => {
          void message.success('재료를 수정했습니다.');
          onClose();
        },
      },
    );
  };

  return (
    <Modal
      open
      title="재료 수정"
      okText="저장"
      cancelText="취소"
      confirmLoading={updateMutation.isPending}
      onOk={form.submit}
      onCancel={onClose}
    >
      <Form<EditFormValues>
        form={form}
        layout="vertical"
        requiredMark={false}
        initialValues={{
          rawName: item.rawName,
          quantity: item.quantity,
          storage: item.storage,
          expirationDate: dayjs(item.expirationDate),
        }}
        onFinish={handleFinish}
      >
        <Form.Item
          label="재료명"
          name="rawName"
          rules={[
            { required: true, whitespace: true, message: '재료 이름을 입력해 주세요.' },
            { max: MAX_NAME_LENGTH, message: `재료 이름은 ${MAX_NAME_LENGTH}자 이하여야 합니다.` },
          ]}
        >
          <Input maxLength={MAX_NAME_LENGTH} />
        </Form.Item>
        <Form.Item
          label="수량"
          name="quantity"
          rules={[
            { required: true, message: '수량을 입력해 주세요.' },
            { type: 'number', min: 0.01, message: '수량은 0보다 커야 합니다.' },
          ]}
        >
          <InputNumber
            min={0}
            step={1}
            suffix={item.baseUnit ?? undefined}
            className={styles.fullWidth}
          />
        </Form.Item>
        <Form.Item
          label="소비기한"
          name="expirationDate"
          rules={[{ required: true, message: '소비기한을 입력해 주세요.' }]}
        >
          <DatePicker format="YYYY-MM-DD" className={styles.fullWidth} />
        </Form.Item>
        <Form.Item
          label="보관 방식"
          name="storage"
          rules={[{ required: true, message: '보관방식을 선택해 주세요.' }]}
        >
          <Select options={STORAGE_OPTIONS} />
        </Form.Item>
      </Form>
    </Modal>
  );
}
