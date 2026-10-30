import { useQuery } from '@tanstack/react-query';
import {
  Button,
  Card,
  Col,
  DatePicker,
  Empty,
  Flex,
  Result,
  Row,
  Segmented,
  Spin,
  Statistic,
  Table,
  Typography,
  theme,
  type TableColumnsType,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useSearchParams } from 'react-router';

import { getErrorMessage } from '@/common/api/errors';
import { SectionLoading } from '@/common/components/SectionLoading';
import type {
  SalesAmountResponse,
  SettlementPeriodResponse,
  SettlementSearch,
  SettlementUnit,
} from '@/domains/settlement/api';
import { SettlementChart } from '@/domains/settlement/components/SettlementChart';
import { formatCount, formatPeriod, formatWon } from '@/domains/settlement/format';
import { settlementQueries } from '@/domains/settlement/queries';

import styles from './AdminSettlementPage.module.scss';

const DATE_FORMAT = 'YYYY-MM-DD';
// 백엔드 SettlementSearchRequest와 같은 규칙: 종료일 < 시작일 + 3개월
const MAX_PERIOD_MONTHS = 3;

const UNIT_OPTIONS: { label: string; value: SettlementUnit }[] = [
  { label: '일', value: 'DAY' },
  { label: '주', value: 'WEEK' },
  { label: '월', value: 'MONTH' },
];

// 순매출은 환불이 더 많은 날 음수가 될 수 있다
function NetAmount({ value }: { value?: number }) {
  return (
    <Typography.Text type={(value ?? 0) < 0 ? 'danger' : undefined}>
      {formatWon(value)}
    </Typography.Text>
  );
}

function isWithinMaxPeriod(start: Dayjs, end: Dayjs) {
  return end.isBefore(start.add(MAX_PERIOD_MONTHS, 'month'), 'day');
}

// 오늘 이후와, 먼저 고른 날에서 3개월 이상 떨어진 날은 고를 수 없다
function disabledDate(current: Dayjs, { from }: { from?: Dayjs }) {
  if (current.isAfter(dayjs(), 'day')) return true;
  if (!from) return false;
  return current.isBefore(from, 'day')
    ? !isWithinMaxPeriod(current, from)
    : !isWithinMaxPeriod(from, current);
}

function parseDate(value: string | null) {
  if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return null;
  const date = dayjs(value);
  return date.isValid() && date.format(DATE_FORMAT) === value ? date : null;
}

// URL 조건이 없거나 규칙에 맞지 않으면 이번 달 1일 ~ 오늘을 일 단위로 본다
function readSearch(searchParams: URLSearchParams): SettlementSearch {
  const today = dayjs();
  const unitParam = searchParams.get('unit');
  const unit = UNIT_OPTIONS.find((option) => option.value === unitParam)?.value ?? 'DAY';
  const start = parseDate(searchParams.get('startDate'));
  const end = parseDate(searchParams.get('endDate'));
  const valid =
    start &&
    end &&
    !end.isBefore(start, 'day') &&
    !end.isAfter(today, 'day') &&
    isWithinMaxPeriod(start, end);
  return {
    unit,
    startDate: (valid ? start : today.startOf('month')).format(DATE_FORMAT),
    endDate: (valid ? end : today).format(DATE_FORMAT),
  };
}

function salesStats(sales?: SalesAmountResponse) {
  return [
    { title: '결제금액', value: formatWon(sales?.paidAmount) },
    { title: '환불금액', value: formatWon(sales?.refundedAmount) },
    { title: '순매출', value: formatWon(sales?.netAmount), negative: (sales?.netAmount ?? 0) < 0 },
    { title: '결제 건수', value: formatCount(sales?.paidCount) },
  ];
}

export default function AdminSettlementPage() {
  const { token } = theme.useToken();
  const [searchParams, setSearchParams] = useSearchParams();
  const search = readSearch(searchParams);

  const todayQuery = useQuery(settlementQueries.today());
  const settlementQuery = useQuery(settlementQueries.list(search));

  const changeSearch = (next: SettlementSearch) =>
    setSearchParams({ unit: next.unit, startDate: next.startDate, endDate: next.endDate });

  const handleRangeChange = (range: [Dayjs | null, Dayjs | null] | null) => {
    const [start, end] = range ?? [];
    if (!start || !end) return;
    changeSearch({
      ...search,
      startDate: start.format(DATE_FORMAT),
      endDate: end.format(DATE_FORMAT),
    });
  };

  const columns: TableColumnsType<SettlementPeriodResponse> = [
    { title: '기간', key: 'period', render: (_, period) => formatPeriod(search.unit, period) },
    { title: '결제금액', dataIndex: 'paidAmount', align: 'right', render: formatWon },
    { title: '환불금액', dataIndex: 'refundedAmount', align: 'right', render: formatWon },
    {
      title: '순매출',
      dataIndex: 'netAmount',
      align: 'right',
      render: (value?: number) => <NetAmount value={value} />,
    },
    {
      title: '결제 건수',
      dataIndex: 'paidCount',
      align: 'right',
      render: formatCount,
    },
  ];

  const summary = settlementQuery.data?.summary;
  const periods = settlementQuery.data?.periods ?? [];
  const hasSales = periods.some((period) => period.paidAmount || period.refundedAmount);

  return (
    <section className={styles.container}>
      <header className={styles.header}>
        <Typography.Title level={2} className={styles.title}>
          정산
        </Typography.Title>
        <Typography.Text type="secondary" className={styles.description}>
          결제·환불 기록 기준 매출이에요. 오늘은 지금까지의 매출을 보여줘요
        </Typography.Text>
      </header>

      <Card
        size="small"
        className={styles.today}
        title={
          todayQuery.data
            ? `오늘 매출 (${dayjs(todayQuery.dataUpdatedAt).format('HH:mm')} 기준)`
            : '오늘 매출'
        }
        extra={
          <Button
            size="small"
            onClick={() => void todayQuery.refetch()}
            loading={todayQuery.isFetching}
          >
            새로고침
          </Button>
        }
      >
        {todayQuery.isError && !todayQuery.data ? (
          <Typography.Text type="danger">{getErrorMessage(todayQuery.error)}</Typography.Text>
        ) : (
          <Spin spinning={todayQuery.isPending}>
            <Row gutter={[token.marginSM, token.marginSM]}>
              {salesStats(todayQuery.data?.sales).map((stat) => (
                <Col key={stat.title} xs={12} md={6}>
                  <Statistic
                    title={stat.title}
                    value={stat.value}
                    styles={stat.negative ? { content: { color: token.colorError } } : undefined}
                  />
                </Col>
              ))}
            </Row>
          </Spin>
        )}
      </Card>

      <Flex wrap gap="middle" align="center" className={styles.controls}>
        <Segmented<SettlementUnit>
          options={UNIT_OPTIONS}
          value={search.unit}
          onChange={(unit) => changeSearch({ ...search, unit })}
        />
        <DatePicker.RangePicker
          className={styles.rangePicker}
          allowClear={false}
          value={[dayjs(search.startDate), dayjs(search.endDate)]}
          disabledDate={disabledDate}
          onChange={handleRangeChange}
        />
      </Flex>

      {settlementQuery.isPending ? (
        <div className={styles.loading}>
          <Spin />
        </div>
      ) : settlementQuery.isError && !settlementQuery.data ? (
        <Result
          status="error"
          title="정산 내역을 불러오지 못했습니다"
          subTitle={getErrorMessage(settlementQuery.error)}
          extra={<Button onClick={() => void settlementQuery.refetch()}>다시 시도</Button>}
        />
      ) : (
        <SectionLoading loading={settlementQuery.isFetching}>
          <Row gutter={[token.marginSM, token.marginSM]} className={styles.summary}>
            {salesStats(summary).map((stat) => (
              <Col key={stat.title} xs={12} md={6}>
                <Card size="small">
                  <Statistic
                    title={stat.title}
                    value={stat.value}
                    styles={stat.negative ? { content: { color: token.colorError } } : undefined}
                  />
                </Card>
              </Col>
            ))}
          </Row>
          <Card size="small" className={styles.chartCard}>
            {hasSales ? (
              <SettlementChart unit={search.unit} periods={periods} />
            ) : (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="이 기간에는 매출이 없어요" />
            )}
          </Card>
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
                  <NetAmount value={summary?.netAmount} />
                </Table.Summary.Cell>
                <Table.Summary.Cell index={4} align="right">
                  {formatCount(summary?.paidCount)}
                </Table.Summary.Cell>
              </Table.Summary.Row>
            )}
          />
        </SectionLoading>
      )}
    </section>
  );
}
