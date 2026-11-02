import { Fragment, type MouseEvent, type ReactNode } from 'react';
import { DownOutlined, EditOutlined, UpOutlined } from '@ant-design/icons';
import { Button, Empty, Flex, Pagination, Select, Tabs, Tag, Tooltip, Typography } from 'antd';
import dayjs from 'dayjs';

import { FallbackImage } from '@/common/components/FallbackImage';

import type { CookingSortType, CookingTabType, MyRecipeResponse } from '../api';
import { RecipeCookingHistorySection } from './RecipeCookingHistorySection';

import styles from './MyRecipeListSection.module.scss';

export interface MyRecipeListSectionProps {
  recipes: MyRecipeResponse[];
  allCount: number;
  unreviewedCount: number;
  reviewedCount: number;
  totalCookCount: number;
  consumedIngredientCount: number;
  // 요리 기록이 없으면 없다. 요약 줄에서 이 항목만 뺀다.
  mostCookedRecipe?: { name: string; cookCount: number };
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
  consumedIngredientCount,
  mostCookedRecipe,
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
                  내 별점
                </th>
              </tr>
            </thead>
            <tbody>
              {recipes.map((item) => {
                const recipeId = item.recipeId ?? 0;
                const isExpanded = expandedRecipeId === recipeId;

                // 줄의 빈 곳을 눌러도 펼치기/접기. 사진·이름·[후기 쓰기]·요리 횟수 버튼은 각자 동작만 한다.
                // 키보드는 요리 횟수 버튼으로 같은 동작을 할 수 있다.
                const handleRowClick = (event: MouseEvent<HTMLTableRowElement>) => {
                  if (event.target instanceof Element && event.target.closest('button, a')) return;
                  onToggleExpand(recipeId);
                };

                return (
                  <Fragment key={recipeId}>
                    <tr className={styles.dataRow} onClick={handleRowClick}>
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
                              <FallbackImage
                                src={item.recipeImageUrl}
                                alt={item.recipeName ?? '레시피'}
                                kind="recipe"
                                size="small"
                              />
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

                      {/* 내 별점: 쓴 줄은 별점만(수정은 펼친 영역에서), 안 쓴 줄은 [후기 쓰기] */}
                      <td className={styles.tdReview}>
                        {item.review ? (
                          <span className={styles.ratingText}>
                            <span className={styles.ratingStar} aria-hidden="true">
                              ★
                            </span>{' '}
                            {item.review.rating ?? 0}
                          </span>
                        ) : (
                          <Button
                            color="primary"
                            variant="outlined"
                            size="small"
                            onClick={() => onOpenReviewModal(item)}
                          >
                            후기 쓰기
                          </Button>
                        )}
                      </td>
                    </tr>

                    {/* 아코디언 펼침 내용: 내 후기 + 요리 기록 */}
                    {isExpanded && (
                      <tr className={styles.expandedRow}>
                        <td colSpan={4} className={styles.expandedCell}>
                          <div className={styles.expandedPanel}>
                            {/* 내 후기 영역 */}
                            {item.review ? (
                              <div className={styles.detailReviewSection}>
                                <Typography.Text type="secondary" className={styles.detailLabel}>
                                  내 후기
                                </Typography.Text>
                                <div className={styles.detailReviewContent}>
                                  {/* 별점 · 내용 · 연필 · "MM.DD 수정됨"을 한 줄로 붙여 둔다(넘치면 다음 줄로). */}
                                  <Flex
                                    align="center"
                                    gap="small"
                                    wrap="wrap"
                                    className={styles.reviewHeader}
                                  >
                                    <span className={styles.ratingText}>
                                      <span className={styles.ratingStar} aria-hidden="true">
                                        ★
                                      </span>{' '}
                                      {item.review.rating ?? 0}
                                    </span>
                                    <Typography.Text className={styles.reviewText}>
                                      &ldquo;{item.review.content}&rdquo;
                                    </Typography.Text>
                                    {/* 요리 기록 수정 연필과 같은 모양 */}
                                    <Tooltip title="후기 수정">
                                      <Button
                                        type="text"
                                        size="small"
                                        icon={<EditOutlined />}
                                        aria-label={`${item.recipeName ?? '레시피'} 후기 수정`}
                                        onClick={() => onOpenReviewModal(item)}
                                      />
                                    </Tooltip>
                                    {item.review.updatedAt && (
                                      <Typography.Text
                                        type="secondary"
                                        className={styles.reviewDate}
                                      >
                                        {dayjs(item.review.updatedAt).format('MM.DD')} 수정됨
                                      </Typography.Text>
                                    )}
                                  </Flex>
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

  // 탭 키는 CookingTabType만 쓴다. Tabs가 넘겨 주는 문자열 키는 이 목록에서 찾아 타입을 확인한다.
  const tabItems: { key: CookingTabType; label: ReactNode; children: ReactNode }[] = [
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

  // 가장 많이 만든 레시피는 이름만 값으로 두고 횟수는 덧붙임(" · 5회")으로 뺀다. 요리 기록이 없으면 '-'로 칸 자리를 지킨다.
  const stats: { label: string; value: string; suffix?: string }[] = [
    { label: '요리한 레시피', value: `${allCount}개` },
    { label: '요리 완료', value: `${totalCookCount}번` },
    { label: '요리에 쓴 재료', value: `${consumedIngredientCount}종` },
    {
      label: '가장 많이 만든 레시피',
      value: mostCookedRecipe?.name ?? '-',
      suffix: mostCookedRecipe ? `· ${mostCookedRecipe.cookCount}회` : undefined,
    },
  ];

  return (
    <section className={styles.section} aria-label="내 레시피 관리">
      {/* 상단 헤더: 제목, 정렬 */}
      <div className={styles.header}>
        <Typography.Title level={3} className={styles.title}>
          내 레시피 관리
        </Typography.Title>
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

      {/* 요약 칸: 설명(위, 12 회색) → 값(아래, 16 / 600) */}
      <dl className={styles.stats}>
        {stats.map((stat) => (
          <div key={stat.label} className={styles.statItem}>
            <dt className={styles.statLabel}>{stat.label}</dt>
            <dd className={styles.statValue}>
              <span className={styles.statMain} title={stat.value}>
                {stat.value}
              </span>
              {stat.suffix && <span className={styles.statSuffix}>{stat.suffix}</span>}
            </dd>
          </div>
        ))}
      </dl>

      {/* 탭 및 목록 컨텐츠 */}
      <Tabs
        activeKey={currentTab}
        onChange={(key) => {
          const tab = tabItems.find((item) => item.key === key)?.key;
          if (tab) onTabChange(tab);
        }}
        items={tabItems}
        className={styles.tabs}
        destroyOnHidden
      />
    </section>
  );
}
