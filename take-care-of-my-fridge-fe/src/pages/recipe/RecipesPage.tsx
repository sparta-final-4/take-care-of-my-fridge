import { Typography } from 'antd';

import { RecipeListSection } from '@/domains/recipe/components/RecipeListSection';
import { RecipeSearchSection } from '@/domains/recipe/components/RecipeSearchSection';

import styles from './RecipesPage.module.scss';

// 재료/밀키트 화면과 같은 구성(레퍼런스 row7seeds.com 목록 구조): 가운데 제목·한 줄 설명 →
// 왼쪽 검색 조건 칸 + 오른쪽 레시피 목록(위에 붙는 도구 줄: 조건 열기·개수·정렬). 좁은 화면은 조건을 서랍으로 연다.
export default function RecipesPage() {
  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <Typography.Title level={2} className={styles.title}>
          레시피
        </Typography.Title>
        <Typography.Paragraph className={styles.lede}>
          냉장고에 있는 재료로 오늘 만들 요리를 찾아보세요.
        </Typography.Paragraph>
      </header>

      <div className={styles.collection}>
        <aside className={styles.sidebar} aria-label="레시피 검색 조건">
          <RecipeSearchSection />
        </aside>
        <div className={styles.main}>
          <RecipeListSection />
        </div>
      </div>
    </div>
  );
}
