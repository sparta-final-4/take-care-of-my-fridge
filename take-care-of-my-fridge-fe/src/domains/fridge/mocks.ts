export interface QuickIngredientChip {
  id: string;
  name: string;
}

// 빠른 선택 추천 API가 아직 없어 고정 목록을 쓴다.
export const MOCK_QUICK_CHIPS: QuickIngredientChip[] = [
  { id: 'chip-1', name: '참치캔' },
  { id: 'chip-2', name: '두부' },
  { id: 'chip-3', name: '계란' },
  { id: 'chip-4', name: '대파' },
];
