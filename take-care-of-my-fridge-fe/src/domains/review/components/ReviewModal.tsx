import { App, Form, Input, Modal, Rate, Typography } from 'antd';
import { isAxiosError } from 'axios';

import { getErrorCode, getErrorMessage } from '@/common/api/errors';
import { useCreateReview, useUpdateReview } from '../queries';

import styles from './ReviewModal.module.scss';

export interface InitialReviewData {
  id: number;
  rating: number;
  content: string;
}

export interface ReviewModalProps {
  open: boolean;
  recipeId: number;
  recipeName?: string;
  initialReview?: InitialReviewData | null;
  onClose: () => void;
  onSuccess?: () => void;
}

interface FormValues {
  rating: number;
  content: string;
}

const MAX_CONTENT_LENGTH = 100;

export function ReviewModal({
  open,
  recipeId,
  recipeName,
  initialReview,
  onClose,
  onSuccess,
}: ReviewModalProps) {
  const { message } = App.useApp();
  const [form] = Form.useForm<FormValues>();
  const contentValue = Form.useWatch('content', form) ?? '';
  const contentLength = typeof contentValue === 'string' ? contentValue.length : 0;

  const isEdit = Boolean(initialReview);
  const createMutation = useCreateReview();
  const updateMutation = useUpdateReview();

  const isPending = createMutation.isPending || updateMutation.isPending;

  const initialValues: Partial<FormValues> = {
    rating: initialReview?.rating,
    content: initialReview?.content ?? '',
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      if (isEdit && initialReview) {
        updateMutation.mutate(
          {
            reviewId: initialReview.id,
            request: {
              rating: values.rating,
              content: values.content.trim(),
            },
          },
          {
            onSuccess: () => {
              message.success('후기가 수정되었습니다.');
              // TODO: 가짜 데이터에서 실제 백엔드 목록 연동 시 쿼리 무효화 및 상태 반영
              onSuccess?.();
              onClose();
            },
            onError: (error) => {
              message.error(getErrorMessage(error));
            },
          },
        );
      } else {
        createMutation.mutate(
          {
            recipeId,
            rating: values.rating,
            content: values.content.trim(),
          },
          {
            onSuccess: () => {
              message.success('후기가 등록되었습니다.');
              // TODO: 가짜 데이터에서 실제 백엔드 목록 연동 시 쿼리 무효화 및 상태 반영
              onSuccess?.();
              onClose();
            },
            onError: (error) => {
              const code = getErrorCode(error);
              if (
                code === 'REVIEW_002' ||
                (isAxiosError(error) && error.response?.status === 409)
              ) {
                message.error('이미 후기를 작성한 레시피예요');
              } else {
                message.error(getErrorMessage(error));
              }
            },
          },
        );
      }
    } catch {
      // 폼 유효성 검사 실패는 Form에서 자체 인라인 에러로 표시
    }
  };

  return (
    <Modal
      open={open}
      title={isEdit ? '후기 수정' : '후기 쓰기'}
      okText={isEdit ? '수정' : '등록'}
      cancelText="취소"
      confirmLoading={isPending}
      okButtonProps={{ disabled: isPending }}
      onOk={handleSubmit}
      onCancel={onClose}
      destroyOnHidden
    >
      {recipeName && (
        <Typography.Paragraph type="secondary" className={styles.recipeName}>
          {recipeName}
        </Typography.Paragraph>
      )}

      <Form
        key={`${recipeId}-${initialReview?.id ?? 'new'}`}
        form={form}
        layout="vertical"
        requiredMark={false}
        className={styles.form}
        initialValues={initialValues}
        preserve={false}
      >
        <Form.Item
          name="rating"
          label="별점"
          rules={[{ required: true, message: '별점을 선택해 주세요.' }]}
        >
          <Rate allowHalf={false} className={styles.rate} />
        </Form.Item>

        <Form.Item
          name="content"
          label={
            <div className={styles.labelWrapper}>
              <span>내용</span>
              <span className={styles.counter}>{MAX_CONTENT_LENGTH - contentLength}자 남음</span>
            </div>
          }
          rules={[
            { required: true, message: '후기 내용을 입력해 주세요.' },
            { max: MAX_CONTENT_LENGTH, message: '후기 내용은 100자 이하여야 합니다.' },
          ]}
        >
          <Input.TextArea
            rows={4}
            maxLength={MAX_CONTENT_LENGTH}
            placeholder="요리는 어떠셨나요? 솔직한 후기를 남겨주세요."
          />
        </Form.Item>
      </Form>
    </Modal>
  );
}
