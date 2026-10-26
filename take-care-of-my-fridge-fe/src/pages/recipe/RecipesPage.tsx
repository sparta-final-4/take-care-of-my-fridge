import { Typography } from 'antd';

import { RecipeListSection } from '@/domains/recipe/components/RecipeListSection';
import { RecipeSearchSection } from '@/domains/recipe/components/RecipeSearchSection';

import styles from './RecipesPage.module.scss';

export default function RecipesPage() {
  return (
    <div className={styles.page}>
      <Typography.Title level={2} className={styles.title}>
        레시피
      </Typography.Title>
      <RecipeSearchSection />
      <RecipeListSection />
    </div>
  );
}
