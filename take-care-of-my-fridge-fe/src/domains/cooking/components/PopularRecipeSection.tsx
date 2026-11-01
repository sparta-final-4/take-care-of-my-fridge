import { StarFilled } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Button, Skeleton, Typography } from 'antd';
import { generatePath, Link } from 'react-router';

import { ROUTES } from '@/app/paths';
import { FallbackImage } from '@/common/components/FallbackImage';
import { SectionCard } from '@/common/components/SectionCard';
import { cookingQueries } from '@/domains/cooking/queries';

import styles from './PopularRecipeSection.module.scss';

const POPULAR_SIZE = 6;

export function PopularRecipeSection() {
  const {
    data: recipes,
    isLoading,
    isError,
    refetch,
  } = useQuery(cookingQueries.popular(POPULAR_SIZE));

  return (
    <SectionCard
      title="이번 달 많이 만든 레시피"
      extra={
        <Link to={ROUTES.recipes} className={styles.moreLink}>
          레시피 더 보기 &gt;
        </Link>
      }
    >
      {isLoading && (
        <div className={styles.list}>
          {Array.from({ length: POPULAR_SIZE }).map((_, idx) => (
            <div key={idx} className={styles.skeletonItem}>
              <Skeleton.Button active style={{ width: 24, height: 24, minWidth: 24 }} />
              <Skeleton.Button active style={{ width: 64, height: 64, minWidth: 64 }} />
              <div className={styles.info}>
                <Skeleton.Input active size="small" style={{ width: '45%' }} />
                <Skeleton.Input active size="small" style={{ width: '70%' }} />
              </div>
            </div>
          ))}
        </div>
      )}

      {isError && (
        <div className={styles.errorState}>
          <Typography.Text type="secondary">인기 레시피를 불러오지 못했어요</Typography.Text>
          <Button size="small" onClick={() => void refetch()}>
            다시 시도
          </Button>
        </div>
      )}

      {!isLoading && !isError && recipes && recipes.length === 0 && (
        <div className={styles.emptyState}>
          <Typography.Text type="secondary">이번 달 요리 기록이 아직 없어요</Typography.Text>
          <Link to={ROUTES.recipes}>
            <Button size="small" type="primary">
              레시피 둘러보기
            </Button>
          </Link>
        </div>
      )}

      {!isLoading && !isError && recipes && recipes.length > 0 && (
        <div className={styles.list}>
          {recipes.map((recipe, idx) => {
            if (recipe.recipeId === undefined) return null;

            const rank = idx + 1;
            const isTopRank = rank <= 3;
            const name = recipe.recipeName ?? '';
            const count = recipe.cookedMemberCount ?? 0;
            const rating = recipe.averageRating;
            const reviewCount = recipe.reviewCount ?? 0;

            return (
              <Link
                key={recipe.recipeId}
                to={generatePath(ROUTES.recipeDetail, {
                  recipeId: String(recipe.recipeId),
                })}
                className={styles.item}
              >
                <span className={`${styles.rank} ${isTopRank ? styles.topRank : ''}`}>{rank}</span>

                <div className={styles.thumb}>
                  <FallbackImage
                    src={recipe.imageUrl}
                    alt={name}
                    kind="recipe"
                    size="small"
                    className={styles.image}
                    loading="lazy"
                  />
                </div>

                <div className={styles.info}>
                  <Typography.Text strong className={styles.name} title={name}>
                    {name}
                  </Typography.Text>

                  <div className={styles.meta}>
                    <span>{count}명이 만들었어요</span>
                    <span> · </span>
                    {rating !== undefined && rating !== null ? (
                      <span>
                        <StarFilled className={styles.starIcon} /> {rating.toFixed(1)} (
                        {reviewCount})
                      </span>
                    ) : (
                      <span>아직 후기가 없어요</span>
                    )}
                  </div>

                  {recipe.tipSummary && (
                    <div className={styles.tipSummary} title={recipe.tipSummary}>
                      💬 {recipe.tipSummary}
                    </div>
                  )}
                </div>
              </Link>
            );
          })}
        </div>
      )}
    </SectionCard>
  );
}
