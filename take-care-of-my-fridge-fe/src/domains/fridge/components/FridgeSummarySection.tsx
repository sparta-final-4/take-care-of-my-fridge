import { Typography } from 'antd';
import dayjs from 'dayjs';

import { SectionCard } from '@/common/components/SectionCard';

import { withoutBasicSeasoning } from '../filter';
import { useFridgeSort } from '../hooks/useFridgeSort';
import { useFridgeItems, useFridgeLogs } from '../queries';
import styles from './FridgeSummarySection.module.scss';

export function FridgeSummarySection() {
  const [sort] = useFridgeSort();
  const { data } = useFridgeItems(sort, true);
  // 기본 양념은 개수에서 빼고 밀키트는 포함한다.
  const items = data && withoutBasicSeasoning(data);
  // 개수만 필요하므로 1건만 받아 totalElements를 쓴다.
  const { data: discardedLogs } = useFridgeLogs({
    action: 'DISCARDED',
    from: dayjs().startOf('month').format('YYYY-MM-DD'),
    size: 1,
  });

  const stats = [
    { label: '전체 재료', value: items?.length, note: '기본 양념 제외' },
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
            {stat.note && (
              <Typography.Text type="secondary" className={styles.label}>
                {stat.note}
              </Typography.Text>
            )}
          </div>
        ))}
      </div>
    </SectionCard>
  );
}
