import { DownOutlined, UpOutlined } from '@ant-design/icons';
import { Button, Empty, Flex, Select, Space, Tag, Typography } from 'antd';
import dayjs from 'dayjs';

import type { CookingSortType, CookingTabType, MyCookedRecipeItem } from '../api';

import styles from './MyRecipeListSection.module.scss';

export interface MyRecipeListSectionProps {
  recipes: MyCookedRecipeItem[];
  allCount: number;
  unreviewedCount: number;
  reviewedCount: number;
  totalCookCount: number;
  currentTab: CookingTabType;
  currentSort: CookingSortType;
  expandedRecipeId: number | null;
  onTabChange: (tab: CookingTabType) => void;
  onSortChange: (sort: CookingSortType) => void;
  onToggleExpand: (recipeId: number) => void;
  onOpenReviewModal: (recipe: MyCookedRecipeItem) => void;
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
  onTabChange,
  onSortChange,
  onToggleExpand,
  onOpenReviewModal,
  onViewRecipe,
}: MyRecipeListSectionProps) {
  const tabs: Array<{ key: CookingTabType; label: string; count: number }> = [
    { key: 'ALL', label: '전체', count: allCount },
    { key: 'UNREVIEWED', label: '후기 미작성', count: unreviewedCount },
    { key: 'REVIEWED', label: '후기 작성 완료', count: reviewedCount },
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

      {/* 탭 버튼 목록 */}
      <div className={styles.tabs} role="tablist">
        {tabs.map((tab) => {
          const isActive = currentTab === tab.key;
          return (
            <button
              key={tab.key}
              type="button"
              role="tab"
              aria-selected={isActive}
              className={
                isActive ? `${styles.tabButton} ${styles.tabButtonActive}` : styles.tabButton
              }
              onClick={() => onTabChange(tab.key)}
            >
              <span>{tab.label}</span>
              <span className={styles.tabCount}>{tab.count}</span>
            </button>
          );
        })}
      </div>

      {/* 레시피 표 */}
      {recipes.length === 0 ? (
        <div className={styles.emptyWrapper}>
          <Empty description="아직 요리 완료한 레시피가 없어요" />
        </div>
      ) : (
        <div className={styles.tableWrapper}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th className={styles.thRecipe}>레시피</th>
                <th className={styles.thCookCount}>요리 횟수</th>
                <th className={styles.thLastCooked}>마지막 요리</th>
                <th className={styles.thReview}>내 후기</th>
                <th className={styles.thActions}>
                  <span className="sr-only">액션</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {recipes.map((item) => {
                const isExpanded = expandedRecipeId === item.recipeId;
                const hasReview = Boolean(item.review);

                return (
                  <tr key={item.recipeId} className={styles.rowGroup}>
                    <td colSpan={5} className={styles.rowCell}>
                      <div className={styles.mainRow}>
                        {/* 레시피 정보 (이미지, 배지, 이름) */}
                        <div className={styles.colRecipe}>
                          <div className={styles.thumbnail}>
                            {item.imageUrl ? (
                              <img src={item.imageUrl} alt={item.recipeName} />
                            ) : (
                              <span className={styles.placeholderImg}>[이미지]</span>
                            )}
                          </div>
                          <div className={styles.recipeInfo}>
                            {item.isAiRecommended && (
                              <Tag color="default" className={styles.aiTag}>
                                AI 추천
                              </Tag>
                            )}
                            <Typography.Text strong className={styles.recipeName}>
                              {item.recipeName}
                            </Typography.Text>
                          </div>
                        </div>

                        {/* 요리 횟수 (펼침 토글 버튼) */}
                        <div className={styles.colCookCount}>
                          <button
                            type="button"
                            className={styles.expandTrigger}
                            onClick={() => onToggleExpand(item.recipeId)}
                            aria-expanded={isExpanded}
                            aria-label={`${item.recipeName} 요리 기록 펼치기`}
                          >
                            <span>{item.cookCount}회</span>
                            {isExpanded ? (
                              <UpOutlined className={styles.expandIcon} />
                            ) : (
                              <DownOutlined className={styles.expandIcon} />
                            )}
                          </button>
                        </div>

                        {/* 마지막 요리 일자 (MM.DD) */}
                        <div className={styles.colLastCooked}>
                          <Typography.Text type="secondary">
                            {dayjs(item.lastCookedAt).format('MM.DD')}
                          </Typography.Text>
                        </div>

                        {/* 내 후기 (별점 또는 미작성 배지) */}
                        <div className={styles.colReview}>
                          {hasReview && item.review ? (
                            <span className={styles.ratingText}>★ {item.review.rating}</span>
                          ) : (
                            <Tag className={styles.unreviewedTag}>미작성</Tag>
                          )}
                        </div>

                        {/* 버튼들: [레시피 보기], [후기 쓰기 / 후기 수정] */}
                        <div className={styles.colActions}>
                          <Space size="small">
                            <Button
                              type="default"
                              size="middle"
                              onClick={() => onViewRecipe(item.recipeId)}
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
                                type="primary"
                                size="middle"
                                className={styles.primaryActionButton}
                                onClick={() => onOpenReviewModal(item)}
                              >
                                후기 쓰기
                              </Button>
                            )}
                          </Space>
                        </div>
                      </div>

                      {/* 아코디언 펼침 내용: 내 후기 + 요리 기록 */}
                      {isExpanded && (
                        <div className={styles.expandedPanel}>
                          {/* 내 후기 영역 */}
                          {item.review ? (
                            <div className={styles.detailReviewSection}>
                              <Typography.Text type="secondary" className={styles.detailLabel}>
                                내 후기
                              </Typography.Text>
                              <div className={styles.detailReviewContent}>
                                <Flex align="center" gap="small" className={styles.reviewHeader}>
                                  <span className={styles.ratingText}>★ {item.review.rating}</span>
                                  <Typography.Text className={styles.reviewText}>
                                    &ldquo;{item.review.content}&rdquo;
                                  </Typography.Text>
                                </Flex>
                                <Typography.Text type="secondary" className={styles.reviewDate}>
                                  {dayjs(item.review.updatedAt).format('MM.DD')} 수정됨
                                </Typography.Text>
                              </div>
                            </div>
                          ) : null}

                          {/* 요리 기록 영역 */}
                          <div className={styles.detailHistorySection}>
                            <Typography.Text type="secondary" className={styles.detailLabel}>
                              요리 기록
                            </Typography.Text>
                            <div className={styles.historyList}>
                              {item.history.map((hist) => (
                                <div key={hist.id} className={styles.historyItem}>
                                  <span className={styles.historyDate}>
                                    {dayjs(hist.date).format('MM.DD')}
                                  </span>
                                  <Tag
                                    color={hist.result === 'SUCCESS' ? 'default' : undefined}
                                    className={
                                      hist.result === 'SUCCESS'
                                        ? styles.successTag
                                        : styles.failedTag
                                    }
                                  >
                                    {hist.result === 'SUCCESS' ? '성공' : '실패'}
                                  </Tag>
                                  <span className={styles.historyMemo}>{hist.memo}</span>
                                </div>
                              ))}
                            </div>
                          </div>
                        </div>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
