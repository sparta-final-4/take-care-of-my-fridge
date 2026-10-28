import { useQuery } from '@tanstack/react-query';
import { Flex, Tag, Typography } from 'antd';

import { recipeQueries } from '@/domains/recipe/queries';
import { useSearchKeyword } from '@/domains/search/hooks/useSearchKeyword';

import styles from './SearchSummarySection.module.scss';

export function SearchSummarySection() {
  const { keyword, recipeRequest } = useSearchKeyword();
  const { data } = useQuery({ ...recipeQueries.list(recipeRequest), enabled: Boolean(keyword) });
  const recipeCount = data?.totalElements;

  return (
    <section className={styles.summary}>
      <Flex vertical gap="small">
        <Typography.Title level={3} className={styles.title}>
          {keyword ? (
            <>
              <span className={styles.keyword}>‘{keyword}’</span> 검색 결과
              {recipeCount !== undefined && ` ${recipeCount.toLocaleString('ko-KR')}건`}
            </>
          ) : (
            '통합 검색'
          )}
        </Typography.Title>
        {keyword && (
          <Flex wrap gap="small">
            <Tag className={styles.countTag}>
              레시피 {recipeCount !== undefined ? recipeCount.toLocaleString('ko-KR') : '-'}
            </Tag>
          </Flex>
        )}
      </Flex>
    </section>
  );
}
