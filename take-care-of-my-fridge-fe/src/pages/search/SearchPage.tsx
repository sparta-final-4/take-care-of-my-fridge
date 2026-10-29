import { Empty } from 'antd';

import { SearchProductSection } from '@/domains/search/components/SearchProductSection';
import { SearchRecipeSection } from '@/domains/search/components/SearchRecipeSection';
import { SearchSummarySection } from '@/domains/search/components/SearchSummarySection';
import { useSearchKeyword } from '@/domains/search/hooks/useSearchKeyword';

import styles from './SearchPage.module.scss';

export default function SearchPage() {
  const { keyword } = useSearchKeyword();

  return (
    <div className={styles.page}>
      <SearchSummarySection />
      {keyword ? (
        <>
          <SearchRecipeSection />
          <SearchProductSection />
        </>
      ) : (
        <Empty description="상단 검색창에 재료나 요리 이름을 입력해 주세요." />
      )}
    </div>
  );
}
