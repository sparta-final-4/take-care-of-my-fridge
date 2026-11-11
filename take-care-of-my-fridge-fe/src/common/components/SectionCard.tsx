import type { ReactNode } from 'react';
import { Typography } from 'antd';

import styles from './SectionCard.module.scss';

export interface SectionCardProps {
  title: string;
  // 같은 화면의 다른 곳에서 이 섹션으로 스크롤할 때 쓴다.
  id?: string;
  extra?: ReactNode;
  // 제목 모양. card: 섹션 제목(20px / 600), page: 화면 제목(Title level 3, 24px / 600)
  titleVariant?: 'card' | 'page';
  children: ReactNode;
}

export function SectionCard({
  title,
  id,
  extra,
  titleVariant = 'card',
  children,
}: SectionCardProps) {
  return (
    <section id={id} className={styles.card}>
      <div className={styles.header}>
        {titleVariant === 'page' && (
          <Typography.Title level={3} className={styles.pageTitle}>
            {title}
          </Typography.Title>
        )}
        {titleVariant === 'card' && (
          <Typography.Text strong className={styles.title}>
            {title}
          </Typography.Text>
        )}
        {extra && <div className={styles.extra}>{extra}</div>}
      </div>
      <div className={styles.body}>{children}</div>
    </section>
  );
}
