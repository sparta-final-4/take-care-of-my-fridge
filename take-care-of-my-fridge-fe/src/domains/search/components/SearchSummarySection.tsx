import { useQuery } from '@tanstack/react-query';
import { Flex, Tag, Typography } from 'antd';

import { useSearchKeyword } from '@/domains/search/hooks/useSearchKeyword';
import { searchQueries } from '@/domains/search/queries';

import styles from './SearchSummarySection.module.scss';

export function SearchSummarySection() {
  const { keyword, recipeRequest, productRequest } = useSearchKeyword();
  const enabled = Boolean(keyword);
  const { data: recipes } = useQuery({ ...searchQueries.recipes(recipeRequest), enabled });
  const { data: products } = useQuery({ ...searchQueries.products(productRequest), enabled });
  const recipeCount = recipes?.totalElements;
  const productCount = products?.totalElements;
  // 두 결과가 모두 와야 합계를 보여 준다.
  const totalCount =
    recipeCount !== undefined && productCount !== undefined
      ? recipeCount + productCount
      : undefined;

  return (
    <section className={styles.summary}>
      <Flex vertical gap="small">
        <Typography.Title level={3} className={styles.title}>
          {keyword ? (
            <>
              <span className={styles.keyword}>‘{keyword}’</span> 검색 결과
              {totalCount !== undefined && ` ${totalCount.toLocaleString('ko-KR')}건`}
            </>
          ) : (
            '통합 검색'
          )}
        </Typography.Title>
        {keyword && (
          <Flex wrap gap="small">
            <Tag className={styles.countTag}>레시피 {formatCount(recipeCount)}</Tag>
            <Tag className={styles.countTag}>재료·밀키트 {formatCount(productCount)}</Tag>
          </Flex>
        )}
      </Flex>
    </section>
  );
}

function formatCount(count: number | undefined) {
  return count !== undefined ? count.toLocaleString('ko-KR') : '-';
}
