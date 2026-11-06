import { DownOutlined, UpOutlined } from '@ant-design/icons';
import { Button, Tag, Typography } from 'antd';
import { useId, useState } from 'react';
import { generatePath, Link } from 'react-router';

import { ROUTES } from '@/app/paths';
import { FallbackImage } from '@/common/components/FallbackImage';
import { useAddToCart } from '@/domains/cart/hooks/useAddToCart';
import { ddayTagColor } from '@/domains/fridge/format';

import {
  type ChatRecommendationMissingIngredient,
  type ChatRecommendedRecipe,
  summarizeImminent,
} from '../recommendations';
import { ChatShoppingList } from './ChatShoppingList';
import styles from './ChatRecommendationCards.module.scss';

interface ChatRecommendationCardsProps {
  recipes: ChatRecommendedRecipe[];
  // 받는 중인 카드 설명 (카드 순서). 있으면 저장된 설명(recipe.description) 대신 쓴다
  descriptions?: string[];
  // 카드를 눌러 레시피 상세로 갈 때 (모바일 전체 화면 채팅창은 닫는다)
  onNavigate?: () => void;
}

// AI 상담 추천 카드 (최대 3개). 소개 말풍선 아래에 카드 → 설명 → 카드 → 설명 순서로 쌓는다.
export function ChatRecommendationCards({
  recipes,
  descriptions,
  onNavigate,
}: ChatRecommendationCardsProps) {
  if (recipes.length === 0) return null;
  return (
    <ul className={styles.cards} aria-label="추천 레시피">
      {recipes.map((recipe, index) => (
        <RecommendationCard
          key={recipe.recipeId}
          recipe={recipe}
          description={descriptions?.[index] || recipe.description}
          onNavigate={onNavigate}
        />
      ))}
    </ul>
  );
}

interface RecommendationCardProps {
  recipe: ChatRecommendedRecipe;
  // 카드 아래 AI 설명. 없으면 카드 안에 이유 한 줄을 대신 보여 준다
  description: string | null;
  onNavigate?: () => void;
}

function RecommendationCard({ recipe, description, onNavigate }: RecommendationCardProps) {
  const [showShopping, setShowShopping] = useState(false);
  const shoppingListId = useId();
  const imminent = summarizeImminent(recipe.imminentIngredients);
  const meta = [
    recipe.category,
    recipe.cookTimeMin === null ? '시간 정보 없음' : `${recipe.cookTimeMin}분`,
  ]
    .filter(Boolean)
    .join(' · ');

  return (
    <li className={styles.item}>
      <div className={styles.card}>
        {/* 카드 전체를 덮는 링크. 장볼 재료 버튼만 링크 위에 올려 따로 누른다 */}
        <Link
          to={generatePath(ROUTES.recipeDetail, { recipeId: String(recipe.recipeId) })}
          onClick={onNavigate}
          className={styles.cardLink}
          aria-label={`${recipe.name} 레시피 보기`}
        />
        <div className={styles.row}>
          <FallbackImage
            src={recipe.imageUrl}
            alt=""
            kind="recipe"
            size="small"
            className={styles.thumb}
          />
          <div className={styles.body}>
            <div className={styles.titleLine}>
              <Typography.Text strong ellipsis className={styles.name}>
                {recipe.name}
              </Typography.Text>
              <span className={styles.meta}>{meta}</span>
            </div>
            {!description && recipe.reason && (
              <Typography.Text type="secondary" ellipsis className={styles.reason}>
                {recipe.reason}
              </Typography.Text>
            )}
            <div className={styles.infoLine}>
              {imminent && (
                <Tag variant="filled" color={ddayTagColor(imminent.dDay)} className={styles.tag}>
                  {imminent.label}
                </Tag>
              )}
              {recipe.missingSelectedIngredients.length > 0 && (
                <Tag variant="filled" color="error" className={styles.tag}>
                  {recipe.missingSelectedIngredients.join('·')} 없음
                </Tag>
              )}
              {recipe.missingIngredients.length > 0 && (
                <Button
                  type="link"
                  size="small"
                  onClick={() => setShowShopping((shown) => !shown)}
                  aria-expanded={showShopping}
                  aria-controls={shoppingListId}
                  className={styles.shoppingToggle}
                >
                  장볼 재료 {recipe.missingIngredients.length}
                  {showShopping ? <UpOutlined /> : <DownOutlined />}
                </Button>
              )}
              {recipe.lacksManyIngredients && (
                <Tag variant="filled" color="warning" className={styles.tag}>
                  장보기 필요
                </Tag>
              )}
            </div>
          </div>
        </div>
        {showShopping && (
          <ShoppingSection id={shoppingListId} ingredients={recipe.missingIngredients} />
        )}
      </div>
      {description && (
        <Typography.Paragraph className={styles.description}>{description}</Typography.Paragraph>
      )}
    </li>
  );
}

interface ShoppingSectionProps {
  id: string;
  ingredients: ChatRecommendationMissingIngredient[];
}

// 펼친 장볼 재료. 담기는 상품 목록·상세와 같은 장바구니 담기를 쓰고, 상담 창은 그대로 둔다
function ShoppingSection({ id, ingredients }: ShoppingSectionProps) {
  const { addToCart, pendingProductId } = useAddToCart();
  return (
    <div id={id} className={styles.shopping}>
      <ChatShoppingList
        ingredients={ingredients}
        onAdd={(productId) => addToCart(productId, 1)}
        pendingProductId={pendingProductId}
      />
      <Typography.Text type="secondary" className={styles.shoppingNote}>
        소금·간장 같은 기본 양념은 빼고 셌어요
      </Typography.Text>
    </div>
  );
}
