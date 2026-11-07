import { StarFilled } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Skeleton, Typography } from 'antd';
import { generatePath, Link } from 'react-router';

import { ROUTES } from '@/app/paths';
import { FallbackImage } from '@/common/components/FallbackImage';
import { cookingQueries } from '@/domains/cooking/queries';

import styles from './MainRecipeBand.module.scss';

// 레퍼런스의 레시피 카드 줄처럼 사진이 큰 카드 3장. 메인에서는 곁들이는 구역이라
// 불러오지 못했거나(비로그인이면 API가 거절한다) 기록이 없으면 띠를 그리지 않는다.
const RECIPE_COUNT = 3;

export function MainRecipeBand() {
  const { data: recipes, isPending, isError } = useQuery(cookingQueries.popular(RECIPE_COUNT));
  const items = (recipes ?? []).filter((recipe) => recipe.recipeId !== undefined);

  if (isError || (!isPending && items.length === 0)) return null;

  return (
    <section className={styles.band} aria-labelledby="main-recipe-title">
      <div className={styles.inner}>
        <div className={styles.head}>
          <Typography.Title id="main-recipe-title" level={2} className={styles.title}>
            이번 달 많이 만든 레시피
          </Typography.Title>
          <Link to={ROUTES.recipes} className={styles.moreLink}>
            레시피 더 보기
          </Link>
        </div>

        <ul className={styles.list}>
          {isPending
            ? Array.from({ length: RECIPE_COUNT }, (_, index) => (
                <li key={index}>
                  <Skeleton.Node active className={styles.skeletonImage}>
                    <span />
                  </Skeleton.Node>
                  <Skeleton active title={false} paragraph={{ rows: 2 }} />
                </li>
              ))
            : items.map((recipe) => {
                const name = recipe.recipeName ?? '';
                const rating = recipe.averageRating;
                return (
                  <li key={recipe.recipeId}>
                    <Link
                      to={generatePath(ROUTES.recipeDetail, { recipeId: String(recipe.recipeId) })}
                      className={styles.card}
                    >
                      <span className={styles.frame}>
                        <FallbackImage
                          src={recipe.imageUrl}
                          alt={name}
                          kind="recipe"
                          className={styles.image}
                          loading="lazy"
                        />
                      </span>
                      <span className={styles.name}>{name}</span>
                      <span className={styles.meta}>
                        {recipe.cookedMemberCount ?? 0}명이 만들었어요
                        {rating !== undefined && rating !== null && (
                          <span className={styles.rating}>
                            <StarFilled aria-hidden className={styles.star} />
                            {rating.toFixed(1)}
                            <span className={styles.reviewCount}>({recipe.reviewCount ?? 0})</span>
                          </span>
                        )}
                      </span>
                    </Link>
                  </li>
                );
              })}
        </ul>
      </div>
    </section>
  );
}
