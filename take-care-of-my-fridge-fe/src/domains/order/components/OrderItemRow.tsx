import { Flex, Tag, Typography } from 'antd';
import { generatePath, Link } from 'react-router';

import { ROUTES } from '@/app/paths';
import type { OrderDetailItemResponse } from '@/common/api/clients';
import { FadeImage } from '@/common/components/FadeImage';
import { formatPrice } from '@/domains/cart/format';
import type { OrderItemRefundState } from '@/domains/refund/format';
import { SALE_TYPE_COLORS, SALE_TYPE_LABELS } from '@/domains/sale/format';
import styles from './OrderItemRow.module.scss';

interface OrderItemRowProps {
  item: OrderDetailItemResponse;
  // 주문 상세에서만 환불 태그를 보인다.
  refundState?: OrderItemRefundState;
  // 주문 상세에서만 이미지·이름을 상품 상세로 연결한다. 결제 전 주문 확인 화면에서는 결제 흐름을 벗어나지 않게 연결하지 않는다.
  linkToProduct?: boolean;
}

// 상품이 한 행으로 펼쳐지는 md부터 보인다. 상품 행과 같은 격자라 칸이 맞는다.
export function OrderItemHeader() {
  return (
    <div className={`${styles.row} ${styles.head}`} aria-hidden="true">
      <Typography.Text strong className={styles.headProduct}>
        상품
      </Typography.Text>
      <Typography.Text strong className={styles.quantity}>
        수량
      </Typography.Text>
      <Typography.Text strong className={`${styles.price} ${styles.headRight}`}>
        단가
      </Typography.Text>
      <Typography.Text strong className={styles.total}>
        금액
      </Typography.Text>
    </div>
  );
}

// 가격은 주문할 때 저장한 값이다. 지금 상품 가격은 쓰지 않는다.
// 구매 확정된 상품은 확정 수량(주문 수량 - 환불 수량)과 그 수량의 금액을 보인다. 확정 수량이 0이면 흐리게 보인다.
export function OrderItemRow({ item, linkToProduct = false, refundState }: OrderItemRowProps) {
  const name = item.productName ?? '';
  const saleType = item.saleType === 'NONE' ? undefined : item.saleType;
  const detailPath =
    linkToProduct && item.productId !== undefined
      ? generatePath(ROUTES.productDetail, { productId: String(item.productId) })
      : undefined;

  const confirmed = item.confirmedQuantity !== undefined && item.confirmedQuantity !== null;
  const quantity = confirmed ? item.confirmedQuantity : item.quantity;
  const lineAmount = confirmed
    ? (item.confirmedQuantity ?? 0) * (item.unitPrice ?? 0)
    : item.lineAmount;

  const image = item.productImageUrl ? (
    <FadeImage src={item.productImageUrl} alt={detailPath ? '' : name} className={styles.image} />
  ) : (
    <span className={styles.imagePlaceholder} aria-hidden="true" />
  );
  const nameText = (
    <Typography.Text strong title={name} className={styles.name}>
      {name}
    </Typography.Text>
  );

  return (
    <div className={quantity === 0 ? styles.rowEmpty : styles.row}>
      {/* 이미지와 이름이 같은 상세로 가므로 키보드 이동은 이름 링크 하나만 받는다. */}
      {detailPath ? (
        <Link to={detailPath} tabIndex={-1} aria-hidden="true" className={styles.thumb}>
          {image}
        </Link>
      ) : (
        <div className={styles.thumb}>{image}</div>
      )}
      <Flex vertical gap="small" className={styles.info}>
        <Flex align="center" gap="small" className={styles.nameLine}>
          {detailPath ? (
            <Link to={detailPath} className={styles.nameLink}>
              {nameText}
            </Link>
          ) : (
            nameText
          )}
          {saleType && (
            <Tag color={SALE_TYPE_COLORS[saleType]} className={styles.saleTag}>
              {SALE_TYPE_LABELS[saleType]}
            </Tag>
          )}
          {refundState === 'full' && <Tag className={styles.refundTag}>환불</Tag>}
          {refundState === 'partial' && <Tag className={styles.refundTag}>부분 환불</Tag>}
        </Flex>
      </Flex>
      <Typography.Text className={styles.quantity}>{quantity ?? 0}개</Typography.Text>
      <Flex vertical align="flex-end" className={styles.price}>
        {saleType && (
          <Typography.Text delete type="secondary" className={styles.listPrice}>
            {formatPrice(item.listPrice ?? 0)}
          </Typography.Text>
        )}
        <Typography.Text>{formatPrice(item.unitPrice ?? 0)}</Typography.Text>
      </Flex>
      <Typography.Text strong className={styles.total}>
        {formatPrice(lineAmount ?? 0)}
      </Typography.Text>
    </div>
  );
}
