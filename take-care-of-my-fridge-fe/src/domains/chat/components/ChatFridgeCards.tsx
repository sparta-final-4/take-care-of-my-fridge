import { Tag, Typography } from 'antd';
import { useId } from 'react';
import { generatePath, Link } from 'react-router';

import { ROUTES } from '@/app/paths';
import { FallbackImage } from '@/common/components/FallbackImage';
import { ddayTagColor, formatQuantity } from '@/domains/fridge/format';

import { type ChatFridgeItem, formatImminentLabel, IMMINENT_DAYS } from '../recommendations';
import styles from './ChatFridgeCards.module.scss';

interface ChatFridgeCardsProps {
  items: ChatFridgeItem[];
  // 밀키트 카드를 눌러 상품 상세로 갈 때 (모바일 전체 화면 채팅창은 닫는다)
  onNavigate?: () => void;
}

// 냉장고 보기 답의 냉장고 줄 카드 (소비기한 순, 지난 줄은 맨 아래). 이 답에서만 보이고 저장하지 않는다.
// D-3 이내면 남은 날 태그, 지났으면 "지났어요" 태그. 밀키트 줄은 연결 상품 이미지와 상품 상세 링크를 붙인다.
export function ChatFridgeCards({ items, onNavigate }: ChatFridgeCardsProps) {
  const headingId = useId();
  if (items.length === 0) return null;
  return (
    <section className={styles.section} aria-labelledby={headingId}>
      <Typography.Text strong id={headingId} className={styles.heading}>
        냉장고에 있어요
      </Typography.Text>
      <ul className={styles.list}>
        {items.map((item) => (
          <FridgeItemCard key={item.fridgeId} item={item} onNavigate={onNavigate} />
        ))}
      </ul>
    </section>
  );
}

interface FridgeItemCardProps {
  item: ChatFridgeItem;
  onNavigate?: () => void;
}

function FridgeItemCard({ item, onNavigate }: FridgeItemCardProps) {
  const expired = item.dDay < 0;
  const label = expired
    ? '지났어요'
    : item.dDay <= IMMINENT_DAYS
      ? formatImminentLabel(item.dDay)
      : null;
  const quantity =
    item.quantity === null
      ? null
      : formatQuantity({ quantity: item.quantity, baseUnit: item.unit ?? undefined });
  const productId = item.mealkit ? item.productId : null;

  return (
    <li className={styles.card}>
      {productId !== null && (
        <Link
          to={generatePath(ROUTES.productDetail, { productId: String(productId) })}
          onClick={onNavigate}
          className={styles.cardLink}
          aria-label={`${item.name} 상품 보기`}
        />
      )}
      <div className={styles.row}>
        {item.mealkit && (
          <FallbackImage
            src={item.imageUrl}
            alt=""
            kind="product"
            size="small"
            className={styles.thumb}
          />
        )}
        <div className={styles.body}>
          <Typography.Text strong ellipsis className={styles.name}>
            {item.name}
          </Typography.Text>
          {quantity && <span className={styles.meta}>{quantity}</span>}
        </div>
        {label && (
          <Tag variant="filled" color={ddayTagColor(item.dDay)} className={styles.tag}>
            {label}
          </Tag>
        )}
      </div>
    </li>
  );
}
