import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, App, Button, Empty, Result, Space, Table, Tag, Typography } from 'antd';
import { useNavigate } from 'react-router';

import { ROUTES } from '@/app/paths';
import type { Candidate } from '@/common/api/clients';
import { getErrorCode } from '@/common/api/errors';
import { saveDealReview } from '@/domains/deal/api';
import { dealQueries } from '@/domains/deal/queries';

import styles from './AdminDealPage.module.scss';

const money = (value?: number) =>
  value === undefined ? '-' : `${value.toLocaleString('ko-KR')}원`;

export default function AdminDealPage() {
  const navigate = useNavigate();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const { data: analysis, error, isPending, isError, refetch } = useQuery(dealQueries.latest());

  const review = useMutation({
    mutationFn: (candidateId: number) =>
      saveDealReview(analysis!.analysisId!, {
        reviews: [{ candidateId, reviewStatus: 'SELECTED' }],
      }),
    onSuccess: () => {
      void message.success('행사 후보로 선택했습니다.');
      return queryClient.invalidateQueries({ queryKey: dealQueries.all });
    },
  });

  if (isPending) return <div className={styles.container}>할인 후보를 불러오고 있습니다.</div>;
  if (isError) {
    if (getErrorCode(error) === 'D002') {
      return (
        <div className={styles.container}>
          <Empty description="아직 할인 후보 분석 결과가 없습니다." />
        </div>
      );
    }
    return (
      <div className={styles.container}>
        <Result
          status="error"
          title="할인 후보를 불러오지 못했습니다"
          extra={<Button onClick={() => void refetch()}>다시 시도</Button>}
        />
      </div>
    );
  }

  return (
    <section className={styles.container}>
      <Space className={styles.heading}>
        <Typography.Title level={2}>할인 후보</Typography.Title>
        <Button onClick={() => navigate(ROUTES.adminSaleEventNew)}>행사 관리</Button>
      </Space>
      <Typography.Paragraph type="secondary">
        {analysis.targetWeekStart} 주 · 추천 할인율 {analysis.discountRate}%
      </Typography.Paragraph>
      {analysis.syntheticData && (
        <Alert
          type="warning"
          showIcon
          message="가상 데이터로 만든 분석 결과는 행사에 등록할 수 없습니다."
          className={styles.notice}
        />
      )}
      <Table<Candidate>
        rowKey={(item) => item.candidateId ?? item.productId ?? 0}
        dataSource={analysis.candidates ?? []}
        scroll={{ x: 820 }}
        pagination={false}
        columns={[
          { title: '상품', dataIndex: 'productName' },
          { title: '정가', dataIndex: 'regularPrice', render: money },
          { title: '추천 가격', dataIndex: 'discountPrice', render: money },
          { title: '재고', dataIndex: 'currentStock' },
          {
            title: '검토',
            dataIndex: 'reviewStatus',
            render: (status: Candidate['reviewStatus']) => (
              <Tag color={status === 'SELECTED' ? 'green' : undefined}>
                {status === 'SELECTED' ? '선택' : status === 'HELD' ? '보류' : '미검토'}
              </Tag>
            ),
          },
          {
            title: '행사',
            dataIndex: 'eventRequestStatus',
            render: (status: Candidate['eventRequestStatus']) =>
              status === 'APPROVED' ? (
                <Tag color="blue">등록 완료</Tag>
              ) : status === 'REQUESTED' ? (
                <Tag>요청 중</Tag>
              ) : status === 'REJECTED' ? (
                <Tag>반려</Tag>
              ) : (
                '-'
              ),
          },
          {
            title: '작업',
            render: (_, item) =>
              item.eventRequestStatus === 'NONE' && item.reviewStatus !== 'SELECTED' ? (
                <Button
                  loading={review.isPending}
                  disabled={item.candidateId === undefined}
                  onClick={() => review.mutate(item.candidateId!)}
                >
                  선택
                </Button>
              ) : null,
          },
        ]}
      />
    </section>
  );
}
