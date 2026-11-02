import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Flex, Typography } from 'antd';
import { useEffect } from 'react';

import type { ChatSessionSummaryResponse } from '@/common/api/clients';

import { formatChatDateTime } from '../format';
import { chatQueries } from '../queries';
import { ChatRoomSection } from './ChatRoomSection';
import { ChatSessionConversation } from './ChatSessionConversation';
import { ChatSessionStatusTag } from './ChatSessionStatusTag';
import styles from './ChatSessionLiveConversation.module.scss';

interface ChatSessionLiveConversationProps {
  // 목록에서 고른 진행 중 상담. 아직 목록에 없는 새 상담(첫 메시지 전)이면 없다.
  session?: ChatSessionSummaryResponse;
  // 상담 끝내기가 성공했을 때
  onSessionCompleted?: () => void;
}

// 진행 중 상담을 상담 내역 오른쪽 칸에서 바로 이어 간다. 대화 기능은 채팅창과 같은 ChatRoomSection을 쓴다.
// ChatRoomSection은 회원의 진행 중 상담(activeSession)을 다루므로, 목록의 상담과 같을 때만 입력창을 보여 준다.
// 상담을 끝내면 activeSession이 먼저 바뀌어 목록이 다시 오기 전에도 그 자리에서 읽기 전용으로 바뀐다.
export function ChatSessionLiveConversation({
  session,
  onSessionCompleted,
}: ChatSessionLiveConversationProps) {
  const queryClient = useQueryClient();
  const { data: active, refetch } = useQuery(chatQueries.activeSessionQuery());
  const listedId = session?.sessionId;
  const isOtherSession = active !== undefined && listedId !== undefined && active.id !== listedId;

  // 목록과 진행 중 상담이 다르면(다른 탭에서 시작·종료 등) 둘 다 서버 값으로 다시 맞춘다.
  useEffect(() => {
    if (!isOtherSession) return;
    void refetch();
    void queryClient.invalidateQueries({ queryKey: chatQueries.sessions() });
  }, [isOtherSession, refetch, queryClient]);

  if (session && (isOtherSession || (active && active.status !== 'ACTIVE'))) {
    const status = isOtherSession ? session.status : active?.status;
    return <ChatSessionConversation session={{ ...session, status }} />;
  }

  const startedAt = session?.startedAt ?? active?.createdAt;
  return (
    <div className={styles.panel}>
      <ChatRoomSection
        hideStatusBanner
        onSessionCompleted={onSessionCompleted}
        title={
          <Flex align="center" gap="small" wrap>
            <Typography.Text strong className={styles.date}>
              {formatChatDateTime(startedAt)}
            </Typography.Text>
            <ChatSessionStatusTag status="ACTIVE" />
          </Flex>
        }
      />
    </div>
  );
}
