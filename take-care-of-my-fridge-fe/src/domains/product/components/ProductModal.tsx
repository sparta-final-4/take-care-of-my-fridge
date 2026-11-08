import { MinusOutlined, PlusOutlined, ShoppingCartOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Button, Modal, Result, Skeleton } from 'antd';
import { useState } from 'react';

import { isNotFound } from '@/common/api/errors';
import { FallbackImage } from '@/common/components/FallbackImage';
import { useAddToCart } from '@/domains/cart/hooks/useAddToCart';
import { formatPrice } from '@/domains/cart/format';
import { STORAGE_LABELS } from '@/domains/fridge/format';
import { PRODUCT_TYPE_LABELS } from '@/domains/product/format';
import { productQueries } from '@/domains/product/queries';
import { SaleInfo } from '@/domains/sale/components/SaleInfo';
import { SalePrice } from '@/domains/sale/components/SalePrice';
import { FLASH_DROP_PURCHASE_LIMIT, SALE_TYPE_LABELS } from '@/domains/sale/format';

import styles from './ProductModal.module.scss';

interface ProductModalProps {
  // 주소의 상품 번호. 없으면 닫힌다. 숫자가 아닌 주소면 NaN이 와서 "찾을 수 없음"을 보여 준다.
  productId: number | undefined;
  onClose: () => void;
}

// 상품 정보 모달(/products/:productId). 상품 설명이 짧아 따로 상세 화면을 두지 않고 목록 위에 띄운다.
// 왼쪽 사진, 오른쪽은 예전 카드 뒷면과 같은 살구색 칸: [종류·보관 칩] → [이름·가격] → [설명] → [정보 상자] → [수량 · 합계 담기].
export function ProductModal({ productId, onClose }: ProductModalProps) {
  // 닫히는 동안에도(주소가 목록으로 바뀐 뒤) 내용이 사라지지 않게 마지막 상품을 남겨 둔다.
  // 숫자가 아닌 주소는 NaN이고 NaN !== NaN이라, 같은 값인지는 Object.is로 본다(아니면 렌더가 끝나지 않는다).
  const [shownId, setShownId] = useState(productId);
  if (productId !== undefined && !Object.is(productId, shownId)) setShownId(productId);

  return (
    <Modal
      open={productId !== undefined}
      onCancel={onClose}
      footer={null}
      centered
      width={880}
      title="상품 정보"
      classNames={{ mask: styles.mask }}
    >
      {/* 다른 상품으로 바뀌면 수량을 처음부터 시작한다. */}
      {shownId !== undefined && <ProductModalBody key={shownId} productId={shownId} />}
    </Modal>
  );
}

function ProductModalBody({ productId }: { productId: number }) {
  const valid = Number.isInteger(productId) && productId > 0;
  const {
    data: product,
    isPending,
    isError,
    error,
    refetch,
  } = useQuery({ ...productQueries.detail(productId), enabled: valid });
  const { addToCart, pendingProductId } = useAddToCart();
  const [quantity, setQuantity] = useState(1);

  // 판매 중지 상품도 백엔드가 404로 응답한다.
  if (!valid || (isError && isNotFound(error))) {
    return (
      <Result
        status="404"
        title="상품을 찾을 수 없습니다"
        subTitle="판매가 끝났거나 없는 상품입니다."
      />
    );
  }
  if (isError) {
    return (
      <Result
        status="error"
        title="상품을 불러오지 못했습니다."
        extra={<Button onClick={() => void refetch()}>다시 시도</Button>}
      />
    );
  }
  if (isPending) return <Skeleton active paragraph={{ rows: 8 }} />;

  const {
    name = '',
    imageUrl,
    productType,
    price,
    storage,
    shelfDays,
    ingredient,
    mealkit,
    description,
    sale,
    soldOut = false,
  } = product;
  const unitPrice = sale?.salePrice ?? price;
  // 번개 드롭은 1인 1개
  const maxQuantity = sale?.type === 'FLASH_DROP' ? FLASH_DROP_PURCHASE_LIMIT : undefined;
  // 재료는 용량, 밀키트는 인분
  const amount = ingredient?.unitAmount
    ? { label: '용량', value: `${ingredient.unitAmount}${ingredient.unit ?? ''}` }
    : mealkit?.servings
      ? { label: '구성', value: `${mealkit.servings}인분` }
      : undefined;
  const specs = [
    ...(amount ? [amount] : []),
    ...(storage ? [{ label: '보관', value: STORAGE_LABELS[storage] }] : []),
    ...(shelfDays ? [{ label: '권장 보관', value: `받은 날부터 ${shelfDays}일` }] : []),
  ];

  return (
    <div className={styles.layout}>
      <div className={styles.media}>
        <FallbackImage src={imageUrl} alt={name} kind="product" className={styles.image} />
        {soldOut && <span className={styles.soldOutMark}>품절</span>}
      </div>

      <div className={styles.panel}>
        <div className={styles.chips}>
          {productType && <span className={styles.chip}>{PRODUCT_TYPE_LABELS[productType]}</span>}
          {storage && <span className={styles.chip}>{STORAGE_LABELS[storage]} 보관</span>}
          {sale?.type && <span className={styles.chip}>{SALE_TYPE_LABELS[sale.type]}</span>}
        </div>

        <div className={styles.heading}>
          <h3 className={styles.name}>{name}</h3>
          {price !== undefined && (
            <span className={styles.priceRow}>
              <SalePrice listPrice={price} sale={sale} priceClassName={styles.price} />
            </span>
          )}
          {sale && <SaleInfo sale={sale} />}
        </div>

        <p className={styles.description}>{description || '아직 상품 설명이 없어요.'}</p>

        {specs.length > 0 && (
          <dl className={styles.specs}>
            {specs.map((spec) => (
              <div key={spec.label} className={styles.spec}>
                <dt>{spec.label}</dt>
                <dd>{spec.value}</dd>
              </div>
            ))}
          </dl>
        )}

        <div className={styles.actions}>
          {!soldOut && (
            <div className={styles.stepper} role="group" aria-label={`${name} 수량`}>
              <Button
                type="text"
                icon={<MinusOutlined />}
                aria-label="수량 줄이기"
                disabled={quantity <= 1}
                onClick={() => setQuantity((value) => Math.max(1, value - 1))}
                className={styles.stepButton}
              />
              <span className={styles.quantity} aria-live="polite">
                {quantity}
              </span>
              <Button
                type="text"
                icon={<PlusOutlined />}
                aria-label="수량 늘리기"
                disabled={maxQuantity !== undefined && quantity >= maxQuantity}
                onClick={() => setQuantity((value) => value + 1)}
                className={styles.stepButton}
              />
            </div>
          )}
          <Button
            type="primary"
            icon={<ShoppingCartOutlined />}
            disabled={soldOut}
            loading={pendingProductId === productId}
            onClick={() => addToCart(productId, quantity)}
            aria-label={soldOut ? `${name} 품절` : `${name} ${quantity}개 장바구니 담기`}
            className={styles.cartButton}
          >
            {soldOut
              ? '품절'
              : unitPrice !== undefined
                ? `${formatPrice(unitPrice * quantity)} 담기`
                : '담기'}
          </Button>
        </div>
      </div>
    </div>
  );
}
