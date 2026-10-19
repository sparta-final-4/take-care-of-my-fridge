import { Empty, Flex, Skeleton, Tag, Typography } from 'antd';
import dayjs from 'dayjs';

import { SectionCard } from '@/common/components/SectionCard';

import { useFridgeLogs } from '../queries';
import styles from './RecentFridgeLogSection.module.scss';

const RECENT_SIZE = 4;

export function RecentFridgeLogSection() {
  // TODO: 지금은 가짜 데이터. GET /api/fridge/logs가 생기면 queries.ts만 바꾸면 된다.
  const { data, isPending } = useFridgeLogs();

  const renderBody = () => {
    if (isPending) return <Skeleton active paragraph={{ rows: 3 }} />;
    if (!data || data.recentLogs.length === 0) {
      return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="최근 기록이 없어요" />;
    }
    return (
      <Flex vertical gap="xs">
        {data.recentLogs.slice(0, RECENT_SIZE).map((log) => (
          <div key={log.id} className={styles.row}>
            <Typography.Text strong className={styles.name}>
              {log.rawName}
            </Typography.Text>
            <Tag
              className={log.action === 'DISCARDED' ? styles.discardedBadge : styles.consumedBadge}
            >
              {log.action === 'DISCARDED' ? '버렸어요' : '먹었어요'}
            </Tag>
            <Typography.Text type="secondary" className={styles.date}>
              {dayjs(log.createdAt).format('YYYY-MM-DD')}
            </Typography.Text>
          </div>
        ))}
      </Flex>
    );
  };

  return <SectionCard title="최근 기록">{renderBody()}</SectionCard>;
}
