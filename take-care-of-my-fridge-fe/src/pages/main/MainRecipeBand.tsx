import { StarFilled } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Skeleton, Typography } from 'antd';
import { generatePath, Link } from 'react-router';

import { ROUTES } from '@/app/paths';
import type { PopularRecipeResponse } from '@/common/api/clients';
import { FallbackImage } from '@/common/components/FallbackImage';
import { cookingQueries } from '@/domains/cooking/queries';

import styles from './MainRecipeBand.module.scss';

// 1등은 사진이 큰 카드(후기 요약까지), 2~7등은 오른쪽 순위 목록. 메인에서는 곁들이는 구역이라
// 불러오지 못했거나 기록이 없으면 띠를 그리지 않는다.
const RECIPE_COUNT = 7;

function recipePath(recipeId: number) {
  return generatePath(ROUTES.recipeDetail, { recipeId: String(recipeId) });
}

// 후기가 하나라도 있어 별점을 보여 줄 수 있는지
function hasReview(recipe: PopularRecipeResponse) {
  return typeof recipe.averageRating === 'number' && (recipe.reviewCount ?? 0) > 0;
}

// 별점과 후기 수 (hasReview일 때만 그린다)
function Rating({ recipe }: { recipe: PopularRecipeResponse }) {
  return (
    <span className={styles.rating}>
      <StarFilled aria-hidden className={styles.star} />
      {(recipe.averageRating ?? 0).toFixed(1)}
      <span className={styles.reviewCount}>({recipe.reviewCount ?? 0})</span>
    </span>
  );
}

function TopRecipe({ recipe }: { recipe: PopularRecipeResponse }) {
  const name = recipe.recipeName ?? '';
  return (
    <Link to={recipePath(recipe.recipeId ?? 0)} className={styles.top}>
      <span className={styles.frame}>
        <FallbackImage
          src={recipe.imageUrl}
          alt={name}
          kind="recipe"
          className={styles.image}
          loading="lazy"
        />
        <span className={styles.topRank} aria-label="1위">
          1
        </span>
      </span>
      <span className={styles.topName}>{name}</span>
      <span className={styles.meta}>
        {recipe.cookedMemberCount ?? 0}명이 만들었어요
        <span aria-hidden>·</span>
        {hasReview(recipe) ? <Rating recipe={recipe} /> : '아직 후기가 없어요'}
      </span>
      {recipe.tipSummary && <span className={styles.summary}>{recipe.tipSummary}</span>}
    </Link>
  );
}

function RankedRecipe({ recipe, rank }: { recipe: PopularRecipeResponse; rank: number }) {
  const name = recipe.recipeName ?? '';
  return (
    <li>
      <Link to={recipePath(recipe.recipeId ?? 0)} className={styles.row}>
        <span className={styles.rank} aria-label={`${rank}위`}>
          {rank}
        </span>
        <span className={styles.thumb}>
          <FallbackImage
            src={recipe.imageUrl}
            alt=""
            kind="recipe"
            size="small"
            className={styles.image}
            loading="lazy"
          />
        </span>
        <span className={styles.rowText}>
          <span className={styles.rowName} title={name}>
            {name}
          </span>
          <span className={styles.meta}>
            {recipe.cookedMemberCount ?? 0}명
            {hasReview(recipe) && (
              <>
                <span aria-hidden>·</span>
                <Rating recipe={recipe} />
              </>
            )}
          </span>
        </span>
      </Link>
    </li>
  );
}

function BoardSkeleton() {
  return (
    <div className={styles.board} aria-hidden>
      <div className={styles.topSkeleton}>
        <Skeleton.Node active className={styles.skeletonImage}>
          <span />
        </Skeleton.Node>
        <Skeleton active title={false} paragraph={{ rows: 3 }} />
      </div>
      <ol className={styles.ranks}>
        {Array.from({ length: RECIPE_COUNT - 1 }, (_, index) => (
          <li key={index} className={styles.rowSkeleton}>
            <Skeleton.Node active className={styles.skeletonThumb}>
              <span />
            </Skeleton.Node>
            <Skeleton active title={false} paragraph={{ rows: 2, width: ['70%', '40%'] }} />
          </li>
        ))}
      </ol>
    </div>
  );
}

export function MainRecipeBand() {
  const { data: recipes, isPending, isError } = useQuery(cookingQueries.popular(RECIPE_COUNT));
  const items = (recipes ?? []).filter((recipe) => recipe.recipeId !== undefined);

  if (isError || (!isPending && items.length === 0)) return null;

  const [top, ...rest] = items;

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

        {isPending ? (
          <BoardSkeleton />
        ) : (
          <div className={rest.length > 0 ? styles.board : styles.single}>
            <TopRecipe recipe={top} />
            {rest.length > 0 && (
              <ol className={styles.ranks} start={2}>
                {rest.map((recipe, index) => (
                  <RankedRecipe key={recipe.recipeId} recipe={recipe} rank={index + 2} />
                ))}
              </ol>
            )}
          </div>
        )}
      </div>
    </section>
  );
}
