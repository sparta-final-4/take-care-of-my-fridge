import { useQuery } from '@tanstack/react-query';
import {
  Button,
  DatePicker,
  Input,
  InputNumber,
  Result,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import { useState } from 'react';

import {
  getAuditLogs,
  type AuditLogResponse,
  type AuditLogSearchRequest,
} from '@/domains/admin/api';

import styles from './AdminAuditPage.module.scss';

const PAGE_SIZE = 20;
const actions: AuditLogResponse['action'][] = [
  'PRODUCT_CREATE',
  'PRODUCT_DELETE',
  'PRICE_UPDATE',
  'DEAL_REVIEW',
  'EVENT_REQUEST',
  'ROLE_CHANGE',
];

type Filters = Omit<AuditLogSearchRequest, 'page' | 'size'>;

export default function AdminAuditPage() {
  const [filters, setFilters] = useState<Filters>({});
  const [applied, setApplied] = useState<Filters>({});
  const [page, setPage] = useState(1);
  const { data, isPending, isError, refetch } = useQuery({
    queryKey: ['admin', 'audit', applied, page],
    queryFn: ({ signal }) => getAuditLogs({ ...applied, page, size: PAGE_SIZE }, signal),
  });

  return (
    <section className={styles.container}>
      <Typography.Title level={2}>관리자 작업 이력</Typography.Title>
      <Space wrap className={styles.filters}>
        <DatePicker
          aria-label="시작일"
          placeholder="시작일"
          onChange={(value) =>
            setFilters((previous) => ({ ...previous, from: value?.format('YYYY-MM-DD') }))
          }
        />
        <DatePicker
          aria-label="종료일"
          placeholder="종료일"
          onChange={(value) =>
            setFilters((previous) => ({ ...previous, to: value?.format('YYYY-MM-DD') }))
          }
        />
        <Select
          aria-label="작업 종류"
          placeholder="전체 작업"
          allowClear
          className={styles.select}
          options={actions.map((action) => ({ value: action, label: action }))}
          onChange={(action) => setFilters((previous) => ({ ...previous, action }))}
        />
        <Select
          aria-label="결과"
          placeholder="전체 결과"
          allowClear
          className={styles.select}
          options={[
            { value: 'SUCCESS', label: '성공' },
            { value: 'FAILED', label: '실패' },
            { value: 'DENIED', label: '권한 거부' },
          ]}
          onChange={(result) => setFilters((previous) => ({ ...previous, result }))}
        />
        <Input
          aria-label="대상 종류"
          placeholder="대상 종류"
          maxLength={50}
          className={styles.select}
          value={filters.targetType}
          onChange={(event) =>
            setFilters((previous) => ({ ...previous, targetType: event.target.value }))
          }
        />
        <InputNumber
          aria-label="대상 ID"
          placeholder="대상 ID"
          min={1}
          value={filters.targetId}
          onChange={(value) =>
            setFilters((previous) => ({ ...previous, targetId: value ?? undefined }))
          }
        />
        <Button
          type="primary"
          onClick={() => {
            setApplied({ ...filters });
            setPage(1);
          }}
        >
          조회
        </Button>
      </Space>
      {isError ? (
        <Result
          status="error"
          title="작업 이력을 불러오지 못했습니다"
          extra={<Button onClick={() => void refetch()}>다시 시도</Button>}
        />
      ) : (
        <Table<AuditLogResponse>
          rowKey="auditLogId"
          loading={isPending}
          dataSource={data?.content}
          scroll={{ x: 900 }}
          locale={{ emptyText: '조건에 맞는 작업 이력이 없습니다.' }}
          pagination={{
            current: page,
            pageSize: PAGE_SIZE,
            total: data?.totalElements,
            showSizeChanger: false,
            onChange: setPage,
          }}
          expandable={{
            expandedRowRender: (record) => (
              <div className={styles.details}>
                <div>
                  <strong>변경 전</strong>
                  <pre>{JSON.stringify(record.beforeValue ?? {}, null, 2)}</pre>
                </div>
                <div>
                  <strong>변경 후</strong>
                  <pre>{JSON.stringify(record.afterValue ?? {}, null, 2)}</pre>
                </div>
              </div>
            ),
            rowExpandable: (record) => Boolean(record.beforeValue || record.afterValue),
          }}
          columns={[
            {
              title: '시각',
              dataIndex: 'createdAt',
              key: 'createdAt',
              render: (value: string) => value.replace('T', ' '),
            },
            { title: '작업자', dataIndex: 'actorEmail', key: 'actorEmail' },
            { title: '역할', dataIndex: 'actorRole', key: 'actorRole' },
            { title: '작업', dataIndex: 'action', key: 'action' },
            {
              title: '대상',
              key: 'target',
              render: (_, row) => `${row.targetType} ${row.targetId ?? '-'}`,
            },
            {
              title: '결과',
              dataIndex: 'result',
              key: 'result',
              render: (value: AuditLogResponse['result']) => (
                <Tag color={value === 'SUCCESS' ? 'green' : value === 'FAILED' ? 'orange' : 'red'}>
                  {value}
                </Tag>
              ),
            },
          ]}
        />
      )}
    </section>
  );
}
