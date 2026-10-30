import { ClockCircleOutlined } from '@ant-design/icons';
import { Flex, Tag, Typography } from 'antd';
import { generatePath, Link } from 'react-router';

import { ROUTES } from '@/app/paths';
import type { PopularSaleResponse } from '@/common/api/clients';
import { FadeImage } from '@/common/components/FadeImage';
import { formatPrice } from '@/domains/cart/format';
import { SALE_TYPE_COLORS, SALE_TYPE_LABELS } from '@/domains/sale/format';

import { SaleCountdown } from './SaleCountdown';
import styles from './SaleCard.module.scss';

interface SaleCardProps {
  sale: PopularSaleResponse;
}

// 메인 화면 할인 행사 카드. 담기 버튼 없이 카드 전체가 상품 상세로 가는 링크다.
export function SaleCard({ sale: popular }: SaleCardProps) {
  const { productId, name = '', imageUrl, price, sale } = popular;
  if (productId === undefined) return null;

  return (
    <Link
      to={generatePath(ROUTES.productDetail, { productId: String(productId) })}
      className={styles.card}
    >
      <div className={styles.thumb}>
        {imageUrl ? (
          <FadeImage src={imageUrl} alt={name} className={styles.image} loading="lazy" />
        ) : (
          <span className={styles.imagePlaceholder} aria-hidden="true" />
        )}
      </div>
      {/* 카드가 작아 이미지 위에 올리면 사진을 가려, 남은 수량은 할인 구분 옆에, 남은 시간은 그 아래 한 줄에 둔다.
          특가와 번개 드롭 카드의 줄 수가 같아진다. */}
      {sale?.type && (
        <Flex align="center" gap="small" wrap className={styles.saleHead}>
          <Tag color={SALE_TYPE_COLORS[sale.type]} className={styles.typeTag}>
            {SALE_TYPE_LABELS[sale.type]}
          </Tag>
          {sale.type === 'FLASH_DROP' && (
            <span className={styles.saleInfo}>
              {sale.remainingQuantity}/{sale.limitedQuantity}개 남음
            </span>
          )}
        </Flex>
      )}
      {sale?.endsAt && (
        <span className={styles.saleInfo}>
          <ClockCircleOutlined aria-label="남은 시간" />{' '}
          <SaleCountdown endsAt={sale.endsAt} compact />
        </span>
      )}
      <Typography.Text className={styles.name} title={name}>
        {name}
      </Typography.Text>
      {/* 할인율·할인가와 정가를 늘 두 줄로 나눠, 가격 길이가 달라도 카드끼리 줄이 맞게 한다. */}
      {price !== undefined && (
        <div className={styles.price}>
          {sale?.salePrice !== undefined && (
            <Flex align="baseline" gap="small">
              <Typography.Text strong type="danger">
                {sale.discountRate}%
              </Typography.Text>
              <Typography.Text strong>{formatPrice(sale.salePrice)}</Typography.Text>
            </Flex>
          )}
          <Typography.Text delete={sale !== undefined} type="secondary">
            {formatPrice(price)}
          </Typography.Text>
        </div>
      )}
    </Link>
  );
}
