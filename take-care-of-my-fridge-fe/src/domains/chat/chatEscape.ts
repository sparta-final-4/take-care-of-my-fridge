// 상담 서랍 ESC 닫기. 포커스가 뒤 화면에 있어도 닫히게 document에서 받는다(서랍은 덮지 않아 포커스가 밖에 있을 수 있다).

// 상담 서랍 위에 열린 것: 다른 서랍(장바구니 등), 모달·확인 창, 드롭다운·선택 목록·날짜 선택·말풍선 확인.
// 이것들이 열려 있으면 ESC는 그것만 닫는다.
const OVERLAY_SELECTORS = [
  '.ant-drawer-open',
  '.ant-modal-wrap',
  '.ant-dropdown:not(.ant-dropdown-hidden)',
  '.ant-select-dropdown:not(.ant-select-dropdown-hidden)',
  '.ant-picker-dropdown:not(.ant-picker-dropdown-hidden)',
  '.ant-popover:not(.ant-popover-hidden)',
].join(',');

function isVisible(element: Element) {
  return element.getClientRects().length > 0;
}

// 상담 서랍(chatRoot) 말고 위에 열린 것이 있는지
export function hasOverlayAboveChat(chatRoot: Element | null): boolean {
  return [...document.querySelectorAll(OVERLAY_SELECTORS)].some(
    (element) => element !== chatRoot && !chatRoot?.contains(element) && isVisible(element),
  );
}

interface EscapeState {
  key: string;
  // 한글 등 조합 중이면 ESC는 조합 취소만 한다(조합 중 keydown은 keyCode 229로 오기도 한다).
  isComposing: boolean;
  keyCode: number;
  overlayOpen: boolean;
}

export function shouldCloseChatOnEscape({ key, isComposing, keyCode, overlayOpen }: EscapeState) {
  if (key !== 'Escape') return false;
  if (isComposing || keyCode === 229) return false;
  return !overlayOpen;
}
