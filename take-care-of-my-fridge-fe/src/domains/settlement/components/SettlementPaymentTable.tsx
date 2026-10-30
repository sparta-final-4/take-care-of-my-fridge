import { useQuery } from '@tanstack/react-query';
import { Button, Result, Table, Typography, type TableColumnsType } from 'antd';
import dayjs from 'dayjs';

import { getErrorMessage } from '@/common/api/errors';
import { SectionLoading } from '@/common/components/SectionLoading';

import type { SalesAmountResponse, SettlementPaymentResponse } from '../api';
import { formatWon } from '../format';
import { settlementQueries } from '../queries';
import { SettlementNetAmount } from './SettlementNetAmount';

const PAGE_SIZE = 20;

// 기간 밖의 결제·환불은 0으로 오므로 빈칸(-)으로 보여 준다
const wonOrDash = (value?: number) => (value ? formatWon(value) : '-');

const columns: TableColumnsType<SettlementPaymentResponse> = [
  {
    title: '결제일시',
    dataIndex: 'paidAt',
    render: (value?: string) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-'),
  },
  { title: '주문번호', dataIndex: 'orderNumber', render: (value?: string) => value ?? '-' },
  { title: '구매자', dataIndex: 'buyerName', render: (value?: string) => value ?? '-' },
  { title: '결제금액', dataIndex: 'paidAmount', align: 'right', render: wonOrDash },
  { title: '환불금액', dataIndex: 'refundedAmount', align: 'right', render: wonOrDash },
  {
    title: '순매출',
    dataIndex: 'netAmount',
    align: 'right',
    render: (value?: number) => <SettlementNetAmount value={value} />,
  },
];

interface SettlementPaymentTableProps {
  startDate: string;
  endDate: string;
  page: number;
  summary?: SalesAmountResponse;
  // 기간 합계를 불러오지 못했을 때만 넘긴다. 합계 행 자리에 다시 시도 버튼을 보여 준다
  onRetrySummary?: () => void;
  onPageChange: (page: number) => void;
}

// 건별 탭: 기간 안에 결제·환불이 있었던 결제 한 건이 한 줄. 합계 행은 페이지가 아니라 조회 기간 전체이고,
// 합계를 아직 못 받았으면(로딩·이전 기간 값) 보이지 않고, 실패하면 다시 시도 버튼을 보여 준다
export function SettlementPaymentTable({
  startDate,
  endDate,
  page,
  summary,
  onRetrySummary,
  onPageChange,
}: SettlementPaymentTableProps) {
  const paymentsQuery = useQuery(
    settlementQueries.payments({ startDate, endDate, page, size: PAGE_SIZE }),
  );

  if (paymentsQuery.isError && !paymentsQuery.data) {
    return (
      <Result
        status="error"
        title="결제 내역을 불러오지 못했습니다"
        subTitle={getErrorMessage(paymentsQuery.error)}
        extra={<Button onClick={() => void paymentsQuery.refetch()}>다시 시도</Button>}
      />
    );
  }

  return (
    <SectionLoading loading={paymentsQuery.isFetching && !paymentsQuery.isPending}>
      <Table<SettlementPaymentResponse>
        rowKey="paymentId"
        columns={columns}
        dataSource={paymentsQuery.data?.content ?? []}
        loading={paymentsQuery.isPending}
        scroll={{ x: 'max-content' }}
        locale={{
          // 주소의 페이지가 마지막 페이지를 넘으면 빈 목록이 오고 antd 페이지 버튼으로는 돌아갈 수 없어 버튼을 둔다
          emptyText:
            page > 1 ? (
              <Button type="link" onClick={() => onPageChange(1)}>
                첫 페이지로 이동
              </Button>
            ) : (
              '이 기간에는 결제·환불이 없어요'
            ),
        }}
        pagination={{
          current: page,
          pageSize: PAGE_SIZE,
          total: paymentsQuery.data?.totalElements ?? 0,
          showSizeChanger: false,
          hideOnSinglePage: true,
          onChange: onPageChange,
        }}
        summary={() => {
          if (onRetrySummary) {
            return (
              <Table.Summary.Row>
                <Table.Summary.Cell index={0} colSpan={6}>
                  <Typography.Text type="danger">
                    조회 기간 합계를 불러오지 못했습니다.
                  </Typography.Text>{' '}
                  <Button type="link" size="small" onClick={onRetrySummary}>
                    다시 시도
                  </Button>
                </Table.Summary.Cell>
              </Table.Summary.Row>
            );
          }
          if (!summary) return null;
          return (
            <Table.Summary.Row>
              <Table.Summary.Cell index={0} colSpan={3}>
                <Typography.Text strong>조회 기간 합계</Typography.Text>
              </Table.Summary.Cell>
              <Table.Summary.Cell index={3} align="right">
                {formatWon(summary.paidAmount)}
              </Table.Summary.Cell>
              <Table.Summary.Cell index={4} align="right">
                {formatWon(summary.refundedAmount)}
              </Table.Summary.Cell>
              <Table.Summary.Cell index={5} align="right">
                <SettlementNetAmount value={summary.netAmount} />
              </Table.Summary.Cell>
            </Table.Summary.Row>
          );
        }}
      />
    </SectionLoading>
  );
}
