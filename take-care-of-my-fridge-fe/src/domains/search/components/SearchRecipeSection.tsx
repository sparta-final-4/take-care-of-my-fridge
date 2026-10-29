import { useQuery } from '@tanstack/react-query';
import { Button, Col, Empty, Flex, Pagination, Result, Row, Skeleton } from 'antd';
import { Link } from 'react-router';

import { ROUTES } from '@/app/paths';
import { SectionCard } from '@/common/components/SectionCard';
import { SectionLoading } from '@/common/components/SectionLoading';
import { RecipeCard } from '@/domains/recipe/components/RecipeCard';
import { searchQueries } from '@/domains/search/queries';
import { SEARCH_RECIPE_PAGE_SIZE, useSearchKeyword } from '@/domains/search/hooks/useSearchKeyword';

export function SearchRecipeSection() {
  const { keyword, page, setPage, recipeRequest } = useSearchKeyword();
  const { data, isPending, isError, isPlaceholderData, refetch } = useQuery(
    searchQueries.recipes(recipeRequest),
  );

  const handleChangePage = (nextPage: number) => {
    setPage(nextPage);
    window.scrollTo({ top: 0 });
  };

  const totalElements = data?.totalElements ?? 0;
  const recipes = data?.content ?? [];

  return (
    <SectionCard
      title={data ? `레시피 ${totalElements.toLocaleString('ko-KR')}` : '레시피'}
      extra={
        <Link to={`${ROUTES.recipes}?${new URLSearchParams({ q: keyword })}`}>
          레시피 화면에서 조건 더 고르기 &gt;
        </Link>
      }
    >
      {isPending ? (
        <Skeleton active paragraph={{ rows: 6 }} />
      ) : isError ? (
        <Result
          status="error"
          title="레시피를 불러오지 못했습니다."
          extra={<Button onClick={() => refetch()}>다시 시도</Button>}
        />
      ) : recipes.length === 0 ? (
        <Empty description={`‘${keyword}’에 맞는 레시피가 없습니다.`}>
          <Link to={ROUTES.recipes}>
            <Button>전체 레시피 보기</Button>
          </Link>
        </Empty>
      ) : (
        <SectionLoading loading={isPlaceholderData}>
          <Flex vertical gap="large">
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
              pageSize={SEARCH_RECIPE_PAGE_SIZE}
              total={totalElements}
              showSizeChanger={false}
              onChange={handleChangePage}
            />
          </Flex>
        </SectionLoading>
      )}
    </SectionCard>
  );
}
