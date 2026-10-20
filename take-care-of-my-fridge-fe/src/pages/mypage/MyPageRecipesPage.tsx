import { App, Flex, Spin } from 'antd';
import { useMemo, useState } from 'react';

import type { CookingSortType, CookingTabType, MyCookedRecipeItem } from '@/domains/cooking/api';
import {
  calculateThisMonthSummary,
  countRecipesByTab,
  filterAndSortMyRecipes,
} from '@/domains/cooking/mocks';
import { useMyCookedRecipes } from '@/domains/cooking/queries';
import { MyCookingSummaryCards } from '@/domains/cooking/components/MyCookingSummaryCards';
import { MyRecipeListSection } from '@/domains/cooking/components/MyRecipeListSection';
import { ReviewModal, type InitialReviewData } from '@/domains/review/components/ReviewModal';

import styles from './MyPageRecipesPage.module.scss';

export default function MyPageRecipesPage() {
  const { message } = App.useApp();

  const [tab, setTab] = useState<CookingTabType>('ALL');
  const [sort, setSort] = useState<CookingSortType>('LATEST');
  const [expandedRecipeId, setExpandedRecipeId] = useState<number | null>(1); // 와이어프레임처럼 첫 번째 레시피 기본 펼침

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

  // 요리 완료 레시피 목록 쿼리 (가짜 데이터 연동)
  const { data: rawRecipes = [], isLoading } = useMyCookedRecipes(sort);

  // 탭별 개수 계산
  const counts = useMemo(() => countRecipesByTab(rawRecipes), [rawRecipes]);

  // 필터 및 정렬 적용된 레시피 목록
  const displayedRecipes = useMemo(
    () => filterAndSortMyRecipes(rawRecipes, tab, sort),
    [rawRecipes, tab, sort],
  );

  // 이번 달 통계 데이터 계산
  const summary = useMemo(() => calculateThisMonthSummary(rawRecipes), [rawRecipes]);

  // 아코디언 펼침/접힘 토글 (한 번에 하나만 펼침)
  const handleToggleExpand = (recipeId: number) => {
    setExpandedRecipeId((prev) => (prev === recipeId ? null : recipeId));
  };

  // 후기 모달 열기
  const handleOpenReviewModal = (recipe: MyCookedRecipeItem) => {
    setReviewModalState({
      open: true,
      recipeId: recipe.recipeId,
      recipeName: recipe.recipeName,
      initialReview: recipe.review
        ? {
            id: recipe.review.id,
            rating: recipe.review.rating,
            content: recipe.review.content,
          }
        : null,
    });
  };

  // 후기 모달 닫기
  const handleCloseReviewModal = () => {
    setReviewModalState((prev) => ({ ...prev, open: false }));
  };

  // [레시피 보기] 클릭 핸들러
  const handleViewRecipe = (recipeId: number) => {
    // TODO: 레시피 상세 경로(ROUTES.recipeDetail)가 등록되면 navigate(`${ROUTES.recipeDetail}/${recipeId}`)로 이동
    message.info(`레시피 상세 화면이 준비 중입니다. (레시피 ID: ${recipeId})`);
  };

  if (isLoading) {
    return (
      <Flex justify="center" align="center" className={styles.loadingWrapper}>
        <Spin size="large" />
      </Flex>
    );
  }

  return (
    <div className={styles.container}>
      {/* 1. 상단 내 레시피 관리 목록 섹션 */}
      <MyRecipeListSection
        recipes={displayedRecipes}
        allCount={counts.ALL}
        unreviewedCount={counts.UNREVIEWED}
        reviewedCount={counts.REVIEWED}
        totalCookCount={summary.completedCookCount}
        currentTab={tab}
        currentSort={sort}
        expandedRecipeId={expandedRecipeId}
        onTabChange={setTab}
        onSortChange={setSort}
        onToggleExpand={handleToggleExpand}
        onOpenReviewModal={handleOpenReviewModal}
        onViewRecipe={handleViewRecipe}
      />

      {/* 2. 하단 이번 달 요리 기록 & 후기 유도 카드 */}
      <MyCookingSummaryCards
        summary={summary}
        unreviewedCount={counts.UNREVIEWED}
        onGoToUnreviewed={() => setTab('UNREVIEWED')}
      />

      {/* 3. 후기 쓰기/수정 모달 (실제 API 연동) */}
      <ReviewModal
        open={reviewModalState.open}
        recipeId={reviewModalState.recipeId}
        recipeName={reviewModalState.recipeName}
        initialReview={reviewModalState.initialReview}
        onClose={handleCloseReviewModal}
      />
    </div>
  );
}
