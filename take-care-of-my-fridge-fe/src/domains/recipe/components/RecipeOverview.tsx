import { Flex, Tag, Typography } from 'antd';

import type { RecipeDetailResponse } from '@/common/api/clients';
import { FallbackImage } from '@/common/components/FallbackImage';
import type { RecipeFridgeMatch } from '@/domains/recipe/hooks/useRecipeFridgeMatch';

import styles from './RecipeOverview.module.scss';

interface RecipeOverviewProps {
  recipe: RecipeDetailResponse;
  fridgeMatch: RecipeFridgeMatch;
}

// 레시피 머리: 사진 + 글(이름 칸 heading, 정보 칸 details). 배치는 styles/_recipe-detail-theme.scss 레시피 상세 세트가 정한다
// (글 | 사진 두 칸, 사진 배경 위 카드, 사진 위 · 글 아래). 좁은 화면은 사진이 맨 위.
export function RecipeOverview({ recipe, fridgeMatch }: RecipeOverviewProps) {
  const {
    name = '',
    imageUrl,
    summary,
    tags,
    cookTimeMin,
    servings,
    cuisineType,
    category,
    ingredients,
    steps,
  } = recipe;
  const kind = [cuisineType, category].filter(Boolean).join(' · ');
  // 재료·단계 수(count)는 소개·조리 시간이 없는 레시피도 머리가 비지 않게 붙인다. 보일지는 SCSS 설정이 정한다.
  const facts = [
    cookTimeMin ? { label: '조리 시간', value: `${cookTimeMin}분` } : undefined,
    servings ? { label: '분량', value: `${servings}인분` } : undefined,
    ingredients?.length
      ? { label: '재료', value: `${ingredients.length}개`, count: true }
      : undefined,
    steps?.length ? { label: '조리', value: `${steps.length}단계`, count: true } : undefined,
  ].filter((fact) => fact !== undefined);
  const tagList =
    tags
      ?.split(',')
      .map((tag) => tag.trim())
      .filter(Boolean) ?? [];

  return (
    <header id="recipe-overview" className={styles.hero}>
      <div className={styles.media}>
        {/* 사진 전체 보기 세트에서 사진 맨 끝 줄을 늘려 배경을 채운다(그 밖의 세트는 CSS로 숨김). 주소는 실행 중에 정해져 인라인으로 넘긴다. */}
        {imageUrl && (
          <div
            aria-hidden="true"
            className={styles.backdrop}
            style={{ backgroundImage: `url(${JSON.stringify(imageUrl)})` }}
          />
        )}
        <FallbackImage src={imageUrl} alt={name} kind="recipe" className={styles.image} />
      </div>
      <div className={styles.content}>
        <div className={styles.heading}>
          {kind && <Typography.Text className={styles.kind}>{kind}</Typography.Text>}
          <Typography.Title level={1} className={styles.name}>
            {name}
          </Typography.Title>
        </div>
        <div className={styles.details}>
          {facts.length > 0 && (
            <dl className={styles.facts}>
              {facts.map((fact) => (
                <div
                  key={fact.label}
                  className={'count' in fact ? `${styles.fact} ${styles.countFact}` : styles.fact}
                >
                  <dt>{fact.label}</dt>
                  <dd>{fact.value}</dd>
                </div>
              ))}
            </dl>
          )}
          {fridgeMatch.checked && (
            <Typography.Text
              className={fridgeMatch.missingCount === 0 ? styles.ready : styles.missing}
            >
              {fridgeMatch.missingCount === 0
                ? '내 냉장고 재료로 바로 만들 수 있어요'
                : `내 냉장고에 재료 ${fridgeMatch.missingCount}개가 부족해요`}
            </Typography.Text>
          )}
          {summary && (
            <Typography.Paragraph className={styles.summary}>{summary}</Typography.Paragraph>
          )}
          {tagList.length > 0 && (
            <Flex gap="small" wrap>
              {tagList.map((tag) => (
                <Tag key={tag} className={styles.tag}>
                  #{tag}
                </Tag>
              ))}
            </Flex>
          )}
        </div>
      </div>
    </header>
  );
}
