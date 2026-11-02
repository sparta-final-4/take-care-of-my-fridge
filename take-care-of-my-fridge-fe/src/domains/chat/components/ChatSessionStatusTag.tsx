import { Tag } from 'antd';

import type { ChatSessionSummaryResponseStatusEnum } from '@/common/api/clients';

import { CHAT_SESSION_STATUS_LABELS } from '../format';
import styles from './ChatSessionStatusTag.module.scss';

interface ChatSessionStatusTagProps {
  status: ChatSessionSummaryResponseStatusEnum;
}

// 상담 상태 태그. 상담 중은 초록 점 + 초록 바탕으로 가장 눈에 띄게, 종료·만료는 테두리만 있는 흐린 태그.
export function ChatSessionStatusTag({ status }: ChatSessionStatusTagProps) {
  if (status === 'ACTIVE') {
    return (
      <Tag color="success" variant="filled" className={styles.tag}>
        <span className={styles.dot} aria-hidden="true" />
        {CHAT_SESSION_STATUS_LABELS[status]}
      </Tag>
    );
  }
  return (
    <Tag variant="outlined" className={`${styles.tag} ${styles.ended}`}>
      {CHAT_SESSION_STATUS_LABELS[status]}
    </Tag>
  );
}
