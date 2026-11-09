import { Flex, Typography } from 'antd';
import { useState } from 'react';
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
  const { recipeId, name = '', imageUrl, summary, lastStepImageUrl } = recipe;
  // 마지막 조리 단계 사진은 마우스를 처음 올릴 때 받는다(목록이 뜰 때 사진을 두 배로 받지 않도록).
  // 다 받은 뒤에만 겹쳐 보여 주고, 받지 못하면 대표 사진을 그대로 둔다.
  const [hoverImageRequested, setHoverImageRequested] = useState(false);
  const [hoverImageLoaded, setHoverImageLoaded] = useState(false);
  if (recipeId === undefined) return null;

  const meta = formatRecipeMeta(recipe);
  const ingredientCount = fridgeMatch?.ingredientCount ?? 0;
  const ownedCount = fridgeMatch?.ownedCount ?? 0;

  return (
    <Link
      to={generatePath(ROUTES.recipeDetail, { recipeId: String(recipeId) })}
      className={styles.card}
      onPointerEnter={(event) => {
        if (lastStepImageUrl && event.pointerType === 'mouse') setHoverImageRequested(true);
      }}
    >
      <div className={styles.thumb}>
        <FallbackImage
          src={imageUrl}
          alt={name}
          kind="recipe"
          className={styles.image}
          loading="lazy"
        />
        {hoverImageRequested && lastStepImageUrl && (
          <img
            src={lastStepImageUrl}
            alt=""
            aria-hidden
            decoding="async"
            className={
              hoverImageLoaded
                ? `${styles.hoverImage} ${styles.hoverImageLoaded}`
                : styles.hoverImage
            }
            onLoad={() => setHoverImageLoaded(true)}
          />
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
