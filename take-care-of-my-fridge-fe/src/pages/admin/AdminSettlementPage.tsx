import { useQuery } from '@tanstack/react-query';
import { Button, DatePicker, Flex, Result, Segmented, Spin, Tabs, Typography } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useSearchParams } from 'react-router';

import { getErrorMessage } from '@/common/api/errors';
import { SectionLoading } from '@/common/components/SectionLoading';
import type { SettlementUnit } from '@/domains/settlement/api';
import { SettlementDailyTable } from '@/domains/settlement/components/SettlementDailyTable';
import { SettlementPaymentTable } from '@/domains/settlement/components/SettlementPaymentTable';
import { SettlementSummarySection } from '@/domains/settlement/components/SettlementSummarySection';
import { SettlementTodayCard } from '@/domains/settlement/components/SettlementTodayCard';
import { settlementQueries } from '@/domains/settlement/queries';
import {
  disabledSettlementDate,
  readSettlementView,
  SETTLEMENT_DATE_FORMAT,
  SETTLEMENT_TABS,
  SETTLEMENT_UNIT_OPTIONS,
  toSettlementParams,
  type SettlementView,
} from '@/domains/settlement/search';

import styles from './AdminSettlementPage.module.scss';

export default function AdminSettlementPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const view = readSettlementView(searchParams);
  const { tab, unit, startDate, endDate, page } = view;

  // 세 탭이 같은 기간 합계를 쓴다. 건별 탭은 합계 행에만 쓴다
  const settlementQuery = useQuery(settlementQueries.list({ unit, startDate, endDate }));

  const changeView = (next: Partial<SettlementView>) =>
    setSearchParams(toSettlementParams({ ...view, ...next }));

  const handleRangeChange = (range: [Dayjs | null, Dayjs | null] | null) => {
    const [start, end] = range ?? [];
    if (!start || !end) return;
    // 기간이 바뀌면 건별 목록은 첫 페이지부터 본다
    changeView({
      startDate: start.format(SETTLEMENT_DATE_FORMAT),
      endDate: end.format(SETTLEMENT_DATE_FORMAT),
      page: 1,
    });
  };

  const summary = settlementQuery.data?.summary;
  const periods = settlementQuery.data?.periods ?? [];

  const renderTab = () => {
    if (tab === 'payments') {
      return (
        <SettlementPaymentTable
          startDate={startDate}
          endDate={endDate}
          page={page}
          summary={settlementQuery.isPlaceholderData ? undefined : summary}
          onRetrySummary={
            settlementQuery.isError && !settlementQuery.data
              ? () => void settlementQuery.refetch()
              : undefined
          }
          onPageChange={(nextPage) => changeView({ page: nextPage })}
        />
      );
    }
    if (settlementQuery.isPending) {
      return (
        <div className={styles.loading}>
          <Spin />
        </div>
      );
    }
    if (settlementQuery.isError && !settlementQuery.data) {
      return (
        <Result
          status="error"
          title="정산 내역을 불러오지 못했습니다"
          subTitle={getErrorMessage(settlementQuery.error)}
          extra={<Button onClick={() => void settlementQuery.refetch()}>다시 시도</Button>}
        />
      );
    }
    return (
      <SectionLoading loading={settlementQuery.isFetching}>
        {tab === 'summary' ? (
          <SettlementSummarySection unit={unit} summary={summary} periods={periods} />
        ) : (
          <SettlementDailyTable unit={unit} summary={summary} periods={periods} />
        )}
      </SectionLoading>
    );
  };

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

      <Flex wrap gap="middle" align="center" className={styles.controls}>
        <DatePicker.RangePicker
          className={styles.rangePicker}
          allowClear={false}
          value={[dayjs(startDate), dayjs(endDate)]}
          disabledDate={disabledSettlementDate}
          onChange={handleRangeChange}
        />
        {tab !== 'payments' && (
          <Segmented<SettlementUnit>
            options={SETTLEMENT_UNIT_OPTIONS}
            value={unit}
            onChange={(nextUnit) => changeView({ unit: nextUnit })}
          />
        )}
      </Flex>

      <Tabs
        activeKey={tab}
        items={SETTLEMENT_TABS}
        onChange={(key) => {
          const nextTab = SETTLEMENT_TABS.find((item) => item.key === key)?.key;
          if (nextTab) changeView({ tab: nextTab });
        }}
      />
      {tab === 'summary' && <SettlementTodayCard className={styles.today} />}
      {renderTab()}
    </section>
  );
}
