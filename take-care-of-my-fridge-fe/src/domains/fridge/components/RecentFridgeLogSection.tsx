import { Button, Empty, Flex, Result, Skeleton, Tag, Typography } from 'antd';
import dayjs from 'dayjs';

import { SectionCard } from '@/common/components/SectionCard';

import { formatQuantity } from '../format';
import { useFridgeLogs } from '../queries';
import styles from './RecentFridgeLogSection.module.scss';

const RECENT_SIZE = 4;

export function RecentFridgeLogSection() {
  const { data, isPending, isError, refetch } = useFridgeLogs({ size: RECENT_SIZE });

  const renderBody = () => {
    if (isPending) return <Skeleton active paragraph={{ rows: 3 }} />;
    if (isError) {
      return (
        <Result
          status="error"
          subTitle="최근 기록을 불러오지 못했어요."
          extra={<Button onClick={() => void refetch()}>다시 시도</Button>}
        />
      );
    }
    if (data.content.length === 0) {
      return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="최근 기록이 없어요" />;
    }
    return (
      <Flex vertical gap="xs">
        {data.content.map((log) => (
          <div key={log.id} className={styles.row}>
            <Typography.Text strong title={log.rawName} className={styles.name}>
              {log.rawName}
            </Typography.Text>
            <Typography.Text className={styles.quantity}>{formatQuantity(log)}</Typography.Text>
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
