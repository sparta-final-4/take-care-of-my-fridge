import { LeftOutlined, PlusOutlined, RightOutlined } from '@ant-design/icons';
import { Button, Tag, Typography } from 'antd';
import { useRef } from 'react';

import type { BillingKeyResponse } from '@/common/api/clients';
import {
  CARD_TYPE_LABELS,
  cardName,
  MAX_CARDS,
  maskedCardNumber,
} from '@/domains/payment/cardFormat';

import styles from './CardPicker.module.scss';

interface CardPickerProps {
  cards: BillingKeyResponse[];
  selectedCardId?: number;
  disabled?: boolean;
  registering?: boolean;
  onSelect: (cardId: number) => void;
  onRegister: () => void;
}

// 주문 확인 화면의 등록 카드 고르기. 카드를 옆으로 넘겨 고르고(모바일은 밀어서, 데스크톱은 화살표), 마지막 칸에서 카드를 등록한다.
// 카드 삭제는 결제 중 실수를 막으려고 카드 관리 화면에서만 한다.
export function CardPicker({
  cards,
  selectedCardId,
  disabled,
  registering,
  onSelect,
  onRegister,
}: CardPickerProps) {
  const trackRef = useRef<HTMLDivElement>(null);
  const scrollBy = (direction: 1 | -1) =>
    trackRef.current?.scrollBy({
      left: direction * trackRef.current.clientWidth * 0.8,
      behavior: 'smooth',
    });

  return (
    <div className={styles.picker}>
      <Button
        shape="circle"
        icon={<LeftOutlined />}
        aria-label="이전 카드"
        onClick={() => scrollBy(-1)}
        className={styles.arrow}
      />
      <div ref={trackRef} className={styles.track} role="radiogroup" aria-label="등록 카드">
        {cards.map((card) => {
          const selected = card.cardId === selectedCardId;
          return (
            <button
              key={card.cardId}
              type="button"
              role="radio"
              aria-checked={selected}
              disabled={disabled}
              onClick={() => onSelect(card.cardId ?? 0)}
              className={`${styles.card} ${selected ? styles.selected : ''}`}
            >
              <span className={styles.cardTop}>
                <Typography.Text strong ellipsis className={styles.name}>
                  {cardName(card)}
                </Typography.Text>
                {card.isDefault && <Tag color="blue">대표</Tag>}
              </span>
              <Typography.Text className={styles.number}>{maskedCardNumber(card)}</Typography.Text>
              {card.cardType && (
                <Typography.Text type="secondary">
                  {CARD_TYPE_LABELS[card.cardType]}
                </Typography.Text>
              )}
            </button>
          );
        })}
        {cards.length < MAX_CARDS && (
          <button
            type="button"
            disabled={disabled || registering}
            onClick={onRegister}
            className={`${styles.card} ${styles.add}`}
          >
            <PlusOutlined aria-hidden="true" />
            <Typography.Text>{registering ? '카드 등록 중…' : '카드 등록'}</Typography.Text>
          </button>
        )}
      </div>
      <Button
        shape="circle"
        icon={<RightOutlined />}
        aria-label="다음 카드"
        onClick={() => scrollBy(1)}
        className={styles.arrow}
      />
    </div>
  );
}
