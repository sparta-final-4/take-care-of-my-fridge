import { useInfiniteQuery, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Empty, Flex, Popconfirm, Rate, Segmented, Skeleton, Typography } from 'antd';
import dayjs from 'dayjs';
import { useState } from 'react';

import type { ReviewResponse, ReviewSearchRequestSortEnum } from '@/common/api/clients';
import { SectionCard } from '@/common/components/SectionCard';
import { useAuthStore } from '@/common/auth/authStore';
import { cookingQueries } from '@/domains/cooking/queries';
import { getReviews } from '@/domains/review/api';
import { reviewQueries, useDeleteReview } from '@/domains/review/queries';

import { ReviewModal, type InitialReviewData } from './ReviewModal';
import styles from './RecipeReviewSection.module.scss';

interface RecipeReviewSectionProps {
  recipeId: number;
}

const PAGE_SIZE = 10;

export function RecipeReviewSection({ recipeId }: RecipeReviewSectionProps) {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const authStatus = useAuthStore((state) => state.status);
  const authenticated = authStatus === 'authenticated';

  const [sort, setSort] = useState<ReviewSearchRequestSortEnum>('LATEST');
  const [modalOpen, setModalOpen] = useState(false);
  const [editingReview, setEditingReview] = useState<InitialReviewData | null>(null);

  // 1. 요리 완료 기록 여부 확인 (로그인 사용자 대상)
  const { data: logsData, isPending: isLogsPending } = useQuery({
    ...cookingQueries.logs({ recipeId, size: 1 }),
    enabled: authenticated,
  });
  const hasCooked = (logsData?.totalElements ?? 0) > 0;

  // 2. 내 작성 후기 확인 (로그인 사용자 대상)
  const { data: myReviewData, isPending: isMyReviewPending } = useQuery({
    ...reviewQueries.myReview(recipeId),
    enabled: authenticated,
  });
  const myReview = myReviewData?.content?.[0] ?? null;

  // 3. 후기 목록 무한 스크롤 조회 (비로그인도 조회 가능)
  const {
    data: reviewsData,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    isPending: isReviewsPending,
  } = useInfiniteQuery({
    queryKey: [...reviewQueries.lists(), { recipeId, sort, size: PAGE_SIZE }],
    queryFn: ({ pageParam = 1, signal }) =>
      getReviews({ recipeId, sort, page: pageParam, size: PAGE_SIZE }, signal),
    initialPageParam: 1,
    getNextPageParam: (lastPage) => {
      const isLast = lastPage.isLast ?? true;
      const pageNumber = lastPage.pageNumber ?? 1;
      return isLast ? undefined : pageNumber + 1;
    },
  });

  const totalElements = reviewsData?.pages[0]?.totalElements ?? 0;
  const reviews = reviewsData?.pages.flatMap((page) => page.content ?? []) ?? [];
  const otherReviews = reviews.filter((review) => !review.mine);

  // 4. 후기 삭제 Mutation
  const deleteMutation = useDeleteReview();

  const handleDelete = (reviewId: number) => {
    deleteMutation.mutate(reviewId, {
      onSuccess: () => {
        message.success('후기가 삭제되었습니다.');
        queryClient.invalidateQueries({ queryKey: cookingQueries.all });
      },
    });
  };

  const handleOpenCreate = () => {
    setEditingReview(null);
    setModalOpen(true);
  };

  const handleOpenEdit = (review: ReviewResponse) => {
    if (review.id == null) return;
    setEditingReview({
      id: review.id,
      rating: review.rating ?? 5,
      content: review.content ?? '',
    });
    setModalOpen(true);
  };

  // 후기 쓰기 / 내 후기 영역 렌더링
  const renderWriteArea = () => {
    if (!authenticated) {
      return (
        <div className={styles.noticeBox}>
          <Typography.Text type="secondary">
            로그인하고 요리를 완료하면 후기를 쓸 수 있어요
          </Typography.Text>
        </div>
      );
    }

    if (isLogsPending || isMyReviewPending) {
      return <Skeleton active paragraph={{ rows: 2 }} title={false} />;
    }

    if (!hasCooked) {
      return (
        <div className={styles.noticeBox}>
          <Typography.Text type="secondary">요리를 완료하면 후기를 쓸 수 있어요</Typography.Text>
        </div>
      );
    }

    if (myReview) {
      return (
        <div className={styles.myReviewBox}>
          <Flex justify="space-between" align="center" className={styles.myReviewHeader}>
            <Typography.Text strong className={styles.myReviewBadge}>
              내가 작성한 후기
            </Typography.Text>
            <Flex gap="small">
              <Button type="link" size="small" onClick={() => handleOpenEdit(myReview)}>
                수정
              </Button>
              <Popconfirm
                title="후기 삭제"
                description="작성한 후기를 삭제하시겠습니까?"
                onConfirm={() => handleDelete(myReview.id!)}
                okText="삭제"
                cancelText="취소"
                okButtonProps={{ danger: true }}
              >
                <Button type="link" danger size="small" loading={deleteMutation.isPending}>
                  삭제
                </Button>
              </Popconfirm>
            </Flex>
          </Flex>
          <Flex vertical gap="small">
            <Flex gap="middle" align="center">
              <Rate disabled value={myReview.rating} />
              <Typography.Text type="secondary">
                {myReview.createdAt ? dayjs(myReview.createdAt).format('YYYY-MM-DD') : ''}
              </Typography.Text>
            </Flex>
            <Typography.Paragraph className={styles.content}>
              {myReview.content}
            </Typography.Paragraph>
          </Flex>
        </div>
      );
    }

    return (
      <Flex justify="flex-end">
        <Button type="primary" onClick={handleOpenCreate}>
          후기 쓰기
        </Button>
      </Flex>
    );
  };

  return (
    <SectionCard
      title={isReviewsPending ? '후기' : `후기 ${totalElements.toLocaleString('ko-KR')}개`}
      extra={
        <Segmented
          size="small"
          value={sort}
          onChange={(val) => setSort(val as ReviewSearchRequestSortEnum)}
          options={[
            { label: '최신순', value: 'LATEST' },
            { label: '별점순', value: 'RATING' },
          ]}
          aria-label="후기 정렬"
        />
      }
    >
      <div className={styles.section}>
        {renderWriteArea()}

        {isReviewsPending ? (
          <Skeleton active paragraph={{ rows: 4 }} />
        ) : totalElements === 0 ? (
          <Empty description="아직 후기가 없어요" />
        ) : (
          <Flex vertical gap="middle">
            {otherReviews.map((review) => (
              <div key={review.id} className={styles.reviewItem}>
                <Flex justify="space-between" align="flex-start" className={styles.header}>
                  <Flex vertical gap={4}>
                    <Flex gap="middle" align="center">
                      <Typography.Text strong>{review.nickname ?? '익명'}</Typography.Text>
                      <Typography.Text type="secondary">
                        {review.createdAt ? dayjs(review.createdAt).format('YYYY-MM-DD') : ''}
                      </Typography.Text>
                    </Flex>
                    <Rate disabled value={review.rating} />
                  </Flex>

                  {review.mine && (
                    <Flex gap="small">
                      <Button type="link" size="small" onClick={() => handleOpenEdit(review)}>
                        수정
                      </Button>
                      <Popconfirm
                        title="후기 삭제"
                        description="작성한 후기를 삭제하시겠습니까?"
                        onConfirm={() => handleDelete(review.id!)}
                        okText="삭제"
                        cancelText="취소"
                        okButtonProps={{ danger: true }}
                      >
                        <Button type="link" danger size="small" loading={deleteMutation.isPending}>
                          삭제
                        </Button>
                      </Popconfirm>
                    </Flex>
                  )}
                </Flex>

                <Typography.Paragraph className={styles.content}>
                  {review.content}
                </Typography.Paragraph>
              </div>
            ))}

            {hasNextPage && (
              <Flex justify="center" className={styles.moreButtonWrapper}>
                <Button onClick={() => fetchNextPage()} loading={isFetchingNextPage}>
                  더 보기
                </Button>
              </Flex>
            )}
          </Flex>
        )}
      </div>

      <ReviewModal
        open={modalOpen}
        recipeId={recipeId}
        initialReview={editingReview}
        onClose={() => setModalOpen(false)}
        onSuccess={() => {
          queryClient.invalidateQueries({ queryKey: cookingQueries.all });
        }}
      />
    </SectionCard>
  );
}
