import { ClockCircleOutlined, FilterOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { useMutation } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Card,
  Col,
  Empty,
  Flex,
  Popconfirm,
  Row,
  Spin,
  Statistic,
  Table,
  Tag,
  Typography,
  theme,
  type TableColumnsType,
} from 'antd';
import { generatePath } from 'react-router';

import { ROUTES } from '@/app/paths';
import { runReviewSummary, type ReviewSummaryItem } from '@/domains/review/api';

import styles from './AdminReviewSummaryPage.module.scss';

const PAGE_SIZE = 20;
// 백엔드 ReviewSummaryFacade의 시간 예산 초과 이유와 같은 문구
const TIME_BUDGET_REASON = '시간 초과로 다음 실행';

// 실행 전에도 보이는 안내. "최대 20초"는 백엔드 관리자 실행 시간 예산(ReviewSummaryAdminController)과 같다.
const GUIDES = [
  { icon: <ClockCircleOutlined />, title: '자동 실행', text: '매일 새벽 4시' },
  { icon: <FilterOutlined />, title: '요약 대상', text: '후기 5개 이상, 새 후기가 생긴 레시피' },
  {
    icon: <ThunderboltOutlined />,
    title: '지금 실행',
    text: '한 번에 최대 20초, 남으면 다시 누르기',
  },
];

const STATUS_TAGS: Record<
  NonNullable<ReviewSummaryItem['status']>,
  { color: string; label: string }
> = {
  SUCCESS: { color: 'success', label: '성공' },
  SKIPPED: { color: 'default', label: '건너뜀' },
  FAILED: { color: 'error', label: '실패' },
};

const numberOrDash = (value?: number | null) => value?.toLocaleString('ko-KR') ?? '-';
const usd = (value?: number | null) => (value == null ? '-' : `$${value.toFixed(5)}`);

const columns: TableColumnsType<ReviewSummaryItem> = [
  {
    title: '레시피',
    dataIndex: 'recipeName',
    render: (_, item) =>
      item.recipeId == null ? (
        (item.recipeName ?? '-')
      ) : (
        <Typography.Link
          href={generatePath(ROUTES.recipeDetail, { recipeId: String(item.recipeId) })}
          target="_blank"
          rel="noopener noreferrer"
        >
          {item.recipeName ?? `레시피 ${item.recipeId}`}
        </Typography.Link>
      ),
  },
  {
    title: '결과',
    dataIndex: 'status',
    render: (_, item) =>
      item.status ? (
        <Tag color={STATUS_TAGS[item.status].color}>{STATUS_TAGS[item.status].label}</Tag>
      ) : (
        '-'
      ),
  },
  { title: '후기 수', dataIndex: 'reviewCount', align: 'right', render: numberOrDash },
  {
    title: '토큰 입력·출력',
    key: 'tokens',
    align: 'right',
    render: (_, item) => `${numberOrDash(item.inputTokens)} / ${numberOrDash(item.outputTokens)}`,
  },
  { title: '비용', dataIndex: 'costUsd', align: 'right', render: usd },
  {
    title: '요약',
    key: 'summary',
    render: (_, item) => {
      const text = item.status === 'SUCCESS' ? item.newSummary : item.reason;
      return (
        <Typography.Text
          type={item.status === 'SUCCESS' ? undefined : 'secondary'}
          ellipsis={{ tooltip: text }}
          className={styles.summaryText}
        >
          {text ?? '-'}
        </Typography.Text>
      );
    },
  },
];

interface SummaryCompareProps {
  item: ReviewSummaryItem;
}

// 펼친 행: 이전 요약과 새 요약을 나란히 보여준다(모바일은 위아래)
function SummaryCompare({ item }: SummaryCompareProps) {
  const { token } = theme.useToken();
  return (
    <Row gutter={[token.margin, token.marginXS]}>
      <Col xs={24} md={12}>
        <Typography.Text type="secondary">이전 요약</Typography.Text>
        <Typography.Paragraph className={styles.compareText}>
          {item.previousSummary ?? '(없음)'}
        </Typography.Paragraph>
      </Col>
      <Col xs={24} md={12}>
        <Typography.Text type="secondary">새 요약</Typography.Text>
        <Typography.Paragraph className={styles.compareText}>
          {item.newSummary ?? '(없음)'}
        </Typography.Paragraph>
      </Col>
    </Row>
  );
}

export default function AdminReviewSummaryPage() {
  const { token } = theme.useToken();
  const { mutate, data: result, isPending } = useMutation({ mutationFn: runReviewSummary });

  const items = result?.items ?? [];
  const hasTimeBudgetSkip = items.some(
    (item) => item.status === 'SKIPPED' && item.reason === TIME_BUDGET_REASON,
  );

  const stats = result && [
    { title: '대상', value: numberOrDash(result.targetCount), suffix: '개' },
    { title: '성공', value: numberOrDash(result.successCount), suffix: '개' },
    { title: '건너뜀', value: numberOrDash(result.skippedCount), suffix: '개' },
    { title: '실패', value: numberOrDash(result.failedCount), suffix: '개' },
    {
      title: '걸린 시간',
      value: result.elapsedMillis == null ? '-' : (result.elapsedMillis / 1000).toFixed(1),
      suffix: '초',
    },
    { title: '입력 토큰', value: numberOrDash(result.totalInputTokens) },
    { title: '출력 토큰', value: numberOrDash(result.totalOutputTokens) },
    { title: '총 비용', value: usd(result.totalCostUsd) },
  ];

  return (
    <section className={styles.container}>
      <header className={styles.header}>
        <Flex wrap justify="space-between" align="center" gap="middle">
          <Typography.Title level={2} className={styles.title}>
            후기 요약
          </Typography.Title>
          <Popconfirm
            title="후기 요약을 지금 실행할까요?"
            description="레시피마다 AI를 호출해 비용이 나가요."
            okText="실행"
            cancelText="취소"
            onConfirm={() => mutate()}
          >
            <Button type="primary" icon={<ThunderboltOutlined />} loading={isPending}>
              지금 실행
            </Button>
          </Popconfirm>
        </Flex>
        <Typography.Text type="secondary" className={styles.description}>
          레시피 후기를 AI로 요약해 레시피 상세에 보여줘요
        </Typography.Text>
      </header>

      <Card className={styles.guide}>
        <Row gutter={[token.marginLG, token.margin]} align="stretch">
          {GUIDES.map((guide) => (
            <Col key={guide.title} xs={24} md={8}>
              <Flex gap="small" align="flex-start" className={styles.guideItem}>
                <span className={styles.guideIcon} aria-hidden>
                  {guide.icon}
                </span>
                <div>
                  <div className={styles.guideTitle}>{guide.title}</div>
                  <p className={styles.guideText}>{guide.text}</p>
                </div>
              </Flex>
            </Col>
          ))}
        </Row>
      </Card>

      {isPending ? (
        <Card>
          <Flex vertical align="center" gap="small" className={styles.loading}>
            <Spin />
            <Typography.Text type="secondary">
              요약하는 중이에요. 대상이 많으면 시간이 걸릴 수 있어요.
            </Typography.Text>
          </Flex>
        </Card>
      ) : !result ? (
        <Card>
          <Typography.Text type="secondary" className={styles.state}>
            [지금 실행]을 누르면 결과가 여기에 보여요
          </Typography.Text>
        </Card>
      ) : result.targetCount === 0 ? (
        <Card>
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={
              <Flex vertical gap="small">
                <Typography.Text>요약할 레시피가 없어요</Typography.Text>
                <Typography.Text type="secondary" className={styles.emptyHint}>
                  새 후기가 생기면 다시 실행해 보세요
                </Typography.Text>
              </Flex>
            }
          />
        </Card>
      ) : (
        <>
          <Row gutter={[token.marginSM, token.marginSM]} className={styles.summary}>
            {stats?.map((stat) => (
              <Col key={stat.title} xs={12} md={6}>
                <Card size="small">
                  <Statistic title={stat.title} value={stat.value} suffix={stat.suffix} />
                </Card>
              </Col>
            ))}
          </Row>
          {hasTimeBudgetSkip && (
            <Alert
              type="info"
              showIcon
              className={styles.notice}
              title="남은 레시피는 [지금 실행]을 한 번 더 누르면 이어서 요약해요."
            />
          )}
          <Table<ReviewSummaryItem>
            rowKey="recipeId"
            columns={columns}
            dataSource={items}
            scroll={{ x: 'max-content' }}
            pagination={{ pageSize: PAGE_SIZE, hideOnSinglePage: true }}
            expandable={{
              rowExpandable: (item) => item.status === 'SUCCESS',
              expandedRowRender: (item) => <SummaryCompare item={item} />,
            }}
          />
        </>
      )}
    </section>
  );
}
