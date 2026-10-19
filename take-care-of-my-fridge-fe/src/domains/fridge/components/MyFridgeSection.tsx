import { RightOutlined, WarningFilled } from '@ant-design/icons';
import { Button, Empty, Flex, Result, Skeleton, Tag, Typography } from 'antd';
import dayjs from 'dayjs';

import { useAuthStore } from '@/common/auth/authStore';
import { SectionCard } from '@/common/components/SectionCard';

import type { FridgeItemResponse } from '../api';
import { useFridgeItems } from '../queries';
import styles from './MyFridgeSection.module.scss';

const SUMMARY_SIZE = 5;

type FridgeItemStatus = 'EXPIRED' | 'IMMINENT' | 'NORMAL';

// 임박·만료 판단은 서버 응답(expired, imminent)을 그대로 쓴다.
function getStatus(item: FridgeItemResponse): FridgeItemStatus {
  if (item.expired) return 'EXPIRED';
  if (item.imminent) return 'IMMINENT';
  return 'NORMAL';
}

// 응답에 D-day 값이 없어 소비기한으로 표시 문구만 만든다.
function getDDayLabel(expirationDate: string) {
  const days = dayjs(expirationDate).diff(dayjs().startOf('day'), 'day');
  if (days === 0) return 'D-Day';
  return days > 0 ? `D-${days}` : `D+${-days}`;
}

export function MyFridgeSection() {
  const authStatus = useAuthStore((state) => state.status);
  const isAuthenticated = authStatus === 'authenticated';
  const { data, isPending, isError, refetch } = useFridgeItems('EXPIRATION', isAuthenticated);

  const imminentCount = data?.filter((item) => getStatus(item) === 'IMMINENT').length ?? 0;

  const renderBody = () => {
    if (authStatus === 'initializing') return <Skeleton active paragraph={{ rows: 4 }} />;
    if (!isAuthenticated) {
      return (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="로그인하면 내 냉장고를 볼 수 있어요"
        />
      );
    }
    if (isPending) return <Skeleton active paragraph={{ rows: 4 }} />;
    if (isError) {
      return (
        <Result
          status="error"
          subTitle="냉장고 재료를 불러오지 못했어요."
          extra={<Button onClick={() => void refetch()}>다시 시도</Button>}
        />
      );
    }
    if (data.length === 0) {
      return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="냉장고가 비어 있어요" />;
    }

    return (
      <Flex vertical gap="xs" className={styles.itemList}>
        {data.slice(0, SUMMARY_SIZE).map((item) => {
          const isImminent = getStatus(item) !== 'NORMAL';

          return (
            <div key={item.id} className={styles.itemRow}>
              <Typography.Text strong={isImminent} className={styles.itemName}>
                {item.rawName}
              </Typography.Text>
              <Flex align="center" gap="xs">
                {isImminent && (
                  <WarningFilled className={styles.warningIcon} aria-label="소비기한 임박 경고" />
                )}
                <Tag className={isImminent ? styles.imminentBadge : styles.normalBadge}>
                  {getDDayLabel(item.expirationDate)}
                </Tag>
              </Flex>
            </div>
          );
        })}
      </Flex>
    );
  };

  return (
    <SectionCard
      title="내 냉장고"
      extra={
        data && (
          <Typography.Text type="secondary" className={styles.summaryText}>
            {data.length}개 · 임박 {imminentCount}
          </Typography.Text>
        )
      }
    >
      <div className={styles.container}>
        {renderBody()}

        <Flex justify="flex-end" className={styles.footer}>
          <Button shape="round" size="small" className={styles.shortcutButton}>
            내 냉장고 바로가기 <RightOutlined />
          </Button>
        </Flex>
      </div>
    </SectionCard>
  );
}
