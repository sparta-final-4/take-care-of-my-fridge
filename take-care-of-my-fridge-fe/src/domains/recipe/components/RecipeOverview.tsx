import { Flex, Tag, Typography } from 'antd';

import type { RecipeDetailResponse } from '@/common/api/clients';
import { FallbackImage } from '@/common/components/FallbackImage';
import { formatRecipeMeta } from '@/domains/recipe/format';
import type { RecipeFridgeMatch } from '@/domains/recipe/hooks/useRecipeFridgeMatch';

import styles from './RecipeOverview.module.scss';

interface RecipeOverviewProps {
  recipe: RecipeDetailResponse;
  fridgeMatch: RecipeFridgeMatch;
}

export function RecipeOverview({ recipe, fridgeMatch }: RecipeOverviewProps) {
  const { name = '', imageUrl, summary, tags } = recipe;
  const meta = formatRecipeMeta(recipe);
  const tagList =
    tags
      ?.split(',')
      .map((tag) => tag.trim())
      .filter(Boolean) ?? [];

  return (
    <section className={styles.card}>
      <div className={styles.thumb}>
        <FallbackImage src={imageUrl} alt={name} kind="recipe" className={styles.image} />
      </div>
      <Flex vertical gap="small">
        <Typography.Title level={3} className={styles.name}>
          {name}
        </Typography.Title>
        <Flex align="center" gap="small" wrap>
          {fridgeMatch.checked &&
            (fridgeMatch.missingCount === 0 ? (
              <Tag color="green" className={styles.tag}>
                바로 가능
              </Tag>
            ) : (
              <Tag color="orange" className={styles.tag}>
                재료 {fridgeMatch.missingCount}개 부족
              </Tag>
            ))}
          {meta && <Typography.Text type="secondary">{meta}</Typography.Text>}
        </Flex>
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
      </Flex>
    </section>
  );
}
