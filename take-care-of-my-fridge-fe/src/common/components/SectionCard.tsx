import type { ReactNode } from 'react';
import { Typography } from 'antd';

import styles from './SectionCard.module.scss';

export interface SectionCardProps {
  title: string;
  // 같은 화면의 다른 곳에서 이 섹션으로 스크롤할 때 쓴다.
  id?: string;
  extra?: ReactNode;
  // false면 제목을 대괄호 없이 보여 준다.
  bracket?: boolean;
  children: ReactNode;
}

export function SectionCard({ title, id, extra, bracket = true, children }: SectionCardProps) {
  return (
    <section id={id} className={styles.card}>
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
