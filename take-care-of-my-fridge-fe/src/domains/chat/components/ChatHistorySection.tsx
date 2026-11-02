import { ArrowLeftOutlined } from '@ant-design/icons';
import { useInfiniteQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Empty, Grid, Result, Segmented, Skeleton, Spin, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router';

import type {
  ChatSessionListRequestStatusEnum,
  ChatSessionStatusCounts,
  ChatSessionSummaryResponse,
} from '@/common/api/clients';
import { SectionCard } from '@/common/components/SectionCard';
import { SectionLoading } from '@/common/components/SectionLoading';

import { formatChatDateTime, formatChatTime } from '../format';
import { useChatModalStore } from '../model/useChatModalStore';
import { chatQueries, removeEndedActiveSessionAfterUnmount } from '../queries';
import { ChatSessionConversation } from './ChatSessionConversation';
import { ChatSessionLiveConversation } from './ChatSessionLiveConversation';
import { ChatSessionStatusTag } from './ChatSessionStatusTag';
import styles from './ChatHistorySection.module.scss';

const PAGE_SIZE = 10;

type ChatTab = ChatSessionListRequestStatusEnum;

const TABS: {
  value: ChatTab;
  label: string;
  count: keyof ChatSessionStatusCounts;
  empty: string;
}[] = [
  { value: 'ALL', label: '전체', count: 'total', empty: '아직 상담 기록이 없어요' },
  { value: 'ACTIVE', label: '상담 중', count: 'active', empty: '진행 중인 상담이 없어요' },
  { value: 'ENDED', label: '종료', count: 'ended', empty: '끝난 상담이 없어요' },
];

function parseTab(value: string | null): ChatTab {
  return value === 'ACTIVE' || value === 'ENDED' ? value : 'ALL';
}

// 마이페이지 AI 상담 내역. 최근 시작한 상담부터 보여 주고, 메시지가 없는 상담은 서버가 보내지 않는다.
// 왼쪽은 상태 탭(전체·상담 중·종료, 건수) + 목록(끝까지 내리면 다음 페이지), 오른쪽은 고른 상담의 대화.
// lg부터 두 칸이고 처음엔 첫 상담을 고른다. 탭을 바꿔 고른 상담이 목록에 없으면 그 탭의 첫 상담을 보여 준다.
// 그보다 좁으면 탭 + 목록만 보이고, 줄을 누르면 대화 화면으로 바뀐다([← 목록]으로 돌아감).
// 진행 중 상담은 오른쪽 칸에서 바로 대화한다. 이 화면에 있는 동안은 채팅창을 닫고, 채팅창 열기 요청은
// 이 화면의 진행 중 상담(없으면 새 상담)을 고르는 것으로 바꿔 같은 상담이 두 곳에서 열리지 않게 한다.
// 탭은 URL 쿼리(status)에 두어 새로고침·뒤로 가기에도 유지한다. 고른 상담은 화면 상태라 useState에 둔다.
export function ChatHistorySection() {
  const openChat = useChatModalStore((state) => state.openChat);
  const setInlineHost = useChatModalStore((state) => state.setInlineHost);
  const isSplit = Grid.useBreakpoint().lg ?? false;
  const [searchParams, setSearchParams] = useSearchParams();
  const tab = parseTab(searchParams.get('status'));
  const [selectedId, setSelectedId] = useState<number | null>(null);
  // 채팅창 열기 요청을 받아 진행 중 상담을 보여 주는 중인지
  const [isLiveRequested, setIsLiveRequested] = useState(false);
  // 목록 끝 표시. 화면에 들어오면 다음 페이지를 받는다.
  const [listEnd, setListEnd] = useState<HTMLDivElement | null>(null);

  const changeTab = (next: ChatTab) => {
    setSearchParams(next === 'ALL' ? {} : { status: next }, { replace: true });
  };

  useEffect(() => {
    setInlineHost(() => {
      setSearchParams({}, { replace: true });
      setSelectedId(null);
      setIsLiveRequested(true);
    });
    return () => setInlineHost(null);
  }, [setInlineHost, setSearchParams]);

  // 이 화면을 떠나면 오른쪽 칸이 보던 끝난 상담 캐시를 비워, 다음에 채팅창을 열면 새 상담이 열리게 한다.
  const queryClient = useQueryClient();
  useEffect(() => () => removeEndedActiveSessionAfterUnmount(queryClient), [queryClient]);

  const {
    data,
    isPending,
    isError,
    isPlaceholderData,
    refetch,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
  } = useInfiniteQuery(chatQueries.sessionListInfiniteQuery({ status: tab, size: PAGE_SIZE }));

  // 무한 스크롤: 목록 끝이 보이면(두 칸 화면은 목록 칸 안에서) 다음 페이지를 받는다.
  // 받은 줄이 칸을 다 채우지 못해 끝이 계속 보이면 받기가 끝날 때마다 다시 관찰해 이어 받는다.
  const canLoadMore = hasNextPage && !isFetchingNextPage && !isPlaceholderData;
  useEffect(() => {
    if (!listEnd || !canLoadMore) return;
    const observer = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting)) void fetchNextPage();
    });
    observer.observe(listEnd);
    return () => observer.disconnect();
  }, [listEnd, canLoadMore, fetchNextPage]);

  const sessions = data?.pages.flatMap((page) => page.sessions?.content ?? []) ?? [];
  const counts = data?.pages[0]?.counts;
  const currentTab = TABS.find((item) => item.value === tab) ?? TABS[0];
  const activeListed = sessions.find((session) => session.status === 'ACTIVE');
  // 고른 상담이 지금 목록에 없으면(처음 진입·탭 전환) 두 칸 화면은 첫 상담, 좁은 화면은 목록을 보여 준다.
  const selected: ChatSessionSummaryResponse | undefined =
    sessions.find((session) => session.sessionId === selectedId) ??
    (isLiveRequested ? activeListed : isSplit ? sessions[0] : undefined);
  // 열기 요청을 받았는데 목록에 진행 중 상담이 없으면(첫 메시지 전 새 상담) 목록과 상관없이 대화 칸을 연다.
  const isNewLive = isLiveRequested && !selected;

  const handleSelect = (sessionId: number | null) => {
    setSelectedId(sessionId);
    setIsLiveRequested(false);
    // 좁은 화면은 목록 ↔ 대화가 화면을 바꾸므로 맨 위부터 보여 준다.
    if (!isSplit) window.scrollTo({ top: 0 });
  };

  const handleChangeTab = (next: ChatTab) => {
    setIsLiveRequested(false);
    changeTab(next);
  };

  // "상담 중" 탭에서 상담을 끝내면 목록에서 빠지므로, 끝낸 상담이 보이는 "종료" 탭으로 옮긴다.
  const handleSessionCompleted = () => {
    if (tab === 'ACTIVE') changeTab('ENDED');
  };

  const renderConversation = () => {
    if (isNewLive) return <ChatSessionLiveConversation />;
    if (!selected) {
      return (
        <div className={styles.placeholder}>
          <Typography.Text type="secondary">상담을 고르면 대화를 볼 수 있어요</Typography.Text>
        </div>
      );
    }
    return selected.status === 'ACTIVE' ? (
      <ChatSessionLiveConversation
        key={selected.sessionId}
        session={selected}
        onSessionCompleted={handleSessionCompleted}
      />
    ) : (
      <ChatSessionConversation key={selected.sessionId} session={selected} />
    );
  };

  const renderRows = () => {
    if (sessions.length === 0) {
      return (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description={currentTab.empty}
          className={styles.empty}
        >
          {/* 상담 시작은 openChat을 거쳐 이 화면의 대화 칸으로 온다(isNewLive). */}
          {tab === 'ALL' && (
            <Button type="primary" onClick={openChat}>
              AI 상담 시작하기
            </Button>
          )}
        </Empty>
      );
    }
    return (
      <ul className={styles.list}>
        {sessions.map((session) => {
          const isSelected = isSplit && session.sessionId === selected?.sessionId;
          return (
            <li key={session.sessionId}>
              <button
                type="button"
                className={isSelected ? `${styles.row} ${styles.selected}` : styles.row}
                aria-current={isSelected || undefined}
                onClick={() => handleSelect(session.sessionId ?? null)}
              >
                <span className={styles.rowLine}>
                  <Typography.Text strong className={styles.question}>
                    {session.firstQuestion ?? '-'}
                  </Typography.Text>
                  {session.status && <ChatSessionStatusTag status={session.status} />}
                </span>
                <Typography.Text type="secondary" className={styles.meta}>
                  {formatChatDateTime(session.startedAt)} · 메시지 {session.messageCount ?? 0}개
                </Typography.Text>
                <span className={styles.rowLine}>
                  <Typography.Text type="secondary" className={styles.preview}>
                    {session.lastMessagePreview ?? ''}
                  </Typography.Text>
                  <Typography.Text type="secondary" className={styles.time}>
                    {formatChatTime(session.lastMessageAt)}
                  </Typography.Text>
                </span>
              </button>
            </li>
          );
        })}
      </ul>
    );
  };

  const renderListPanel = () => (
    <div className={styles.listPanel}>
      <Segmented<ChatTab>
        block
        value={tab}
        onChange={handleChangeTab}
        options={TABS.map((item) => ({
          value: item.value,
          label: (
            <span className={styles.tabLabel}>
              {item.label} {counts?.[item.count] ?? 0}
            </span>
          ),
        }))}
        className={styles.tabs}
      />
      <div className={styles.scroller}>
        <SectionLoading loading={isPlaceholderData}>{renderRows()}</SectionLoading>
        <div ref={setListEnd} className={styles.listEnd}>
          {isFetchingNextPage && <Spin size="small" />}
        </div>
      </div>
    </div>
  );

  const renderContent = () => {
    if (isPending) return <Skeleton active paragraph={{ rows: 6 }} />;
    if (isError) {
      return (
        <Result
          status="error"
          title="상담 내역을 불러오지 못했습니다."
          extra={<Button onClick={() => refetch()}>다시 시도</Button>}
        />
      );
    }
    if (isSplit) {
      return (
        <div className={styles.split}>
          {renderListPanel()}
          {renderConversation()}
        </div>
      );
    }
    if (selected || isNewLive) {
      return (
        <div className={styles.detail}>
          <Button
            type="text"
            icon={<ArrowLeftOutlined />}
            className={styles.back}
            onClick={() => handleSelect(null)}
          >
            목록
          </Button>
          {renderConversation()}
        </div>
      );
    }
    return renderListPanel();
  };

  return (
    <SectionCard title="AI 상담 내역" bracket={false} titleVariant="page">
      {renderContent()}
    </SectionCard>
  );
}
