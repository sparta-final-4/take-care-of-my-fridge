import { ReloadOutlined } from '@ant-design/icons';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Button, Col, Empty, Flex, Pagination, Result, Row, Skeleton } from 'antd';
import type { MouseEvent } from 'react';
import { Link } from 'react-router';

import { ROUTES } from '@/app/paths';
import { SectionCard } from '@/common/components/SectionCard';
import { SectionLoading } from '@/common/components/SectionLoading';
import { RecipeCard } from '@/domains/recipe/components/RecipeCard';
import { COOK_TIME_OPTIONS } from '@/domains/recipe/format';
import { recipeQueries } from '@/domains/recipe/queries';
import { recordSearchRecipeClick } from '@/domains/search/api';
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
  const { mutate: recordClick } = useMutation({
    mutationFn: recordSearchRecipeClick,
    // 클릭 기록은 사용자가 모르게 남긴다. 실패해도 알리지 않고 상세 이동은 그대로 한다.
    meta: { skipGlobalError: true },
  });

  // 카드의 상세 링크를 누를 때만 기록한다.
  const handleClickRecipe = (recipeId: number) => (event: MouseEvent<HTMLElement>) => {
    const link = event.target instanceof Element ? event.target.closest('a') : null;
    if (!link || !event.currentTarget.contains(link)) return;
    recordClick({ recipeId, keyword });
  };

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
                  <Col
                    key={recipe.recipeId}
                    xs={12}
                    md={8}
                    lg={6}
                    onClick={
                      recipe.recipeId === undefined ? undefined : handleClickRecipe(recipe.recipeId)
                    }
                  >
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
