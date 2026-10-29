import { useQuery } from '@tanstack/react-query';
import { Button, Descriptions, Flex, Result, Spin, Typography } from 'antd';
import { useNavigate, useParams } from 'react-router';

import { ROUTES } from '@/app/paths';
import { productQueries } from '@/domains/product/queries';

import styles from './AdminProductDetailPage.module.scss';

const TYPE_LABELS = { INGREDIENT: '재료', MEALKIT: '밀키트' };
const STATUS_LABELS = { ON_SALE: '판매 중', SOLD_OUT: '품절', STOPPED: '판매 중지' };
const STORAGE_LABELS = { ROOM_TEMP: '상온', REFRIGERATED: '냉장', FROZEN: '냉동' };
function AdminProductDetail({ productId }: { productId: number }) {
  const navigate = useNavigate();
  const { data, isPending, isError, refetch } = useQuery(productQueries.adminDetail(productId));

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
