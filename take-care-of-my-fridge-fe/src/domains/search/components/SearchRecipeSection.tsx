import { ReloadOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Button, Col, Empty, Flex, Pagination, Result, Row, Skeleton } from 'antd';
import { Link } from 'react-router';

import { ROUTES } from '@/app/paths';
import { SectionCard } from '@/common/components/SectionCard';
import { SectionLoading } from '@/common/components/SectionLoading';
import { RecipeCard } from '@/domains/recipe/components/RecipeCard';
import { COOK_TIME_OPTIONS } from '@/domains/recipe/format';
import { recipeQueries } from '@/domains/recipe/queries';
import { SearchFilterRow } from '@/domains/search/components/SearchFilterRow';
import { searchQueries } from '@/domains/search/queries';
import {
  SEARCH_RECIPE_PAGE_SIZE,
  SEARCH_SECTION_IDS,
  useSearchKeyword,
} from '@/domains/search/hooks/useSearchKeyword';

export function SearchRecipeSection() {
  const {
    keyword,
    page,
    setPage,
    cuisineType,
    cookTimeKey,
    recipeFiltered,
    setCuisineType,
    setCookTime,
    resetRecipeFilters,
    recipeRequest,
  } = useSearchKeyword();
  const { data, isPending, isError, isPlaceholderData, refetch } = useQuery(
    searchQueries.recipes(recipeRequest),
  );
  // 분류를 못 받아 오면 음식 종류 칩 줄만 비워 두고 조리 시간 필터는 그대로 쓴다.
  const { data: classifications } = useQuery(recipeQueries.classifications());
  const cuisineTypes = classifications?.cuisineTypes ?? [];

  const handleChangePage = (nextPage: number) => {
    setPage(nextPage);
    // 화면 맨 위가 아니라 레시피 섹션 상단으로 올린다.
    document.getElementById(SEARCH_SECTION_IDS.recipes)?.scrollIntoView();
  };

  const totalElements = data?.totalElements ?? 0;
  const recipes = data?.content ?? [];

  return (
    <SectionCard
      id={SEARCH_SECTION_IDS.recipes}
      title={data ? `레시피 ${totalElements.toLocaleString('ko-KR')}` : '레시피'}
      extra={
        recipeFiltered && (
          <Button type="link" icon={<ReloadOutlined />} onClick={resetRecipeFilters}>
            초기화
          </Button>
        )
      }
    >
      <Flex vertical gap="large">
        <Flex vertical gap="small">
          {cuisineTypes.length > 0 && (
            <SearchFilterRow
              label="음식 종류"
              options={cuisineTypes.map((value) => ({ value, label: value }))}
              selected={cuisineType}
              onChange={setCuisineType}
            />
          )}
          <SearchFilterRow
            label="조리 시간"
            options={COOK_TIME_OPTIONS.map((option) => ({
              value: option.key,
              label: option.label,
            }))}
            selected={cookTimeKey}
            onChange={(value) => setCookTime(COOK_TIME_OPTIONS.find((o) => o.key === value)?.key)}
          />
        </Flex>
        {isPending ? (
          <Skeleton active paragraph={{ rows: 6 }} />
        ) : isError ? (
          <Result
            status="error"
            title="레시피를 불러오지 못했습니다."
            extra={<Button onClick={() => refetch()}>다시 시도</Button>}
          />
        ) : recipes.length === 0 ? (
          recipeFiltered ? (
            <Empty description={`‘${keyword}’ 중 고른 조건에 맞는 레시피가 없습니다.`}>
              <Button onClick={resetRecipeFilters}>초기화</Button>
            </Empty>
          ) : (
            <Empty description={`‘${keyword}’에 맞는 레시피가 없습니다.`}>
              <Link to={ROUTES.recipes}>
                <Button>전체 레시피 보기</Button>
              </Link>
            </Empty>
          )
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
      </Flex>
    </SectionCard>
  );
}
