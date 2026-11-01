import { UploadOutlined } from '@ant-design/icons';
import { useMutation } from '@tanstack/react-query';
import { App, Button, Flex, Image, Input, Upload } from 'antd';
import { useState } from 'react';

import { getImageUploadErrorMessage } from '@/common/api/errors';
import { FallbackImage } from '@/common/components/FallbackImage';
import { getImageSrc } from '@/common/components/imageFallback';
import { uploadProductImage } from '@/domains/product/api';
import { productImageUploadKey } from '@/domains/product/queries';

import styles from './ProductImageUpload.module.scss';

// 백엔드 POST /api/admin/images/presign 이 허용하는 형식·크기
const ACCEPTED_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
const MAX_FILE_SIZE = 5 * 1024 * 1024;
// ProductImageUpload.module.scss의 $product-image-preview-size와 같은 값
const PREVIEW_SIZE = 160;

interface ProductImageUploadProps {
  value?: string;
  onChange?: (value: string) => void;
}

// Form.Item 아래에 두면 imageUrl 값을 받는다. 파일을 올리면 받은 URL로 바꾸고, URL을 직접 입력할 수도 있다.
export function ProductImageUpload({ value, onChange }: ProductImageUploadProps) {
  const { message } = App.useApp();
  // 불러오지 못한 주소는 미리보기(크게 보기) 없이 기본 이미지로 보여 준다.
  const [failedSrc, setFailedSrc] = useState<string>();
  const previewSrc = getImageSrc(value, failedSrc);
  const upload = useMutation({
    mutationKey: productImageUploadKey,
    mutationFn: uploadProductImage,
    meta: { skipGlobalError: true },
  });

  const handleBeforeUpload = (file: File) => {
    if (!ACCEPTED_TYPES.includes(file.type)) {
      void message.error('JPG, PNG, WEBP 이미지만 올릴 수 있습니다.');
      return Upload.LIST_IGNORE;
    }
    if (file.size > MAX_FILE_SIZE) {
      void message.error('이미지는 5MB 이하만 올릴 수 있습니다.');
      return Upload.LIST_IGNORE;
    }
    upload.mutate(file, {
      onSuccess: (imageUrl) => {
        onChange?.(imageUrl);
        void message.success('이미지를 올렸습니다.');
      },
      onError: (error) => void message.error(getImageUploadErrorMessage(error)),
    });
    // 파일은 직접 올렸으므로 antd Upload의 기본 업로드는 막는다.
    return false;
  };

  return (
    <Flex vertical gap="small">
      {value &&
        (previewSrc ? (
          <Image
            src={previewSrc}
            alt="상품 이미지 미리보기"
            width={PREVIEW_SIZE}
            height={PREVIEW_SIZE}
            onError={() => setFailedSrc(previewSrc)}
            className={styles.preview}
          />
        ) : (
          <FallbackImage
            alt="상품 이미지 미리보기"
            kind="product"
            className={styles.previewFallback}
          />
        ))}
      <Upload
        accept={ACCEPTED_TYPES.join(',')}
        showUploadList={false}
        beforeUpload={handleBeforeUpload}
        disabled={upload.isPending}
      >
        <Button icon={<UploadOutlined />} loading={upload.isPending}>
          이미지 업로드
        </Button>
      </Upload>
      <Input
        value={value}
        onChange={(event) => onChange?.(event.target.value)}
        maxLength={500}
        placeholder="https:// (직접 입력)"
        aria-label="이미지 URL"
      />
    </Flex>
  );
}
