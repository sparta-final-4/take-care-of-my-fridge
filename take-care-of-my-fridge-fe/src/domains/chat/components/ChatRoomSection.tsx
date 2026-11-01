import { CloseOutlined, DownOutlined, RobotOutlined, SendOutlined } from '@ant-design/icons';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Flex, Input, type InputRef, Result, Spin, Typography } from 'antd';
import { type KeyboardEvent, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router';

import { ROUTES } from '@/app/paths';
import { getErrorCode } from '@/common/api/errors';
import { useAuthStore } from '@/common/auth/authStore';
import { streamMessage } from '../api';
import { getChatErrorInfo, type ChatErrorInfo } from '../errors';
import { chatQueries, useCompleteChatSession, useStartChatSession } from '../queries';
import { ChatMessageBubble } from './ChatMessageBubble';
import styles from './ChatRoomSection.module.scss';

export interface ChatRoomSectionProps {
  onStreamingChange?: (streaming: boolean) => void;
  onRegisterAbort?: (abortFn: () => void) => void;
  onCloseModal?: () => void;
}

export function ChatRoomSection({
  onStreamingChange,
  onRegisterAbort,
  onCloseModal,
}: ChatRoomSectionProps = {}) {
  const navigate = useNavigate();
  const { modal } = App.useApp();
  const queryClient = useQueryClient();

  const authStatus = useAuthStore((state) => state.status);
  const isAuthenticated = authStatus === 'authenticated';

  const [inputText, setInputText] = useState('');
  const [streamingUserMessage, setStreamingUserMessage] = useState<string | null>(null);
  const [streamingAssistantMessage, setStreamingAssistantMessage] = useState<string | null>(null);
  const [sendErrorInfo, setSendErrorInfo] = useState<ChatErrorInfo | null>(null);
  const [sendLimitExceeded, setSendLimitExceeded] = useState(false);
  const [visibleSendError, setVisibleSendError] = useState<string | null>(null);
  const [showScrollBottom, setShowScrollBottom] = useState(false);

  const containerRef = useRef<HTMLDivElement>(null);
  const isScrolledUpRef = useRef<boolean>(false);
  const abortControllerRef = useRef<AbortController | null>(null);
  const inputRef = useRef<InputRef>(null);
  const errorTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

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

  useEffect(() => {
    onStreamingChange?.(isStreaming);
  }, [isStreaming, onStreamingChange]);

  useEffect(() => {
    onRegisterAbort?.(() => {
      abortControllerRef.current?.abort();
    });
  }, [onRegisterAbort]);

  // 전송 실패 에러 배너 (3초 후 자동 소멸)
  const triggerSendError = useCallback((message: string) => {
    setVisibleSendError(message);
    if (errorTimerRef.current) clearTimeout(errorTimerRef.current);
    errorTimerRef.current = setTimeout(() => {
      setVisibleSendError(null);
    }, 3000);
  }, []);

  // 세션 시작 쿼리 에러로부터 파생된 에러 정보
  const queryErrorInfo = useMemo(() => {
    if (!sessionQueryError) return null;
    const err = sessionQueryError as { error?: { code?: string } };
    return getChatErrorInfo(err?.error?.code);
  }, [sessionQueryError]);

  // 우선순위: 전송 시 에러 > 쿼리 시 에러
  const activeErrorInfo = sendErrorInfo ?? queryErrorInfo;
  const dailyLimitExceeded = sendLimitExceeded || activeErrorInfo?.action === 'blockInput';

  // 입력 비활성화 여부
  const isInputDisabled =
    !isAuthenticated ||
    isSessionLoading ||
    sessionId <= 0 ||
    isSessionEnded ||
    isStreaming ||
    dailyLimitExceeded;

  // input 비활성화 해제 시 자동 포커스 복구
  const prevDisabledRef = useRef(isInputDisabled);
  useEffect(() => {
    if (prevDisabledRef.current && !isInputDisabled) {
      inputRef.current?.focus();
    }
    prevDisabledRef.current = isInputDisabled;
  }, [isInputDisabled]);

  // 스크롤 최하단 이동 함수
  const scrollToBottom = useCallback((behavior: ScrollBehavior = 'smooth') => {
    if (containerRef.current) {
      containerRef.current.scrollTo({
        top: containerRef.current.scrollHeight,
        behavior,
      });
      setShowScrollBottom(false);
      isScrolledUpRef.current = false;
    }
  }, []);

  // 스크롤 이벤트 감지
  const handleScroll = useCallback(() => {
    if (!containerRef.current) return;
    const { scrollTop, scrollHeight, clientHeight } = containerRef.current;
    const isUp = scrollHeight - scrollTop - clientHeight > 80;
    isScrolledUpRef.current = isUp;
    setShowScrollBottom(isUp);
  }, []);

  // 새 메시지 및 스트리밍 조각 수신 시 스크롤 처리
  useEffect(() => {
    if (!isScrolledUpRef.current) {
      scrollToBottom('auto');
    } else {
      setShowScrollBottom(true);
    }
  }, [messagesData, streamingUserMessage, streamingAssistantMessage, scrollToBottom]);

  // 새 상담 시작 핸들러
  const handleStartNewSession = () => {
    setSendErrorInfo(null);
    setSendLimitExceeded(false);
    setVisibleSendError(null);
    setInputText('');
    setStreamingUserMessage(null);
    setStreamingAssistantMessage(null);

    startSessionMutation.mutate(undefined, {
      onError: (err) => {
        const errorInfo = getChatErrorInfo(getErrorCode(err));
        triggerSendError(errorInfo.message);
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
      cancelText: '취소',
      onOk: () => {
        completeSessionMutation.mutate(sessionId, {
          onError: (err) => {
            const errorInfo = getChatErrorInfo(getErrorCode(err));
            triggerSendError(errorInfo.message);
          },
        });
      },
    });
  };

  // 메시지 전송 핸들러
  const handleSendMessage = async () => {
    const text = inputText.trim();
    if (!text || isInputDisabled || sessionId <= 0) return;

    setInputText('');
    setSendErrorInfo(null);
    setVisibleSendError(null);
    setStreamingUserMessage(text);
    setStreamingAssistantMessage('');

    const controller = new AbortController();
    abortControllerRef.current = controller;

    try {
      await streamMessage({
        sessionId,
        content: text,
        signal: controller.signal,
        onChunk: (chunk: string) => {
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
        onError: (chatError) => {
          const errorInfo = getChatErrorInfo(chatError.code);
          triggerSendError(errorInfo.message);

          if (errorInfo.action === 'blockInput') {
            setSendLimitExceeded(true);
          }
          if (errorInfo.action === 'restart') {
            setSendErrorInfo(errorInfo);
          }

          setStreamingUserMessage(null);
          setStreamingAssistantMessage(null);
        },
      });
    } catch {
      triggerSendError('답변 생성 중 문제가 발생했습니다.');
      setStreamingUserMessage(null);
      setStreamingAssistantMessage(null);
    } finally {
      abortControllerRef.current = null;
    }
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      void handleSendMessage();
    }
  };

  // 비로그인 상태 안내
  if (!isAuthenticated) {
    return (
      <div className={styles.loginPrompt}>
        <header className={styles.chatHeader}>
          <div className={styles.headerTitle}>
            <RobotOutlined style={{ fontSize: 18, color: 'var(--ant-color-primary)' }} />
            <span>AI 냉장고 상담</span>
          </div>
          {onCloseModal && (
            <Button
              type="text"
              size="small"
              icon={<CloseOutlined />}
              onClick={onCloseModal}
              className={styles.closeButton}
              aria-label="닫기"
            />
          )}
        </header>

        <div className={styles.loginPromptBody}>
          <Result
            status="info"
            title="로그인이 필요합니다"
            subTitle="냉장고 속 재료로 요리를 추천받으려면 로그인해 주세요."
            extra={
              <Button
                type="primary"
                onClick={() => {
                  onCloseModal?.();
                  navigate(ROUTES.login);
                }}
              >
                로그인하기
              </Button>
            }
          />
        </div>
      </div>
    );
  }

  // 초기 세션 로딩 중
  if (isSessionLoading) {
    return (
      <div className={styles.chatContainer}>
        <header className={styles.chatHeader}>
          <div className={styles.headerTitle}>
            <RobotOutlined style={{ fontSize: 18, color: 'var(--ant-color-primary)' }} />
            <span>AI 냉장고 상담</span>
          </div>
          {onCloseModal && (
            <Button
              type="text"
              size="small"
              icon={<CloseOutlined />}
              onClick={onCloseModal}
              className={styles.closeButton}
              aria-label="닫기"
            />
          )}
        </header>
        <Flex justify="center" align="center" style={{ flex: 1 }}>
          <Spin size="default" description="상담 세션을 불러오는 중..." />
        </Flex>
      </div>
    );
  }

  return (
    <div className={styles.chatContainer}>
      {/* 1단: 헤더 (고정) */}
      <header className={styles.chatHeader}>
        <div className={styles.headerTitle}>
          <RobotOutlined style={{ fontSize: 18, color: 'var(--ant-color-primary)' }} />
          <span>AI 냉장고 상담</span>
        </div>

        <div className={styles.headerActions}>
          {!isSessionEnded && (
            <Button
              size="small"
              onClick={handleEndSession}
              disabled={isStreaming || completeSessionMutation.isPending}
              loading={completeSessionMutation.isPending}
            >
              상담 끝내기
            </Button>
          )}
          {onCloseModal && (
            <Button
              type="text"
              size="small"
              icon={<CloseOutlined />}
              onClick={onCloseModal}
              className={styles.closeButton}
              aria-label="닫기"
            />
          )}
        </div>
      </header>

      {/* 2단: 상태 배너 (고정, 헤더 바로 아래 얇은 줄) */}
      <div className={styles.statusBanner}>
        {isSessionEnded ? (
          <>
            <div className={styles.statusIndicator}>
              <span className={`${styles.statusDot} ${styles.ended}`} />
              <span>상담이 끝났어요</span>
            </div>
            <Button
              type="link"
              size="small"
              style={{ padding: 0, height: 'auto', fontSize: 'var(--ant-font-size-sm)' }}
              onClick={handleStartNewSession}
              loading={startSessionMutation.isPending}
            >
              [새 상담 시작]
            </Button>
          </>
        ) : (
          <div className={styles.statusIndicator}>
            <span className={`${styles.statusDot} ${styles.active}`} />
            <span>상담 중</span>
          </div>
        )}
      </div>

      {/* 3단: 메시지 영역 (스크롤) */}
      <div className={styles.messageAreaWrapper}>
        <div ref={containerRef} onScroll={handleScroll} className={styles.messageArea}>
          {isMessagesLoading ? (
            <Flex justify="center" align="center" style={{ flex: 1 }}>
              <Spin description="대화 기록을 불러오는 중..." />
            </Flex>
          ) : messagesData && messagesData.length > 0 ? (
            messagesData.map((msg) => (
              <ChatMessageBubble
                key={msg.id ?? msg.sequence}
                senderRole={msg.role === 'USER' ? 'USER' : 'ASSISTANT'}
                content={msg.content ?? ''}
                createdAt={msg.createdAt}
              />
            ))
          ) : (
            !streamingUserMessage && (
              <div className={styles.emptyArea}>
                <RobotOutlined style={{ fontSize: 32, color: 'var(--ant-color-primary)' }} />
                <Typography.Text type="secondary">
                  냉장고 속 재료로 만들고 싶은 메뉴나
                  <br />
                  요리 고민을 편하게 물어보세요!
                </Typography.Text>
              </div>
            )
          )}

          {/* 스트리밍 중인 사용자 질문 */}
          {streamingUserMessage && (
            <ChatMessageBubble senderRole="USER" content={streamingUserMessage} />
          )}

          {/* 스트리밍 중인 AI 답변 */}
          {streamingAssistantMessage !== null && (
            <ChatMessageBubble
              senderRole="ASSISTANT"
              content={streamingAssistantMessage}
              isStreaming={true}
            />
          )}
        </div>

        {/* 최신 메시지 버튼 */}
        {showScrollBottom && (
          <button
            type="button"
            onClick={() => scrollToBottom('smooth')}
            className={styles.scrollBottomButton}
          >
            <span>최신 메시지</span>
            <DownOutlined style={{ fontSize: 10 }} />
          </button>
        )}
      </div>

      {/* 4단: 하단 입력 영역 (고정) */}
      <footer className={styles.inputArea}>
        {/* 전송 실패 배너 (3초 뒤 자동 소멸) */}
        {visibleSendError && (
          <div className={styles.sendErrorBanner}>
            <span className={styles.errorDot} />
            <span>{visibleSendError}</span>
          </div>
        )}

        <div className={styles.inputForm}>
          <Input
            ref={inputRef}
            value={inputText}
            maxLength={200}
            onChange={(e) => setInputText(e.target.value)}
            onKeyDown={handleKeyDown}
            disabled={isInputDisabled}
            placeholder={
              dailyLimitExceeded
                ? '오늘 상담 횟수를 모두 소진했습니다.'
                : isSessionEnded
                  ? '상담이 종료되었습니다. 상단 [새 상담 시작]을 눌러주세요.'
                  : isStreaming
                    ? 'AI 답변을 생성 중입니다...'
                    : '메시지를 입력하세요 (Enter로 전송)'
            }
            className={styles.singleInput}
          />
          <Button
            type="primary"
            icon={<SendOutlined />}
            onClick={() => void handleSendMessage()}
            disabled={isInputDisabled || !inputText.trim()}
          />
        </div>

        <div className={styles.counterRow}>
          <span>{inputText.length} / 200</span>
        </div>
      </footer>
    </div>
  );
}
