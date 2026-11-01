import { App, Form, Input, Modal, Radio, Typography } from 'antd';

import type { CookingLogResponse, CookingLogUpdateRequestResultEnum } from '@/common/api/clients';
import { useUpdateCookingLog } from '../queries';

export interface CookingLogEditModalProps {
  open: boolean;
  log: CookingLogResponse | null;
  onClose: () => void;
}

interface FormValues {
  result: CookingLogUpdateRequestResultEnum;
  memo?: string;
}

export function CookingLogEditModal({ open, log, onClose }: CookingLogEditModalProps) {
  const { message } = App.useApp();
  const [form] = Form.useForm<FormValues>();
  const updateMutation = useUpdateCookingLog();

  // 팝업 내용은 열린 다음 렌더에서야 마운트되므로, 열릴 때 effect로 setFieldsValue를 하면 폼이 아직 없다.
  // 폼이 마운트될 때 초기값으로 넣고, 기록이 바뀌면 key로 새 폼을 만든다. 닫으면 destroyOnHidden으로 폼이 사라진다.
  const initialValues: Partial<FormValues> = {
    result: log?.result ?? 'SUCCESS',
    memo: log?.memo ?? '',
  };

  const handleSubmit = async () => {
    if (!log?.id) return;
    try {
      const values = await form.validateFields();
      updateMutation.mutate(
        {
          cookingLogId: log.id,
          request: {
            result: values.result,
            memo: values.memo?.trim() ?? '',
          },
        },
        {
          onSuccess: () => {
            message.success('요리 기록이 수정되었습니다.');
            onClose();
          },
        },
      );
    } catch {
      // 폼 유효성 검사 실패
    }
  };

  return (
    <Modal
      open={open}
      title="요리 기록 수정"
      okText="저장"
      cancelText="취소"
      confirmLoading={updateMutation.isPending}
      onOk={handleSubmit}
      onCancel={onClose}
      destroyOnHidden
    >
      <div style={{ marginBottom: 16 }}>
        <Typography.Text type="secondary">결과와 메모만 바꿀 수 있어요</Typography.Text>
      </div>

      <Form
        key={log?.id ?? 'none'}
        form={form}
        layout="vertical"
        preserve={false}
        initialValues={initialValues}
      >
        <Form.Item
          name="result"
          label="요리 결과"
          rules={[{ required: true, message: '요리 결과를 선택해 주세요.' }]}
        >
          <Radio.Group
            options={[
              { label: '성공', value: 'SUCCESS' },
              { label: '실패', value: 'FAILED' },
            ]}
          />
        </Form.Item>
        <Form.Item
          name="memo"
          label="메모"
          rules={[{ max: 50, message: '메모는 최대 50자까지 입력할 수 있습니다.' }]}
        >
          <Input.TextArea
            rows={3}
            maxLength={50}
            showCount
            placeholder="요리 시 참고할 점이나 팁을 적어보세요. (최대 50자)"
          />
        </Form.Item>
      </Form>
    </Modal>
  );
}
