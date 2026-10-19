import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Input, Result, Select, Space, Table, Tag, Typography } from 'antd';
import { useState } from 'react';

import {
  promoteMember,
  type MemberListRequest,
  type MemberSummaryResponse,
} from '@/domains/admin/api';
import { adminQueries } from '@/domains/admin/queries';

import styles from './AdminMembersPage.module.scss';

const PAGE_SIZE = 20;

export default function AdminMembersPage() {
  const { modal, message } = App.useApp();
  const queryClient = useQueryClient();
  const [keywordInput, setKeywordInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [role, setRole] = useState<MemberListRequest['role']>();
  const [page, setPage] = useState(1);
  const { data, isPending, isError, refetch } = useQuery(
    adminQueries.members({ keyword: keyword || undefined, role, page, size: PAGE_SIZE }),
  );
  const promotion = useMutation({
    mutationFn: promoteMember,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: adminQueries.memberLists() }),
  });

  const confirmPromotion = (member: MemberSummaryResponse) => {
    modal.confirm({
      title: '관리자로 승격하시겠습니까?',
      content: `${member.nickname} (${member.email}) 회원에게 관리자 권한을 부여합니다.`,
      okText: '승격',
      cancelText: '취소',
      onOk: () =>
        new Promise<void>((resolve, reject) => {
          promotion.mutate(member.memberId, {
            onSuccess: () => {
              void message.success('관리자로 승격했습니다.');
              resolve();
            },
            onError: reject,
          });
        }),
    });
  };

  return (
    <section className={styles.container}>
      <Typography.Title level={2}>회원 관리</Typography.Title>
      <Space wrap className={styles.filters}>
        <Input.Search
          placeholder="이메일 또는 닉네임"
          aria-label="회원 검색"
          value={keywordInput}
          onChange={(event) => setKeywordInput(event.target.value)}
          onSearch={(value) => {
            setKeyword(value.trim());
            setPage(1);
          }}
          enterButton="검색"
          allowClear
        />
        <Select
          aria-label="역할 필터"
          placeholder="전체 역할"
          value={role}
          allowClear
          options={[
            { value: 'USER', label: '일반 회원' },
            { value: 'ADMIN', label: '관리자' },
          ]}
          onChange={(value) => {
            setRole(value);
            setPage(1);
          }}
          className={styles.roleFilter}
        />
      </Space>
      {isError ? (
        <Result
          status="error"
          title="회원 목록을 불러오지 못했습니다"
          extra={<Button onClick={() => void refetch()}>다시 시도</Button>}
        />
      ) : (
        <Table<MemberSummaryResponse>
          rowKey="memberId"
          loading={isPending}
          dataSource={data?.content}
          scroll={{ x: 920 }}
          pagination={{
            current: page,
            pageSize: PAGE_SIZE,
            total: data?.totalElements,
            showSizeChanger: false,
            onChange: setPage,
          }}
          locale={{ emptyText: '조건에 맞는 회원이 없습니다.' }}
          columns={[
            { title: '이메일', dataIndex: 'email', key: 'email' },
            { title: '닉네임', dataIndex: 'nickname', key: 'nickname' },
            {
              title: '역할',
              dataIndex: 'role',
              key: 'role',
              render: (value: MemberSummaryResponse['role']) => (
                <Tag color={value === 'ADMIN' ? 'purple' : 'default'}>{value}</Tag>
              ),
            },
            { title: '상태', dataIndex: 'status', key: 'status' },
            {
              title: '가입일',
              dataIndex: 'createdAt',
              key: 'createdAt',
              render: (value: string) => value.slice(0, 10),
            },
            {
              title: '승격 정보',
              key: 'promotion',
              render: (_, member) =>
                member.promotedAt
                  ? `${member.promotedAt.slice(0, 10)} · ${member.promotedBy ?? '-'}`
                  : '-',
            },
            {
              title: '관리',
              key: 'action',
              render: (_, member) => (
                <Button
                  disabled={member.role !== 'USER' || member.status !== 'ACTIVE'}
                  loading={promotion.isPending && promotion.variables === member.memberId}
                  onClick={() => confirmPromotion(member)}
                >
                  관리자 승격
                </Button>
              ),
            },
          ]}
        />
      )}
    </section>
  );
}
