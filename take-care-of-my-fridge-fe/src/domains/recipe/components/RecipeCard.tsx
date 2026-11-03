import { Flex, Typography } from 'antd';
import { generatePath, Link } from 'react-router';

import { ROUTES } from '@/app/paths';
import type { RecipeListItemResponse, RecipeMatch } from '@/common/api/clients';
import { FallbackImage } from '@/common/components/FallbackImage';
import { formatRecipeMeta } from '@/domains/recipe/format';

import styles from './RecipeCard.module.scss';

interface RecipeCardProps {
  recipe: RecipeListItemResponse;
  // 로그인 회원 냉장고와 비교한 보유 재료 수(통합 검색). 비교할 재료가 없으면 표시하지 않는다.
  fridgeMatch?: RecipeMatch;
}

export function RecipeCard({ recipe, fridgeMatch }: RecipeCardProps) {
  const { recipeId, name = '', imageUrl, summary } = recipe;
  if (recipeId === undefined) return null;

  const meta = formatRecipeMeta(recipe);
  const ingredientCount = fridgeMatch?.ingredientCount ?? 0;
  const ownedCount = fridgeMatch?.ownedCount ?? 0;

  return (
    <Link
      to={generatePath(ROUTES.recipeDetail, { recipeId: String(recipeId) })}
      className={styles.card}
    >
      <div className={styles.thumb}>
        <FallbackImage
          src={imageUrl}
          alt={name}
          kind="recipe"
          className={styles.image}
          loading="lazy"
        />
      </div>
      <Flex vertical gap={4} className={styles.info}>
        <Typography.Text strong className={styles.name} title={name}>
          {name}
        </Typography.Text>
        {summary && (
          <Typography.Text type="secondary" ellipsis={{ tooltip: summary }}>
            {summary}
          </Typography.Text>
        )}
        {meta && (
          <Typography.Text type="secondary" className={styles.meta}>
            {meta}
          </Typography.Text>
        )}
        {ingredientCount > 0 && (
          <Typography.Text
            type={ownedCount > 0 ? undefined : 'secondary'}
            className={ownedCount > 0 ? styles.fridgeMatchOwned : styles.meta}
          >
            냉장고 재료 {ownedCount}/{ingredientCount}
          </Typography.Text>
        )}
      </Flex>
    </Link>
  );
}
