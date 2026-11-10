import { App, ConfigProvider, DatePicker, Form, Input, InputNumber, Modal, Select } from 'antd';
import dayjs from 'dayjs';

import type { FridgeItemResponse, StorageType } from '../api';
import {
  buildFridgeItemUpdateRequest,
  hasFridgeItemChanges,
  type EditFormValues,
} from '../editRequest';
import { COMPACT_FIELD_THEME } from '../fieldTheme';
import { getStep, linkedIngredientName, STORAGE_LABELS } from '../format';
import { useUpdateFridgeItem } from '../queries';
import styles from './FridgeItemEditModal.module.scss';

// FridgeItemUpdateRequest.java의 @Size 제한
const MAX_NAME_LENGTH = 200;

// 입력칸이 넷뿐이라 기본 너비(520px)보다 좁게 띄운다. 좁은 화면에서는 antd가 화면 안으로 줄인다.
const MODAL_WIDTH = 420;

// 재료명 아래 안내: 지금 연결된 재료(줄 목록 칩과 같은 값). 이름을 고치는 중이면 저장 뒤 다시 찾는다고만 알린다(실시간 조회 없음).
function linkedIngredientHint(item: FridgeItemResponse, typedName: string | undefined) {
  if (typedName !== undefined && typedName.trim() !== (item.rawName ?? '').trim()) {
    return '저장하면 연결된 재료를 다시 찾아요';
  }
  if (item.ingredientType === 'CUSTOM') return '사전에 없는 재료라 AI 추천에는 쓰이지 않아요';
  if (item.ingredientType === 'MEALKIT') return `밀키트 · ${item.rawName ?? ''}`;
  return `연결된 재료 · ${linkedIngredientName(item)}`;
}

const STORAGE_OPTIONS = (Object.keys(STORAGE_LABELS) as StorageType[]).map((value) => ({
  value,
  label: STORAGE_LABELS[value],
}));

interface FridgeItemEditModalProps {
  item: FridgeItemResponse;
  onClose: () => void;
}

export function FridgeItemEditModal({ item, onClose }: FridgeItemEditModalProps) {
  const { message } = App.useApp();
  const [form] = Form.useForm<EditFormValues>();
  const updateMutation = useUpdateFridgeItem();
  // 밀키트는 개수 단위라 정수로만 수정한다(서버도 소수면 FRIDGE_005로 거절).
  const isMealkit = item.ingredientType === 'MEALKIT';
  // 처음 값과 다른 칸이 없으면 저장 버튼을 막는다. 비운 칸은 바뀐 것으로 보아 버튼이 켜지고, 누르면 검증 안내가 나온다.
  const watchedValues = Form.useWatch((values: Partial<EditFormValues>) => values, form);
  const hasChanges = hasFridgeItemChanges(item, watchedValues ?? {});

  const handleFinish = (values: EditFormValues) => {
    updateMutation.mutate(
      { fridgeId: item.id ?? 0, request: buildFridgeItemUpdateRequest(item, values) },
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
      width={MODAL_WIDTH}
      title="재료 수정"
      okText="저장"
      cancelText="취소"
      okButtonProps={{ disabled: !hasChanges }}
      confirmLoading={updateMutation.isPending}
      onOk={form.submit}
      onCancel={onClose}
    >
      <ConfigProvider theme={COMPACT_FIELD_THEME}>
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
          className={styles.form}
        >
          {/* 재료명 / 수량 + 보관 방식(좁은 화면은 세로) / 소비기한 */}
          <Form.Item
            label="재료명"
            name="rawName"
            extra={linkedIngredientHint(item, watchedValues?.rawName)}
            rules={[
              { required: true, whitespace: true, message: '재료 이름을 입력해 주세요.' },
              {
                max: MAX_NAME_LENGTH,
                message: `재료 이름은 ${MAX_NAME_LENGTH}자 이하여야 합니다.`,
              },
            ]}
          >
            <Input maxLength={MAX_NAME_LENGTH} />
          </Form.Item>
          <div className={styles.pair}>
            <Form.Item
              label="수량"
              name="quantity"
              rules={[
                { required: true, message: '수량을 입력해 주세요.' },
                { type: 'number', min: 0.01, message: '수량은 0보다 커야 합니다.' },
              ]}
            >
              <InputNumber
                min={isMealkit ? 1 : 0}
                step={isMealkit ? 1 : getStep(item.baseUnit)}
                precision={isMealkit ? 0 : undefined}
                suffix={item.baseUnit ?? undefined}
                className={styles.fullWidth}
              />
            </Form.Item>
            <Form.Item
              label="보관 방식"
              name="storage"
              rules={[{ required: true, message: '보관방식을 선택해 주세요.' }]}
            >
              <Select options={STORAGE_OPTIONS} />
            </Form.Item>
          </div>
          <Form.Item
            label="소비기한"
            name="expirationDate"
            rules={[{ required: true, message: '소비기한을 입력해 주세요.' }]}
          >
            <DatePicker format="YYYY-MM-DD" className={styles.fullWidth} />
          </Form.Item>
        </Form>
      </ConfigProvider>
    </Modal>
  );
}
