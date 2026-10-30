import { Table, Typography, type TableColumnsType } from 'antd';

import type { SalesAmountResponse, SettlementPeriodResponse, SettlementUnit } from '../api';
import { formatCount, formatPeriod, formatWon } from '../format';
import { SettlementNetAmount } from './SettlementNetAmount';

interface SettlementDailyTableProps {
  unit: SettlementUnit;
  summary?: SalesAmountResponse;
  periods: SettlementPeriodResponse[];
}

// 일자별 탭: 일·주·월로 묶은 기간마다 한 줄, 맨 아래는 조회 기간 합계
export function SettlementDailyTable({ unit, summary, periods }: SettlementDailyTableProps) {
  const columns: TableColumnsType<SettlementPeriodResponse> = [
    { title: '기간', key: 'period', render: (_, period) => formatPeriod(unit, period) },
    { title: '결제금액', dataIndex: 'paidAmount', align: 'right', render: formatWon },
    { title: '환불금액', dataIndex: 'refundedAmount', align: 'right', render: formatWon },
    {
      title: '순매출',
      dataIndex: 'netAmount',
      align: 'right',
      render: (value?: number) => <SettlementNetAmount value={value} />,
    },
    { title: '결제 건수', dataIndex: 'paidCount', align: 'right', render: formatCount },
    { title: '환불 건수', dataIndex: 'refundedCount', align: 'right', render: formatCount },
  ];

  return (
    <Table<SettlementPeriodResponse>
      rowKey="startDate"
      columns={columns}
      dataSource={periods}
      scroll={{ x: 'max-content' }}
      pagination={false}
      summary={() => (
        <Table.Summary.Row>
          <Table.Summary.Cell index={0}>
            <Typography.Text strong>합계</Typography.Text>
          </Table.Summary.Cell>
          <Table.Summary.Cell index={1} align="right">
            {formatWon(summary?.paidAmount)}
          </Table.Summary.Cell>
          <Table.Summary.Cell index={2} align="right">
            {formatWon(summary?.refundedAmount)}
          </Table.Summary.Cell>
          <Table.Summary.Cell index={3} align="right">
            <SettlementNetAmount value={summary?.netAmount} />
          </Table.Summary.Cell>
          <Table.Summary.Cell index={4} align="right">
            {formatCount(summary?.paidCount)}
          </Table.Summary.Cell>
          <Table.Summary.Cell index={5} align="right">
            {formatCount(summary?.refundedCount)}
          </Table.Summary.Cell>
        </Table.Summary.Row>
      )}
    />
  );
}
