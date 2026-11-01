import { Button, Empty, Flex, Result, Skeleton, Typography } from 'antd';
import { Link } from 'react-router';

import { ROUTES } from '@/app/paths';
import { useAuthStore } from '@/common/auth/authStore';
import { SectionCard } from '@/common/components/SectionCard';

import { withoutBasicSeasoning } from '../filter';
import { formatQuantity } from '../format';
import { useFridgeItems } from '../queries';
import { DdayTag } from './DdayTag';
import { IngredientTypeTag } from './IngredientTypeTag';
import styles from './MyFridgeSection.module.scss';

const SUMMARY_SIZE = 5;

export function MyFridgeSection() {
  const authStatus = useAuthStore((state) => state.status);
  const isAuthenticated = authStatus === 'authenticated';
  const {
    data: allItems,
    isPending,
    isError,
    refetch,
  } = useFridgeItems('EXPIRATION', isAuthenticated);
  // 임박 재료 위주로 보여 주는 곳이라 기본 양념은 뺀다.
  const data = withoutBasicSeasoning(allItems ?? []);

  const imminentCount = data.filter((item) => item.status === 'IMMINENT').length;

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
      <ul className={styles.itemList}>
        {data.slice(0, SUMMARY_SIZE).map((item) => (
          <li key={item.id} className={styles.itemRow}>
            <Flex align="center" gap="xs" className={styles.itemNameWrapper}>
              <Typography.Text className={styles.itemName}>{item.rawName}</Typography.Text>
              <Typography.Text type="secondary" className={styles.itemQuantity}>
                {formatQuantity(item)}
              </Typography.Text>
              <IngredientTypeTag type={item.ingredientType} />
            </Flex>
            {item.dDay !== undefined && <DdayTag dDay={item.dDay} />}
          </li>
        ))}
      </ul>
    );
  };

  return (
    <SectionCard
      title="내 냉장고"
      extra={
        <Flex align="center" gap="small">
          <Typography.Text type="secondary" className={styles.summaryText}>
            {imminentCount > 0 && (
              <>
                임박 <span className={styles.imminentCount}>{imminentCount}</span> ·{' '}
              </>
            )}
            전체 {data.length}
          </Typography.Text>
          <Link to={ROUTES.fridge} className={styles.moreLink}>
            전체 보기 &gt;
          </Link>
        </Flex>
      }
    >
      {renderBody()}
    </SectionCard>
  );
}
