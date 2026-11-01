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
  // 제목 모양. card: 카드 제목(16px / 600), page: 화면 제목(Title level 3, 24px / 600)
  titleVariant?: 'card' | 'page';
  children: ReactNode;
}

export function SectionCard({
  title,
  id,
  extra,
  bracket = true,
  titleVariant = 'card',
  children,
}: SectionCardProps) {
  const text = bracket ? `[${title}]` : title;

  return (
    <section id={id} className={styles.card}>
      <div className={styles.header}>
        {titleVariant === 'page' ? (
          <Typography.Title level={3} className={styles.pageTitle}>
            {text}
          </Typography.Title>
        ) : (
          <Typography.Text strong className={styles.title}>
            {text}
          </Typography.Text>
        )}
        {extra && <div className={styles.extra}>{extra}</div>}
      </div>
      <div className={styles.body}>{children}</div>
    </section>
  );
}
