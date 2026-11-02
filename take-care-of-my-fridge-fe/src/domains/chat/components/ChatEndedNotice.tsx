import { Typography } from 'antd';
import { Link } from 'react-router';

import { ROUTES } from '@/app/paths';

import styles from './ChatEndedNotice.module.scss';

interface ChatEndedNoticeProps {
  // 상담 내역으로 이동할 때 채팅창을 닫는다.
  onNavigate?: () => void;
}

// 끝난 상담을 채팅창에서 본 채로 둘 때, 다음에 열면 새 상담이 열리므로 지난 대화를 볼 곳을 알려 준다.
export function ChatEndedNotice({ onNavigate }: ChatEndedNoticeProps) {
  return (
    <div className={styles.notice}>
      <Link to={ROUTES.mypageChats} onClick={onNavigate} className={styles.link}>
        <Typography.Text type="secondary" className={styles.text}>
          지난 상담은 마이페이지 &gt; AI 상담 내역에서 볼 수 있어요
        </Typography.Text>
      </Link>
    </div>
  );
}
