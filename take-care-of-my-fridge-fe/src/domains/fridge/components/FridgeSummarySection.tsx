import { Typography } from 'antd';
import dayjs from 'dayjs';

import { SectionCard } from '@/common/components/SectionCard';

import { useFridgeSort } from '../hooks/useFridgeSort';
import { useFridgeItems, useFridgeLogs } from '../queries';
import styles from './FridgeSummarySection.module.scss';

export function FridgeSummarySection() {
  const [sort] = useFridgeSort();
  const { data: items } = useFridgeItems(sort, true);
  // 개수만 필요하므로 1건만 받아 totalElements를 쓴다.
  const { data: discardedLogs } = useFridgeLogs({
    action: 'DISCARDED',
    from: dayjs().startOf('month').format('YYYY-MM-DD'),
    size: 1,
  });

  const stats = [
    { label: '전체 재료', value: items?.length },
    { label: '임박 재료', value: items?.filter((item) => item.status === 'IMMINENT').length },
    { label: '이번 달 버린 재료', value: discardedLogs?.totalElements },
  ];

  return (
    <SectionCard title="냉장고 요약">
      <div className={styles.stats}>
        {stats.map((stat) => (
          <div key={stat.label} className={styles.stat}>
            <Typography.Text strong className={styles.value}>
              {stat.value ?? '-'}
            </Typography.Text>
            <Typography.Text type="secondary" className={styles.label}>
              {stat.label}
            </Typography.Text>
          </div>
        ))}
      </div>
    </SectionCard>
  );
}
