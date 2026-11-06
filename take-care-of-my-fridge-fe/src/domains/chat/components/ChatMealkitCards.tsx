import { Typography } from 'antd';
import { useId } from 'react';
import { generatePath, Link } from 'react-router';

import { ROUTES } from '@/app/paths';
import { FallbackImage } from '@/common/components/FallbackImage';
import { formatPrice } from '@/domains/cart/format';

import type { ChatRecommendedMealkit } from '../recommendations';
import { ChatCartButton } from './ChatCartButton';
import styles from './ChatMealkitCards.module.scss';

interface ChatMealkitCardsProps {
  mealkits: ChatRecommendedMealkit[];
  // 장바구니 아이콘을 눌렀을 때 (상품 1개)
  onAdd: (productId: number) => void;
  // 담는 중인 상품 (그 버튼에 로딩을 보여 준다)
  pendingProductId?: number;
  // 카드를 눌러 상품 상세로 갈 때 (모바일 전체 화면 채팅창은 닫는다)
  onNavigate?: () => void;
}

// 상담 답의 밀키트 카드. 레시피 카드 아래 "밀키트로 간편하게" 묶음으로 보여 준다.
// 카드를 누르면 상품 상세로 가고, 장바구니 아이콘은 상담 창 안에서 1개를 담는다 (품절이면 막는다).
export function ChatMealkitCards({
  mealkits,
  onAdd,
  pendingProductId,
  onNavigate,
}: ChatMealkitCardsProps) {
  const headingId = useId();
  if (mealkits.length === 0) return null;
  return (
    <section className={styles.section} aria-labelledby={headingId}>
      <Typography.Text strong id={headingId} className={styles.heading}>
        밀키트로 간편하게
      </Typography.Text>
      <ul className={styles.list}>
        {mealkits.map((mealkit) => (
          <MealkitCard
            key={mealkit.product.productId}
            mealkit={mealkit}
            onAdd={onAdd}
            pending={pendingProductId === mealkit.product.productId}
            onNavigate={onNavigate}
          />
        ))}
      </ul>
    </section>
  );
}

interface MealkitCardProps {
  mealkit: ChatRecommendedMealkit;
  onAdd: (productId: number) => void;
  pending: boolean;
  onNavigate?: () => void;
}

function MealkitCard({ mealkit, onAdd, pending, onNavigate }: MealkitCardProps) {
  const { product } = mealkit;
  const onSale = product.salePrice !== null;
  const meta = [
    mealkit.servings > 0 ? `${mealkit.servings}인분` : null,
    mealkit.recipeName && mealkit.recipeName !== product.name ? mealkit.recipeName : null,
  ]
    .filter(Boolean)
    .join(' · ');

  return (
    <li className={styles.item}>
      <div className={styles.card}>
        {/* 카드 전체를 덮는 상품 상세 링크. 장바구니 아이콘만 링크 위에 올려 따로 누른다 */}
        <Link
          to={generatePath(ROUTES.productDetail, { productId: String(product.productId) })}
          onClick={onNavigate}
          className={styles.cardLink}
          aria-label={`${product.name} 상품 보기`}
        />
        <div className={styles.row}>
          <FallbackImage
            src={product.imageUrl}
            alt=""
            kind="product"
            size="small"
            className={styles.thumb}
          />
          <div className={styles.body}>
            <Typography.Text strong ellipsis className={styles.name}>
              {product.name}
            </Typography.Text>
            {meta && <span className={styles.meta}>{meta}</span>}
            <span className={styles.price}>
              <Typography.Text strong type={onSale ? 'danger' : undefined}>
                {formatPrice(product.salePrice ?? product.price)}
              </Typography.Text>
              {onSale && (
                <Typography.Text type="secondary" delete className={styles.listPrice}>
                  {formatPrice(product.price)}
                </Typography.Text>
              )}
            </span>
          </div>
          <ChatCartButton
            productId={product.productId}
            productName={product.name}
            soldOutLabel={product.name}
            soldOut={product.soldOut}
            pending={pending}
            onAdd={onAdd}
          />
        </div>
      </div>
      {mealkit.description && (
        <Typography.Paragraph type="secondary" className={styles.description}>
          {mealkit.description}
        </Typography.Paragraph>
      )}
    </li>
  );
}
