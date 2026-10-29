import { Tag } from 'antd';

import { ddayTagColor, formatDDayLabel } from '../format';
import styles from './DdayTag.module.scss';

interface DdayTagProps {
  dDay: number;
}

export function DdayTag({ dDay }: DdayTagProps) {
  const color = ddayTagColor(dDay);

  return (
    <Tag
      bordered={false}
      color={color}
      className={color ? styles.tag : `${styles.tag} ${styles.plain}`}
    >
      {formatDDayLabel(dDay)}
    </Tag>
  );
}
