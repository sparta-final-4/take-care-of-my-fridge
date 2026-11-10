import { withDirectionParticle } from '@/domains/chat/selectedIngredients';

import type { FridgeItemResponse } from './api';
import { formatDDayLabel } from './format';

// 문장에 이름을 이만큼까지 쓰고 나머지는 "외 N개"로 줄인다.
const NAME_LIMIT = 3;

// AI 상담 카드의 한 문장. 체크한 재료 → 임박 재료 → 냉장고 전체 순으로 고른다.
const SUFFIX = ' 만들 요리를 추천해 드려요.';
export const DEFAULT_CONSULT_TEXT = '냉장고 재료로 만들 요리를 추천해 드려요.';

// "배추, 대파" / "배추, 대파, 무 외 2개"
function joinNames(names: string[]) {
  const shown = names.slice(0, NAME_LIMIT).join(', ');
  return names.length > NAME_LIMIT ? `${shown} 외 ${names.length - NAME_LIMIT}개` : shown;
}

// 체크한 재료: "배추, 대파로 만들 요리를 추천해 드려요."
export function selectedConsultText(names: string[]) {
  if (names.length === 0) return DEFAULT_CONSULT_TEXT;
  const joined = joinNames(names);
  return `${joined}${withDirectionParticle(joined)}${SUFFIX}`;
}

// 임박 재료: 가장 급한 재료 하나를 앞세운다. "오늘까지인 서울우유 외 3개로 만들 요리를 추천해 드려요."
export function imminentConsultText(items: Pick<FridgeItemResponse, 'rawName' | 'dDay'>[]) {
  if (items.length === 0) return DEFAULT_CONSULT_TEXT;
  const [first] = [...items].sort((a, b) => (a.dDay ?? 0) - (b.dDay ?? 0));
  const dDay = first.dDay ?? 0;
  // 오늘·내일·모레는 "오늘까지인", 그 뒤는 "3일 남은"
  const when = dDay <= 2 ? `${formatDDayLabel(dDay)}인` : `${dDay}일 남은`;
  const subject =
    items.length > 1 ? `${first.rawName ?? ''} 외 ${items.length - 1}개` : (first.rawName ?? '');
  return `${when} ${subject}${withDirectionParticle(subject)}${SUFFIX}`;
}
