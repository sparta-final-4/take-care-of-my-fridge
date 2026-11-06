import { Button, Typography } from 'antd';
import type { MouseEvent } from 'react';

import { formatPrice } from '@/domains/cart/format';

import type { ChatRecommendationMissingIngredient } from '../recommendations';
import styles from './ChatRecommendationCards.module.scss';

interface ChatShoppingListProps {
  ingredients: ChatRecommendationMissingIngredient[];
  // [담기]를 눌렀을 때 (상품 1개)
  onAdd: (productId: number) => void;
  // 담는 중인 상품 (그 버튼에 로딩을 보여 준다)
  pendingProductId?: number;
}

// 추천 카드의 장볼 재료 목록. 재료마다 담을 상품의 가격과 [담기]를 보여 준다.
// 상품이 없으면 이름만 흐리게 "판매 상품 없음", 품절이면 [담기]를 막고 "품절"로 보여 준다.
export function ChatShoppingList({ ingredients, onAdd, pendingProductId }: ChatShoppingListProps) {
  return (
    <ul className={styles.shoppingList} aria-label="장볼 재료">
      {ingredients.map((ingredient) => {
        const { product } = ingredient;
        if (!product) {
          return (
            <li key={ingredient.ingredientId} className={styles.shoppingItem}>
              <Typography.Text type="secondary" className={styles.shoppingName}>
                {ingredient.name}
              </Typography.Text>
              <Typography.Text type="secondary" className={styles.shoppingPrice}>
                판매 상품 없음
              </Typography.Text>
            </li>
          );
        }
        const onSale = product.salePrice !== null;
        const shownPrice = product.salePrice ?? product.price;
        // 카드 전체를 덮는 레시피 링크로 이어지지 않게 막고, 상담 창 안에서 담는다
        const add = (event: MouseEvent) => {
          event.preventDefault();
          event.stopPropagation();
          onAdd(product.productId);
        };
        return (
          <li key={ingredient.ingredientId} className={styles.shoppingItem}>
            <span className={styles.shoppingName}>
              <Typography.Text>{ingredient.name}</Typography.Text>
              <Typography.Text type="secondary" ellipsis className={styles.shoppingProduct}>
                {product.name}
              </Typography.Text>
            </span>
            <span className={styles.shoppingPrice}>
              <Typography.Text strong type={onSale ? 'danger' : undefined}>
                {formatPrice(shownPrice)}
              </Typography.Text>
              {onSale && (
                <Typography.Text type="secondary" delete className={styles.shoppingListPrice}>
                  {formatPrice(product.price)}
                </Typography.Text>
              )}
            </span>
            <Button
              onClick={add}
              disabled={product.soldOut}
              loading={pendingProductId === product.productId}
              aria-label={
                product.soldOut ? `${ingredient.name} 품절` : `${product.name} 장바구니에 담기`
              }
            >
              {product.soldOut ? '품절' : '담기'}
            </Button>
          </li>
        );
      })}
    </ul>
  );
}
