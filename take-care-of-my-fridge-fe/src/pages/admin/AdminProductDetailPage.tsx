import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App,
  Button,
  Descriptions,
  Flex,
  Form,
  InputNumber,
  Result,
  Select,
  Spin,
  Typography,
} from 'antd';
import { useNavigate, useParams } from 'react-router';

import { ROUTES } from '@/app/paths';
import type { ProductUpdateRequest } from '@/common/api/clients';
import { updateProduct } from '@/domains/product/api';
import { productQueries } from '@/domains/product/queries';

import styles from './AdminProductDetailPage.module.scss';

const TYPE_LABELS = { INGREDIENT: '재료', MEALKIT: '밀키트' };
const STATUS_LABELS = { ON_SALE: '판매 중', SOLD_OUT: '품절', STOPPED: '판매 중지' };
const STORAGE_LABELS = { ROOM_TEMP: '상온', REFRIGERATED: '냉장', FROZEN: '냉동' };
// 판매 중·품절은 서버가 재고로 정하므로 판매 여부만 고른다.
const SALES_OPTIONS = [
  { value: 'ON_SALE', label: '판매' },
  { value: 'STOPPED', label: '판매 중지' },
];

function AdminProductDetail({ productId }: { productId: number }) {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { data, isPending, isError, refetch } = useQuery(productQueries.adminDetail(productId));
  const update = useMutation({
    mutationFn: (request: ProductUpdateRequest) => updateProduct(productId, request),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: productQueries.all }),
  });

  if (isError) {
    return (
      <Result
        status="error"
        title="상품 상세를 불러오지 못했습니다"
        extra={<Button onClick={() => void refetch()}>다시 시도</Button>}
      />
    );
  }

  if (isPending || !data) return <Spin tip="상품 정보를 불러오고 있습니다" />;

  const detail = data;
  return (
    <section className={styles.container}>
      <Flex align="center" justify="space-between" wrap gap="middle" className={styles.heading}>
        <Typography.Title level={2}>{detail.name ?? '상품 상세'}</Typography.Title>
        <Button onClick={() => navigate(ROUTES.adminProducts)}>목록으로</Button>
      </Flex>
      <Descriptions bordered column={{ xs: 1, sm: 2 }} className={styles.details}>
        <Descriptions.Item label="상품 번호">{detail.productId ?? '-'}</Descriptions.Item>
        <Descriptions.Item label="종류">
          {detail.productType ? TYPE_LABELS[detail.productType] : '-'}
        </Descriptions.Item>
        <Descriptions.Item label="설명" span={2}>
          {detail.description || '-'}
        </Descriptions.Item>
        <Descriptions.Item label="이미지 URL" span={2}>
          {detail.imageUrl || '-'}
        </Descriptions.Item>
        <Descriptions.Item label="정상가">
          {detail.price === undefined ? '-' : `${detail.price.toLocaleString('ko-KR')}원`}
        </Descriptions.Item>
        <Descriptions.Item label="재고">
          {detail.stock === undefined ? '-' : `${detail.stock.toLocaleString('ko-KR')}개`}
        </Descriptions.Item>
        <Descriptions.Item label="보관 방법">
          {detail.storage ? STORAGE_LABELS[detail.storage] : '-'}
        </Descriptions.Item>
        <Descriptions.Item label="권장 보관 일수">
          {detail.shelfDays === undefined ? '-' : `${detail.shelfDays}일`}
        </Descriptions.Item>
        <Descriptions.Item label="판매 상태" span={2}>
          {detail.status ? STATUS_LABELS[detail.status] : '-'}
        </Descriptions.Item>
        {detail.productType === 'INGREDIENT' && (
          <Descriptions.Item label="재료 용량" span={2}>
            {detail.ingredient?.unitAmount === undefined
              ? '-'
              : `${detail.ingredient.unitAmount} ${detail.ingredient.unit ?? ''}`}
          </Descriptions.Item>
        )}
        {detail.productType === 'MEALKIT' && (
          <Descriptions.Item label="구성 인분" span={2}>
            {detail.mealkit?.servings === undefined ? '-' : `${detail.mealkit.servings}인분`}
          </Descriptions.Item>
        )}
      </Descriptions>
      <Typography.Title level={3}>판매 정보 수정</Typography.Title>
      <Form<ProductUpdateRequest>
        key={`${detail.price}-${detail.status}`}
        layout="vertical"
        initialValues={{
          price: detail.price,
          status: detail.status === 'STOPPED' ? 'STOPPED' : 'ON_SALE',
        }}
        onFinish={(values) =>
          update.mutate(
            {
              price: values.price,
              // 백엔드도 재고로 상태를 맞추지만(#303), 그 전에도 어긋나지 않게 재고로 정해 보낸다.
              status:
                values.status === 'STOPPED'
                  ? 'STOPPED'
                  : detail.stock === 0
                    ? 'SOLD_OUT'
                    : 'ON_SALE',
            },
            { onSuccess: () => void message.success('상품 정보를 수정했습니다.') },
          )
        }
      >
        <Form.Item
          name="price"
          label="정상가 (원)"
          rules={[{ required: true, type: 'number', min: 1, max: 10000000 }]}
        >
          <InputNumber min={1} max={10000000} precision={0} className={styles.fullWidth} />
        </Form.Item>
        <Form.Item
          name="status"
          label="판매 여부"
          rules={[{ required: true }]}
          extra={
            detail.stock === 0
              ? '재고가 0이라 판매로 저장하면 품절로 표시됩니다.'
              : '판매 중·품절은 재고에 따라 정해집니다.'
          }
        >
          <Select options={SALES_OPTIONS} />
        </Form.Item>
        <Button type="primary" htmlType="submit" loading={update.isPending}>
          저장
        </Button>
      </Form>
    </section>
  );
}

export default function AdminProductDetailPage() {
  const navigate = useNavigate();
  const productId = Number(useParams().productId);

  if (!Number.isSafeInteger(productId) || productId <= 0) {
    return (
      <Result
        status="404"
        title="상품을 찾을 수 없습니다"
        extra={<Button onClick={() => navigate(ROUTES.adminProducts)}>목록으로</Button>}
      />
    );
  }

  return <AdminProductDetail key={productId} productId={productId} />;
}
