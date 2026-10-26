import { DeleteOutlined, EditOutlined, StopOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import {
  Button,
  DatePicker,
  Drawer,
  Flex,
  Grid,
  Input,
  Result,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { TableColumnsType, TablePaginationConfig } from 'antd';
import type { SorterResult } from 'antd/es/table/interface';
import dayjs from 'dayjs';
import { useState } from 'react';
import { useSearchParams } from 'react-router';

import type { SaleEventListRequest, SaleEventListResponse } from '@/common/api/clients';
import { formatPrice } from '@/domains/cart/format';
import { SaleEventEditPanel } from '@/domains/sale/components/SaleEventEditPanel';
import { SALE_STATUS_LABELS, SALE_TYPE_LABELS } from '@/domains/sale/format';
import { saleQueries } from '@/domains/sale/queries';

import styles from './SaleEventListSection.module.scss';

const PAGE_SIZE = 10;
// 열 너비를 고정해 정렬로 행이 바뀌어도 열이 움직이지 않게 한다. 표는 이 너비보다 좁아지지 않아, 화면이 이보다
// 좁을 때만 표 안에서 가로로 스크롤한다. 넓으면 열이 비율대로 늘어나 칸 밖으로 넘치지 않는다.
const TABLE_WIDTH = 1095;
const ALL = 'ALL';
const DATE_FORMAT = 'YYYY-MM-DD';
const TIME_FORMAT = 'HH:mm';

const STATUS_COLORS = { SCHEDULED: 'blue', ACTIVE: 'green', ENDED: 'default' } as const;
// 상태와 색이 겹치지 않게 종류는 보라·주황 계열로 구분한다.
const TYPE_COLORS = { SPECIAL: 'purple', FLASH_DROP: 'volcano' } as const;

type Status = keyof typeof SALE_STATUS_LABELS;
type Type = keyof typeof SALE_TYPE_LABELS;

// 응답 필드는 모두 선택값으로 생성돼, 표에 쓰는 값이 다 있는 행만 만든다. 행사에 든 상품 하나가 한 행이다.
interface EventRow {
  saleEventId: number;
  productId: number;
  productName: string;
  type: Type;
  status: Status;
  startsAt: string;
  endsAt: string;
  listPrice: number;
  salePrice: number;
  discountRate: number;
  limitedQuantity?: number;
  remainingQuantity?: number;
  soldQuantity: number;
}

// 시각은 열을 좁히려고 날짜와 시간을 두 줄로 나눠 보여 준다.
const renderDateTime = (value: string) => (
  <Flex vertical>
    <span>{dayjs(value).format(DATE_FORMAT)}</span>
    <Typography.Text type="secondary">{dayjs(value).format(TIME_FORMAT)}</Typography.Text>
  </Flex>
);

const rowKeyOf = (row: EventRow) => `${row.saleEventId}-${row.productId}`;

const toRow = (event: SaleEventListResponse): EventRow[] => {
  const { saleEventId, productId, productName, type, status, startsAt, endsAt } = event;
  const { listPrice, salePrice, discountRate, soldQuantity } = event;
  if (
    saleEventId === undefined ||
    productId === undefined ||
    productName === undefined ||
    type === undefined ||
    status === undefined ||
    startsAt === undefined ||
    endsAt === undefined ||
    listPrice === undefined ||
    salePrice === undefined ||
    discountRate === undefined ||
    soldQuantity === undefined
  )
    return [];
  return [
    {
      saleEventId,
      productId,
      productName,
      type,
      status,
      startsAt,
      endsAt,
      listPrice,
      salePrice,
      discountRate,
      limitedQuantity: event.limitedQuantity,
      remainingQuantity: event.remainingQuantity,
      soldQuantity,
    },
  ];
};

const isStatus = (value: string | null): value is Status =>
  value !== null && value in SALE_STATUS_LABELS;
const isType = (value: string | null): value is Type => value !== null && value in SALE_TYPE_LABELS;

// 머리글로 정렬할 수 있는 열과 서버 정렬 기준의 짝
const SORT_KEYS = {
  productName: 'PRODUCT_NAME',
  type: 'TYPE',
  status: 'STATUS',
  listPrice: 'LIST_PRICE',
  salePrice: 'SALE_PRICE',
  discountRate: 'DISCOUNT_RATE',
  limitedQuantity: 'LIMITED_QUANTITY',
  remainingQuantity: 'REMAINING_QUANTITY',
  startsAt: 'STARTS_AT',
  endsAt: 'ENDS_AT',
} as const;
type SortColumn = keyof typeof SORT_KEYS;

const isSortKey = (value: string | null): value is (typeof SORT_KEYS)[SortColumn] =>
  Object.values<string>(SORT_KEYS).includes(value ?? '');

// 행사 목록 탭. 조건은 주소 쿼리에 두어 새로고침해도 남는다.
export function SaleEventListSection() {
  const [searchParams, setSearchParams] = useSearchParams();
  // 행 아래로 펼친 수정 칸의 행 키. 한 번에 하나만 펼친다.
  const [editing, setEditing] = useState<{ key: string; saleEventId: number } | null>(null);
  // 좁은 화면은 표가 가로로 스크롤돼 행 아래 칸이 한 화면에 들어오지 않아, 아래에서 올라오는 패널로 연다.
  const narrow = !Grid.useBreakpoint().xl;
  const statusParam = searchParams.get('status');
  const typeParam = searchParams.get('kind');
  const status = isStatus(statusParam) ? statusParam : undefined;
  const type = isType(typeParam) ? typeParam : undefined;
  const keyword = searchParams.get('name')?.trim() ?? '';
  const startFrom = searchParams.get('startFrom');
  const endFrom = searchParams.get('endFrom');
  const sortParam = searchParams.get('sort');
  const sort = isSortKey(sortParam) ? sortParam : undefined;
  const direction = searchParams.get('dir') === 'ASC' ? 'ASC' : 'DESC';
  const page = Math.max(1, Number(searchParams.get('listPage')) || 1);

  // 값이 없으면 쿼리에서 지운다. 조건이 바뀌면 첫 페이지로 돌아간다.
  const updateSearch = (changes: Record<string, string | undefined>) => {
    setEditing(null);
    const next = new URLSearchParams(searchParams);
    if (!('listPage' in changes)) next.delete('listPage');
    Object.entries(changes).forEach(([key, value]) =>
      value ? next.set(key, value) : next.delete(key),
    );
    // 페이지 이동만 기록에 남겨 뒤로가기로 이전 페이지에 돌아가게 하고, 정렬·검색·필터 변경은 덮어쓴다.
    setSearchParams(next, { replace: !('listPage' in changes) });
  };

  const request: SaleEventListRequest = {
    status,
    type,
    keyword: keyword || undefined,
    startFrom: startFrom ?? undefined,
    endFrom: endFrom ?? undefined,
    sort,
    direction: sort ? direction : undefined,
    page,
    size: PAGE_SIZE,
  };
  const events = useQuery(saleQueries.events(request));
  const rows = (events.data?.content ?? []).flatMap(toRow);

  // 정렬 중인 열에만 방향을 표시한다. 같은 열을 다시 누르면 방향이 바뀌고, 세 번째에 정렬이 풀린다.
  const sortOrderOf = (column: SortColumn) =>
    sort === SORT_KEYS[column] ? (direction === 'ASC' ? 'ascend' : 'descend') : null;

  const handleChangeTable = (
    pagination: TablePaginationConfig,
    _filters: unknown,
    sorter: SorterResult<EventRow> | SorterResult<EventRow>[],
    extra: { action: string },
  ) => {
    if (extra.action === 'paginate') {
      updateSearch({
        listPage:
          pagination.current && pagination.current > 1 ? String(pagination.current) : undefined,
      });
      return;
    }
    const { columnKey, order } = Array.isArray(sorter) ? sorter[0] : sorter;
    const column = order ? SORT_KEYS[columnKey as SortColumn] : undefined;
    updateSearch({
      sort: column,
      dir: column && order === 'ascend' ? 'ASC' : column ? 'DESC' : undefined,
    });
  };

  // 수정 칸이 열려 있는 행을 다시 누르면 닫는다.
  const toggleEditing = (row: EventRow) =>
    setEditing(
      editing?.key === rowKeyOf(row) ? null : { key: rowKeyOf(row), saleEventId: row.saleEventId },
    );

  const columns: TableColumnsType<EventRow> = [
    {
      title: '상품',
      dataIndex: 'productName',
      key: 'productName',
      width: 136,
      ellipsis: true,
      sorter: true,
      sortOrder: sortOrderOf('productName'),
    },
    {
      title: '종류',
      dataIndex: 'type',
      key: 'type',
      width: 90,
      sorter: true,
      sortOrder: sortOrderOf('type'),
      render: (value: Type) => <Tag color={TYPE_COLORS[value]}>{SALE_TYPE_LABELS[value]}</Tag>,
    },
    {
      title: '상태',
      dataIndex: 'status',
      key: 'status',
      width: 70,
      sorter: true,
      sortOrder: sortOrderOf('status'),
      render: (value: Status) => (
        <Tag color={STATUS_COLORS[value]} variant={value === 'ENDED' ? 'outlined' : undefined}>
          {SALE_STATUS_LABELS[value]}
        </Tag>
      ),
    },
    {
      title: '정가',
      dataIndex: 'listPrice',
      key: 'listPrice',
      width: 80,
      sorter: true,
      sortOrder: sortOrderOf('listPrice'),
      align: 'right',
      render: (value: number) => formatPrice(value),
    },
    {
      title: '할인가',
      dataIndex: 'salePrice',
      key: 'salePrice',
      width: 80,
      sorter: true,
      sortOrder: sortOrderOf('salePrice'),
      align: 'right',
      render: (value: number) => formatPrice(value),
    },
    {
      title: '할인율',
      dataIndex: 'discountRate',
      key: 'discountRate',
      width: 80,
      sorter: true,
      sortOrder: sortOrderOf('discountRate'),
      align: 'right',
      render: (value: number) => `${value}%`,
    },
    {
      title: '한정 수량',
      dataIndex: 'limitedQuantity',
      key: 'limitedQuantity',
      width: 100,
      sorter: true,
      sortOrder: sortOrderOf('limitedQuantity'),
      align: 'right',
      // 번개 드롭만 한정 수량이 있고, 특가는 비워 둔다.
      render: (value: number | undefined) => value ?? '',
    },
    {
      title: '남은 수량',
      dataIndex: 'remainingQuantity',
      key: 'remainingQuantity',
      width: 100,
      sorter: true,
      sortOrder: sortOrderOf('remainingQuantity'),
      align: 'right',
      render: (value: number | undefined) => value ?? '',
    },
    {
      title: '판매량',
      dataIndex: 'soldQuantity',
      key: 'soldQuantity',
      width: 59,
      align: 'right',
    },
    {
      title: '시작',
      dataIndex: 'startsAt',
      key: 'startsAt',
      width: 100,
      sorter: true,
      sortOrder: sortOrderOf('startsAt'),
      render: renderDateTime,
    },
    {
      title: '종료',
      dataIndex: 'endsAt',
      key: 'endsAt',
      width: 100,
      sorter: true,
      sortOrder: sortOrderOf('endsAt'),
      render: renderDateTime,
    },
    {
      title: '작업',
      key: 'actions',
      width: 100,
      // 예정 행사는 수정·삭제, 진행 중 행사는 종료만 한다. 삭제·종료는 API가 붙기 전까지 누를 수 없다.
      render: (_: unknown, row: EventRow) => {
        if (row.status === 'SCHEDULED') {
          return (
            <Space size={0}>
              <Button
                type="text"
                className={styles.action}
                icon={<EditOutlined />}
                aria-label="수정"
                title="수정"
                onClick={(event) => {
                  // 행 전체가 눌려도 펼쳐지므로 아이콘 클릭이 행 클릭으로 한 번 더 번지지 않게 한다.
                  event.stopPropagation();
                  toggleEditing(row);
                }}
              />
              <Button
                type="text"
                className={`${styles.action} ${styles.delete}`}
                danger
                disabled
                icon={<DeleteOutlined />}
                aria-label="삭제"
                title="삭제(준비 중)"
              />
            </Space>
          );
        }
        if (row.status === 'ACTIVE') {
          return (
            <Button
              type="text"
              className={styles.action}
              danger
              disabled
              icon={<StopOutlined />}
              aria-label="종료"
              title="종료(준비 중)"
            />
          );
        }
        return null;
      },
    },
  ];

  if (events.isError) {
    return (
      <Result
        status="error"
        title="행사 목록을 불러오지 못했습니다"
        extra={<Button onClick={() => void events.refetch()}>다시 시도</Button>}
      />
    );
  }

  return (
    <Flex vertical gap="middle">
      <Flex wrap gap="small" align="center">
        <Input.Search
          key={keyword}
          defaultValue={keyword}
          placeholder="상품 이름"
          aria-label="상품 이름 검색"
          enterButton="검색"
          allowClear
          maxLength={50}
          onSearch={(value) => updateSearch({ name: value.trim() || undefined })}
          className={styles.search}
        />
        <Select
          aria-label="행사 상태"
          value={status ?? ALL}
          options={[
            { value: ALL, label: '전체 상태' },
            ...(Object.keys(SALE_STATUS_LABELS) as Status[]).map((value) => ({
              value,
              label: SALE_STATUS_LABELS[value],
            })),
          ]}
          onChange={(value: string) => updateSearch({ status: value === ALL ? undefined : value })}
          className={styles.filter}
        />
        <Select
          aria-label="행사 종류"
          value={type ?? ALL}
          options={[
            { value: ALL, label: '전체 종류' },
            ...(Object.keys(SALE_TYPE_LABELS) as Type[]).map((value) => ({
              value,
              label: SALE_TYPE_LABELS[value],
            })),
          ]}
          onChange={(value: string) => updateSearch({ kind: value === ALL ? undefined : value })}
          className={styles.filter}
        />
        {/* 하나만 고르며 그날 시작(종료)한 행사만 나온다. */}
        <DatePicker
          aria-label="시작일 검색"
          placeholder="시작일"
          value={startFrom ? dayjs(startFrom) : null}
          onChange={(date) => updateSearch({ startFrom: date?.format(DATE_FORMAT) })}
          className={styles.date}
        />
        <DatePicker
          aria-label="종료일 검색"
          placeholder="종료일"
          value={endFrom ? dayjs(endFrom) : null}
          onChange={(date) => updateSearch({ endFrom: date?.format(DATE_FORMAT) })}
          className={styles.date}
        />
      </Flex>
      <Table<EventRow>
        className={styles.table}
        rowKey={rowKeyOf}
        loading={events.isPending}
        dataSource={rows}
        columns={columns}
        tableLayout="fixed"
        scroll={{ x: TABLE_WIDTH }}
        // 예정 행사는 아이콘이 아니라 행 어디를 눌러도 수정 칸이 열린다.
        rowClassName={(row) => (row.status === 'SCHEDULED' ? styles.editableRow : '')}
        onRow={(row) => (row.status === 'SCHEDULED' ? { onClick: () => toggleEditing(row) } : {})}
        expandable={{
          expandedRowKeys: editing === null || narrow ? [] : [editing.key],
          showExpandColumn: false,
          expandedRowRender: (row) => (
            <SaleEventEditPanel saleEventId={row.saleEventId} onClose={() => setEditing(null)} />
          ),
        }}
        onChange={handleChangeTable}
        pagination={{
          current: page,
          pageSize: PAGE_SIZE,
          total: events.data?.totalElements,
          showSizeChanger: false,
        }}
        locale={{ emptyText: '조건에 맞는 행사가 없습니다.' }}
      />
      {narrow && (
        <Drawer
          open={editing !== null}
          title="행사 수정"
          placement="bottom"
          styles={{ wrapper: { height: 'auto', maxHeight: '80vh' } }}
          destroyOnHidden
          onClose={() => setEditing(null)}
        >
          {editing && (
            <SaleEventEditPanel
              saleEventId={editing.saleEventId}
              onClose={() => setEditing(null)}
            />
          )}
        </Drawer>
      )}
    </Flex>
  );
}
