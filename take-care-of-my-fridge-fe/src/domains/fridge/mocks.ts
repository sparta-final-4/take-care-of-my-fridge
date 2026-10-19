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

// TODO: GET /api/fridge/logs가 생기면 백엔드 DTO 기준으로 api.ts에 타입을 옮기고 아래 가짜 데이터를 지운다.
export interface MockFridgeLog {
  id: number;
  rawName: string;
  action: 'CONSUMED' | 'DISCARDED';
  createdAt: string;
}

export interface MockFridgeLogSummary {
  recentLogs: MockFridgeLog[];
  monthlyDiscardedCount: number;
}

export const MOCK_FRIDGE_LOG_SUMMARY: MockFridgeLogSummary = {
  recentLogs: [
    { id: 1, rawName: '두부', action: 'CONSUMED', createdAt: '2026-10-12T19:20:00' },
    { id: 2, rawName: '애호박', action: 'DISCARDED', createdAt: '2026-10-08T09:10:00' },
    { id: 3, rawName: '우유', action: 'CONSUMED', createdAt: '2026-10-07T08:00:00' },
    { id: 4, rawName: '시금치', action: 'DISCARDED', createdAt: '2026-10-02T21:30:00' },
  ],
  monthlyDiscardedCount: 2,
};
