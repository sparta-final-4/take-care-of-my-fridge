import { useQuery } from '@tanstack/react-query';
import { Flex, Tag, Typography } from 'antd';
import type { MouseEvent, ReactNode } from 'react';

import { SEARCH_SECTION_IDS, useSearchKeyword } from '@/domains/search/hooks/useSearchKeyword';
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
            <SectionLink sectionId={SEARCH_SECTION_IDS.recipes}>
              레시피 {formatCount(recipeCount)}
            </SectionLink>
            <SectionLink sectionId={SEARCH_SECTION_IDS.products}>
              재료·밀키트 {formatCount(productCount)}
            </SectionLink>
          </Flex>
        )}
      </Flex>
    </section>
  );
}

interface SectionLinkProps {
  sectionId: string;
  children: ReactNode;
}

// 주소에 #을 남기지 않고 해당 결과 섹션으로 스크롤한다.
function SectionLink({ sectionId, children }: SectionLinkProps) {
  const handleClick = (event: MouseEvent<HTMLAnchorElement>) => {
    event.preventDefault();
    document.getElementById(sectionId)?.scrollIntoView({ behavior: 'smooth' });
  };

  return (
    <a href={`#${sectionId}`} onClick={handleClick} className={styles.countLink}>
      <Tag className={styles.countTag}>{children}</Tag>
    </a>
  );
}

function formatCount(count: number | undefined) {
  return count !== undefined ? count.toLocaleString('ko-KR') : '-';
}
