import { CloseOutlined, DownOutlined, RobotOutlined, SendOutlined } from '@ant-design/icons';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Flex, Input, type InputRef, Result, Spin, Tag, Typography } from 'antd';
import {
  Fragment,
  type KeyboardEvent,
  type ReactNode,
  useCallback,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { useNavigate } from 'react-router';

import { ROUTES } from '@/app/paths';
import { getChatErrorInfo, getErrorCode, type ChatErrorInfo } from '@/common/api/errors';
import { useAuthStore } from '@/common/auth/authStore';
import { useScreens } from '@/common/hooks/useScreens';
import { streamMessage } from '../api';
import { useChatModalStore } from '../model/useChatModalStore';
import { useChatStreamingStore } from '../model/useChatStreamingStore';
import { chatQueries, useCompleteChatSession, useStartChatSession } from '../queries';
import {
  type ChatRecommendations,
  toChatRecommendedMealkits,
  toChatRecommendedRecipes,
} from '../recommendations';
import { selectedRecommendMessage } from '../selectedIngredients';
import { ChatEndedNotice } from './ChatEndedNotice';
import { ChatMessageBubble } from './ChatMessageBubble';
import { ChatRecommendationCards } from './ChatRecommendationCards';
import styles from './ChatRoomSection.module.scss';

// 백엔드 ChatMessageCreateRequest의 @Size(max = 500)와 같은 값
const CHAT_MESSAGE_MAX_LENGTH = 500;

const AUTO_RECOMMEND_MESSAGE = '알아서 추천해줘';

// 맨 아래에서 이만큼 넘게 위에 있으면 "최신 메시지" 버튼을 보여 준다
const SCROLL_BOTTOM_THRESHOLD = 80;
// 보낸 질문 말풍선을 대화 영역 위쪽에 둘 때 위 여백
const QUESTION_TOP_GAP = 8;

// 보낼 메시지. 칩은 mode=RECOMMEND로 AI 의도 판단 없이 추천을 받는다.
interface OutgoingMessage {
  content: string;
  mode: 'AUTO' | 'RECOMMEND';
  useSelectedOnly: boolean;
}

export interface ChatRoomSectionProps {
  onStreamingChange?: (streaming: boolean) => void;
  onRegisterAbort?: (abortFn: () => void) => void;
  onCloseModal?: () => void;
  // 헤더 왼쪽 제목. 마이페이지 상담 내역에서는 날짜·상태 태그를 넣는다. 없으면 "AI 냉장고 상담".
  title?: ReactNode;
  // 마이페이지 상담 내역은 위쪽 상태 태그가 같은 정보를 보여 주므로 "● 상담 중" 상태 줄을 숨긴다.
  hideStatusBanner?: boolean;
  // 상담 끝내기가 성공했을 때. 상담 내역은 "상담 중" 탭에서 끝내면 "종료" 탭으로 옮긴다.
  onSessionCompleted?: () => void;
}

export function ChatRoomSection({
  onStreamingChange,
  onRegisterAbort,
  onCloseModal,
  title,
  hideStatusBanner = false,
  onSessionCompleted,
}: ChatRoomSectionProps = {}) {
  const headerTitle = title ?? (
    <>
      <RobotOutlined style={{ fontSize: 18, color: 'var(--ant-color-primary)' }} />
      <span>AI 냉장고 상담</span>
    </>
  );
  const navigate = useNavigate();
  const { modal } = App.useApp();
  const queryClient = useQueryClient();

  // 내 냉장고에서 넘긴 선택 재료. 보낸 뒤에도 그대로 두고, 사용자가 ✕로 뺀다.
  const selectedIngredients = useChatModalStore((state) => state.selectedIngredients);
  const removeSelectedIngredient = useChatModalStore((state) => state.removeSelectedIngredient);

  const authStatus = useAuthStore((state) => state.status);
  const isAuthenticated = authStatus === 'authenticated';

  const [inputText, setInputText] = useState('');
  const [streamingUserMessage, setStreamingUserMessage] = useState<string | null>(null);
  const [streamingAssistantMessage, setStreamingAssistantMessage] = useState<string | null>(null);
  const [sendErrorInfo, setSendErrorInfo] = useState<ChatErrorInfo | null>(null);
  const [sendLimitExceeded, setSendLimitExceeded] = useState(false);
  const [visibleSendError, setVisibleSendError] = useState<string | null>(null);
  const [showScrollBottom, setShowScrollBottom] = useState(false);
  // 받는 중인 답의 추천 카드와, 첫 이벤트(카드·글) 전에 보여 줄 안내
  const [streamingRecommendations, setStreamingRecommendations] =
    useState<ChatRecommendations | null>(null);
  const [pendingText, setPendingText] = useState<string | null>(null);
  // 받는 중인 카드 설명 (카드 순서). recommendation_text 이벤트로 채운다
  const [streamingDescriptions, setStreamingDescriptions] = useState<string[]>([]);
  const screens = useScreens();

  const containerRef = useRef<HTMLDivElement>(null);
  // 보낸 질문 말풍선. 답을 받는 동안 이 말풍선이 대화 영역 위쪽에 올 때까지만 따라 내려간다.
  const questionAnchorRef = useRef<HTMLDivElement>(null);
  // 따라 내려갈 목표 위치(질문이 위쪽에 오는 scrollTop). 사용자가 직접 스크롤하면 null로 멈춘다.
  const followTargetRef = useRef<number | null>(null);
  // 코드가 마지막으로 맞춘 scrollTop. onScroll이 코드 때문인지 사용자 때문인지 가른다.
  const programmaticTopRef = useRef<number | null>(null);
  // 맨 아래로 한 번 내린 상담. 처음 열 때만 내리고, 그 뒤 기록을 다시 받아도 위치를 지킨다.
  const initialScrolledSessionRef = useRef<number | null>(null);
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

  // 이 창에서 답을 받는 중인 상담을 앱 전체에 알린다. 완료(기록 다시 받기까지)·오류·취소로 스트리밍 말풍선이
  // 사라지거나, 채팅창을 닫아 이 컴포넌트가 사라지면 비운다.
  const startStreaming = useChatStreamingStore((state) => state.startStreaming);
  const finishStreaming = useChatStreamingStore((state) => state.finishStreaming);
  useEffect(() => {
    if (!isStreaming || sessionId <= 0) return;
    startStreaming(sessionId);
    return () => finishStreaming(sessionId);
  }, [isStreaming, sessionId, startStreaming, finishStreaming]);

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

  // 스크롤 규칙
  // - 상담을 처음 보여 줄 때(채팅창 열기·새로고침·새 상담): 한 번 맨 아래로
  // - 보낸 뒤 답을 받는 동안: 내 질문이 대화 영역 위쪽에 올 때까지만 따라 내려가고, 그 뒤로는 따라가지 않는다
  //   (질문+답이 화면보다 짧으면 맨 아래에서 멈추므로 위에 빈 공간이 생기지 않는다)
  // - 기록을 다시 받을 때(답 완료·다른 창 신호): 위치를 그대로 두고 "최신 메시지" 버튼만 맞춘다
  const updateScrollBottomButton = useCallback(() => {
    const container = containerRef.current;
    if (!container) return;
    const { scrollTop, scrollHeight, clientHeight } = container;
    setShowScrollBottom(scrollHeight - scrollTop - clientHeight > SCROLL_BOTTOM_THRESHOLD);
  }, []);

  const scrollToBottom = useCallback((behavior: ScrollBehavior = 'smooth') => {
    const container = containerRef.current;
    if (!container) return;
    followTargetRef.current = null;
    container.scrollTo({ top: container.scrollHeight, behavior });
    setShowScrollBottom(false);
  }, []);

  // 답을 받는 동안 목표 위치(질문이 위쪽)까지만 내려간다. 내용이 짧으면 갈 수 있는 맨 아래까지만.
  const followQuestion = useCallback(() => {
    const container = containerRef.current;
    const target = followTargetRef.current;
    if (!container || target === null) return;
    const top = Math.min(target, container.scrollHeight - container.clientHeight);
    if (top > container.scrollTop) {
      programmaticTopRef.current = top;
      container.scrollTop = top;
    }
  }, []);

  const handleScroll = useCallback(() => {
    const container = containerRef.current;
    if (!container) return;
    // 코드가 맞춘 위치와 다르면 사용자가 스크롤한 것이다. 그때부터는 따라 내려가지 않는다.
    if (
      programmaticTopRef.current !== null &&
      Math.abs(container.scrollTop - programmaticTopRef.current) > 2
    ) {
      followTargetRef.current = null;
      programmaticTopRef.current = null;
    }
    updateScrollBottomButton();
  }, [updateScrollBottomButton]);

  // 상담을 처음 보여 줄 때 한 번 맨 아래로
  useLayoutEffect(() => {
    if (sessionId <= 0 || !messagesData || initialScrolledSessionRef.current === sessionId) return;
    initialScrolledSessionRef.current = sessionId;
    scrollToBottom('auto');
  }, [sessionId, messagesData, scrollToBottom]);

  // 보낸 질문이 그려지면 목표 위치를 정하고 따라 내려가기 시작한다
  useLayoutEffect(() => {
    const container = containerRef.current;
    const question = questionAnchorRef.current;
    if (!streamingUserMessage || !container || !question) {
      followTargetRef.current = null;
      programmaticTopRef.current = null;
      return;
    }
    followTargetRef.current =
      question.getBoundingClientRect().top -
      container.getBoundingClientRect().top +
      container.scrollTop -
      QUESTION_TOP_GAP;
    followQuestion();
  }, [streamingUserMessage, followQuestion]);

  // 답 조각·카드·안내가 바뀌면 목표 위치까지 따라 내려가고 버튼을 맞춘다
  useLayoutEffect(() => {
    followQuestion();
    updateScrollBottomButton();
  }, [
    streamingAssistantMessage,
    streamingRecommendations,
    streamingDescriptions,
    pendingText,
    followQuestion,
    updateScrollBottomButton,
  ]);

  // 기록을 다시 받으면(답 완료·다른 창 신호) 위치는 그대로 두고 버튼만 맞춘다
  useEffect(() => {
    updateScrollBottomButton();
  }, [messagesData, updateScrollBottomButton]);

  // 새 상담 시작 핸들러
  const handleStartNewSession = () => {
    setSendErrorInfo(null);
    setSendLimitExceeded(false);
    setVisibleSendError(null);
    setInputText('');
    setStreamingUserMessage(null);
    setStreamingAssistantMessage(null);
    setStreamingRecommendations(null);
    setStreamingDescriptions([]);
    setPendingText(null);

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
          onSuccess: () => onSessionCompleted?.(),
          onError: (err) => {
            const errorInfo = getChatErrorInfo(getErrorCode(err));
            triggerSendError(errorInfo.message);
          },
        });
      },
    });
  };

  // 카드를 눌러 레시피 상세로 갈 때, 모바일 전체 화면 채팅창은 닫아 상세가 보이게 한다.
  // md 이상(380px 떠 있는 창)과 마이페이지 오른쪽 칸은 그대로 둔다.
  const closeOnNavigate = screens.md ? undefined : onCloseModal;

  // 메시지 전송 핸들러 (직접 입력). 선택 재료가 있으면 같이 보내고, AI가 추천인지 판단한다.
  const handleSendMessage = async () => {
    const text = inputText.trim();
    if (!text) return;
    setInputText('');
    await sendMessage({ content: text, mode: 'AUTO', useSelectedOnly: false });
  };

  // [알아서 추천해줘] 칩
  const handleAutoRecommend = () => {
    void sendMessage({
      content: AUTO_RECOMMEND_MESSAGE,
      mode: 'RECOMMEND',
      useSelectedOnly: false,
    });
  };

  // [이 재료로 추천받기] 칩. 선택 재료를 꼭 쓰는 레시피만 받는다.
  const handleSelectedRecommend = () => {
    if (selectedIngredients.length === 0) return;
    void sendMessage({
      content: selectedRecommendMessage(selectedIngredients),
      mode: 'RECOMMEND',
      useSelectedOnly: true,
    });
  };

  const sendMessage = async ({ content: text, mode, useSelectedOnly }: OutgoingMessage) => {
    if (!text || isInputDisabled || sessionId <= 0) return;
    // 의도 판단·후보 검색으로 첫 이벤트까지 2초쯤 걸린다. 칩은 추천이 확실하므로 레시피를 찾는다고 알린다.
    setPendingText(mode === 'RECOMMEND' ? '레시피를 찾고 있어요…' : '답을 준비하고 있어요…');
    setStreamingRecommendations(null);

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
        mode,
        selectedIngredientIds: selectedIngredients.map((ingredient) => ingredient.ingredientId),
        useSelectedOnly,
        signal: controller.signal,
        onRecommendations: (recommendations) => {
          setStreamingRecommendations(recommendations);
          setPendingText(null);
        },
        onRecommendationText: ({ index, text: piece }) => {
          setPendingText(null);
          setStreamingDescriptions((prev) => {
            const next = [...prev];
            next[index] = (next[index] ?? '') + piece;
            return next;
          });
        },
        onChunk: (chunk: string) => {
          setPendingText(null);
          setStreamingAssistantMessage((prev) => (prev ?? '') + chunk);
        },
        onDone: async () => {
          // 카드는 AI 메시지와 함께 저장됐으므로 기록을 다시 받으면 기록의 카드로 그린다.
          // 다시 받기가 끝난 뒤에 받는 중인 카드를 지워 깜빡이지 않게 한다
          try {
            await queryClient.refetchQueries({ queryKey: chatQueries.messages(sessionId) });
            // 상담 내역의 메시지 수·마지막 시각이 바뀌므로 다시 받게 한다.
            void queryClient.invalidateQueries({ queryKey: chatQueries.sessions() });
          } finally {
            setStreamingUserMessage(null);
            setStreamingAssistantMessage(null);
            setStreamingRecommendations(null);
            setStreamingDescriptions([]);
            setPendingText(null);
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
          // 다시 보낼 수 있는 실패(다른 창에서 답을 받는 중 등)는 직접 입력한 내용을 입력창에 되돌린다.
          if (errorInfo.action === 'restoreInput' && mode === 'AUTO') {
            setInputText(text);
          }

          setStreamingUserMessage(null);
          setStreamingAssistantMessage(null);
          setStreamingRecommendations(null);
          setStreamingDescriptions([]);
          setPendingText(null);
        },
      });
    } catch {
      triggerSendError('답변 생성 중 문제가 발생했습니다.');
      setStreamingUserMessage(null);
      setStreamingAssistantMessage(null);
      setStreamingRecommendations(null);
      setStreamingDescriptions([]);
      setPendingText(null);
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
          <div className={styles.headerTitle}>{headerTitle}</div>
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
          <div className={styles.headerTitle}>{headerTitle}</div>
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
        <div className={styles.headerTitle}>{headerTitle}</div>

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
      {!hideStatusBanner && (
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
      )}
      {!hideStatusBanner && isSessionEnded && <ChatEndedNotice onNavigate={onCloseModal} />}

      {/* 3단: 메시지 영역 (스크롤) */}
      <div className={styles.messageAreaWrapper}>
        <div ref={containerRef} onScroll={handleScroll} className={styles.messageArea}>
          {isMessagesLoading ? (
            <Flex justify="center" align="center" style={{ flex: 1 }}>
              <Spin description="대화 기록을 불러오는 중..." />
            </Flex>
          ) : messagesData && messagesData.length > 0 ? (
            messagesData.map((msg) => {
              const cards = toChatRecommendedRecipes(msg.recommendations);
              const mealkits = toChatRecommendedMealkits(msg.mealkits);
              return (
                <Fragment key={msg.id ?? msg.sequence}>
                  <ChatMessageBubble
                    senderRole={msg.role === 'USER' ? 'USER' : 'ASSISTANT'}
                    content={msg.content ?? ''}
                    createdAt={msg.createdAt}
                  />
                  <ChatRecommendationCards
                    recipes={cards}
                    mealkits={mealkits}
                    onNavigate={closeOnNavigate}
                  />
                </Fragment>
              );
            })
          ) : (
            !streamingUserMessage && (
              <div className={styles.emptyArea}>
                <RobotOutlined style={{ fontSize: 32, color: 'var(--ant-color-primary)' }} />
                <Typography.Text type="secondary">
                  냉장고 속 재료로 만들고 싶은 메뉴나
                  <br />
                  요리 고민을 편하게 물어보세요!
                </Typography.Text>
                {!isSessionEnded && (
                  <Flex wrap justify="center" gap="small" className={styles.recommendChips}>
                    <Button shape="round" onClick={handleAutoRecommend} disabled={isInputDisabled}>
                      알아서 추천해줘
                    </Button>
                    {selectedIngredients.length > 0 && (
                      <Button
                        shape="round"
                        onClick={handleSelectedRecommend}
                        disabled={isInputDisabled}
                      >
                        이 재료로 추천받기
                      </Button>
                    )}
                  </Flex>
                )}
              </div>
            )
          )}

          {/* 스트리밍 중인 사용자 질문 (보낸 뒤 이 말풍선이 위쪽에 오도록 스크롤한다) */}
          {streamingUserMessage && (
            <div ref={questionAnchorRef}>
              <ChatMessageBubble senderRole="USER" content={streamingUserMessage} />
            </div>
          )}

          {/* 스트리밍 중인 AI 답변. 첫 글 조각 전에는 안내 문구("레시피를 찾고 있어요…")를 보여 준다 */}
          {streamingAssistantMessage !== null && (
            <ChatMessageBubble
              senderRole="ASSISTANT"
              content={streamingAssistantMessage}
              pendingText={pendingText ?? undefined}
              isStreaming={true}
            />
          )}

          {/* 스트리밍 중인 추천 카드. 설명보다 먼저 와도 말풍선 아래에 둔다 (후보 없음이면 없음) */}
          {streamingRecommendations && !streamingRecommendations.empty && (
            <ChatRecommendationCards
              recipes={streamingRecommendations.recipes}
              mealkits={streamingRecommendations.mealkits}
              descriptions={streamingDescriptions}
              onNavigate={closeOnNavigate}
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

        {/* 선택 재료 줄. 끝난 상담에는 보이지 않는다. */}
        {!isSessionEnded && selectedIngredients.length > 0 && (
          <Flex align="center" wrap gap="small" className={styles.selectedRow}>
            <Typography.Text type="secondary" className={styles.selectedLabel}>
              선택한 재료
            </Typography.Text>
            {selectedIngredients.map((ingredient) => (
              <Tag
                key={ingredient.ingredientId}
                closable
                onClose={() => removeSelectedIngredient(ingredient.ingredientId)}
                closeIcon={<CloseOutlined aria-label={`${ingredient.name} 빼기`} />}
                className={styles.selectedTag}
              >
                {ingredient.name}
              </Tag>
            ))}
          </Flex>
        )}

        <div className={styles.inputForm}>
          <Input
            ref={inputRef}
            value={inputText}
            maxLength={CHAT_MESSAGE_MAX_LENGTH}
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
          <span>
            {inputText.length} / {CHAT_MESSAGE_MAX_LENGTH}
          </span>
        </div>
      </footer>
    </div>
  );
}
