import { useQueryClient } from '@tanstack/react-query';
import { App } from 'antd';
import { useCallback, useEffect, useRef, useState } from 'react';

import { useChatModalStore } from '../model/useChatModalStore';
import { removeEndedActiveSession } from '../queries';
import { ChatRoomSection } from './ChatRoomSection';
import styles from './ChatModal.module.scss';

export function ChatModal() {
  const { modal } = App.useApp();
  const isOpen = useChatModalStore((state) => state.isOpen);
  const closeChat = useChatModalStore((state) => state.closeChat);
  const queryClient = useQueryClient();

  const [isStreaming, setIsStreaming] = useState(false);
  const abortStreamRef = useRef<(() => void) | null>(null);

  const handleRegisterAbort = useCallback((abortFn: () => void) => {
    abortStreamRef.current = abortFn;
  }, []);

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

  // ESC 키로 창 닫기 지원
  useEffect(() => {
    if (!isOpen) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        handleRequestClose();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, handleRequestClose]);

  // 닫히면(✕·ESC·상담 내역으로 이동 등 닫는 경로 모두 isOpen을 거친다) 끝난 상담 캐시를 비워
  // 다시 열 때 언제 열든 새 상담이 열리게 한다. 닫힐 때 대화 칸이 먼저 사라지므로 이 시점엔 보는 곳이 없다.
  useEffect(() => {
    if (!isOpen) removeEndedActiveSession(queryClient);
  }, [isOpen, queryClient]);

  if (!isOpen) {
    return null;
  }

  return (
    <div
      role="dialog"
      aria-modal="false"
      aria-label="AI 냉장고 상담"
      className={styles.floatingWindow}
    >
      <ChatRoomSection
        onStreamingChange={setIsStreaming}
        onRegisterAbort={handleRegisterAbort}
        onCloseModal={handleRequestClose}
      />
    </div>
  );
}
