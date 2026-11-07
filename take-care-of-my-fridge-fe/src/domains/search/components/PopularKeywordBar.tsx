import { useQuery } from '@tanstack/react-query';
import { Typography } from 'antd';
import { Link } from 'react-router';

import { ROUTES } from '@/app/paths';

import { searchQueries } from '../queries';

import styles from './PopularKeywordBar.module.scss';

// 최근 7일 동안 많이 검색된 검색어를 한 줄로 보여 준다. 누르면 그 검색어로 통합 검색한다.
// 집계된 검색어가 없거나 불러오지 못하면 자리를 차지하지 않도록 그리지 않는다.
export function PopularKeywordBar() {
  const { data: keywords } = useQuery(searchQueries.popular());

  if (!keywords || keywords.length === 0) return null;

  return (
    <nav className={styles.bar} aria-label="인기 검색어">
      <Typography.Text strong className={styles.label}>
        인기 검색어
      </Typography.Text>
      <ol className={styles.list}>
        {keywords.map((keyword, idx) => (
          <li key={keyword}>
            <Link
              to={`${ROUTES.search}?${new URLSearchParams({ keyword })}`}
              className={styles.item}
            >
              <span className={`${styles.rank} ${idx < 3 ? styles.topRank : ''}`}>{idx + 1}</span>
              {keyword}
            </Link>
          </li>
        ))}
      </ol>
    </nav>
  );
}
