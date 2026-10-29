import { App } from 'antd';
import { useCallback, useEffect, useRef, useState } from 'react';

import { useChatModalStore } from '../model/useChatModalStore';
import { ChatRoomSection } from './ChatRoomSection';
import styles from './ChatModal.module.scss';

export function ChatModal() {
  const { modal } = App.useApp();
  const isOpen = useChatModalStore((state) => state.isOpen);
  const closeChat = useChatModalStore((state) => state.closeChat);

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
