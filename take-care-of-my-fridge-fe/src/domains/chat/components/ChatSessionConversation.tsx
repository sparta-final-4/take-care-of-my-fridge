import { useQuery } from '@tanstack/react-query';
import { Button, Empty, Flex, Result, Spin, Typography } from 'antd';
import { Fragment, useEffect, useRef } from 'react';

import type { ChatSessionSummaryResponse } from '@/common/api/clients';

import { formatChatDateTime } from '../format';
import { chatQueries } from '../queries';
import { toChatRecommendedRecipes } from '../recommendations';
import { ChatMessageBubble } from './ChatMessageBubble';
import { ChatRecommendationCards } from './ChatRecommendationCards';
import { ChatSessionStatusTag } from './ChatSessionStatusTag';
import styles from './ChatSessionConversation.module.scss';

interface ChatSessionConversationProps {
  session: ChatSessionSummaryResponse;
}

// 끝난 상담(종료·만료)의 대화를 읽기 전용으로 보여 준다. 진행 중 상담은 ChatSessionLiveConversation이 맡는다.
export function ChatSessionConversation({ session }: ChatSessionConversationProps) {
  const sessionId = session.sessionId ?? 0;
  const { data, isPending, isError, refetch } = useQuery(chatQueries.messagesQuery(sessionId));
  const bodyRef = useRef<HTMLDivElement>(null);

  // 상담을 새로 고르거나 메시지가 바뀌면 최신 메시지(맨 아래)가 보이게 한다.
  useEffect(() => {
    const body = bodyRef.current;
    if (body) body.scrollTop = body.scrollHeight;
  }, [sessionId, data]);

  return (
    <section className={styles.panel} aria-label="상담 대화">
      <Flex align="center" gap="small" wrap className={styles.header}>
        <Typography.Text strong className={styles.date}>
          {formatChatDateTime(session.startedAt)}
        </Typography.Text>
        {session.status && <ChatSessionStatusTag status={session.status} />}
      </Flex>

      <div ref={bodyRef} className={styles.body}>
        {isPending ? (
          <Flex justify="center" className={styles.state}>
            <Spin description="대화 기록을 불러오는 중..." />
          </Flex>
        ) : isError ? (
          <Result
            status="error"
            title="대화 기록을 불러오지 못했습니다."
            extra={<Button onClick={() => refetch()}>다시 시도</Button>}
          />
        ) : data.length === 0 ? (
          <Empty description="대화 기록이 없습니다." />
        ) : (
          data.map((message) => {
            const cards = toChatRecommendedRecipes(message.recommendations);
            return (
              <Fragment key={message.id ?? message.sequence}>
                <ChatMessageBubble
                  senderRole={message.role === 'USER' ? 'USER' : 'ASSISTANT'}
                  content={message.content ?? ''}
                  createdAt={message.createdAt}
                />
                {cards.length > 0 && <ChatRecommendationCards recipes={cards} />}
              </Fragment>
            );
          })
        )}
      </div>

      <div className={styles.footer}>
        <Typography.Text type="secondary" className={styles.notice}>
          지난 상담은 읽기만 할 수 있어요
        </Typography.Text>
      </div>
    </section>
  );
}
