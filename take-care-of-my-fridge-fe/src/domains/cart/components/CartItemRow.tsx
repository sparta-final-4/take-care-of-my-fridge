import { CloseOutlined, MinusOutlined, PlusOutlined } from '@ant-design/icons';
import { Button, Checkbox, Flex, Typography } from 'antd';
import { generatePath, Link } from 'react-router';

import { ROUTES } from '@/app/paths';
import type { SaleResponse } from '@/common/api/clients';
import { FadeImage } from '@/common/components/FadeImage';
import type { CartItemResponse } from '@/domains/cart/api';
import { formatPrice } from '@/domains/cart/format';
import { SaleInfo } from '@/domains/sale/components/SaleInfo';
import { SalePrice } from '@/domains/sale/components/SalePrice';
import { FLASH_DROP_PURCHASE_LIMIT } from '@/domains/sale/format';
import styles from './CartItemRow.module.scss';

// 장바구니 응답은 할인 필드가 따로 와서, 상품 화면과 같은 할인 모양으로 묶어 같은 표시를 쓴다.
const saleOf = (item: CartItemResponse): SaleResponse | undefined =>
  item.saleType === null
    ? undefined
    : {
        type: item.saleType,
        salePrice: item.unitPrice,
        discountRate: item.discountRate ?? undefined,
        endsAt: item.saleEndsAt ?? undefined,
        remainingQuantity: item.flashDropRemaining ?? undefined,
        limitedQuantity: item.flashDropLimit ?? undefined,
      };

interface CartItemRowProps {
  item: CartItemResponse;
  checked: boolean;
  onCheck: (checked: boolean) => void;
  onChangeQuantity: (quantity: number) => void;
  onRemove: () => void;
}

export function CartItemRow({
  item,
  checked,
  onCheck,
  onChangeQuantity,
  onRemove,
}: CartItemRowProps) {
  const { soldOut, quantity } = item;
  const sale = saleOf(item);
  const detailPath = generatePath(ROUTES.productDetail, { productId: String(item.productId) });
  const canIncrease = !(item.saleType === 'FLASH_DROP' && quantity >= FLASH_DROP_PURCHASE_LIMIT);

  return (
    <div className={soldOut ? styles.rowSoldOut : styles.row}>
      <Checkbox
        checked={checked}
        disabled={soldOut}
        onChange={(event) => onCheck(event.target.checked)}
        aria-label={`${item.productName} 선택`}
        className={styles.check}
      />
      {/* 이미지와 이름이 같은 상세로 가므로 키보드 이동은 이름 링크 하나만 받는다. */}
      <Link to={detailPath} tabIndex={-1} aria-hidden="true" className={styles.thumb}>
        {item.imageUrl ? (
          <FadeImage src={item.imageUrl} alt="" className={styles.image} />
        ) : (
          <span className={styles.imagePlaceholder} />
        )}
        {soldOut && <span className={styles.soldOutMark}>품절</span>}
      </Link>
      <Flex vertical gap="small" className={styles.info}>
        <Link to={detailPath} className={styles.nameLink}>
          <Typography.Text strong={!soldOut} title={item.productName} className={styles.name}>
            {item.productName}
          </Typography.Text>
        </Link>
        <SalePrice listPrice={item.listPrice} sale={sale} />
        {sale && <SaleInfo sale={sale} />}
      </Flex>
      <Flex align="center" className={styles.quantity}>
        <Button
          icon={<MinusOutlined />}
          aria-label={`${item.productName} 수량 빼기`}
          disabled={soldOut || quantity <= 1}
          className={styles.touchButton}
          onClick={() => onChangeQuantity(quantity - 1)}
        />
        <span className={styles.quantityValue} aria-label={`${item.productName} 수량`}>
          {quantity}
        </span>
        <Button
          icon={<PlusOutlined />}
          aria-label={`${item.productName} 수량 더하기`}
          disabled={soldOut || !canIncrease}
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
        onClick={onRemove}
        className={`${styles.remove} ${styles.touchButton}`}
      />
    </div>
  );
}
