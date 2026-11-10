// 상담 서랍 [크게 보기] 여부를 이 브라우저에 기억한다. 저장소를 못 쓰는 환경(사생활 보호 창 등)이면 기본 크기로 둔다.
const STORAGE_KEY = 'chat-drawer-wide';

export function readChatDrawerWide(): boolean {
  try {
    return localStorage.getItem(STORAGE_KEY) === 'true';
  } catch {
    return false;
  }
}

export function saveChatDrawerWide(wide: boolean) {
  try {
    if (wide) localStorage.setItem(STORAGE_KEY, 'true');
    else localStorage.removeItem(STORAGE_KEY);
  } catch {
    // 기억하지 못해도 지금 화면에서는 바뀐 크기로 보인다.
  }
}
