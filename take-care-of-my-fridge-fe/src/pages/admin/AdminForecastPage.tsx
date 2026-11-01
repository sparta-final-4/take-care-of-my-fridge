import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Card,
  Col,
  DatePicker,
  Descriptions,
  Empty,
  Result,
  Row,
  Space,
  Spin,
  Statistic,
  Table,
  Tabs,
  Tag,
  Typography,
  theme,
} from 'antd';
import dayjs from 'dayjs';
import { useSearchParams } from 'react-router';

import { getErrorCode } from '@/common/api/errors';
import type { ForecastItem } from '@/common/api/clients';
import { forecastQueries } from '@/domains/forecast/queries';

import styles from './AdminForecastPage.module.scss';

const numberOrDash = (value: number | null) => value?.toLocaleString('ko-KR') ?? '-';
const dateTime = (value?: string) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-');

// URL에 직접 넣은 값은 월요일 날짜(YYYY-MM-DD)일 때만 쓰고, 나머지는 최신 주로 본다.
function toWeekStart(value: string | null) {
  if (!value) return undefined;
  const date = dayjs(value);
  return date.isValid() && date.format('YYYY-MM-DD') === value && date.day() === 1
    ? value
    : undefined;
}

export default function AdminForecastPage() {
  const { token } = theme.useToken();
  const [searchParams, setSearchParams] = useSearchParams();
  const weekStart = toWeekStart(searchParams.get('weekStart'));
  const demoAvailable = import.meta.env.DEV || window.location.hostname === 'dev.imhungry.click';
  const demo = demoAvailable && searchParams.get('demo') === '1';
  const activeTab = searchParams.get('tab') === 'result' ? 'result' : 'forecast';
  const {
    data: fetchedData,
    error,
    isPending,
    isError,
    isPlaceholderData,
    refetch,
  } = useQuery(forecastQueries.detail(weekStart, demo));
  const data = !demo && fetchedData?.syntheticData ? undefined : fetchedData;

  const updateParams = (key: 'weekStart' | 'tab' | 'demo', value?: string) => {
    const next = new URLSearchParams(searchParams);
    if (value) next.set(key, value);
    else next.delete(key);
    if (key === 'weekStart') next.delete('demo');
    if (key === 'demo') next.delete('weekStart');
    setSearchParams(next);
  };

  return (
    <section className={styles.container}>
      <Typography.Title level={2}>판매 예측</Typography.Title>
      <Space wrap className={styles.controls}>
        <DatePicker
          aria-label="조회할 주의 월요일"
          placeholder="조회할 주의 월요일"
          value={weekStart ? dayjs(weekStart) : data ? dayjs(data.weekStart) : null}
          disabledDate={(date) => date.day() !== 1}
          onChange={(date) => updateParams('weekStart', date?.format('YYYY-MM-DD'))}
        />
        <Button
          type={!demo && !weekStart ? 'primary' : 'default'}
          onClick={() => updateParams('weekStart')}
        >
          최신 주
        </Button>
        {demoAvailable && (
          <Button type={demo ? 'primary' : 'default'} onClick={() => updateParams('demo', '1')}>
            임의 테스트 결과
          </Button>
        )}
        <Button
          disabled={isPlaceholderData || !data?.previousWeekStart}
          onClick={() => updateParams('weekStart', data?.previousWeekStart ?? undefined)}
        >
          이전 주
        </Button>
        <Button
          disabled={isPlaceholderData || !data?.nextWeekStart}
          onClick={() => updateParams('weekStart', data?.nextWeekStart ?? undefined)}
        >
          다음 주
        </Button>
      </Space>

      {isPending ? (
        <div className={styles.loading}>
          <Spin description="판매 예측을 불러오고 있습니다" />
        </div>
      ) : isError || !data ? (
        !isError || getErrorCode(error) === 'F001' ? (
          <Empty
            description={
              demo
                ? '시연 예측이 없습니다.'
                : weekStart
                  ? '해당 주의 실제 판매 예측이 없습니다.'
                  : '실제 판매 예측이 아직 없습니다.'
            }
          />
        ) : (
          <Result
            status="error"
            title="판매 예측을 불러오지 못했습니다"
            extra={<Button onClick={() => void refetch()}>다시 시도</Button>}
          />
        )
      ) : (
        <Spin spinning={isPlaceholderData} description="판매 예측을 불러오고 있습니다">
          <Typography.Title level={5}>
            {data.weekStart} ~ {data.weekEnd}
          </Typography.Title>
          {data.syntheticData && (
            <Alert
              type="warning"
              showIcon
              title="시연용 예측·실적 수치입니다. 실제 모델의 예측 결과가 아닙니다."
              className={styles.notice}
            />
          )}
          <Descriptions bordered size="small" column={{ xs: 1, sm: 2 }} className={styles.metadata}>
            <Descriptions.Item label="모델 버전">{data.modelVersion}</Descriptions.Item>
            <Descriptions.Item label="생성 시각">{dateTime(data.createdAt)}</Descriptions.Item>
            <Descriptions.Item label="입력 데이터 기준 시각">
              {dateTime(data.dataCutoffAt)}
            </Descriptions.Item>
            <Descriptions.Item label="실적 확정 시각">
              {dateTime(data.confirmedAt)}
            </Descriptions.Item>
          </Descriptions>

          <Tabs
            activeKey={activeTab}
            onChange={(tab) => updateParams('tab', tab === 'result' ? 'result' : undefined)}
            items={[
              { key: 'forecast', label: '예상 TOP 10' },
              { key: 'result', label: '예측·실적 비교' },
            ]}
          />
          {activeTab === 'forecast' ? (
            <Table<ForecastItem>
              rowKey="productId"
              pagination={false}
              dataSource={data.items?.filter((item) => item.predictedRank != null)}
              locale={{ emptyText: '예상 상품이 없습니다.' }}
              scroll={{ x: 'max-content' }}
              columns={[
                { title: '예상 순위', dataIndex: 'predictedRank', key: 'predictedRank' },
                { title: '상품', dataIndex: 'productName', key: 'productName' },
                {
                  title: '예상 수량',
                  dataIndex: 'predictedQty',
                  key: 'predictedQty',
                  render: numberOrDash,
                },
                {
                  title: '지난주 수량',
                  dataIndex: 'baselineQty',
                  key: 'baselineQty',
                  render: (value: number) => value.toLocaleString('ko-KR'),
                },
              ]}
            />
          ) : !data.confirmed || !data.summary ? (
            <Empty description="실제 판매량이 확정되면 비교 결과를 볼 수 있습니다." />
          ) : (
            <>
              <Row gutter={[token.marginSM, token.marginSM]} className={styles.summary}>
                <Col xs={12} md={8} lg={4}>
                  <Card size="small">
                    <Statistic
                      title="TOP 10 일치"
                      value={data.summary.top10MatchCount}
                      suffix="개"
                    />
                  </Card>
                </Col>
                <Col xs={12} md={8} lg={4}>
                  <Card size="small">
                    <Statistic
                      title="지난주 TOP 10 일치"
                      value={data.summary.baselineTop10MatchCount}
                      suffix="개"
                    />
                  </Card>
                </Col>
                <Col xs={12} md={8} lg={4}>
                  <Card size="small">
                    <Statistic
                      title="예측 MAE"
                      value={data.summary.meanAbsoluteError}
                      precision={1}
                    />
                  </Card>
                </Col>
                <Col xs={12} md={8} lg={4}>
                  <Card size="small">
                    <Statistic
                      title="지난주 수량 MAE"
                      value={data.summary.baselineMeanAbsoluteError}
                      precision={1}
                    />
                  </Card>
                </Col>
                <Col xs={12} md={8} lg={4}>
                  <Card size="small">
                    <Statistic title="새로 진입" value={data.summary.enteredCount} suffix="개" />
                  </Card>
                </Col>
                <Col xs={12} md={8} lg={4}>
                  <Card size="small">
                    <Statistic title="TOP 10 이탈" value={data.summary.droppedCount} suffix="개" />
                  </Card>
                </Col>
              </Row>
              <Typography.Paragraph type="secondary">
                MAE는 예상 TOP 10 상품의 평균 절대 수량 오차입니다. 수량 차이는 예상 수량에서 확정
                실적을 뺀 값이며, 실적은 {dateTime(data.confirmedAt)}에 확정됐습니다.
              </Typography.Paragraph>
              <Table<ForecastItem>
                rowKey="productId"
                pagination={false}
                dataSource={data.items}
                scroll={{ x: 'max-content' }}
                locale={{ emptyText: '비교할 상품이 없습니다.' }}
                columns={[
                  {
                    title: '예상 순위',
                    dataIndex: 'predictedRank',
                    key: 'predictedRank',
                    render: numberOrDash,
                  },
                  {
                    title: '실제 순위',
                    dataIndex: 'actualRank',
                    key: 'actualRank',
                    render: numberOrDash,
                  },
                  { title: '상품', dataIndex: 'productName', key: 'productName' },
                  {
                    title: '예상',
                    dataIndex: 'predictedQty',
                    key: 'predictedQty',
                    render: numberOrDash,
                  },
                  { title: '실적', dataIndex: 'actualQty', key: 'actualQty', render: numberOrDash },
                  {
                    title: '지난주 수량',
                    dataIndex: 'baselineQty',
                    key: 'baselineQty',
                    render: (value: number) => value.toLocaleString('ko-KR'),
                  },
                  {
                    title: '순위 변화',
                    dataIndex: 'rankChange',
                    key: 'rankChange',
                    render: numberOrDash,
                  },
                  {
                    title: '수량 차이',
                    dataIndex: 'qtyError',
                    key: 'qtyError',
                    render: numberOrDash,
                  },
                  {
                    title: '결과',
                    dataIndex: 'status',
                    key: 'status',
                    render: (status: ForecastItem['status']) =>
                      status ? (
                        <Tag
                          color={
                            status === 'MATCHED'
                              ? 'green'
                              : status === 'ENTERED'
                                ? 'blue'
                                : 'orange'
                          }
                        >
                          {status === 'MATCHED' ? '일치' : status === 'ENTERED' ? '진입' : '이탈'}
                        </Tag>
                      ) : (
                        '-'
                      ),
                  },
                ]}
              />
            </>
          )}
        </Spin>
      )}
    </section>
  );
}
