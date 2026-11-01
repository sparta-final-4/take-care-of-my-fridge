import { Button, Empty, Flex, Result, Skeleton, Typography } from 'antd';

import { SectionCard } from '@/common/components/SectionCard';

import { formatLogDate, formatQuantity } from '../format';
import { useFridgeLogs } from '../queries';
import { FridgeLogActionTag } from './FridgeLogActionTag';
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
    const logs = data.content ?? [];
    if (logs.length === 0) {
      return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="최근 기록이 없어요" />;
    }
    return (
      <Flex vertical gap="xs">
        {logs.map((log) => (
          <div key={log.id} className={styles.row}>
            <Typography.Text strong title={log.rawName} className={styles.name}>
              {log.rawName}
            </Typography.Text>
            <Typography.Text className={styles.quantity}>{formatQuantity(log)}</Typography.Text>
            <div className={styles.tag}>
              {log.action && (
                <FridgeLogActionTag
                  action={log.action}
                  cookedByRecipe={log.cookedByRecipe ?? false}
                />
              )}
            </div>
            <Typography.Text type="secondary" className={styles.date}>
              {log.createdAt && formatLogDate(log.createdAt)}
            </Typography.Text>
          </div>
        ))}
      </Flex>
    );
  };

  return <SectionCard title="최근 기록">{renderBody()}</SectionCard>;
}
