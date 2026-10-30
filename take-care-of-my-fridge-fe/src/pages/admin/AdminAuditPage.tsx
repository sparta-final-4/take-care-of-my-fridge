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
import dayjs from 'dayjs';
import { useState } from 'react';
import { useSearchParams } from 'react-router';

import {
  AuditLogResponseActionEnum,
  AuditLogResponseResultEnum,
  type AuditLogResponse,
  type AuditLogResponseActorRoleEnum,
  type AuditLogSearchRequest,
} from '@/common/api/clients';
import { getErrorMessage } from '@/common/api/errors';
import { ACTION_LABELS, RESULT_LABELS } from '@/domains/admin/labels';
import { adminQueries } from '@/domains/admin/queries';
import { ROLE_LABELS } from '@/domains/member/labels';

import styles from './AdminAuditPage.module.scss';

const PAGE_SIZE = 20;
const actions = Object.values(AuditLogResponseActionEnum);
const results = Object.values(AuditLogResponseResultEnum);

type Filters = Omit<AuditLogSearchRequest, 'page' | 'size'>;

function pick<T extends string>(value: string | null, allowed: T[]) {
  return allowed.find((item) => item === value);
}

function readFilters(params: URLSearchParams): Filters {
  return {
    from: params.get('from') ?? undefined,
    to: params.get('to') ?? undefined,
    action: pick(params.get('action'), actions),
    result: pick(params.get('result'), results),
    targetType: params.get('targetType') ?? undefined,
    targetId: Number(params.get('targetId')) || undefined,
  };
}

export default function AdminAuditPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const applied = readFilters(searchParams);
  const page = Math.max(1, Number(searchParams.get('page')) || 1);
  // 조회 버튼을 누르기 전까지 입력 중인 조건은 화면 상태로 둔다.
  const [filters, setFilters] = useState<Filters>(applied);

  const search = (next: Filters, nextPage = 1) => {
    const params = new URLSearchParams();
    Object.entries(next).forEach(([key, value]) => {
      if (value !== undefined && value !== '') params.set(key, String(value));
    });
    if (nextPage > 1) params.set('page', String(nextPage));
    setSearchParams(params);
  };
  const { data, isPending, isError, error, refetch } = useQuery(
    adminQueries.auditLogs({ ...applied, page, size: PAGE_SIZE }),
  );

  return (
    <section className={styles.container}>
      <Typography.Title level={2}>관리자 작업 이력</Typography.Title>
      <Space wrap className={styles.filters}>
        <DatePicker
          aria-label="시작일"
          placeholder="시작일"
          value={filters.from ? dayjs(filters.from) : null}
          onChange={(value) =>
            setFilters((previous) => ({ ...previous, from: value?.format('YYYY-MM-DD') }))
          }
        />
        <DatePicker
          aria-label="종료일"
          placeholder="종료일"
          value={filters.to ? dayjs(filters.to) : null}
          onChange={(value) =>
            setFilters((previous) => ({ ...previous, to: value?.format('YYYY-MM-DD') }))
          }
        />
        <Select
          aria-label="작업 종류"
          placeholder="전체 작업"
          allowClear
          className={styles.select}
          value={filters.action}
          options={actions.map((action) => ({ value: action, label: ACTION_LABELS[action] }))}
          onChange={(action) => setFilters((previous) => ({ ...previous, action }))}
        />
        <Select
          aria-label="결과"
          placeholder="전체 결과"
          allowClear
          className={styles.select}
          value={filters.result}
          options={results.map((result) => ({ value: result, label: RESULT_LABELS[result] }))}
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
        <Button type="primary" onClick={() => search(filters)}>
          조회
        </Button>
      </Space>
      <Typography.Paragraph type="secondary">
        기간을 비우면 최근 7일의 이력을 조회합니다.
      </Typography.Paragraph>
      {isError ? (
        <Result
          status="error"
          title="작업 이력을 불러오지 못했습니다"
          subTitle={getErrorMessage(error)}
          extra={<Button onClick={() => void refetch()}>다시 시도</Button>}
        />
      ) : (
        <Table<AuditLogResponse>
          rowKey="auditLogId"
          loading={isPending}
          dataSource={data?.content}
          scroll={{ x: 'max-content' }}
          locale={{ emptyText: '조건에 맞는 작업 이력이 없습니다.' }}
          pagination={{
            current: page,
            pageSize: PAGE_SIZE,
            total: data?.totalElements,
            showSizeChanger: false,
            onChange: (next) => search(applied, next),
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
              render: (value: string) => dayjs(value).format('YYYY-MM-DD HH:mm'),
            },
            { title: '작업자', dataIndex: 'actorEmail', key: 'actorEmail' },
            {
              title: '역할',
              dataIndex: 'actorRole',
              key: 'actorRole',
              render: (value?: AuditLogResponseActorRoleEnum) => (value ? ROLE_LABELS[value] : '-'),
            },
            {
              title: '작업',
              dataIndex: 'action',
              key: 'action',
              render: (value?: AuditLogResponseActionEnum) => (value ? ACTION_LABELS[value] : '-'),
            },
            {
              title: '대상',
              key: 'target',
              render: (_, row) => `${row.targetType} ${row.targetId ?? '-'}`,
            },
            {
              title: '결과',
              dataIndex: 'result',
              key: 'result',
              render: (value?: AuditLogResponseResultEnum) =>
                value ? (
                  <Tag
                    color={value === 'SUCCESS' ? 'green' : value === 'FAILED' ? 'orange' : 'red'}
                  >
                    {RESULT_LABELS[value]}
                  </Tag>
                ) : (
                  '-'
                ),
            },
          ]}
        />
      )}
    </section>
  );
}
