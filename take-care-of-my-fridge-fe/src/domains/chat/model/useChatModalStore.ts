import { create } from 'zustand';

interface ChatModalState {
  isOpen: boolean;
  openChat: () => void;
  closeChat: () => void;
}

export const useChatModalStore = create<ChatModalState>((set) => ({
  isOpen: false,
  openChat: () => set({ isOpen: true }),
  closeChat: () => set({ isOpen: false }),
}));
