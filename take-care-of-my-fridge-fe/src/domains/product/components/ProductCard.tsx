import { ShoppingCartOutlined } from '@ant-design/icons';
import { Button, Flex, Tag, Typography } from 'antd';
import { generatePath, Link } from 'react-router';

import { ROUTES } from '@/app/paths';
import type { ProductListItemResponse } from '@/common/api/clients';
import { formatPrice } from '@/domains/cart/format';
import { PRODUCT_TYPE_LABELS } from '@/domains/product/format';

import styles from './ProductCard.module.scss';

interface ProductCardProps {
  product: ProductListItemResponse;
  pending: boolean;
  onAddToCart: (productId: number) => void;
  // 오늘의 밀키트처럼 한 줄에 몇 개만 놓을 때 이미지를 가로로 넓게 보여 준다.
  wide?: boolean;
}

export function ProductCard({ product, pending, onAddToCart, wide = false }: ProductCardProps) {
  const { productId, name = '', productType, imageUrl, price, status } = product;
  if (productId === undefined) return null;

  const soldOut = status === 'SOLD_OUT';
  const detailPath = generatePath(ROUTES.productDetail, { productId: String(productId) });

  return (
    <article className={styles.card}>
      <Link to={detailPath} className={styles.link}>
        <div className={wide ? styles.thumbWide : styles.thumb}>
          {imageUrl ? (
            <img src={imageUrl} alt={name} className={styles.image} loading="lazy" />
          ) : (
            <span className={styles.imagePlaceholder} aria-hidden="true" />
          )}
          {soldOut && <span className={styles.soldOutMark}>품절</span>}
        </div>
        <Flex vertical gap={4} className={styles.info}>
          {productType && (
            <Tag
              color={productType === 'MEALKIT' ? 'purple' : 'default'}
              className={styles.typeTag}
            >
              {PRODUCT_TYPE_LABELS[productType]}
            </Tag>
          )}
          <Typography.Text className={styles.name} title={name}>
            {name}
          </Typography.Text>
          <Typography.Text strong className={styles.price}>
            {price === undefined ? '-' : formatPrice(price)}
          </Typography.Text>
        </Flex>
      </Link>
      <Button
        block
        icon={<ShoppingCartOutlined />}
        disabled={soldOut}
        loading={pending}
        onClick={() => onAddToCart(productId)}
        aria-label={soldOut ? `${name} 품절` : `${name} 장바구니 담기`}
        className={styles.cartButton}
      >
        {soldOut ? '품절' : '장바구니 담기'}
      </Button>
    </article>
  );
}
