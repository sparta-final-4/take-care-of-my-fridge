import { Tag, Tooltip } from 'antd';
import dayjs from 'dayjs';

import { ddayBadgeColor, formatDDayLabel } from '../format';
import styles from './DdayTag.module.scss';

interface DdayTagProps {
  dDay: number;
  // 있으면 마우스를 올렸을 때 소비기한 날짜를 보여 준다.
  expirationDate?: string;
}

// 남은 날 뱃지: 지남 빨강 "N일 지남", 오늘~모레 주황 "오늘까지", D-3 노랑, 그 밖은 회색 "D-14"
export function DdayTag({ dDay, expirationDate }: DdayTagProps) {
  const tag = (
    <Tag variant="filled" color={ddayBadgeColor(dDay)} className={styles.tag}>
      {formatDDayLabel(dDay)}
    </Tag>
  );

  if (!expirationDate) return tag;
  return <Tooltip title={`${dayjs(expirationDate).format('YYYY-MM-DD')}까지`}>{tag}</Tooltip>;
}
