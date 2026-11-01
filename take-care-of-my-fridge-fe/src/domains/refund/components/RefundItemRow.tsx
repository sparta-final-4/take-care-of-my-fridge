import { CheckOutlined, LockOutlined, MinusOutlined, PlusOutlined } from '@ant-design/icons';
import { Button, Checkbox, Flex, Tag, Typography } from 'antd';

import type { RefundableItemResponse } from '@/common/api/clients';
import { FallbackImage } from '@/common/components/FallbackImage';
import { formatPrice } from '@/domains/cart/format';
import { SALE_TYPE_COLORS, SALE_TYPE_LABELS } from '@/domains/sale/format';
import { getOrderItemRefundState, getRefundRowState } from '@/domains/refund/format';

import styles from './RefundItemRow.module.scss';

interface RefundItemRowProps {
  item: RefundableItemResponse;
  // 주문 상세의 정가. 특가·번개 드롭 상품만 단가 위에 취소선으로 보여 준다.
  listPrice?: number;
  // 선택하지 않은 상품은 undefined. 선택하면 남은 수량을 모두 채우고, 수량 버튼으로 줄인다.
  quantity?: number;
  onChange: (quantity: number | undefined) => void;
}

// 머리글은 상품이 한 행으로 펼쳐지는 xl부터 보인다. 상품 행과 같은 격자라 칸이 맞는다.
export function RefundItemHeader() {
  return (
    <div className={`${styles.row} ${styles.head}`} aria-hidden="true">
      <Typography.Text strong className={styles.headCheck}>
        선택
      </Typography.Text>
      <Typography.Text strong className={styles.headProduct}>
        상품
      </Typography.Text>
      <Typography.Text strong className={styles.quantity}>
        수량
      </Typography.Text>
      <Typography.Text strong className={styles.price}>
        단가
      </Typography.Text>
      <Typography.Text strong className={styles.amount}>
        금액
      </Typography.Text>
      <Typography.Text strong className={styles.stepper}>
        환불 수량
      </Typography.Text>
      <Typography.Text strong className={styles.status}>
        상태
      </Typography.Text>
    </div>
  );
}

export function RefundItemRow({ item, listPrice, quantity, onChange }: RefundItemRowProps) {
  const name = item.productName ?? '';
  const state = getRefundRowState(item);
  const refundState = getOrderItemRefundState(item);
  const refundable = state === 'refundable';
  const orderedQuantity = item.orderedQuantity ?? 0;
  const refundedQuantity = item.refundedQuantity ?? 0;
  const refundableQuantity = item.refundableQuantity ?? 0;
  const unitPrice = item.unitPrice ?? 0;
  const saleType = item.saleType === 'NONE' ? undefined : item.saleType;
  const selected = quantity !== undefined;

  return (
    <div className={refundable ? styles.row : styles.rowDisabled}>
      <span className={styles.check}>
        {refundable ? (
          <Checkbox
            checked={selected}
            onChange={(event) => onChange(event.target.checked ? refundableQuantity : undefined)}
            aria-label={`${name} 선택`}
          />
        ) : state === 'refunded' ? (
          <CheckOutlined aria-label="환불 완료" />
        ) : (
          <LockOutlined aria-label="환불 불가" />
        )}
      </span>
      <div className={styles.thumb}>
        <FallbackImage
          src={item.productImageUrl}
          alt={name}
          kind="product"
          size="small"
          className={styles.image}
        />
      </div>
      <Flex vertical gap="small" className={styles.info}>
        <Typography.Text strong title={name} className={styles.name}>
          {name}
        </Typography.Text>
        {(saleType || refundState === 'partial' || refundState === 'full') && (
          <Flex gap="small" wrap>
            {saleType && (
              <Tag color={SALE_TYPE_COLORS[saleType]} className={styles.saleTag}>
                {SALE_TYPE_LABELS[saleType]}
              </Tag>
            )}
            {refundState === 'full' && <Tag className={styles.tag}>환불</Tag>}
            {refundState === 'partial' && (
              <Tag className={styles.tag}>부분 환불 {refundedQuantity}개</Tag>
            )}
          </Flex>
        )}
      </Flex>
      <div className={styles.figures}>
        <Typography.Text className={styles.quantity}>{orderedQuantity}개</Typography.Text>
        <Flex vertical align="flex-end" className={styles.price}>
          {saleType && listPrice !== undefined && (
            <Typography.Text delete type="secondary" className={styles.listPrice}>
              {formatPrice(listPrice)}
            </Typography.Text>
          )}
          <Typography.Text>{formatPrice(unitPrice)}</Typography.Text>
        </Flex>
        <Typography.Text strong className={styles.amount}>
          {formatPrice(unitPrice * orderedQuantity)}
        </Typography.Text>
      </div>
      <div className={styles.actions}>
        <Flex align="center" className={styles.stepper}>
          {refundable && (
            <>
              <Button
                icon={<MinusOutlined />}
                aria-label={`${name} 환불 수량 빼기`}
                disabled={!selected || (quantity ?? 0) <= 1}
                className={styles.touchButton}
                onClick={() => onChange((quantity ?? 1) - 1)}
              />
              <span className={styles.stepperValue} aria-label={`${name} 환불 수량`}>
                {quantity ?? 0}
              </span>
              <Button
                icon={<PlusOutlined />}
                aria-label={`${name} 환불 수량 더하기`}
                disabled={(quantity ?? 0) >= refundableQuantity}
                className={styles.touchButton}
                onClick={() => onChange((quantity ?? 0) + 1)}
              />
            </>
          )}
        </Flex>
        <span className={styles.status}>
          {state === 'refundable' && (
            <Typography.Text strong>환불 가능 {refundableQuantity}개</Typography.Text>
          )}
          {state === 'refunded' && <Typography.Text type="secondary">환불 완료</Typography.Text>}
          {state === 'saleBlocked' && saleType && (
            <Tag icon={<LockOutlined />} className={`${styles.tag} ${styles.blockedTag}`}>
              {SALE_TYPE_LABELS[saleType]} · 환불 불가
            </Tag>
          )}
        </span>
      </div>
    </div>
  );
}
