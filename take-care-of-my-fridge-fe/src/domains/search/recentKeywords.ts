// 상단 검색창의 최근 검색어. 서버에 보내지 않고 이 브라우저에만 남긴다.
const STORAGE_KEY = 'search.recentKeywords';
const MAX_RECENT_KEYWORDS = 10;

const listeners = new Set<() => void>();
let cached: string[] | null = null;

function read(): string[] {
  try {
    const parsed: unknown = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]');
    if (!Array.isArray(parsed)) return [];
    return parsed
      .filter((keyword): keyword is string => typeof keyword === 'string')
      .slice(0, MAX_RECENT_KEYWORDS);
  } catch {
    return [];
  }
}

function write(keywords: string[]) {
  cached = keywords;
  try {
    if (keywords.length > 0) localStorage.setItem(STORAGE_KEY, JSON.stringify(keywords));
    else localStorage.removeItem(STORAGE_KEY);
  } catch {
    // 저장소를 못 써도 지금 화면에서는 목록이 유지된다.
  }
  listeners.forEach((listener) => listener());
}

// useSyncExternalStore가 비교할 수 있도록 바뀌기 전까지 같은 배열을 돌려준다.
export function getRecentKeywords() {
  cached ??= read();
  return cached;
}

export function subscribeRecentKeywords(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

// 같은 검색어는 맨 앞으로 올리고, 오래된 것부터 버린다.
export function addRecentKeyword(value: string) {
  const keyword = value.trim();
  if (!keyword) return;
  const rest = getRecentKeywords().filter((saved) => saved !== keyword);
  write([keyword, ...rest].slice(0, MAX_RECENT_KEYWORDS));
}

export function removeRecentKeyword(keyword: string) {
  write(getRecentKeywords().filter((saved) => saved !== keyword));
}

export function clearRecentKeywords() {
  write([]);
}
