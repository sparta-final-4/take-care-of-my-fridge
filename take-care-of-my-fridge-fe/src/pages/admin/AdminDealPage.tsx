import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App,
  Button,
  DatePicker,
  Descriptions,
  Drawer,
  Empty,
  Form,
  InputNumber,
  Result,
  Select,
  Skeleton,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { Dayjs } from 'dayjs';
import dayjs from 'dayjs';
import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';

import { ROUTES } from '@/app/paths';
import type {
  Candidate,
  DealAnalysisRunRequestDayOfWeekEnum,
  DealEventRequestTypeEnum,
  Excluded,
} from '@/common/api/clients';
import { getErrorCode } from '@/common/api/errors';
import { registerDealEvent, runDealAnalysis, saveDealReview } from '@/domains/deal/api';
import { dealQueries } from '@/domains/deal/queries';
import { DATE_TIME_FORMAT, REQUEST_TIME_FORMAT } from '@/domains/sale/format';
import { saleQueries } from '@/domains/sale/queries';

import styles from './AdminDealPage.module.scss';

interface AnalysisFormValues {
  targetWeekStart: Dayjs;
  dayOfWeek: DealAnalysisRunRequestDayOfWeekEnum;
  discountRate: number;
}
interface EventFormValues {
  type: DealEventRequestTypeEnum;
  startsAt: Dayjs;
  endsAt: Dayjs;
  limitedQuantity?: number;
}

const money = (value?: number) =>
  value === undefined ? '-' : `${value.toLocaleString('ko-KR')}원`;
const dateTime = (value?: string) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-');
const dayLabels = { MON: '월', TUE: '화', WED: '수', THU: '목', FRI: '금', SAT: '토', SUN: '일' };

export default function AdminDealPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const demoAvailable = import.meta.env.DEV || window.location.hostname === 'dev.imhungry.click';
  const demo = demoAvailable && searchParams.get('demo') === '1';
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<EventFormValues>();
  const [analysisForm] = Form.useForm<AnalysisFormValues>();
  const [candidate, setCandidate] = useState<Candidate | null>(null);
  const type = Form.useWatch('type', form);
  const {
    data: analysis,
    error,
    isPending,
    isError,
    refetch,
  } = useQuery(demo ? dealQueries.demo() : dealQueries.latest());

  const review = useMutation({
    mutationFn: ({
      candidateId,
      reviewStatus,
    }: {
      candidateId: number;
      reviewStatus: 'SELECTED' | 'HELD';
    }) =>
      saveDealReview(analysis!.analysisId!, {
        reviews: [{ candidateId, reviewStatus }],
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: dealQueries.all }),
  });

  const registration = useMutation({
    mutationFn: (values: EventFormValues) =>
      registerDealEvent(analysis!.analysisId!, {
        candidateId: candidate!.candidateId!,
        type: values.type,
        startsAt: values.startsAt.format(REQUEST_TIME_FORMAT),
        endsAt: values.endsAt.format(REQUEST_TIME_FORMAT),
        ...(values.type === 'FLASH_DROP' ? { limitedQuantity: values.limitedQuantity } : {}),
      }),
    onSuccess: () => {
      void message.success('할인 행사를 등록했습니다.');
      setCandidate(null);
      form.resetFields();
      void queryClient.invalidateQueries({ queryKey: saleQueries.all });
      return queryClient.invalidateQueries({ queryKey: dealQueries.all });
    },
  });

  const runAnalysis = useMutation({
    mutationFn: (values: AnalysisFormValues) =>
      runDealAnalysis({
        targetWeekStart: values.targetWeekStart.format('YYYY-MM-DD'),
        dayOfWeek: values.dayOfWeek,
        discountRate: values.discountRate,
      }),
    // 실패 문구를 아래 onError에서 직접 띄우므로 전역 알림은 끈다.
    meta: { skipGlobalError: true },
    onSuccess: () => {
      void message.success('할인 후보 분석을 완료했습니다.');
      setSearchParams({});
      return queryClient.invalidateQueries({ queryKey: dealQueries.all });
    },
    onError: (error) =>
      void message.error(
        getErrorCode(error) === 'D007'
          ? '일반 특가 이력이 부족해 분석할 수 없습니다.'
          : '할인 후보 분석을 실행하지 못했습니다.',
      ),
  });
  const runControls = (
    <Form<AnalysisFormValues>
      form={analysisForm}
      layout="inline"
      initialValues={{
        targetWeekStart: dayjs().day(1).add(1, 'week'),
        dayOfWeek: 'MON',
        discountRate: 20,
      }}
      onFinish={(values) => runAnalysis.mutate(values)}
      className={styles.notice}
    >
      <Form.Item name="targetWeekStart" label="목표 주 월요일" rules={[{ required: true }]}>
        <DatePicker disabledDate={(date) => date.day() !== 1} />
      </Form.Item>
      <Form.Item name="dayOfWeek" label="요일" rules={[{ required: true }]}>
        <Select
          style={{ width: 90 }}
          options={Object.entries(dayLabels).map(([value, label]) => ({ value, label }))}
        />
      </Form.Item>
      <Form.Item name="discountRate" label="할인율" rules={[{ required: true }]}>
        <InputNumber min={1} max={99} suffix="%" />
      </Form.Item>
      <Form.Item>
        <Button type="primary" htmlType="submit" loading={runAnalysis.isPending}>
          분석 실행
        </Button>
      </Form.Item>
    </Form>
  );
  const demoToggle = demoAvailable && (
    <Button onClick={() => setSearchParams(demo ? {} : { demo: '1' })}>
      {demo ? '최근 분석 보기' : '임의 할인 이벤트 추천 보기'}
    </Button>
  );
  const heading = (
    <Space className={styles.heading}>
      <Typography.Title level={2}>할인 후보</Typography.Title>
      {demoToggle}
      <Button onClick={() => navigate(ROUTES.adminSaleEventNew)}>행사 관리</Button>
    </Space>
  );

  if (isPending) {
    return (
      <div className={styles.container}>
        {heading}
        {runControls}
        <Skeleton active paragraph={{ rows: 8 }} />
      </div>
    );
  }
  if (isError) {
    if (getErrorCode(error) === 'D002') {
      return (
        <div className={styles.container}>
          {heading}
          {runControls}
          <Empty
            description={
              demo ? '시연 결과가 아직 없습니다.' : '아직 할인 후보 분석 결과가 없습니다.'
            }
          />
        </div>
      );
    }
    return (
      <div className={styles.container}>
        {heading}
        <Result
          status="error"
          title="할인 후보를 불러오지 못했습니다"
          extra={<Button onClick={() => void refetch()}>다시 시도</Button>}
        />
      </div>
    );
  }

  return (
    <section className={styles.container}>
      {heading}
      {runControls}
      <Typography.Paragraph type="secondary">
        {analysis.targetWeekStart} 주 · {analysis.dayOfWeek ? dayLabels[analysis.dayOfWeek] : '-'}
        요일 · 분석 할인율 {analysis.discountRate}% · 최소 재고{' '}
        {analysis.minStock?.toLocaleString('ko-KR') ?? '-'}개
      </Typography.Paragraph>
      <Descriptions bordered size="small" column={{ xs: 1, sm: 2 }} className={styles.notice}>
        <Descriptions.Item label="모델 버전">{analysis.modelVersion ?? '-'}</Descriptions.Item>
        <Descriptions.Item label="입력 데이터 기준">
          {dateTime(analysis.dataCutoffAt)}
        </Descriptions.Item>
        <Descriptions.Item label="분석 시각">{dateTime(analysis.createdAt)}</Descriptions.Item>
        <Descriptions.Item label="검토 저장 시각">
          {dateTime(analysis.lastSavedAt)}
        </Descriptions.Item>
        <Descriptions.Item label="검토 현황">
          선택 {analysis.reviewCounts?.selected ?? 0} · 보류 {analysis.reviewCounts?.held ?? 0} ·
          미검토 {analysis.reviewCounts?.unreviewed ?? 0}
        </Descriptions.Item>
      </Descriptions>
      {analysis.syntheticData && (
        <Alert
          type="warning"
          showIcon
          message={
            demoAvailable
              ? '시연용 수치이며 모델 결과가 아닙니다. 등록하면 dev 상품에 실제 할인이 적용됩니다.'
              : '가상 데이터 분석 결과는 행사에 등록할 수 없습니다.'
          }
          className={styles.notice}
        />
      )}
      <Table<Candidate>
        rowKey={(item) => item.candidateId ?? item.productId ?? 0}
        dataSource={analysis.candidates ?? []}
        scroll={{ x: 1050 }}
        pagination={false}
        columns={[
          { title: '상품', dataIndex: 'productName' },
          { title: '정가', dataIndex: 'regularPrice', render: money },
          { title: '추천 가격', dataIndex: 'discountPrice', render: money },
          { title: '재고', dataIndex: 'currentStock' },
          { title: '검색 클릭', dataIndex: 'searchClicks' },
          { title: '순판매 수량', dataIndex: 'purchases' },
          {
            title: '클릭−순판매 차이',
            render: (_, item) =>
              ((item.searchClicks ?? 0) - (item.purchases ?? 0)).toLocaleString('ko-KR'),
          },
          {
            title: '예상 판매 증가',
            dataIndex: 'expectedIncrease',
            render: (value?: number) => value?.toLocaleString('ko-KR') ?? '-',
          },
          {
            title: '추천 이유',
            dataIndex: 'candidateReason',
            render: (value?: string) => value || '-',
          },
          {
            title: '검토',
            dataIndex: 'reviewStatus',
            render: (status: Candidate['reviewStatus']) => (
              <Tag color={status === 'SELECTED' ? 'green' : undefined}>
                {status === 'SELECTED' ? '선택' : status === 'HELD' ? '보류' : '미검토'}
              </Tag>
            ),
          },
          {
            title: '행사',
            dataIndex: 'eventRequestStatus',
            render: (status: Candidate['eventRequestStatus']) =>
              status === 'APPROVED' ? (
                <Tag color="blue">등록 완료</Tag>
              ) : status === 'REQUESTED' ? (
                <Tag>요청 중</Tag>
              ) : status === 'REJECTED' ? (
                <Tag>반려</Tag>
              ) : (
                '-'
              ),
          },
          {
            title: '작업',
            render: (_, item) => (
              <Space>
                {item.eventRequestStatus === 'NONE' && item.reviewStatus !== 'SELECTED' && (
                  <Button
                    loading={review.isPending}
                    disabled={item.candidateId === undefined}
                    onClick={() =>
                      review.mutate(
                        { candidateId: item.candidateId!, reviewStatus: 'SELECTED' },
                        { onSuccess: () => void message.success('행사 후보로 선택했습니다.') },
                      )
                    }
                  >
                    선택
                  </Button>
                )}
                {item.eventRequestStatus === 'NONE' && item.reviewStatus !== 'HELD' && (
                  <Button
                    loading={review.isPending}
                    disabled={item.candidateId === undefined}
                    onClick={() =>
                      review.mutate(
                        { candidateId: item.candidateId!, reviewStatus: 'HELD' },
                        { onSuccess: () => void message.success('후보를 보류했습니다.') },
                      )
                    }
                  >
                    보류
                  </Button>
                )}
                {item.eventRequestStatus === 'NONE' && item.reviewStatus === 'SELECTED' && (
                  <Button
                    type="primary"
                    disabled={analysis.syntheticData && !demoAvailable}
                    onClick={() => {
                      form.setFieldsValue({ type: 'SPECIAL' });
                      setCandidate(item);
                    }}
                  >
                    행사 등록
                  </Button>
                )}
              </Space>
            ),
          },
        ]}
      />
      <Typography.Title level={4} className={styles.excludedTitle}>
        제외 상품
      </Typography.Title>
      <Table<Excluded>
        rowKey={(item) => item.candidateId ?? item.productId ?? 0}
        dataSource={analysis.excluded ?? []}
        pagination={false}
        scroll={{ x: 540 }}
        locale={{ emptyText: '제외된 상품이 없습니다.' }}
        columns={[
          { title: '상품', dataIndex: 'productName' },
          {
            title: '제외 사유',
            dataIndex: 'excludeReason',
            render: (reason: Excluded['excludeReason']) =>
              reason === 'TOP_SELLER_ON_DAY'
                ? '해당 요일 상위 판매 상품'
                : reason === 'LOW_STOCK'
                  ? '재고 부족'
                  : '-',
          },
          { title: '해당 요일 평균 판매', dataIndex: 'avgSalesOnDay' },
          { title: '현재 재고', dataIndex: 'currentStock' },
        ]}
      />
      <Drawer
        title={`${candidate?.productName ?? ''} 행사 등록`}
        open={candidate !== null}
        onClose={() => {
          setCandidate(null);
          form.resetFields();
        }}
        destroyOnHidden
        width="min(440px, 100vw)"
      >
        <Typography.Paragraph>
          추천 할인율 {analysis.discountRate}% · 분석 당시 예상 할인가{' '}
          {money(candidate?.discountPrice)}
        </Typography.Paragraph>
        <Form<EventFormValues>
          form={form}
          layout="vertical"
          onFinish={(values) => registration.mutate(values)}
        >
          <Form.Item name="type" label="행사 종류" rules={[{ required: true }]}>
            <Select
              options={[
                { label: '특가', value: 'SPECIAL' },
                { label: '번개 드롭', value: 'FLASH_DROP' },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="startsAt"
            label="시작 시각"
            rules={[
              { required: true, message: '시작 시각을 정해 주세요.' },
              {
                validator: (_, value: Dayjs | undefined) =>
                  !value || value.isAfter(dayjs())
                    ? Promise.resolve()
                    : Promise.reject(new Error('시작 시각은 지금 이후여야 합니다.')),
              },
            ]}
          >
            <DatePicker showTime format={DATE_TIME_FORMAT} className={styles.dateTime} />
          </Form.Item>
          <Form.Item
            name="endsAt"
            label="종료 시각"
            dependencies={['startsAt']}
            rules={[
              { required: true, message: '종료 시각을 정해 주세요.' },
              {
                validator: (_, value: Dayjs | undefined) =>
                  !value || value.isAfter(form.getFieldValue('startsAt'))
                    ? Promise.resolve()
                    : Promise.reject(new Error('종료 시각은 시작 시각보다 늦어야 합니다.')),
              },
            ]}
          >
            <DatePicker showTime format={DATE_TIME_FORMAT} className={styles.dateTime} />
          </Form.Item>
          {type === 'FLASH_DROP' && (
            <Form.Item
              name="limitedQuantity"
              label="한정 수량"
              rules={[{ required: true }, { type: 'number', min: 1 }]}
            >
              <InputNumber min={1} precision={0} className={styles.dateTime} />
            </Form.Item>
          )}
          <Button type="primary" htmlType="submit" loading={registration.isPending} block>
            행사 등록
          </Button>
        </Form>
      </Drawer>
    </section>
  );
}
