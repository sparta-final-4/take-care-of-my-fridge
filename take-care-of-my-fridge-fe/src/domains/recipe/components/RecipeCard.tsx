import { Flex, Typography } from 'antd';
import { generatePath, Link } from 'react-router';

import { ROUTES } from '@/app/paths';
import type { RecipeListItemResponse } from '@/common/api/clients';
import { FadeImage } from '@/common/components/FadeImage';
import { formatRecipeMeta } from '@/domains/recipe/format';

import styles from './RecipeCard.module.scss';

interface RecipeCardProps {
  recipe: RecipeListItemResponse;
}

export function RecipeCard({ recipe }: RecipeCardProps) {
  const { recipeId, name = '', imageUrl, summary } = recipe;
  if (recipeId === undefined) return null;

  const meta = formatRecipeMeta(recipe);

  return (
    <Link
      to={generatePath(ROUTES.recipeDetail, { recipeId: String(recipeId) })}
      className={styles.card}
    >
      <div className={styles.thumb}>
        {imageUrl ? (
          <FadeImage src={imageUrl} alt={name} className={styles.image} loading="lazy" />
        ) : (
          <span className={styles.imagePlaceholder} aria-hidden="true" />
        )}
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
      </Flex>
    </Link>
  );
}
