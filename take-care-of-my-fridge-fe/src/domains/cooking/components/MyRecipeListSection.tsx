import { Fragment } from 'react';
import { DownOutlined, UpOutlined } from '@ant-design/icons';
import { Button, Empty, Flex, Pagination, Select, Space, Tabs, Tag, Typography } from 'antd';
import dayjs from 'dayjs';

import type { CookingSortType, CookingTabType, MyRecipeResponse } from '../api';
import { RecipeCookingHistorySection } from './RecipeCookingHistorySection';

import styles from './MyRecipeListSection.module.scss';

export interface MyRecipeListSectionProps {
  recipes: MyRecipeResponse[];
  allCount: number;
  unreviewedCount: number;
  reviewedCount: number;
  totalCookCount: number;
  currentTab: CookingTabType;
  currentSort: CookingSortType;
  expandedRecipeId: number | null;
  currentPage: number;
  pageSize: number;
  totalElements: number;
  onTabChange: (tab: CookingTabType) => void;
  onSortChange: (sort: CookingSortType) => void;
  onPageChange: (page: number) => void;
  onToggleExpand: (recipeId: number) => void;
  onOpenReviewModal: (recipe: MyRecipeResponse) => void;
  onViewRecipe: (recipeId: number) => void;
}

export function MyRecipeListSection({
  recipes,
  allCount,
  unreviewedCount,
  reviewedCount,
  totalCookCount,
  currentTab,
  currentSort,
  expandedRecipeId,
  currentPage,
  pageSize,
  totalElements,
  onTabChange,
  onSortChange,
  onPageChange,
  onToggleExpand,
  onOpenReviewModal,
  onViewRecipe,
}: MyRecipeListSectionProps) {
  const tabContent =
    recipes.length === 0 ? (
      <div className={styles.emptyWrapper}>
        <Empty description="아직 요리 완료한 레시피가 없어요" />
      </div>
    ) : (
      <>
        <div className={styles.tableWrapper}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th scope="col" className={styles.thRecipe}>
                  레시피
                </th>
                <th scope="col" className={styles.thCookCount}>
                  요리 횟수
                </th>
                <th scope="col" className={styles.thLastCooked}>
                  마지막 요리
                </th>
                <th scope="col" className={styles.thReview}>
                  내 후기
                </th>
                <th scope="col" className={styles.thActions} aria-label="액션" />
              </tr>
            </thead>
            <tbody>
              {recipes.map((item) => {
                const recipeId = item.recipeId ?? 0;
                const isExpanded = expandedRecipeId === recipeId;
                const hasReview = Boolean(item.review);

                return (
                  <Fragment key={recipeId}>
                    <tr className={styles.dataRow}>
                      {/* 레시피 정보 (이미지, 배지, 이름) */}
                      <td className={styles.tdRecipe}>
                        <div className={styles.recipeCell}>
                          <button
                            type="button"
                            className={styles.thumbnailButton}
                            onClick={() => onViewRecipe(recipeId)}
                            aria-label={`${item.recipeName ?? '레시피'} 상세 보기`}
                          >
                            <div className={styles.thumbnail}>
                              {item.recipeImageUrl ? (
                                <img src={item.recipeImageUrl} alt={item.recipeName ?? '레시피'} />
                              ) : (
                                <span className={styles.placeholderImg}>[이미지]</span>
                              )}
                            </div>
                          </button>
                          <div className={styles.recipeInfo}>
                            {item.fromChat && (
                              <Tag color="default" className={styles.aiTag}>
                                AI 추천
                              </Tag>
                            )}
                            <button
                              type="button"
                              className={styles.recipeNameButton}
                              onClick={() => onViewRecipe(recipeId)}
                              title={item.recipeName}
                            >
                              {item.recipeName}
                            </button>
                          </div>
                        </div>
                      </td>

                      {/* 요리 횟수 (펼침 토글 버튼) */}
                      <td className={styles.tdCookCount}>
                        <button
                          type="button"
                          className={styles.expandTrigger}
                          onClick={() => onToggleExpand(recipeId)}
                          aria-expanded={isExpanded}
                          aria-label={`${item.recipeName ?? '레시피'} 요리 기록 펼치기`}
                        >
                          <span>{item.cookCount ?? 0}회</span>
                          {isExpanded ? (
                            <UpOutlined className={styles.expandIcon} />
                          ) : (
                            <DownOutlined className={styles.expandIcon} />
                          )}
                        </button>
                      </td>

                      {/* 마지막 요리 일자 (MM.DD) */}
                      <td className={styles.tdLastCooked}>
                        <Typography.Text type="secondary">
                          {item.lastCookedAt ? dayjs(item.lastCookedAt).format('MM.DD') : '-'}
                        </Typography.Text>
                      </td>

                      {/* 내 후기 (별점 또는 미작성 배지) */}
                      <td className={styles.tdReview}>
                        {hasReview && item.review ? (
                          <span className={styles.ratingText}>★ {item.review.rating ?? 0}</span>
                        ) : (
                          <Tag className={styles.unreviewedTag}>미작성</Tag>
                        )}
                      </td>

                      {/* 버튼들: [레시피 보기], [후기 쓰기 / 후기 수정] */}
                      <td className={styles.tdActions}>
                        <Space size="small">
                          <Button
                            type="default"
                            size="middle"
                            onClick={() => onViewRecipe(recipeId)}
                          >
                            레시피 보기
                          </Button>
                          {hasReview ? (
                            <Button
                              type="default"
                              size="middle"
                              onClick={() => onOpenReviewModal(item)}
                            >
                              후기 수정
                            </Button>
                          ) : (
                            <Button
                              color="default"
                              variant="solid"
                              size="middle"
                              onClick={() => onOpenReviewModal(item)}
                            >
                              후기 쓰기
                            </Button>
                          )}
                        </Space>
                      </td>
                    </tr>

                    {/* 아코디언 펼침 내용: 내 후기 + 요리 기록 */}
                    {isExpanded && (
                      <tr className={styles.expandedRow}>
                        <td colSpan={5} className={styles.expandedCell}>
                          <div className={styles.expandedPanel}>
                            {/* 내 후기 영역 */}
                            {item.review ? (
                              <div className={styles.detailReviewSection}>
                                <Typography.Text type="secondary" className={styles.detailLabel}>
                                  내 후기
                                </Typography.Text>
                                <div className={styles.detailReviewContent}>
                                  <Flex align="center" gap="small" className={styles.reviewHeader}>
                                    <span className={styles.ratingText}>
                                      ★ {item.review.rating ?? 0}
                                    </span>
                                    <Typography.Text className={styles.reviewText}>
                                      &ldquo;{item.review.content}&rdquo;
                                    </Typography.Text>
                                  </Flex>
                                  {item.review.updatedAt && (
                                    <Typography.Text type="secondary" className={styles.reviewDate}>
                                      {dayjs(item.review.updatedAt).format('MM.DD')} 수정됨
                                    </Typography.Text>
                                  )}
                                </div>
                              </div>
                            ) : null}

                            {/* 요리 기록 영역 (펼쳤을 때만 조회) */}
                            <RecipeCookingHistorySection recipeId={recipeId} />
                          </div>
                        </td>
                      </tr>
                    )}
                  </Fragment>
                );
              })}
            </tbody>
          </table>
        </div>

        {totalElements > pageSize && (
          <div className={styles.paginationWrapper}>
            <Pagination
              current={currentPage}
              pageSize={pageSize}
              total={totalElements}
              onChange={onPageChange}
              showSizeChanger={false}
            />
          </div>
        )}
      </>
    );

  const tabItems = [
    {
      key: 'ALL',
      label: (
        <span>
          전체 <span className={styles.tabCount}>{allCount}</span>
        </span>
      ),
      children: tabContent,
    },
    {
      key: 'UNREVIEWED',
      label: (
        <span>
          후기 미작성 <span className={styles.tabCount}>{unreviewedCount}</span>
        </span>
      ),
      children: tabContent,
    },
    {
      key: 'REVIEWED',
      label: (
        <span>
          후기 작성 완료 <span className={styles.tabCount}>{reviewedCount}</span>
        </span>
      ),
      children: tabContent,
    },
  ];

  return (
    <section className={styles.section} aria-label="내 레시피 관리">
      {/* 상단 헤더: 제목 및 요약 정보, 정렬 */}
      <div className={styles.header}>
        <div>
          <Typography.Title level={4} className={styles.title}>
            내 레시피 관리
          </Typography.Title>
          <Typography.Text type="secondary" className={styles.subtitle}>
            요리 완료한 레시피 {allCount}개 · 총 {totalCookCount}번 요리
          </Typography.Text>
        </div>
        <Select<CookingSortType>
          value={currentSort}
          onChange={onSortChange}
          className={styles.sortSelect}
          options={[
            { value: 'LATEST', label: '최근 요리순' },
            { value: 'MOST_COOKED', label: '많이 만든 순' },
          ]}
        />
      </div>

      {/* 탭 및 목록 컨텐츠 */}
      <Tabs
        activeKey={currentTab}
        onChange={(key) => onTabChange(key as CookingTabType)}
        items={tabItems}
        className={styles.tabs}
        destroyInactiveTabPane
      />
    </section>
  );
}
