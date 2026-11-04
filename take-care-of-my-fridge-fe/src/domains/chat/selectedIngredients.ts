import type { FridgeItemResponse } from '@/common/api/clients';

import type { ChatSelectedIngredient } from './model/useChatModalStore';

// 내 냉장고에서 체크한 줄을 AI 상담 선택 재료로 바꾼다.
// 직접 입력(기타)·밀키트·기본 양념은 레시피 재료로 찾을 수 없어 빼고, 같은 재료는 한 번만 넣는다.
export function toChatSelectedIngredients(items: FridgeItemResponse[]): ChatSelectedIngredient[] {
  const selected = new Map<number, ChatSelectedIngredient>();
  for (const item of items) {
    if (
      item.ingredientId === undefined ||
      item.ingredientType !== 'NORMAL' ||
      item.basicSeasoning
    ) {
      continue;
    }
    if (!selected.has(item.ingredientId)) {
      selected.set(item.ingredientId, {
        ingredientId: item.ingredientId,
        name: item.ingredientName ?? item.rawName ?? '',
      });
    }
  }
  return [...selected.values()];
}

// [이 재료로 추천받기] 칩이 보내는 문장. 예: "배추, 버섯으로 추천해줘"
export function selectedRecommendMessage(selected: ChatSelectedIngredient[]): string {
  const names = selected.map((ingredient) => ingredient.name).join(', ');
  return `${names}${withDirectionParticle(selected[selected.length - 1]?.name ?? '')} 추천해줘`;
}

// 마지막 글자에 받침이 없거나 ㄹ 받침이면 '로', 다른 받침이면 '으로', 한글이 아니면 '(으)로'
function withDirectionParticle(name: string): string {
  const code = name.charCodeAt(name.length - 1) - 0xac00;
  if (!(code >= 0 && code <= 11171)) return '(으)로';
  const finalConsonant = code % 28;
  return finalConsonant === 0 || finalConsonant === 8 ? '로' : '으로';
}
