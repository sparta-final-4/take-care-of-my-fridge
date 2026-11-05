import { Table, type TableColumnsType } from 'antd';

import type { SettlementMethodResponse } from '../api';
import { formatCount, formatPaymentMethod, formatWon } from '../format';
import { SettlementNetAmount } from './SettlementNetAmount';

const columns: TableColumnsType<SettlementMethodResponse> = [
  { title: '결제 수단', key: 'paymentMethod', render: (_, method) => formatPaymentMethod(method) },
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

interface SettlementMethodTableProps {
  methods: SettlementMethodResponse[];
}

// 요약 탭의 결제 수단별 합계. 서버가 결제금액이 큰 순으로 준다. 환불은 원래 결제의 수단으로 센다
export function SettlementMethodTable({ methods }: SettlementMethodTableProps) {
  return (
    <Table<SettlementMethodResponse>
      rowKey={(method) => `${method.registeredCard ? 'card' : 'pg'}-${method.pgProvider ?? ''}`}
      columns={columns}
      dataSource={methods}
      size="small"
      scroll={{ x: 'max-content' }}
      pagination={false}
    />
  );
}
