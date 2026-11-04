import { DownOutlined, UpOutlined } from '@ant-design/icons';
import { Button, Flex, Tag, Typography } from 'antd';
import { useState } from 'react';
import { generatePath, Link } from 'react-router';

import { ROUTES } from '@/app/paths';
import { FallbackImage } from '@/common/components/FallbackImage';
import { ddayTagColor } from '@/domains/fridge/format';

import { type ChatRecommendedRecipe, formatImminentLabel } from '../recommendations';
import styles from './ChatRecommendationCards.module.scss';

interface ChatRecommendationCardsProps {
  recipes: ChatRecommendedRecipe[];
  // 카드를 눌러 레시피 상세로 갈 때 (모바일 전체 화면 채팅창은 닫는다)
  onNavigate?: () => void;
}

// AI 상담 추천 카드 (최대 3개). 채팅창 380px에 맞게 세로로 쌓는다.
export function ChatRecommendationCards({ recipes, onNavigate }: ChatRecommendationCardsProps) {
  if (recipes.length === 0) return null;
  return (
    <ul className={styles.cards} aria-label="추천 레시피">
      {recipes.map((recipe) => (
        <RecommendationCard key={recipe.recipeId} recipe={recipe} onNavigate={onNavigate} />
      ))}
    </ul>
  );
}

interface RecommendationCardProps {
  recipe: ChatRecommendedRecipe;
  onNavigate?: () => void;
}

function RecommendationCard({ recipe, onNavigate }: RecommendationCardProps) {
  const [showMissing, setShowMissing] = useState(false);
  const meta = [
    recipe.category,
    recipe.cookTimeMin === null ? '시간 정보 없음' : `${recipe.cookTimeMin}분`,
  ]
    .filter(Boolean)
    .join(' · ');

  return (
    <li className={styles.card}>
      <Link
        to={generatePath(ROUTES.recipeDetail, { recipeId: String(recipe.recipeId) })}
        onClick={onNavigate}
        className={styles.link}
      >
        <FallbackImage
          src={recipe.imageUrl}
          alt={recipe.name}
          kind="recipe"
          size="small"
          className={styles.thumb}
        />
        <Flex vertical gap={4} className={styles.body}>
          <Typography.Text strong ellipsis className={styles.name}>
            {recipe.name}
          </Typography.Text>
          <Typography.Text type="secondary" className={styles.meta}>
            {meta}
          </Typography.Text>
          <Flex wrap gap={4}>
            {recipe.missingSelectedIngredients.map((name) => (
              <Tag key={`missing-${name}`} variant="filled" color="error" className={styles.tag}>
                {name} 없음
              </Tag>
            ))}
            {recipe.imminentIngredients.map((ingredient) => (
              <Tag
                key={`imminent-${ingredient.name}`}
                variant="filled"
                color={ddayTagColor(ingredient.dDay)}
                className={styles.tag}
              >
                {ingredient.name} {formatImminentLabel(ingredient.dDay)}
              </Tag>
            ))}
            {recipe.lacksManyIngredients && (
              <Tag variant="filled" color="warning" className={styles.tag}>
                장보기 필요
              </Tag>
            )}
          </Flex>
        </Flex>
      </Link>
      {recipe.missingIngredients.length > 0 && (
        <div className={styles.missing}>
          <Button
            type="link"
            size="small"
            onClick={() => setShowMissing((shown) => !shown)}
            aria-expanded={showMissing}
            className={styles.missingToggle}
          >
            부족한 재료 {recipe.missingIngredients.length}개{' '}
            {showMissing ? <UpOutlined /> : <DownOutlined />}
          </Button>
          {showMissing && (
            <Typography.Text type="secondary" className={styles.missingNames}>
              {recipe.missingIngredients.map((ingredient) => ingredient.name).join(', ')}
            </Typography.Text>
          )}
        </div>
      )}
    </li>
  );
}
