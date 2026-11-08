import { ClockCircleOutlined } from '@ant-design/icons';
import { Flex, Tag, Typography } from 'antd';
import { Link } from 'react-router';

import type { ProductListItemResponse } from '@/common/api/clients';
import { FallbackImage } from '@/common/components/FallbackImage';
import { PRODUCT_TYPE_LABELS } from '@/domains/product/format';
import { PRODUCT_MODAL_STATE, productModalPath } from '@/domains/product/productModal';
import { SaleCountdown } from '@/domains/sale/components/SaleCountdown';
import { SalePrice } from '@/domains/sale/components/SalePrice';
import { SALE_TYPE_COLORS, SALE_TYPE_LABELS } from '@/domains/sale/format';

import styles from './ProductCard.module.scss';

interface ProductCardProps {
  product: ProductListItemResponse;
  // 모달 뒤에 깔릴 상품 목록의 조건(?type=…·?q=…). 상품 목록에서는 지금 주소의 조건, 검색 화면에서는 검색어.
  listSearch: string;
  // 상품 목록 화면 안에서 여는지. 그러면 모달을 닫을 때 뒤로 가기로 목록 기록에 돌아간다.
  fromList?: boolean;
  // 오늘의 밀키트처럼 한 줄에 몇 개만 놓을 때 이미지를 가로로 넓게 보여 준다.
  wide?: boolean;
}

// 레퍼런스(row7seeds.com) 상품 카드: 테두리 없이 큰 사진, 그 아래 가운데 정렬한 종류·이름·가격.
// 사진을 누르면 상품 정보 모달(/products/:productId)이 목록 위에 뜬다.
export function ProductCard({
  product,
  listSearch,
  fromList = false,
  wide = false,
}: ProductCardProps) {
  const { productId, name = '', productType, imageUrl, price, sale, soldOut = false } = product;
  if (productId === undefined) return null;

  return (
    <article className={wide ? `${styles.card} ${styles.wide}` : styles.card}>
      <Link
        to={productModalPath(productId, listSearch)}
        state={fromList ? PRODUCT_MODAL_STATE : undefined}
        className={styles.media}
        aria-label={`${name} 정보 보기`}
      >
        <FallbackImage
          src={imageUrl}
          alt={name}
          kind="product"
          className={styles.image}
          loading="lazy"
        />
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
      </Link>

      {/* 종류·이름·가격 */}
      <Flex vertical align="center" gap={6} className={styles.info}>
        {/* 할인 구분은 이미지에 따라 안 보일 수 있어 상품 종류 옆에 둔다. */}
        <Flex align="center" justify="center" gap={4} wrap className={styles.tags}>
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
          <span className={styles.priceRow}>
            <SalePrice listPrice={price} sale={sale} priceClassName={styles.price} />
          </span>
        )}
      </Flex>
    </article>
  );
}
