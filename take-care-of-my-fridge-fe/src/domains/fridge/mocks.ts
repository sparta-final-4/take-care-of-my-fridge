export interface QuickIngredientChip {
  id: string;
  name: string;
}

// 빠른 선택 추천 API가 아직 없어 고정 목록을 쓴다.
// 표준 재료 사전(ingredients.name)에 있는 이름만 넣어야 기타(ING_CUSTOM)로 빠지지 않는다.
export const MOCK_QUICK_CHIPS: QuickIngredientChip[] = [
  { id: 'chip-1', name: '양파' },
  { id: 'chip-2', name: '마늘' },
  { id: 'chip-3', name: '달걀' },
  { id: 'chip-4', name: '대파' },
];
