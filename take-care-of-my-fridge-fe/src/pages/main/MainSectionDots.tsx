import { type RefObject, useEffect, useState } from 'react';

import { scrollToElement } from '@/common/scroll/smoothScroll';

import styles from './MainSectionDots.module.scss';

interface MainSectionDotsProps {
  // 메인 화면 띠들을 바로 아래 자식으로 가진 요소
  pageRef: RefObject<HTMLDivElement | null>;
}

interface Band {
  element: HTMLElement;
  label: string;
}

// 띠 이름: 띠의 aria-label, 없으면 띠 안 첫 제목(줄바꿈은 띄어쓰기로)
function bandLabel(element: HTMLElement): string {
  const heading = element.querySelector<HTMLElement>('h1, h2, h3');
  return element.getAttribute('aria-label') ?? heading?.innerText.replace(/\s+/g, ' ').trim() ?? '';
}

// 어두운 바탕 띠인지(첫 화면 사진·AI 상담). 바탕색이 있고 어두우면 점을 밝게 그린다.
function isDarkBand(element: HTMLElement): boolean {
  const [r = 255, g = 255, b = 255, a = 1] =
    getComputedStyle(element)
      .backgroundColor.match(/[\d.]+/g)
      ?.map(Number) ?? [];
  return a > 0 && (0.299 * r + 0.587 * g + 0.114 * b) / 255 < 0.5;
}

// 메인 화면 오른쪽 끝, 세로 가운데의 점. 띠마다 점 하나, 지금 보고 있는 띠의 점이 진해진다.
// 점 색은 지금 띠의 바탕에 맞춘다(어두운 띠는 종이색, 밝은 띠는 잉크색).
// 점을 누르면 그 띠 시작으로 이동한다. 넓은 화면(lg 이상)에서만 보인다.
export function MainSectionDots({ pageRef }: MainSectionDotsProps) {
  const [bands, setBands] = useState<Band[]>([]);
  const [activeIndex, setActiveIndex] = useState(0);
  const [onDark, setOnDark] = useState(true);

  useEffect(() => {
    const page = pageRef.current;
    if (!page) return;

    // 보이는 띠 목록(밀키트가 없어 숨은 띠처럼 높이가 0인 것은 뺀다)과, 화면 세로 가운데를 지난 마지막 띠
    const update = () => {
      const elements = Array.from(page.children).filter(
        (child): child is HTMLElement => child instanceof HTMLElement && child.offsetHeight > 0,
      );
      setBands((prev) =>
        prev.length === elements.length && prev.every((band, i) => band.element === elements[i])
          ? prev
          : elements.map((element) => ({ element, label: bandLabel(element) })),
      );
      const middle = window.innerHeight / 2;
      const index = elements.findLastIndex(
        (element) => element.getBoundingClientRect().top <= middle,
      );
      setActiveIndex(Math.max(index, 0));
      const active = elements[Math.max(index, 0)];
      setOnDark(active ? isDarkBand(active) : false);
    };

    update();
    // 불러온 데이터로 띠가 생기거나 높이가 바뀌면 다시 센다.
    const resizeObserver = new ResizeObserver(update);
    resizeObserver.observe(page);
    window.addEventListener('scroll', update, { passive: true });
    window.addEventListener('resize', update);
    return () => {
      resizeObserver.disconnect();
      window.removeEventListener('scroll', update);
      window.removeEventListener('resize', update);
    };
  }, [pageRef]);

  if (bands.length < 2) return null;

  return (
    <nav
      className={onDark ? `${styles.dots} ${styles.onDark}` : styles.dots}
      aria-label="메인 화면 위치"
    >
      {bands.map((band, index) => (
        <button
          key={band.label || index}
          type="button"
          className={index === activeIndex ? `${styles.dot} ${styles.active}` : styles.dot}
          aria-label={band.label}
          aria-current={index === activeIndex ? 'location' : undefined}
          onClick={() => scrollToElement(band.element)}
        />
      ))}
    </nav>
  );
}
