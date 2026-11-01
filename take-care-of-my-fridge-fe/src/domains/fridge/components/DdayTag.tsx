import { Tag, Tooltip } from 'antd';
import dayjs from 'dayjs';

import { ddayTagColor, formatDDayLabel } from '../format';
import styles from './DdayTag.module.scss';

interface DdayTagProps {
  dDay: number;
  // 있으면 마우스를 올렸을 때 소비기한 날짜를 보여 준다.
  expirationDate?: string;
}

export function DdayTag({ dDay, expirationDate }: DdayTagProps) {
  const color = ddayTagColor(dDay);
  const tag = (
    <Tag
      variant="filled"
      color={color}
      className={color ? styles.tag : `${styles.tag} ${styles.plain}`}
    >
      {formatDDayLabel(dDay)}
    </Tag>
  );

  if (!expirationDate) return tag;
  return <Tooltip title={`${dayjs(expirationDate).format('YYYY-MM-DD')}까지`}>{tag}</Tooltip>;
}
