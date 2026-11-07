import { Flex, Tag, Typography } from 'antd';

import type { RecipeDetailResponse } from '@/common/api/clients';
import { FallbackImage } from '@/common/components/FallbackImage';
import type { RecipeFridgeMatch } from '@/domains/recipe/hooks/useRecipeFridgeMatch';

import styles from './RecipeOverview.module.scss';

interface RecipeOverviewProps {
  recipe: RecipeDetailResponse;
  fridgeMatch: RecipeFridgeMatch;
}

// 레시피 머리(레퍼런스 row7seeds 레시피 글): 넓은 화면은 왼쪽 글(분류 · 이름 · 조리 정보 · 소개 · 태그) + 오른쪽 사진,
// 위아래를 얇은 선으로 나눈다. 좁은 화면은 사진이 맨 위.
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
  } = recipe;
  const kind = [cuisineType, category].filter(Boolean).join(' · ');
  const facts = [
    cookTimeMin ? { label: '조리 시간', value: `${cookTimeMin}분` } : undefined,
    servings ? { label: '분량', value: `${servings}인분` } : undefined,
  ].filter((fact) => fact !== undefined);
  const tagList =
    tags
      ?.split(',')
      .map((tag) => tag.trim())
      .filter(Boolean) ?? [];

  return (
    <header className={styles.hero}>
      <div className={styles.media}>
        <FallbackImage src={imageUrl} alt={name} kind="recipe" className={styles.image} />
      </div>
      <div className={styles.content}>
        {kind && <Typography.Text className={styles.kind}>{kind}</Typography.Text>}
        <Typography.Title level={1} className={styles.name}>
          {name}
        </Typography.Title>
        {facts.length > 0 && (
          <dl className={styles.facts}>
            {facts.map((fact) => (
              <div key={fact.label} className={styles.fact}>
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
    </header>
  );
}
