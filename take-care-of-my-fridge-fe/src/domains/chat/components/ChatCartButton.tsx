import { ShoppingCartOutlined } from '@ant-design/icons';
import { Button } from 'antd';
import type { MouseEvent } from 'react';

import styles from './ChatCartButton.module.scss';

interface ChatCartButtonProps {
  productId: number;
  // aria-label에 쓸 상품 이름 ("○○ 장바구니에 담기")
  productName: string;
  // 품절일 때 aria-label에 쓸 이름 ("○○ 품절")
  soldOutLabel: string;
  soldOut: boolean;
  pending: boolean;
  onAdd: (productId: number) => void;
}

// 상담 카드의 장바구니 담기 아이콘 버튼 (장볼 재료·밀키트 카드가 같이 쓴다).
// 보이는 크기는 기본 버튼 높이로 두고 누르는 영역만 44px로 넓힌다. 품절이면 막고 "품절"을 보여 준다.
// 카드 전체를 덮는 링크로 이어지지 않게 막고, 상담 창 안에서 담는다.
export function ChatCartButton({
  productId,
  productName,
  soldOutLabel,
  soldOut,
  pending,
  onAdd,
}: ChatCartButtonProps) {
  if (soldOut) {
    return (
      <Button
        color="default"
        variant="text"
        disabled
        aria-label={`${soldOutLabel} 품절`}
        className={styles.button}
      >
        품절
      </Button>
    );
  }
  const add = (event: MouseEvent) => {
    event.preventDefault();
    event.stopPropagation();
    onAdd(productId);
  };
  return (
    <Button
      color="primary"
      variant="text"
      icon={<ShoppingCartOutlined className={styles.icon} />}
      loading={pending}
      onClick={add}
      aria-label={`${productName} 장바구니에 담기`}
      className={styles.button}
    />
  );
}
