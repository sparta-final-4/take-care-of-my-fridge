import { Fragment } from 'react';
import { DownOutlined, UpOutlined } from '@ant-design/icons';
import { Button, Empty, Flex, Select, Space, Tabs, Tag, Typography } from 'antd';
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
  const tabContent =
    recipes.length === 0 ? (
      <div className={styles.emptyWrapper}>
        <Empty description="아직 요리 완료한 레시피가 없어요" />
      </div>
    ) : (
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
              const isExpanded = expandedRecipeId === item.recipeId;
              const hasReview = Boolean(item.review);

              return (
                <Fragment key={item.recipeId}>
                  <tr className={styles.dataRow}>
                    {/* 레시피 정보 (이미지, 배지, 이름) */}
                    <td className={styles.tdRecipe}>
                      <div className={styles.recipeCell}>
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
                    </td>

                    {/* 요리 횟수 (펼침 토글 버튼) */}
                    <td className={styles.tdCookCount}>
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
                    </td>

                    {/* 마지막 요리 일자 (MM.DD) */}
                    <td className={styles.tdLastCooked}>
                      <Typography.Text type="secondary">
                        {dayjs(item.lastCookedAt).format('MM.DD')}
                      </Typography.Text>
                    </td>

                    {/* 내 후기 (별점 또는 미작성 배지) */}
                    <td className={styles.tdReview}>
                      {hasReview && item.review ? (
                        <span className={styles.ratingText}>★ {item.review.rating}</span>
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
                      </td>
                    </tr>
                  )}
                </Fragment>
              );
            })}
          </tbody>
        </table>
      </div>
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
