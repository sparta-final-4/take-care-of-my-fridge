import { CloseOutlined, MinusOutlined, PlusOutlined } from '@ant-design/icons';
import { Button, Checkbox, Flex, InputNumber, Space, Typography } from 'antd';
import { useState } from 'react';
import { generatePath, Link } from 'react-router';

import { ROUTES } from '@/app/paths';
import { FallbackImage } from '@/common/components/FallbackImage';
import type { CartItemResponse } from '@/domains/cart/api';
import { formatPrice } from '@/domains/cart/format';
import { SaleInfo } from '@/domains/sale/components/SaleInfo';
import { SalePrice } from '@/domains/sale/components/SalePrice';
import { FLASH_DROP_PURCHASE_LIMIT } from '@/domains/sale/format';
import styles from './CartItemRow.module.scss';

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
  const sale = item.sale ?? undefined;
  const detailPath = generatePath(ROUTES.productDetail, { productId: String(item.productId) });
  const maxQuantity = item.sale?.type === 'FLASH_DROP' ? FLASH_DROP_PURCHASE_LIMIT : undefined;
  const canIncrease = maxQuantity === undefined || quantity < maxQuantity;
  // 담아 둔 뒤 번개 드롭으로 바뀌었으면 한 번에 구매 한도까지 내린다.
  const decreasedQuantity = maxQuantity ?? quantity - 1;
  const [draft, setDraft] = useState<number | null>(quantity);
  const [syncedQuantity, setSyncedQuantity] = useState(quantity);
  // 변경이 반영되거나 실패해서 수량이 바뀌면 입력칸도 그 값으로 맞춘다.
  if (quantity !== syncedQuantity) {
    setSyncedQuantity(quantity);
    setDraft(quantity);
  }

  // 입력칸은 칸을 벗어날 때 한 번만 보낸다. 비우거나 그대로면 원래 수량으로 되돌린다.
  const commitDraft = () => {
    if (draft === null || draft === quantity) {
      setDraft(quantity);
      return;
    }
    onChangeQuantity(draft);
  };

  return (
    <div className={soldOut ? styles.rowSoldOut : styles.row}>
      <Checkbox
        checked={checked}
        disabled={soldOut}
        onChange={(event) => onCheck(event.target.checked)}
        aria-label={`${item.productName} 선택`}
        className={styles.check}
      />
      {/* 좁은 칸에서는 이미지와 수량 칸을 한 묶음으로 세로로 쌓고, 넓은 칸에서는 묶음을 풀어 각자 칸에 둔다. */}
      <div className={styles.media}>
        {/* 이미지와 이름이 같은 상세로 가므로 키보드 이동은 이름 링크 하나만 받는다. */}
        <Link to={detailPath} tabIndex={-1} aria-hidden="true" className={styles.thumb}>
          <FallbackImage
            src={item.imageUrl}
            alt=""
            kind="product"
            size="small"
            className={styles.image}
          />
          {soldOut && <span className={styles.soldOutMark}>품절</span>}
        </Link>
        <Space.Compact className={styles.quantity}>
          <Button
            type="text"
            icon={<MinusOutlined />}
            aria-label={`${item.productName} 수량 빼기`}
            disabled={soldOut || quantity <= 1}
            className={styles.stepButton}
            onClick={() => onChangeQuantity(decreasedQuantity)}
          />
          <InputNumber
            min={1}
            max={maxQuantity}
            precision={0}
            controls={false}
            variant="borderless"
            value={draft}
            disabled={soldOut}
            aria-label={`${item.productName} 수량`}
            className={styles.quantityInput}
            onChange={setDraft}
            onBlur={commitDraft}
            onPressEnter={(event) => event.currentTarget.blur()}
          />
          <Button
            type="text"
            icon={<PlusOutlined />}
            aria-label={`${item.productName} 수량 더하기`}
            disabled={soldOut || !canIncrease}
            className={styles.stepButton}
            onClick={() => onChangeQuantity(quantity + 1)}
          />
        </Space.Compact>
      </div>
      {/* 상품 정보와 금액·삭제를 한 묶음으로 둔다. 금액·삭제는 묶음 맨 아래 줄이다. */}
      <div className={styles.content}>
        <Flex vertical gap="small" className={styles.info}>
          <Link to={detailPath} className={styles.nameLink}>
            <Typography.Text strong={!soldOut} title={item.productName} className={styles.name}>
              {item.productName}
            </Typography.Text>
          </Link>
          <SalePrice listPrice={item.listPrice} sale={sale} priceClassName={styles.price} />
          {sale && <SaleInfo sale={sale} />}
        </Flex>
        <div className={styles.footer}>
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
      </div>
    </div>
  );
}
