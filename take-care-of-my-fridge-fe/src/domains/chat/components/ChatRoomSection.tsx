import { SendOutlined, RobotOutlined, WarningOutlined } from '@ant-design/icons';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, App, Button, Flex, Input, type InputRef, Result, Spin, Typography } from 'antd';
import dayjs from 'dayjs';
import { type KeyboardEvent, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router';

import { ROUTES } from '@/app/paths';
import { useAuthStore } from '@/common/auth/authStore';
import { streamMessage } from '../api';
import { getChatErrorInfo, type ChatErrorInfo } from '../errors';
import { chatQueries, useCompleteChatSession, useStartChatSession } from '../queries';
import styles from './ChatRoomSection.module.scss';

export function ChatRoomSection() {
  const navigate = useNavigate();
  const { message: toast, modal } = App.useApp();
  const queryClient = useQueryClient();

  const authStatus = useAuthStore((state) => state.status);
  const isAuthenticated = authStatus === 'authenticated';

  const [inputText, setInputText] = useState('');
  const [streamingUserMessage, setStreamingUserMessage] = useState<string | null>(null);
  const [streamingAssistantMessage, setStreamingAssistantMessage] = useState<string | null>(null);
  const [sendErrorInfo, setSendErrorInfo] = useState<ChatErrorInfo | null>(null);
  const [sendLimitExceeded, setSendLimitExceeded] = useState(false);

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const abortControllerRef = useRef<AbortController | null>(null);

  // 1. 활성 상담 세션 조회
  const {
    data: activeSession,
    isLoading: isSessionLoading,
    error: sessionQueryError,
  } = useQuery({
    ...chatQueries.activeSessionQuery(),
    enabled: isAuthenticated,
  });

  const sessionId = activeSession?.id ?? 0;
  const isSessionEnded =
    activeSession?.status === 'COMPLETED' || activeSession?.status === 'EXPIRED';

  // 2. 세션의 메시지 기록 조회
  const { data: messagesData, isLoading: isMessagesLoading } = useQuery({
    ...chatQueries.messagesQuery(sessionId),
    enabled: isAuthenticated && sessionId > 0,
  });

  const startSessionMutation = useStartChatSession();
  const completeSessionMutation = useCompleteChatSession();

  const isStreaming = streamingAssistantMessage !== null;

  // 세션 시작 쿼리 에러로부터 파생된 에러 정보
  const queryErrorInfo = useMemo(() => {
    if (!sessionQueryError) return null;
    const err = sessionQueryError as { error?: { code?: string } };
    return getChatErrorInfo(err?.error?.code);
  }, [sessionQueryError]);

  const activeErrorInfo = sendErrorInfo ?? queryErrorInfo;
  const dailyLimitExceeded = sendLimitExceeded || activeErrorInfo?.action === 'blockInput';

  const isInputDisabled =
    isStreaming ||
    isSessionEnded ||
    dailyLimitExceeded ||
    startSessionMutation.isPending ||
    completeSessionMutation.isPending;

  const inputRef = useRef<InputRef>(null);
  const prevDisabledRef = useRef(isInputDisabled);

  // 입력창이 막혀 있다가(disabled) 풀리는 순간 자동으로 포커스 복원
  useEffect(() => {
    if (prevDisabledRef.current && !isInputDisabled) {
      inputRef.current?.focus();
    }
    prevDisabledRef.current = isInputDisabled;
  }, [isInputDisabled]);

  // 컴포넌트 언마운트(화면 벗어남) 시 진행 중인 스트림 중단
  useEffect(() => {
    return () => {
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
      }
    };
  }, []);

  // 메시지 업데이트 또는 스트리밍 시 스크롤 자동 이동
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messagesData, streamingUserMessage, streamingAssistantMessage]);

  // 새 상담 시작 핸들러
  const handleStartNewSession = () => {
    setSendErrorInfo(null);
    setSendLimitExceeded(false);
    setStreamingUserMessage(null);
    setStreamingAssistantMessage(null);
    startSessionMutation.mutate(undefined, {
      onSuccess: () => {
        toast.success('새로운 상담을 시작합니다.');
      },
      onError: (err: unknown) => {
        const errorRes = err as { error?: { code?: string } };
        const info = getChatErrorInfo(errorRes?.error?.code);
        setSendErrorInfo(info);
        if (info.action === 'blockInput') {
          setSendLimitExceeded(true);
        }
        toast.error(info.message);
      },
    });
  };

  // 상담 종료 핸들러
  const handleEndSession = () => {
    if (!sessionId || isSessionEnded || isStreaming) return;

    modal.confirm({
      title: '상담을 종료할까요?',
      content: '상담을 종료하면 대화가 마감되며 언제든 새 상담을 시작할 수 있어요.',
      okText: '상담 끝내기',
      cancelText: '계속 대화하기',
      onOk: () => {
        completeSessionMutation.mutate(sessionId, {
          onSuccess: () => {
            toast.info('상담이 종료되었습니다.');
          },
          onError: (err: unknown) => {
            const errorRes = err as { error?: { code?: string } };
            const info = getChatErrorInfo(errorRes?.error?.code);
            toast.error(info.message);
          },
        });
      },
    });
  };

  // 메시지 전송 핸들러
  const handleSend = async () => {
    const trimmed = inputText.trim();
    if (!trimmed || !sessionId || isStreaming || isSessionEnded || dailyLimitExceeded) {
      return;
    }

    setSendErrorInfo(null);
    setStreamingUserMessage(trimmed);
    setStreamingAssistantMessage('');
    setInputText('');

    const abortController = new AbortController();
    abortControllerRef.current = abortController;

    await streamMessage({
      sessionId,
      content: trimmed,
      signal: abortController.signal,
      onChunk: (chunk) => {
        setStreamingAssistantMessage((prev) => (prev ?? '') + chunk);
      },
      onDone: async () => {
        try {
          await queryClient.refetchQueries({ queryKey: chatQueries.messages(sessionId) });
        } finally {
          setStreamingUserMessage(null);
          setStreamingAssistantMessage(null);
        }
      },
      onError: ({ code }) => {
        setStreamingUserMessage(null);
        setStreamingAssistantMessage(null);

        const info = getChatErrorInfo(code);
        setSendErrorInfo(info);

        if (info.action === 'restoreInput') {
          setInputText(trimmed);
        } else if (info.action === 'blockInput') {
          setSendLimitExceeded(true);
        }

        toast.error(info.message);
      },
    });
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  // 비로그인 상태 안내
  if (!isAuthenticated) {
    return (
      <div className={styles.loginPrompt}>
        <Result
          status="info"
          title="로그인이 필요합니다"
          subTitle="냉장고 속 재료로 레시피와 식단을 추천받으려면 로그인해 주세요."
          extra={
            <Button type="primary" size="large" onClick={() => navigate(ROUTES.login)}>
              로그인하기
            </Button>
          }
        />
      </div>
    );
  }

  // 초기 로딩
  if (isSessionLoading) {
    return (
      <div className={styles.chatContainer}>
        <Flex justify="center" align="center" style={{ height: 400 }}>
          <Spin size="large" tip="상담 세션을 불러오는 중..." />
        </Flex>
      </div>
    );
  }

  return (
    <div className={styles.chatContainer}>
      {/* 헤더 */}
      <header className={styles.chatHeader}>
        <div className={styles.headerTitle}>
          <RobotOutlined style={{ fontSize: 20, color: 'var(--ant-color-primary)' }} />
          <Typography.Title level={5} style={{ margin: 0 }}>
            AI 냉장고 상담
          </Typography.Title>
        </div>

        <Flex gap="small" align="center">
          {isSessionEnded ? (
            <Button
              type="primary"
              onClick={handleStartNewSession}
              loading={startSessionMutation.isPending}
            >
              새 상담 시작
            </Button>
          ) : (
            <Button
              onClick={handleEndSession}
              disabled={isStreaming || completeSessionMutation.isPending}
              loading={completeSessionMutation.isPending}
            >
              상담 끝내기
            </Button>
          )}
        </Flex>
      </header>

      {/* 에러 및 만료/종료 알림 배너 */}
      {activeErrorInfo && (
        <div className={styles.alertBanner}>
          <Alert
            type={activeErrorInfo.action === 'blockInput' ? 'warning' : 'info'}
            showIcon
            icon={activeErrorInfo.action === 'blockInput' ? <WarningOutlined /> : undefined}
            message={activeErrorInfo.message}
            action={
              activeErrorInfo.action === 'restart' && (
                <Button
                  size="small"
                  type="primary"
                  onClick={handleStartNewSession}
                  loading={startSessionMutation.isPending}
                >
                  새 상담 시작
                </Button>
              )
            }
          />
        </div>
      )}

      {/* 대화 메시지 영역 */}
      <div className={styles.messageArea}>
        {isMessagesLoading ? (
          <Flex justify="center" align="center" style={{ height: '100%' }}>
            <Spin tip="대화 기록을 불러오는 중..." />
          </Flex>
        ) : messagesData && messagesData.length > 0 ? (
          messagesData.map((msg) => {
            const isUser = msg.role === 'USER';
            return (
              <div
                key={msg.id ?? msg.sequence}
                className={`${styles.messageRow} ${isUser ? styles.user : styles.assistant}`}
              >
                <div className={`${styles.messageFooter} ${isUser ? styles.user : ''}`}>
                  <div
                    className={`${styles.messageBubble} ${
                      isUser ? styles.userBubble : styles.assistantBubble
                    }`}
                  >
                    {msg.content}
                  </div>
                  {msg.createdAt && (
                    <span className={styles.messageTime}>
                      {dayjs(msg.createdAt).format('HH:mm')}
                    </span>
                  )}
                </div>
              </div>
            );
          })
        ) : (
          !streamingUserMessage && (
            <div className={styles.emptyArea}>
              <RobotOutlined style={{ fontSize: 36 }} />
              <Typography.Text type="secondary">
                냉장고 속 재료로 만들고 싶은 메뉴나 요리 고민을 편하게 물어보세요!
              </Typography.Text>
            </div>
          )
        )}

        {/* 스트리밍 중인 사용자 질문 */}
        {streamingUserMessage && (
          <div className={`${styles.messageRow} ${styles.user}`}>
            <div className={`${styles.messageFooter} ${styles.user}`}>
              <div className={`${styles.messageBubble} ${styles.userBubble}`}>
                {streamingUserMessage}
              </div>
              <span className={styles.messageTime}>{dayjs().format('HH:mm')}</span>
            </div>
          </div>
        )}

        {/* 스트리밍 중인 AI 답변 (깜빡이는 커서) */}
        {streamingAssistantMessage !== null && (
          <div className={`${styles.messageRow} ${styles.assistant}`}>
            <div className={styles.messageFooter}>
              <div className={`${styles.messageBubble} ${styles.assistantBubble}`}>
                {streamingAssistantMessage}
                <span className={styles.cursor} />
              </div>
              <span className={styles.messageTime}>{dayjs().format('HH:mm')}</span>
            </div>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      {/* 입력 영역 */}
      <footer className={styles.inputArea}>
        <div className={styles.inputForm}>
          <div className={styles.textareaWrapper}>
            <Input.TextArea
              ref={inputRef}
              value={inputText}
              onChange={(e) => setInputText(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder={
                dailyLimitExceeded
                  ? '오늘 상담 횟수를 모두 소진했습니다.'
                  : isSessionEnded
                    ? '상담이 종료되었습니다. 상단의 [새 상담 시작]을 눌러주세요.'
                    : isStreaming
                      ? 'AI 답변을 생성하는 중입니다...'
                      : '메시지를 입력하세요 (Shift + Enter 줄바꿈)'
              }
              maxLength={500}
              showCount
              autoSize={{ minRows: 1, maxRows: 4 }}
              disabled={isInputDisabled}
            />
          </div>
          <Button
            type="primary"
            icon={<SendOutlined />}
            onClick={handleSend}
            disabled={isInputDisabled || !inputText.trim()}
            loading={isStreaming}
          />
        </div>
      </footer>
    </div>
  );
}
