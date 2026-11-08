import { useQuery } from '@tanstack/react-query';
import { Typography } from 'antd';
import { Link } from 'react-router';

import { ROUTES } from '@/app/paths';

import { searchQueries } from '../queries';

import styles from './PopularKeywordBar.module.scss';

interface PopularKeywordBarProps {
  // 놓이는 자리의 좌우 여백 등
  className?: string;
  // 검색어를 눌러 이동할 때(왼쪽 메뉴 Drawer를 닫는 데 쓴다)
  onNavigate?: () => void;
}

// 오늘 많이 검색된 검색어를 한 줄로 보여 준다. 누르면 그 검색어로 통합 검색한다.
// 헤더 검색 줄(입력칸 아래)과 좁은 화면의 왼쪽 메뉴 검색칸 아래에 놓인다.
// 집계된 검색어가 없거나 불러오지 못하면 자리를 차지하지 않도록 그리지 않는다.
export function PopularKeywordBar({ className, onNavigate }: PopularKeywordBarProps) {
  const { data: keywords } = useQuery(searchQueries.popular());

  if (!keywords || keywords.length === 0) return null;

  return (
    <nav className={className ? `${styles.bar} ${className}` : styles.bar} aria-label="인기 검색어">
      <Typography.Text strong className={styles.label}>
        인기 검색어
      </Typography.Text>
      <ol className={styles.list}>
        {keywords.map((keyword, idx) => (
          <li key={keyword}>
            <Link
              to={`${ROUTES.search}?${new URLSearchParams({ keyword })}`}
              onClick={onNavigate}
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
