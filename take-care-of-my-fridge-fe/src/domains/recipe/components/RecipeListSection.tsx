import { useQuery } from '@tanstack/react-query';
import { Button, Col, Empty, Flex, Pagination, Result, Row, Segmented, Skeleton } from 'antd';

import { SectionCard } from '@/common/components/SectionCard';
import { RECIPE_SORT_OPTIONS, useRecipeFilters } from '@/domains/recipe/hooks/useRecipeFilters';
import { recipeQueries } from '@/domains/recipe/queries';

import { RecipeCard } from './RecipeCard';
import styles from './RecipeListSection.module.scss';

const PAGE_SIZE = 16;

export function RecipeListSection() {
  const { keyword, request, filtered, sort, page, setSort, setPage, reset } = useRecipeFilters();
  const { data, isPending, isError, isPlaceholderData, refetch } = useQuery(
    recipeQueries.list({ ...request, size: PAGE_SIZE }),
  );

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
    <SectionCard
      title={data ? `${label} ${totalElements.toLocaleString('ko-KR')}개` : label}
      extra={
        <Segmented
          size="small"
          options={RECIPE_SORT_OPTIONS}
          value={sort}
          onChange={setSort}
          aria-label="정렬"
        />
      }
    >
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
        <Flex vertical gap="large" className={isPlaceholderData ? styles.loading : undefined}>
          <Row gutter={[16, 16]}>
            {recipes.map((recipe) => (
              <Col key={recipe.recipeId} xs={12} md={8} lg={6}>
                <RecipeCard recipe={recipe} />
              </Col>
            ))}
          </Row>
          <Pagination
            align="center"
            current={page}
            pageSize={PAGE_SIZE}
            total={totalElements}
            showSizeChanger={false}
            onChange={handleChangePage}
          />
        </Flex>
      )}
    </SectionCard>
  );
}
