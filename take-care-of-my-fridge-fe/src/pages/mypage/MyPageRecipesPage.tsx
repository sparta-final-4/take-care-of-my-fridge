import { Button, Flex, Result, Spin } from 'antd';
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { generatePath, useLocation, useNavigate } from 'react-router';

import { ROUTES } from '@/app/paths';
import {
  MyRecipeSearchRequestReviewStatusEnum,
  MyRecipeSearchRequestSortEnum,
  type MyRecipeResponse,
} from '@/common/api/clients';
import type { CookingSortType, CookingTabType } from '@/domains/cooking/api';
import { cookingQueries } from '@/domains/cooking/queries';
import { MyCookingSummaryCards } from '@/domains/cooking/components/MyCookingSummaryCards';
import { MyRecipeListSection } from '@/domains/cooking/components/MyRecipeListSection';
import { ReviewModal, type InitialReviewData } from '@/domains/review/components/ReviewModal';

import styles from './MyPageRecipesPage.module.scss';

const PAGE_SIZE = 5;

export default function MyPageRecipesPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();

  const locationState = location.state as { expandedRecipeId?: number } | null;
  const initialExpandedId = locationState?.expandedRecipeId ?? null;

  const [page, setPage] = useState<number>(1);
  const [tab, setTab] = useState<CookingTabType>('ALL');
  const [sort, setSort] = useState<CookingSortType>('LATEST');
  const [expandedRecipeId, setExpandedRecipeId] = useState<number | null>(initialExpandedId);

  // 후기 작성/수정 모달 상태
  const [reviewModalState, setReviewModalState] = useState<{
    open: boolean;
    recipeId: number;
    recipeName: string;
    initialReview: InitialReviewData | null;
  }>({
    open: false,
    recipeId: 0,
    recipeName: '',
    initialReview: null,
  });

  // 탭 매핑 (ALL -> ALL, UNREVIEWED -> NOT_WRITTEN, REVIEWED -> WRITTEN)
  const reviewStatusParam =
    tab === 'UNREVIEWED'
      ? MyRecipeSearchRequestReviewStatusEnum.NotWritten
      : tab === 'REVIEWED'
        ? MyRecipeSearchRequestReviewStatusEnum.Written
        : MyRecipeSearchRequestReviewStatusEnum.All;

  // 정렬 매핑 (LATEST -> RECENT, MOST_COOKED -> MOST)
  const sortParam =
    sort === 'MOST_COOKED'
      ? MyRecipeSearchRequestSortEnum.Most
      : MyRecipeSearchRequestSortEnum.Recent;

  // 실제 API 연동 쿼리 (탭·정렬·페이지 전환 시 깜빡임 방지를 위해 keepPreviousData 적용)
  const {
    data: myRecipeData,
    isLoading,
    isError,
    refetch,
  } = useQuery({
    ...cookingQueries.myRecipes({
      page,
      size: PAGE_SIZE,
      reviewStatus: reviewStatusParam,
      sort: sortParam,
    }),
    placeholderData: keepPreviousData,
  });

  const recipes = myRecipeData?.recipes?.content ?? [];
  const totalElements = myRecipeData?.recipes?.totalElements ?? 0;
  const summary = myRecipeData?.summary;

  const allCount = Number(summary?.recipeCount ?? 0);
  const totalCookCount = Number(summary?.totalCookCount ?? 0);
  const unreviewedCount = Number(summary?.notWrittenCount ?? 0);
  const reviewedCount = Number(summary?.writtenCount ?? 0);

  // 탭 변경
  const handleTabChange = (nextTab: CookingTabType) => {
    setTab(nextTab);
    setPage(1);
    setExpandedRecipeId(null);
  };

  // 정렬 변경
  const handleSortChange = (nextSort: CookingSortType) => {
    setSort(nextSort);
    setPage(1);
    setExpandedRecipeId(null);
  };

  // 페이지 변경
  const handlePageChange = (nextPage: number) => {
    setPage(nextPage);
    setExpandedRecipeId(null);
  };

  // 아코디언 펼침/접힘 토글 (한 번에 하나만 펼침)
  const handleToggleExpand = (recipeId: number) => {
    setExpandedRecipeId((prev) => (prev === recipeId ? null : recipeId));
  };

  // 후기 모달 열기
  const handleOpenReviewModal = (recipe: MyRecipeResponse) => {
    if (!recipe.recipeId) return;
    setReviewModalState({
      open: true,
      recipeId: recipe.recipeId,
      recipeName: recipe.recipeName ?? '',
      initialReview: recipe.review?.id
        ? {
            id: recipe.review.id,
            rating: recipe.review.rating ?? 5,
            content: recipe.review.content ?? '',
          }
        : null,
    });
  };

  // 후기 모달 닫기
  const handleCloseReviewModal = () => {
    setReviewModalState((prev) => ({ ...prev, open: false }));
  };

  // [레시피 보기] 및 레시피 이름 클릭 시 레시피 상세 화면으로 이동
  const handleViewRecipe = (recipeId: number) => {
    navigate(generatePath(ROUTES.recipeDetail, { recipeId: String(recipeId) }));
  };

  if (isLoading) {
    return (
      <Flex justify="center" align="center" className={styles.loadingWrapper}>
        <Spin size="large" />
      </Flex>
    );
  }

  if (isError) {
    return (
      <div className={styles.container}>
        <Result
          status="error"
          title="내 레시피를 불러오지 못했어요"
          extra={<Button onClick={() => void refetch()}>다시 시도</Button>}
        />
      </div>
    );
  }

  // 하단 지금까지 요리 기록 카드용 요약 데이터 (전체 데이터 기준)
  const bottomSummary = {
    completedCookCount: totalCookCount,
    consumedIngredientCount: Number(summary?.consumedIngredientCount ?? 0),
    mostCookedRecipeName: summary?.mostCookedRecipe?.recipeName || '-',
    mostCookedRecipeCount: Number(summary?.mostCookedRecipe?.cookCount ?? 0),
  };

  return (
    <div className={styles.container}>
      {/* 1. 상단 내 레시피 관리 목록 섹션 */}
      <MyRecipeListSection
        recipes={recipes}
        allCount={allCount}
        unreviewedCount={unreviewedCount}
        reviewedCount={reviewedCount}
        totalCookCount={totalCookCount}
        currentTab={tab}
        currentSort={sort}
        expandedRecipeId={expandedRecipeId}
        currentPage={page}
        pageSize={PAGE_SIZE}
        totalElements={totalElements}
        onTabChange={handleTabChange}
        onSortChange={handleSortChange}
        onPageChange={handlePageChange}
        onToggleExpand={handleToggleExpand}
        onOpenReviewModal={handleOpenReviewModal}
        onViewRecipe={handleViewRecipe}
      />

      {/* 2. 하단 지금까지 요리 기록 & 후기 유도 카드 */}
      <MyCookingSummaryCards
        summary={bottomSummary}
        unreviewedCount={unreviewedCount}
        onGoToUnreviewed={() => handleTabChange('UNREVIEWED')}
      />

      {/* 3. 후기 쓰기/수정 모달 (저장 성공 시 cookingQueries.all 무효화) */}
      <ReviewModal
        open={reviewModalState.open}
        recipeId={reviewModalState.recipeId}
        recipeName={reviewModalState.recipeName}
        initialReview={reviewModalState.initialReview}
        onClose={handleCloseReviewModal}
        onSuccess={() => {
          queryClient.invalidateQueries({ queryKey: cookingQueries.all });
        }}
      />
    </div>
  );
}
