import { ShoppingCartOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import {
  Button,
  Descriptions,
  Divider,
  Flex,
  InputNumber,
  Result,
  Skeleton,
  Tag,
  Typography,
} from 'antd';
import { useState } from 'react';
import { useNavigate } from 'react-router';

import { ROUTES } from '@/app/paths';
import { isNotFound } from '@/common/api/errors';
import { useAddToCart } from '@/domains/cart/hooks/useAddToCart';
import { formatPrice } from '@/domains/cart/format';
import { STORAGE_LABELS } from '@/domains/fridge/format';
import { PRODUCT_TYPE_LABELS } from '@/domains/product/format';
import { productQueries } from '@/domains/product/queries';

import styles from './ProductDetailSection.module.scss';

interface ProductDetailSectionProps {
  productId: number;
}

export function ProductDetailSection({ productId }: ProductDetailSectionProps) {
  const navigate = useNavigate();
  const {
    data: product,
    isPending,
    isError,
    error,
    refetch,
  } = useQuery(productQueries.detail(productId));
  const { addToCart, pendingProductId } = useAddToCart();
  const [quantity, setQuantity] = useState(1);

  const handleGoList = () => navigate(ROUTES.products);

  if (isPending) return <Skeleton active paragraph={{ rows: 8 }} />;
  if (isError) {
    // 판매 중지 상품도 백엔드가 404로 응답한다.
    return isNotFound(error) ? (
      <Result
        status="404"
        title="상품을 찾을 수 없습니다"
        subTitle="판매가 끝났거나 없는 상품입니다."
        extra={<Button onClick={handleGoList}>목록으로</Button>}
      />
    ) : (
      <Result
        status="error"
        title="상품을 불러오지 못했습니다."
        extra={<Button onClick={() => refetch()}>다시 시도</Button>}
      />
    );
  }

  const {
    name = '',
    imageUrl,
    productType,
    price,
    storage,
    shelfDays,
    ingredient,
    mealkit,
  } = product;
  const soldOut = product.status === 'SOLD_OUT';

  // 재료는 용량, 밀키트는 인분을 보여 준다. 상세 행이 없으면 줄을 뺀다.
  const amountItem = ingredient?.unitAmount
    ? { key: 'amount', label: '용량', children: `${ingredient.unitAmount}${ingredient.unit ?? ''}` }
    : mealkit?.servings
      ? { key: 'amount', label: '구성', children: `${mealkit.servings}인분` }
      : undefined;
  const infoItems = [
    ...(amountItem ? [amountItem] : []),
    ...(storage ? [{ key: 'storage', label: '보관', children: STORAGE_LABELS[storage] }] : []),
    ...(shelfDays
      ? [{ key: 'shelfDays', label: '권장 보관', children: `받은 날부터 ${shelfDays}일` }]
      : []),
  ];

  return (
    <div className={styles.layout}>
      <div className={styles.thumb}>
        {imageUrl ? (
          <img src={imageUrl} alt={name} className={styles.image} />
        ) : (
          <span className={styles.imagePlaceholder} aria-hidden="true" />
        )}
        {soldOut && <span className={styles.soldOutMark}>품절</span>}
      </div>

      <Flex vertical gap="middle" className={styles.info}>
        <Flex gap="small" wrap>
          {productType && (
            <Tag color={productType === 'MEALKIT' ? 'purple' : 'default'} className={styles.tag}>
              {PRODUCT_TYPE_LABELS[productType]}
            </Tag>
          )}
          {soldOut && <Tag className={styles.tag}>품절</Tag>}
        </Flex>
        <Typography.Title level={3} className={styles.name}>
          {name}
        </Typography.Title>
        <Typography.Text strong className={styles.price}>
          {price === undefined ? '-' : formatPrice(price)}
        </Typography.Text>

        {infoItems.length > 0 && <Descriptions column={1} size="small" items={infoItems} />}
        {product.description && (
          <Typography.Paragraph type="secondary" className={styles.description}>
            {product.description}
          </Typography.Paragraph>
        )}

        <Divider className={styles.divider} />

        <Flex align="center" justify="space-between" gap="middle" wrap>
          <Flex align="center" gap="small">
            <Typography.Text id="product-quantity-label">수량</Typography.Text>
            <InputNumber
              min={1}
              precision={0}
              value={quantity}
              disabled={soldOut}
              onChange={(value) => setQuantity(value ?? 1)}
              aria-labelledby="product-quantity-label"
            />
          </Flex>
          {price !== undefined && (
            <Typography.Text>
              합계 <Typography.Text strong>{formatPrice(price * quantity)}</Typography.Text>
            </Typography.Text>
          )}
        </Flex>

        <Flex gap="small" className={styles.actions}>
          <Button size="large" onClick={handleGoList} className={styles.secondaryAction}>
            목록으로
          </Button>
          <Button
            type="primary"
            size="large"
            icon={<ShoppingCartOutlined />}
            disabled={soldOut}
            loading={pendingProductId === productId}
            onClick={() => addToCart(productId, quantity)}
            className={styles.primaryAction}
          >
            {soldOut ? '품절' : '장바구니 담기'}
          </Button>
        </Flex>
      </Flex>
    </div>
  );
}
