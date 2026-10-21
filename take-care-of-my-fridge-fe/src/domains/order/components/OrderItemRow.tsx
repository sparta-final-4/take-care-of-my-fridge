import { Flex, Tag, Typography } from 'antd';

import type { OrderItemResponse } from '@/common/api/clients';
import { formatPrice, SALE_TYPE_LABELS } from '@/domains/cart/format';
import styles from './OrderItemRow.module.scss';

interface OrderItemRowProps {
  item: OrderItemResponse;
}

// 가격은 주문할 때 저장한 값이다. 지금 상품 가격은 쓰지 않는다.
export function OrderItemRow({ item }: OrderItemRowProps) {
  const name = item.productName ?? '';
  const saleType = item.saleType === 'NONE' ? undefined : item.saleType;

  return (
    <div className={styles.row}>
      <div className={styles.thumb}>
        {item.productImageUrl ? (
          <img src={item.productImageUrl} alt={name} className={styles.image} />
        ) : (
          <span className={styles.imagePlaceholder} aria-hidden="true" />
        )}
      </div>
      <Flex vertical gap="small" className={styles.info}>
        {saleType && <Tag className={styles.saleTag}>{SALE_TYPE_LABELS[saleType]}</Tag>}
        <Typography.Text strong title={name} className={styles.name}>
          {name}
        </Typography.Text>
      </Flex>
      <Typography.Text className={styles.quantity}>{item.quantity ?? 0}개</Typography.Text>
      <Flex vertical align="flex-end" className={styles.price}>
        {saleType && (
          <Typography.Text delete type="secondary" className={styles.listPrice}>
            {formatPrice(item.listPrice ?? 0)}
          </Typography.Text>
        )}
        <Typography.Text>{formatPrice(item.unitPrice ?? 0)}</Typography.Text>
      </Flex>
      <Typography.Text strong className={styles.total}>
        {formatPrice(item.lineAmount ?? 0)}
      </Typography.Text>
    </div>
  );
}
