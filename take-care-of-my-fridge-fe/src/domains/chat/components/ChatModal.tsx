import { useQueryClient } from '@tanstack/react-query';
import { App, Drawer } from 'antd';
import { useCallback, useEffect, useRef, useState } from 'react';

import { useChatModalStore } from '../model/useChatModalStore';
import { removeEndedActiveSession } from '../queries';
import { ChatRoomSection } from './ChatRoomSection';
import styles from './ChatModal.module.scss';

// 장바구니 서랍(antd 기본 1000)보다 낮게 둔다. 상담 창이 열린 채로 장바구니를 열면 장바구니가 위로 덮고,
// 장바구니를 닫으면 상담 창이 그대로 보인다.
const CHAT_DRAWER_Z_INDEX = 990;

// 폭은 CSS 변수로 정한다(ChatModal.module.scss: 휴대폰 전체 화면, md 이상 $chat-drawer-width).
const CHAT_DRAWER_SIZE = 'var(--chat-drawer-width)';

// AI 상담 창: 장바구니와 같은 오른쪽 서랍. 뒤 화면을 덮지 않아 상담하면서 다른 화면을 계속 쓸 수 있다.
export function ChatModal() {
  const { modal } = App.useApp();
  const isOpen = useChatModalStore((state) => state.isOpen);
  const closeChat = useChatModalStore((state) => state.closeChat);
  const queryClient = useQueryClient();

  const [isStreaming, setIsStreaming] = useState(false);
  const abortStreamRef = useRef<(() => void) | null>(null);
  // 서랍 머리 오른쪽에서 대화 칸이 [상담 끝내기]를 그릴 자리
  const [headerActionsSlot, setHeaderActionsSlot] = useState<HTMLElement | null>(null);

  const handleRegisterAbort = useCallback((abortFn: () => void) => {
    abortStreamRef.current = abortFn;
  }, []);

  // ✕·ESC(서랍이 받는다)·대화 칸의 닫기 모두 여기를 거친다. 답을 받는 중이면 확인 후 닫는다.
  const handleRequestClose = useCallback(() => {
    if (isStreaming) {
      modal.confirm({
        title: '답을 받는 중이에요',
        content: '닫으면 이 답은 저장되지 않아요',
        okText: '닫기',
        cancelText: '계속 대화하기',
        onOk: () => {
          abortStreamRef.current?.();
          closeChat();
        },
      });
      return;
    }
    closeChat();
  }, [isStreaming, modal, closeChat]);

  // 닫히면(✕·ESC·상담 내역으로 이동 등 닫는 경로 모두 isOpen을 거친다) 끝난 상담 캐시를 비워
  // 다시 열 때 언제 열든 새 상담이 열리게 한다. 닫힐 때 대화 칸이 먼저 사라지므로 이 시점엔 보는 곳이 없다.
  useEffect(() => {
    if (!isOpen) removeEndedActiveSession(queryClient);
  }, [isOpen, queryClient]);

  return (
    <Drawer
      title="AI 냉장고 상담"
      placement="right"
      size={CHAT_DRAWER_SIZE}
      open={isOpen}
      onClose={handleRequestClose}
      mask={false}
      // 덮지 않으므로 포커스를 서랍 안에 가두지 않는다(뒤 화면을 키보드로도 쓸 수 있게).
      focusable={{ trap: false }}
      zIndex={CHAT_DRAWER_Z_INDEX}
      destroyOnHidden
      extra={<div ref={setHeaderActionsSlot} className={styles.headerActions} />}
      rootClassName={styles.root}
      classNames={{
        header: styles.header,
        title: styles.title,
        section: styles.section,
        body: styles.body,
      }}
    >
      <ChatRoomSection
        onStreamingChange={setIsStreaming}
        onRegisterAbort={handleRegisterAbort}
        onCloseModal={handleRequestClose}
        headerActionsSlot={headerActionsSlot}
      />
    </Drawer>
  );
}
