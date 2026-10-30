import type { ReactNode } from 'react';
import { Typography } from 'antd';

import styles from './SectionCard.module.scss';

export interface SectionCardProps {
  title: string;
  extra?: ReactNode;
  // false면 제목을 대괄호 없이 보여 준다.
  bracket?: boolean;
  children: ReactNode;
}

export function SectionCard({ title, extra, bracket = true, children }: SectionCardProps) {
  return (
    <section className={styles.card}>
      <div className={styles.header}>
        <Typography.Text strong className={styles.title}>
          {bracket ? `[${title}]` : title}
        </Typography.Text>
        {extra && <div className={styles.extra}>{extra}</div>}
      </div>
      <div className={styles.body}>{children}</div>
    </section>
  );
}
