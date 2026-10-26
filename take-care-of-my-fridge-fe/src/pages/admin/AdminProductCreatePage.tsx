import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Alert, App, Button, Form, Input, InputNumber, Select, Typography } from 'antd';

import type { ProductCreateRequest } from '@/common/api/clients';
import { createProduct } from '@/domains/product/api';
import { productQueries } from '@/domains/product/queries';

import styles from './AdminProductCreatePage.module.scss';

export default function AdminProductCreatePage() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<ProductCreateRequest>();
  const productType = Form.useWatch('productType', form);
  const creation = useMutation({
    mutationFn: createProduct,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: productQueries.all }),
  });

  const handleFinish = (values: ProductCreateRequest) => {
    const request: ProductCreateRequest = {
      productType: values.productType,
      name: values.name,
      description: values.description,
      imageUrl: values.imageUrl,
      price: values.price,
      stock: values.stock,
      storage: values.storage,
      shelfDays: values.shelfDays,
      ...(values.productType === 'INGREDIENT'
        ? { ingredient: values.ingredient }
        : { mealkit: values.mealkit }),
    };
    creation.mutate(request, {
      onSuccess: (product) => {
        void message.success(`상품 ${product.productId ?? ''} 등록을 완료했습니다.`);
        form.resetFields();
      },
    });
  };

  return (
    <section className={styles.container}>
      <Typography.Title level={2}>상품 등록</Typography.Title>
      <Typography.Paragraph type="secondary">
        이미지 URL과 권장 보관 일수는 현재 상품 API 기준으로 입력합니다.
      </Typography.Paragraph>
      <Form<ProductCreateRequest>
        form={form}
        layout="vertical"
        initialValues={{ productType: 'INGREDIENT', storage: 'REFRIGERATED', stock: 0 }}
        onFinish={handleFinish}
      >
        <Form.Item name="productType" label="상품 종류" rules={[{ required: true }]}>
          <Select
            options={[
              { value: 'INGREDIENT', label: '재료' },
              { value: 'MEALKIT', label: '밀키트' },
            ]}
          />
        </Form.Item>
        <Form.Item
          name="name"
          label="상품명"
          rules={[{ required: true, whitespace: true, max: 200 }]}
        >
          <Input maxLength={200} />
        </Form.Item>
        <Form.Item name="description" label="설명" rules={[{ max: 2000 }]}>
          <Input.TextArea maxLength={2000} rows={4} showCount />
        </Form.Item>
        <Form.Item name="imageUrl" label="이미지 URL" rules={[{ type: 'url', max: 500 }]}>
          <Input maxLength={500} placeholder="https://" />
        </Form.Item>
        <Form.Item
          name="price"
          label="정상가 (원)"
          rules={[{ required: true, type: 'number', min: 1, max: 10000000 }]}
        >
          <InputNumber min={1} max={10000000} precision={0} className={styles.fullWidth} />
        </Form.Item>
        <Form.Item
          name="stock"
          label="재고 (개)"
          rules={[{ required: true, type: 'number', min: 0, max: 100000 }]}
        >
          <InputNumber min={0} max={100000} precision={0} className={styles.fullWidth} />
        </Form.Item>
        <Form.Item name="storage" label="보관 방법" rules={[{ required: true }]}>
          <Select
            options={[
              { value: 'ROOM_TEMP', label: '상온' },
              { value: 'REFRIGERATED', label: '냉장' },
              { value: 'FROZEN', label: '냉동' },
            ]}
          />
        </Form.Item>
        <Form.Item
          name="shelfDays"
          label="권장 보관 일수"
          rules={[{ required: true, type: 'number', min: 1 }]}
        >
          <InputNumber min={1} precision={0} className={styles.fullWidth} />
        </Form.Item>

        {productType === 'INGREDIENT' ? (
          <>
            <Alert
              type="info"
              showIcon
              message="연결할 재료의 ID를 입력해 주세요."
              className={styles.notice}
            />
            <Form.Item
              name={['ingredient', 'ingredientId']}
              label="재료 ID"
              rules={[{ required: true, type: 'number', min: 1 }]}
            >
              <InputNumber min={1} precision={0} className={styles.fullWidth} />
            </Form.Item>
            <Form.Item
              name={['ingredient', 'unitAmount']}
              label="용량"
              rules={[{ required: true, type: 'number', min: 0.01, max: 999999.99 }]}
            >
              <InputNumber min={0.01} max={999999.99} precision={2} className={styles.fullWidth} />
            </Form.Item>
            <Form.Item
              name={['ingredient', 'unit']}
              label="용량 단위"
              rules={[{ required: true, whitespace: true, max: 20 }]}
            >
              <Input maxLength={20} placeholder="g, ml 등" />
            </Form.Item>
            <Form.Item
              name={['ingredient', 'baseQuantity']}
              label="재료 기본 단위 수량"
              rules={[{ required: true, type: 'number', min: 0.01, max: 99999999.99 }]}
            >
              <InputNumber
                min={0.01}
                max={99999999.99}
                precision={2}
                className={styles.fullWidth}
              />
            </Form.Item>
          </>
        ) : (
          <Form.Item
            name={['mealkit', 'servings']}
            label="구성 인분"
            rules={[{ required: true, type: 'number', min: 1 }]}
          >
            <InputNumber min={1} precision={0} className={styles.fullWidth} />
          </Form.Item>
        )}
        <Button type="primary" htmlType="submit" loading={creation.isPending}>
          상품 등록
        </Button>
      </Form>
    </section>
  );
}
