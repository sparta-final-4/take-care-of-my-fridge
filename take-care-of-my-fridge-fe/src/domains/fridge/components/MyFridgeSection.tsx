import { RightOutlined } from '@ant-design/icons';
import { Button, Empty, Flex, Result, Skeleton, Typography } from 'antd';
import { useNavigate } from 'react-router';

import { ROUTES } from '@/app/paths';
import { useAuthStore } from '@/common/auth/authStore';
import { SectionCard } from '@/common/components/SectionCard';

import { useFridgeItems } from '../queries';
import { DdayTag } from './DdayTag';
import { IngredientTypeTag } from './IngredientTypeTag';
import styles from './MyFridgeSection.module.scss';

const SUMMARY_SIZE = 5;

export function MyFridgeSection() {
  const navigate = useNavigate();
  const authStatus = useAuthStore((state) => state.status);
  const isAuthenticated = authStatus === 'authenticated';
  const { data, isPending, isError, refetch } = useFridgeItems('EXPIRATION', isAuthenticated);

  const imminentCount = data?.filter((item) => item.status === 'IMMINENT').length ?? 0;
  const expiredCount = data?.filter((item) => item.status === 'EXPIRED').length ?? 0;

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
          const isImminent = item.status !== 'NORMAL';

          return (
            <div key={item.id} className={styles.itemRow}>
              <Flex align="center" gap="xs" className={styles.itemNameWrapper}>
                <Typography.Text strong={isImminent} className={styles.itemName}>
                  {item.rawName}
                </Typography.Text>
                <IngredientTypeTag type={item.ingredientType} />
              </Flex>
              {item.dDay !== undefined && <DdayTag dDay={item.dDay} />}
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
            {expiredCount > 0 && ` · 기한 지남 ${expiredCount}`}
          </Typography.Text>
        )
      }
    >
      <div className={styles.container}>
        {renderBody()}

        <Flex justify="flex-end" className={styles.footer}>
          <Button
            shape="round"
            size="small"
            onClick={() => navigate(ROUTES.fridge)}
            className={styles.shortcutButton}
          >
            내 냉장고 바로가기 <RightOutlined />
          </Button>
        </Flex>
      </div>
    </SectionCard>
  );
}
