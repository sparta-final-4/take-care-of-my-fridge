import { create } from 'zustand';

// 내 냉장고 목록에서 체크한 재료 id. 목록·아래 선택 바·오른쪽 AI 상담 카드가 같이 읽는다.
// 재료 데이터는 서버 목록(useFridgeItems)에서 id로 찾아 쓰고 여기에는 id만 둔다.
interface FridgeSelectionState {
  checkedIds: number[];
  setChecked: (fridgeId: number, checked: boolean) => void;
  // 냉장고에서 빠진 재료의 체크를 푼다.
  uncheck: (fridgeIds: number[]) => void;
  clear: () => void;
}

export const useFridgeSelectionStore = create<FridgeSelectionState>((set) => ({
  checkedIds: [],
  setChecked: (fridgeId, checked) =>
    set((state) => {
      const others = state.checkedIds.filter((id) => id !== fridgeId);
      return { checkedIds: checked ? [...others, fridgeId] : others };
    }),
  uncheck: (fridgeIds) =>
    set((state) => ({ checkedIds: state.checkedIds.filter((id) => !fridgeIds.includes(id)) })),
  clear: () => set({ checkedIds: [] }),
}));
