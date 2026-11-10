import Lenis from 'lenis';
import Snap from 'lenis/snap';

// 마우스 휠·트랙패드 스크롤에 관성을 붙여 부드럽게 미끄러지게 한다(Lenis). 화면(window) 스크롤 하나만 맡는다.
// 터치 스크롤은 원래대로 두고(Lenis 기본값), 움직임 줄이기 설정이면 Lenis가 스스로 끈다.
// 모달·서랍·드롭다운 위의 휠은 그 안에서 원래대로 스크롤되게 넘긴다(뒤 화면이 같이 움직이지 않게).
const NATIVE_SCROLL_SELECTOR = [
  '.ant-modal-wrap',
  '.ant-drawer',
  '.ant-select-dropdown',
  '.ant-dropdown',
  '.ant-popover',
  '.ant-picker-dropdown',
  '.ant-image-preview-wrap',
].join(', ');

// 프레임마다 목표 위치로 다가가는 비율. 작을수록 휠을 멈춘 뒤에도 더 오래 미끄러진다(Lenis 기본 0.1).
const SCROLL_LERP = 0.06;

let lenis: Lenis | null = null;

// 앱을 시작할 때 한 번 켠다(main.tsx). 화면이 그려지기 전에 켜 두어야 각 화면이 띠 스냅을 붙일 수 있다.
export function startSmoothScroll() {
  if (lenis) return;
  lenis = new Lenis({
    autoRaf: true,
    lerp: SCROLL_LERP,
    // 필터 칸처럼 안에서 따로 스크롤되는 칸은 그 칸이 먼저 스크롤된다.
    allowNestedScroll: true,
    // 링크를 누르면 남은 관성을 멈춰, 다음 화면이 미끄러지며 시작하지 않게 한다.
    stopInertiaOnNavigate: true,
    prevent: (node) => node.matches(NATIVE_SCROLL_SELECTOR),
  });
}

// 서랍처럼 화면 위에 떠서 따로 스크롤되는 칸에 화면과 같은 부드러운 스크롤을 붙인다. 반환한 함수로 해제한다.
// content는 wrapper의 바로 아래 자식이어야 한다.
export function addSmoothScroll(wrapper: HTMLElement, content: HTMLElement): () => void {
  const panel = new Lenis({
    wrapper,
    content,
    autoRaf: true,
    lerp: SCROLL_LERP,
    allowNestedScroll: true,
  });
  return () => panel.destroy();
}

// 띠 스냅: 휠을 멈춘 곳이 띠 시작에서 화면 높이의 이 비율 안이면 그 띠 시작으로 살짝 당겨 맞춘다.
// 띠 중간을 읽는 중에는 당기지 않는다.
const SECTION_SNAP_THRESHOLD = '20%';
// 마우스로 스크롤하는 PC에서만 당긴다(휴대폰 터치 스크롤은 그대로). 움직임 줄이기 설정이면 당기지 않는다.
const SECTION_SNAP_MEDIA =
  '(hover: hover) and (pointer: fine) and (prefers-reduced-motion: no-preference)';

// 넘긴 띠들의 시작(위쪽 끝)에 약하게 붙게 한다. 반환한 함수로 해제한다.
export function addSectionSnap(elements: HTMLElement[]): () => void {
  if (!lenis || elements.length === 0 || !window.matchMedia(SECTION_SNAP_MEDIA).matches) {
    return () => {};
  }
  const snap = new Snap(lenis, { type: 'proximity', distanceThreshold: SECTION_SNAP_THRESHOLD });
  snap.addElements(elements, { align: 'start' });
  return () => snap.destroy();
}

// 화면 맨 위로 바로 이동한다. Lenis가 움직이는 중이어도 남은 관성을 버리고 맨 위에서 멈춘다.
export function scrollToTopInstantly() {
  if (lenis) lenis.scrollTo(0, { immediate: true, force: true });
  else window.scrollTo({ top: 0 });
}

// 요소 시작으로 부드럽게 이동한다(메인 화면 띠 위치 점).
export function scrollToElement(element: HTMLElement) {
  if (lenis) lenis.scrollTo(element);
  else element.scrollIntoView({ behavior: 'smooth' });
}
