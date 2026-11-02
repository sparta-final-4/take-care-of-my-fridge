import { create } from 'zustand';

interface ChatModalState {
  isOpen: boolean;
  // 화면 안에서 직접 대화하는 곳(마이페이지 상담 내역)이 떠 있으면 그 화면이 열기 요청을 받는다.
  // 같은 상담이 채팅창과 화면 두 곳에서 열리지 않게 한다.
  inlineHost: (() => void) | null;
  openChat: () => void;
  closeChat: () => void;
  // 등록하면 열려 있던 채팅창을 닫는다. 화면을 떠날 때 null로 푼다.
  setInlineHost: (host: (() => void) | null) => void;
}

export const useChatModalStore = create<ChatModalState>((set, get) => ({
  isOpen: false,
  inlineHost: null,
  openChat: () => {
    const { inlineHost } = get();
    if (inlineHost) {
      inlineHost();
      return;
    }
    set({ isOpen: true });
  },
  closeChat: () => set({ isOpen: false }),
  setInlineHost: (host) => set(host ? { inlineHost: host, isOpen: false } : { inlineHost: null }),
}));
