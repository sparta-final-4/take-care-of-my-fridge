import { CloseOutlined, MinusOutlined, PlusOutlined } from '@ant-design/icons';
import { Button, Checkbox, Flex, Tag, Typography } from 'antd';

import type { CartItemResponse } from '@/domains/cart/api';
import { SaleCountdown } from '@/domains/cart/components/SaleCountdown';
import { formatPrice, SALE_TYPE_LABELS } from '@/domains/cart/format';
import styles from './CartItemRow.module.scss';

// 번개 드롭은 1인 1개라 수량을 올릴 수 없다.
const FLASH_DROP_MAX_QUANTITY = 1;

interface CartItemRowProps {
  item: CartItemResponse;
  checked: boolean;
  pending: boolean;
  onCheck: (checked: boolean) => void;
  onChangeQuantity: (quantity: number) => void;
  onRemove: () => void;
}

export function CartItemRow({
  item,
  checked,
  pending,
  onCheck,
  onChangeQuantity,
  onRemove,
}: CartItemRowProps) {
  const { soldOut, quantity } = item;
  const discounted = item.saleType !== null;
  const canIncrease = !(item.saleType === 'FLASH_DROP' && quantity >= FLASH_DROP_MAX_QUANTITY);

  return (
    <div className={soldOut ? styles.rowSoldOut : styles.row}>
      <Checkbox
        checked={checked}
        disabled={soldOut}
        onChange={(event) => onCheck(event.target.checked)}
        aria-label={`${item.productName} 선택`}
        className={styles.check}
      />
      <div className={styles.thumb}>
        {item.imageUrl ? (
          <img src={item.imageUrl} alt={item.productName} className={styles.image} />
        ) : (
          <span className={styles.imagePlaceholder} aria-hidden="true" />
        )}
        {soldOut && <span className={styles.soldOutMark}>품절</span>}
      </div>
      <Flex vertical gap="small" className={styles.info}>
        {item.saleType && <Tag className={styles.saleTag}>{SALE_TYPE_LABELS[item.saleType]}</Tag>}
        <Typography.Text strong={!soldOut} title={item.productName} className={styles.name}>
          {item.productName}
        </Typography.Text>
        <Flex align="baseline" gap="small" wrap>
          {discounted && (
            <Typography.Text delete type="secondary" className={styles.listPrice}>
              {formatPrice(item.listPrice)}
            </Typography.Text>
          )}
          <Typography.Text strong={!soldOut}>{formatPrice(item.unitPrice)}</Typography.Text>
        </Flex>
        {item.saleEndsAt && !soldOut && <SaleCountdown endsAt={item.saleEndsAt} />}
        {item.flashDropRemaining !== null && !soldOut && (
          <Typography.Text type="secondary">
            재고 {item.flashDropRemaining} / {item.flashDropLimit}개
          </Typography.Text>
        )}
      </Flex>
      <Flex align="center" className={styles.quantity}>
        <Button
          icon={<MinusOutlined />}
          aria-label={`${item.productName} 수량 빼기`}
          disabled={soldOut || pending || quantity <= 1}
          className={styles.touchButton}
          onClick={() => onChangeQuantity(quantity - 1)}
        />
        <span className={styles.quantityValue} aria-label={`${item.productName} 수량`}>
          {quantity}
        </span>
        <Button
          icon={<PlusOutlined />}
          aria-label={`${item.productName} 수량 더하기`}
          disabled={soldOut || pending || !canIncrease}
          className={styles.touchButton}
          onClick={() => onChangeQuantity(quantity + 1)}
        />
      </Flex>
      <Typography.Text strong className={styles.total}>
        {soldOut ? '-' : formatPrice(item.unitPrice * quantity)}
      </Typography.Text>
      <Button
        type="text"
        icon={<CloseOutlined />}
        aria-label={`${item.productName} 삭제`}
        disabled={pending}
        onClick={onRemove}
        className={`${styles.remove} ${styles.touchButton}`}
      />
    </div>
  );
}
