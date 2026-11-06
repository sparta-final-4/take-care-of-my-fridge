import { Typography } from 'antd';

import { formatPrice } from '@/domains/cart/format';

import type { ChatRecommendationMissingIngredient, ChatShoppingProduct } from '../recommendations';
import { ChatCartButton } from './ChatCartButton';
import styles from './ChatRecommendationCards.module.scss';

interface ChatShoppingListProps {
  ingredients: ChatRecommendationMissingIngredient[];
  // 담기 버튼을 눌렀을 때 (상품 1개)
  onAdd: (productId: number) => void;
  // 담는 중인 상품 (그 버튼에 로딩을 보여 준다)
  pendingProductId?: number;
}

interface ProductIngredient {
  ingredient: ChatRecommendationMissingIngredient;
  product: ChatShoppingProduct;
}

// 추천 카드의 장볼 재료 목록. 살 수 있는 상품 → 품절 → 판매 상품 없음 순서로 보여 준다.
// 상품이 있으면 재료·상품 이름 두 줄과 가격, 장바구니 아이콘 버튼(품절이면 막고 "품절").
// 상품이 없는 재료는 한 줄로 묶어 "판매 상품 없음"으로 흐리게 보여 준다.
export function ChatShoppingList({ ingredients, onAdd, pendingProductId }: ChatShoppingListProps) {
  const withProduct: ProductIngredient[] = [];
  const withoutProduct: ChatRecommendationMissingIngredient[] = [];
  for (const ingredient of ingredients) {
    if (ingredient.product) {
      withProduct.push({ ingredient, product: ingredient.product });
    } else {
      withoutProduct.push(ingredient);
    }
  }
  const sorted = [
    ...withProduct.filter(({ product }) => !product.soldOut),
    ...withProduct.filter(({ product }) => product.soldOut),
  ];

  return (
    <ul className={styles.shoppingList} aria-label="장볼 재료">
      {sorted.map(({ ingredient, product }) => (
        <ShoppingItem
          key={ingredient.ingredientId}
          ingredient={ingredient}
          product={product}
          onAdd={onAdd}
          pending={pendingProductId === product.productId}
        />
      ))}
      {withoutProduct.length > 0 && (
        <li className={styles.shoppingItem}>
          <span className={styles.shoppingName}>
            <Typography.Text type="secondary" className={styles.shoppingIngredient}>
              {withoutProduct.map((ingredient) => ingredient.name).join(' · ')}
            </Typography.Text>
            <Typography.Text type="secondary" className={styles.shoppingProduct}>
              판매 상품 없음
            </Typography.Text>
          </span>
        </li>
      )}
    </ul>
  );
}

interface ShoppingItemProps {
  ingredient: ChatRecommendationMissingIngredient;
  product: ChatShoppingProduct;
  onAdd: (productId: number) => void;
  pending: boolean;
}

function ShoppingItem({ ingredient, product, onAdd, pending }: ShoppingItemProps) {
  const onSale = product.salePrice !== null;
  return (
    <li className={styles.shoppingItem}>
      <span className={styles.shoppingName}>
        <Typography.Text ellipsis className={styles.shoppingIngredient}>
          {ingredient.name}
        </Typography.Text>
        <Typography.Text type="secondary" ellipsis className={styles.shoppingProduct}>
          {product.name}
        </Typography.Text>
      </span>
      <span className={styles.shoppingPrice}>
        <Typography.Text strong type={onSale ? 'danger' : undefined}>
          {formatPrice(product.salePrice ?? product.price)}
        </Typography.Text>
        {onSale && (
          <Typography.Text type="secondary" delete className={styles.shoppingListPrice}>
            {formatPrice(product.price)}
          </Typography.Text>
        )}
      </span>
      <ChatCartButton
        productId={product.productId}
        productName={product.name}
        soldOutLabel={ingredient.name}
        soldOut={product.soldOut}
        pending={pending}
        onAdd={onAdd}
      />
    </li>
  );
}
