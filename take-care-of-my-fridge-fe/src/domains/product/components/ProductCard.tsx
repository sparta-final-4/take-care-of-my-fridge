import { ClockCircleOutlined, ShoppingCartOutlined } from '@ant-design/icons';
import { Button, Flex, Tag, Typography } from 'antd';
import { generatePath, Link } from 'react-router';

import { ROUTES } from '@/app/paths';
import type { ProductListItemResponse } from '@/common/api/clients';
import { FadeImage } from '@/common/components/FadeImage';
import { PRODUCT_TYPE_LABELS } from '@/domains/product/format';
import { SaleCountdown } from '@/domains/sale/components/SaleCountdown';
import { SalePrice } from '@/domains/sale/components/SalePrice';
import { SALE_TYPE_COLORS, SALE_TYPE_LABELS } from '@/domains/sale/format';

import styles from './ProductCard.module.scss';

interface ProductCardProps {
  product: ProductListItemResponse;
  pending: boolean;
  onAddToCart: (productId: number) => void;
  // 오늘의 밀키트처럼 한 줄에 몇 개만 놓을 때 이미지를 가로로 넓게 보여 준다.
  wide?: boolean;
}

export function ProductCard({ product, pending, onAddToCart, wide = false }: ProductCardProps) {
  const { productId, name = '', productType, imageUrl, price, sale, soldOut = false } = product;
  if (productId === undefined) return null;

  const detailPath = generatePath(ROUTES.productDetail, { productId: String(productId) });

  return (
    <article className={styles.card}>
      <Link to={detailPath} className={styles.link}>
        <div className={wide ? styles.thumbWide : styles.thumb}>
          {imageUrl ? (
            <FadeImage src={imageUrl} alt={name} className={styles.image} loading="lazy" />
          ) : (
            <span className={styles.imagePlaceholder} aria-hidden="true" />
          )}
          {soldOut && <span className={styles.soldOutMark}>품절</span>}
          {/* 남은 시간·수량은 이미지 위에 올려, 할인 상품과 일반 상품의 카드 높이를 같게 둔다. */}
          {sale?.endsAt && (
            <span className={styles.saleBand}>
              <span className={styles.saleBandItem}>
                <ClockCircleOutlined aria-label="남은 시간" />{' '}
                <SaleCountdown endsAt={sale.endsAt} compact />
              </span>
              {sale.type === 'FLASH_DROP' && (
                <span className={styles.saleBandItem}>
                  {sale.remainingQuantity}/{sale.limitedQuantity}개 남음
                </span>
              )}
            </span>
          )}
        </div>
        <Flex vertical gap={4} className={styles.info}>
          {/* 할인 구분은 이미지에 따라 안 보일 수 있어 상품 종류 옆에 둔다. */}
          <Flex align="center" gap={4} wrap className={styles.tags}>
            {productType && (
              <Tag
                color={productType === 'MEALKIT' ? 'purple' : 'default'}
                className={styles.typeTag}
              >
                {PRODUCT_TYPE_LABELS[productType]}
              </Tag>
            )}
            {sale?.type && (
              <Tag color={SALE_TYPE_COLORS[sale.type]} className={styles.saleTag}>
                {SALE_TYPE_LABELS[sale.type]}
              </Tag>
            )}
          </Flex>
          <Typography.Text className={styles.name} title={name}>
            {name}
          </Typography.Text>
          {price === undefined ? (
            <Typography.Text strong className={styles.price}>
              -
            </Typography.Text>
          ) : (
            <SalePrice listPrice={price} sale={sale} priceClassName={styles.price} />
          )}
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
