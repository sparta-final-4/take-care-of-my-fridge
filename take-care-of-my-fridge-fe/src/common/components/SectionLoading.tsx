import type { ReactNode } from 'react';

import styles from './SectionLoading.module.scss';

interface SectionLoadingProps {
  loading: boolean;
  children: ReactNode;
}

// 페이지·필터를 바꿔 목록만 다시 받는 동안, 이전 목록을 흐리게 두고 그 위에 냉장고 로고 로딩을 보여 준다.
export function SectionLoading({ loading, children }: SectionLoadingProps) {
  return (
    <div className={styles.section} aria-busy={loading}>
      <div className={loading ? styles.dimmed : undefined}>{children}</div>
      {loading && (
        <div className={styles.overlay}>
          <img className={styles.logo} src="/favicon.svg" alt="" />
        </div>
      )}
    </div>
  );
}
