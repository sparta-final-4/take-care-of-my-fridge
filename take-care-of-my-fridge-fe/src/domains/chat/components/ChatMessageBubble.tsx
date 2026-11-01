import { RobotOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';

import type { ChatMessageResponse } from '@/common/api/clients';

import styles from './ChatMessageBubble.module.scss';

export interface ChatMessageBubbleProps {
  message?: Partial<ChatMessageResponse>;
  senderRole: 'USER' | 'ASSISTANT';
  content: string;
  createdAt?: string;
  isStreaming?: boolean;
}

export function ChatMessageBubble({
  senderRole,
  content,
  createdAt,
  isStreaming = false,
}: ChatMessageBubbleProps) {
  const isUser = senderRole === 'USER';
  const timeText = createdAt ? dayjs(createdAt).format('HH:mm') : dayjs().format('HH:mm');

  if (isUser) {
    return (
      <div className={`${styles.messageWrapper} ${styles.user}`}>
        <div className={styles.userContainer}>
          <div className={`${styles.bubble} ${styles.userBubble}`}>{content}</div>
          <span className={styles.time}>{timeText}</span>
        </div>
      </div>
    );
  }

  return (
    <div className={`${styles.messageWrapper} ${styles.assistant}`}>
      <div className={styles.assistantContainer}>
        <div className={styles.avatar}>
          <RobotOutlined />
        </div>
        <div className={styles.assistantContent}>
          <div className={`${styles.bubble} ${styles.assistantBubble}`}>
            {content}
            {isStreaming && <span className={styles.cursor} />}
          </div>
          <span className={styles.time}>{timeText}</span>
        </div>
      </div>
    </div>
  );
}
