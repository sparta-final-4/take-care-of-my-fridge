import { FilterOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Button, Drawer, Empty, Pagination, Result, Select, Skeleton, Typography } from 'antd';
import { useState } from 'react';

import { SectionLoading } from '@/common/components/SectionLoading';
import { RECIPE_SORT_OPTIONS, useRecipeFilters } from '@/domains/recipe/hooks/useRecipeFilters';
import { recipeQueries } from '@/domains/recipe/queries';

import { RecipeCard } from './RecipeCard';
import styles from './RecipeListSection.module.scss';
import { RecipeSearchSection } from './RecipeSearchSection';

const PAGE_SIZE = 16;

// 재료/밀키트 목록과 같은 도구 줄: [조건 열기(좁은 화면)] [개수] [정렬].
// 넓은 화면의 조건은 화면 왼쪽 칸(RecipesPage)에 있고, 좁은 화면은 같은 조건을 서랍으로 연다.

export function RecipeListSection() {
  const { keyword, request, filtered, sort, page, setSort, setPage, reset } = useRecipeFilters();
  const { data, isPending, isError, isPlaceholderData, refetch } = useQuery(
    recipeQueries.list({ ...request, size: PAGE_SIZE }),
  );
  const [filtersOpen, setFiltersOpen] = useState(false);

  const handleChangePage = (nextPage: number) => {
    setPage(nextPage);
    window.scrollTo({ top: 0 });
  };

  const totalElements = data?.totalElements ?? 0;
  const recipes = data?.content ?? [];
  const label = keyword
    ? `'${keyword}' 검색 결과`
    : filtered
      ? '조건에 맞는 레시피'
      : '레시피 목록';

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
          options={RECIPE_SORT_OPTIONS}
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
            {data ? `${totalElements.toLocaleString('ko-KR')}개 레시피 보기` : '레시피 보기'}
          </Button>
        }
      >
        <RecipeSearchSection />
      </Drawer>

      <div className={styles.body}>
        {isPending ? (
          <Skeleton active paragraph={{ rows: 8 }} />
        ) : isError ? (
          <Result
            status="error"
            title="레시피를 불러오지 못했습니다."
            extra={<Button onClick={() => refetch()}>다시 시도</Button>}
          />
        ) : recipes.length === 0 ? (
          <Empty
            description={filtered ? '조건에 맞는 레시피가 없습니다.' : '등록된 레시피가 없습니다.'}
          >
            {filtered && <Button onClick={reset}>초기화</Button>}
          </Empty>
        ) : (
          <SectionLoading loading={isPlaceholderData}>
            <ul className={styles.grid}>
              {recipes.map((recipe) => (
                <li key={recipe.recipeId} className={styles.item}>
                  <RecipeCard recipe={recipe} />
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
