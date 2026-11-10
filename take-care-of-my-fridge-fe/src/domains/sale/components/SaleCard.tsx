import { ClockCircleOutlined } from '@ant-design/icons';
import { Flex, Tag, Typography } from 'antd';
import { generatePath, Link } from 'react-router';

import { ROUTES } from '@/app/paths';
import type { PopularSaleResponse } from '@/common/api/clients';
import { FallbackImage } from '@/common/components/FallbackImage';
import { PRODUCT_TYPE_COLORS, PRODUCT_TYPE_LABELS } from '@/domains/product/format';
import { SALE_TYPE_COLORS, SALE_TYPE_LABELS } from '@/domains/sale/format';

import { SaleCountdown } from './SaleCountdown';
import { SalePrice } from './SalePrice';
import styles from './SaleCard.module.scss';

interface SaleCardProps {
  sale: PopularSaleResponse;
}

// 메인 화면 할인 행사 카드. 상품 목록·오늘의 밀키트의 상품 카드와 같은 구조(사진 위 남은 시간·수량 띠, 아래 태그 → 이름 → 가격)다.
// 담기 버튼 없이 카드 전체가 상품 상세로 가는 링크다.
export function SaleCard({ sale: popular }: SaleCardProps) {
  const { productId, name = '', productType, imageUrl, price, sale } = popular;
  if (productId === undefined) return null;

  return (
    <Link
      to={generatePath(ROUTES.productDetail, { productId: String(productId) })}
      className={styles.card}
    >
      <div className={styles.thumb}>
        <FallbackImage
          src={imageUrl}
          alt={name}
          kind="product"
          className={styles.image}
          loading="lazy"
        />
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
      <Flex vertical align="center" gap={6} className={styles.info}>
        <Flex align="center" justify="center" gap={4} wrap className={styles.tags}>
          {productType && (
            <Tag color={PRODUCT_TYPE_COLORS[productType]} className={styles.typeTag}>
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
        {price !== undefined && (
          <span className={styles.priceRow}>
            <SalePrice listPrice={price} sale={sale} priceClassName={styles.price} />
          </span>
        )}
      </Flex>
    </Link>
  );
}
