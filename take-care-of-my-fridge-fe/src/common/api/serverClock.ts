// 서버 시각을 모든 화면이 같이 쓴다. 기기 시계가 달라도 카운트다운이 같게 보이도록
// 응답의 Date 헤더로 "서버 시각 − 기기 시각" 차이를 기억해 둔다. 헤더가 초 단위라 1초 안팎의 오차는 있다.
let offset = 0;

export function syncServerClock(dateHeader: unknown, receivedAt = Date.now()) {
  if (typeof dateHeader !== 'string') return;
  const serverTime = Date.parse(dateHeader);
  if (Number.isNaN(serverTime)) return;
  offset = serverTime - receivedAt;
}

export function serverNow() {
  return Date.now() + offset;
}
