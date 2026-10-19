import { Typography } from 'antd';

import { SectionCard } from '@/common/components/SectionCard';

import { useFridgeSort } from '../hooks/useFridgeSort';
import { useFridgeItems, useFridgeLogs } from '../queries';
import styles from './FridgeSummarySection.module.scss';

export function FridgeSummarySection() {
  const [sort] = useFridgeSort();
  const { data: items } = useFridgeItems(sort, true);
  const { data: logs } = useFridgeLogs();

  const stats = [
    { label: '전체 재료', value: items?.length },
    { label: '임박 재료', value: items?.filter((item) => item.status === 'IMMINENT').length },
    // TODO: 기록 API가 생기면 실제 이번 달 폐기 수로 바뀐다(지금은 가짜 데이터).
    { label: '이번 달 버린 재료', value: logs?.monthlyDiscardedCount },
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
