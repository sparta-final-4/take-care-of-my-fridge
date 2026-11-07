import { FilterOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Button, Drawer, Empty, Pagination, Result, Select, Skeleton, Typography } from 'antd';
import { useState } from 'react';
import { useLocation } from 'react-router';

import { SectionLoading } from '@/common/components/SectionLoading';
import { PRODUCT_SORT_OPTIONS, useProductFilters } from '@/domains/product/hooks/useProductFilters';
import { productQueries } from '@/domains/product/queries';

import { ProductCard } from './ProductCard';
import { ProductSearchSection } from './ProductSearchSection';
import styles from './ProductListSection.module.scss';

const PAGE_SIZE = 16;

// 레퍼런스(row7seeds.com 상품 목록)처럼 목록 위에 붙는 도구 줄: [필터(좁은 화면)] [개수] [정렬].
// 넓은 화면의 필터는 화면 왼쪽 칸(ProductsPage)에 있고, 좁은 화면은 필터 버튼이 같은 필터를 서랍으로 연다.
export function ProductListSection() {
  const { keyword, request, filtered, sort, page, setSort, setPage, reset } = useProductFilters();
  const { data, isPending, isError, isPlaceholderData, refetch } = useQuery(
    productQueries.list({ ...request, size: PAGE_SIZE }),
  );
  const { search } = useLocation();
  const [filtersOpen, setFiltersOpen] = useState(false);

  const handleChangePage = (nextPage: number) => {
    setPage(nextPage);
    window.scrollTo({ top: 0 });
  };

  const totalElements = data?.totalElements ?? 0;
  const products = data?.content ?? [];
  const label = keyword ? `'${keyword}' 검색 결과` : filtered ? '조건에 맞는 상품' : '전체 상품';

  return (
    <section className={styles.section} aria-label={label}>
      <div className={styles.toolbar}>
        <Button
          type="text"
          icon={<FilterOutlined />}
          aria-label="검색 조건 열기"
          onClick={() => setFiltersOpen(true)}
          className={styles.filterButton}
        />
        <Typography.Text className={styles.count}>
          {data ? `${label} ${totalElements.toLocaleString('ko-KR')}개` : label}
        </Typography.Text>
        <Select
          variant="borderless"
          options={PRODUCT_SORT_OPTIONS}
          value={sort}
          onChange={setSort}
          aria-label="정렬"
          popupMatchSelectWidth={false}
          className={styles.sort}
        />
      </div>

      <Drawer
        placement="left"
        size="min(92vw, 400px)"
        open={filtersOpen}
        onClose={() => setFiltersOpen(false)}
        footer={
          <Button type="primary" block onClick={() => setFiltersOpen(false)}>
            {data ? `${totalElements.toLocaleString('ko-KR')}개 상품 보기` : '상품 보기'}
          </Button>
        }
      >
        <ProductSearchSection />
      </Drawer>

      <div className={styles.body}>
        {isPending ? (
          <Skeleton active paragraph={{ rows: 8 }} />
        ) : isError ? (
          <Result
            status="error"
            title="상품을 불러오지 못했습니다."
            extra={<Button onClick={() => refetch()}>다시 시도</Button>}
          />
        ) : products.length === 0 ? (
          <Empty
            description={filtered ? '조건에 맞는 상품이 없습니다.' : '판매 중인 상품이 없습니다.'}
          >
            {filtered && <Button onClick={reset}>초기화</Button>}
          </Empty>
        ) : (
          <SectionLoading loading={isPlaceholderData}>
            <ul className={styles.grid}>
              {products.map((product) => (
                <li key={product.productId} className={styles.item}>
                  <ProductCard product={product} listSearch={search} fromList />
                </li>
              ))}
            </ul>
            <Pagination
              align="center"
              current={page}
              pageSize={PAGE_SIZE}
              total={totalElements}
              showSizeChanger={false}
              onChange={handleChangePage}
              className={styles.pagination}
            />
          </SectionLoading>
        )}
      </div>
    </section>
  );
}
