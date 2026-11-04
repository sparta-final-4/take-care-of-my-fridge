import { create } from 'zustand';

// AI 상담에 넘기는 선택 재료. 내 냉장고에서 체크한 재료 중 추천에 쓸 수 있는 것만 담는다(toChatSelectedIngredients).
export interface ChatSelectedIngredient {
  ingredientId: number;
  name: string;
}

interface ChatModalState {
  isOpen: boolean;
  // 화면 안에서 직접 대화하는 곳(마이페이지 상담 내역)이 떠 있으면 그 화면이 열기 요청을 받는다.
  // 같은 상담이 채팅창과 화면 두 곳에서 열리지 않게 한다.
  inlineHost: (() => void) | null;
  // 이번 상담에 넘긴 선택 재료. 채팅창·마이페이지 오른쪽 칸이 같이 읽는다.
  selectedIngredients: ChatSelectedIngredient[];
  // 선택 재료를 주면 그 재료로, 주지 않으면 선택 없이 연다(이전 선택은 비운다).
  openChat: (selected?: ChatSelectedIngredient[]) => void;
  closeChat: () => void;
  removeSelectedIngredient: (ingredientId: number) => void;
  clearSelectedIngredients: () => void;
  // 등록하면 열려 있던 채팅창을 닫는다. 화면을 떠날 때 null로 푼다.
  setInlineHost: (host: (() => void) | null) => void;
}

export const useChatModalStore = create<ChatModalState>((set, get) => ({
  isOpen: false,
  inlineHost: null,
  selectedIngredients: [],
  openChat: (selected) => {
    // 화면 안 대화로 넘길 때도 선택이 유지되도록 먼저 담는다.
    set({ selectedIngredients: selected ?? [] });
    const { inlineHost } = get();
    if (inlineHost) {
      inlineHost();
      return;
    }
    set({ isOpen: true });
  },
  closeChat: () => set({ isOpen: false }),
  removeSelectedIngredient: (ingredientId) =>
    set((state) => ({
      selectedIngredients: state.selectedIngredients.filter(
        (ingredient) => ingredient.ingredientId !== ingredientId,
      ),
    })),
  clearSelectedIngredients: () => set({ selectedIngredients: [] }),
  setInlineHost: (host) => set(host ? { inlineHost: host, isOpen: false } : { inlineHost: null }),
}));
