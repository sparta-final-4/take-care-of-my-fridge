import { LeftOutlined, RightOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Button, Empty, Flex, Skeleton, Typography } from 'antd';
import {
  useEffect,
  useRef,
  useState,
  type FocusEvent,
  type MouseEvent,
  type PointerEvent,
  type TransitionEvent,
} from 'react';

import { SectionCard } from '@/common/components/SectionCard';
import { saleQueries } from '@/domains/sale/queries';

import { SaleCard } from './SaleCard';
import styles from './PopularSaleSection.module.scss';

const PAGE_SIZE = 2;
const AUTO_SLIDE_MS = 5000;
// 이만큼 움직여야 끌기로 본다. 그보다 적으면 카드 클릭이다.
const DRAG_START_PX = 6;
// 보이는 영역 폭의 이 비율 넘게 끌고 놓으면 다음·이전 페이지로 넘긴다. 덜 끌면 제자리로 돌아온다.
const DRAG_SWIPE_RATIO = 0.2;

interface DragState {
  pointerId: number;
  startX: number;
  width: number;
  moved: boolean;
}

const prefersReducedMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches;

// 메인 화면 지금 뜨는 할인. 2장씩 보여 주고, 3개 이상이면 5초마다 다음 2장이 밀려 들어온다.
// 화살표나 마우스·손가락으로 끌어서도 넘길 수 있고, 마지막 다음은 되감기지 않고 처음 페이지가 이어서 들어온다.
export function PopularSaleSection() {
  const { data: sales = [], isPending, isError, refetch } = useQuery(saleQueries.popular());
  const pages = Array.from({ length: Math.ceil(sales.length / PAGE_SIZE) }, (_, index) =>
    sales.slice(index * PAGE_SIZE, (index + 1) * PAGE_SIZE),
  );
  const pageCount = pages.length;
  // 첫 페이지 복제본을 끝에 붙인다. 복제본으로 넘어간 뒤 움직임 없이 진짜 첫 페이지로 자리만 바꿔, 계속 같은 방향으로 도는 것처럼 보이게 한다.
  const slides = pageCount > 1 ? [...pages, pages[0]] : pages;

  const [position, setPosition] = useState(0);
  const [animating, setAnimating] = useState(true);
  // 카드 위에 마우스를 올리거나 키보드로 카드에 들어가 있는 동안은 넘기지 않는다.
  const [paused, setPaused] = useState(false);
  const [dragOffset, setDragOffset] = useState(0);
  const [dragging, setDragging] = useState(false);
  const dragRef = useRef<DragState | null>(null);
  // 끌기를 마친 뒤 이어서 오는 클릭으로 상품 상세에 들어가지 않게 한 번 막는다.
  const suppressClickRef = useRef(false);
  // 다시 받은 목록이 짧아져도 빈 페이지에 머물지 않는다.
  const current = Math.min(position, Math.max(slides.length - 1, 0));

  const goNext = () => {
    // 움직임 줄이기 설정이면 넘기는 애니메이션이 없어 복제본을 거치지 않고 바로 처음으로 간다.
    if (prefersReducedMotion()) {
      setPosition((current + 1) % pageCount);
      return;
    }
    setAnimating(true);
    setPosition(current + 1);
  };

  const goPrev = () => {
    if (current > 0) {
      setAnimating(true);
      setPosition(current - 1);
      return;
    }
    if (prefersReducedMotion()) {
      setPosition(pageCount - 1);
      return;
    }
    // 처음에서 뒤로 가면 끝의 복제본으로 움직임 없이 옮긴 뒤, 그 앞 페이지로 밀어 반대 방향으로도 이어지게 한다.
    setAnimating(false);
    setPosition(pageCount);
    requestAnimationFrame(() =>
      requestAnimationFrame(() => {
        setAnimating(true);
        setPosition(pageCount - 1);
      }),
    );
  };

  const handleTransitionEnd = (event: TransitionEvent<HTMLUListElement>) => {
    // 카드의 그림자 전환 같은 안쪽 요소의 전환은 무시한다.
    if (event.target !== event.currentTarget || current !== pageCount) return;
    setAnimating(false);
    setPosition(0);
  };

  const handlePointerDown = (event: PointerEvent<HTMLDivElement>) => {
    if (pageCount < 2 || event.button !== 0) return;
    dragRef.current = {
      pointerId: event.pointerId,
      startX: event.clientX,
      width: event.currentTarget.clientWidth,
      moved: false,
    };
  };

  const handlePointerMove = (event: PointerEvent<HTMLDivElement>) => {
    const drag = dragRef.current;
    if (!drag) return;
    const offset = event.clientX - drag.startX;
    if (!drag.moved) {
      if (Math.abs(offset) < DRAG_START_PX) return;
      // 끌기로 판단한 뒤에만 포인터를 붙잡는다. 처음부터 붙잡으면 카드 클릭이 링크에 닿지 않는다.
      drag.moved = true;
      event.currentTarget.setPointerCapture(drag.pointerId);
      setDragging(true);
      setAnimating(false);
    }
    // 처음 페이지에서 오른쪽으로 끌면 왼쪽에 마지막 페이지가 보이도록 같은 모양의 복제본 자리로 옮겨 둔다.
    if (offset > 0 && current === 0) setPosition(pageCount);
    if (offset < 0 && current === pageCount) setPosition(0);
    setDragOffset(offset);
  };

  const endDrag = (event: PointerEvent<HTMLDivElement>, swipe: boolean) => {
    const drag = dragRef.current;
    dragRef.current = null;
    if (!drag?.moved) return;
    suppressClickRef.current = true;
    const offset = event.clientX - drag.startX;
    const threshold = drag.width * DRAG_SWIPE_RATIO;
    setDragging(false);
    setDragOffset(0);
    setAnimating(true);
    // 움직임 줄이기 설정이면 복제본에서 처음으로 돌려놓는 전환 끝 알림이 오지 않아 바로 진짜 페이지로 간다.
    const wrap = (target: number) => (prefersReducedMotion() ? target % pageCount : target);
    if (swipe && offset < -threshold) {
      setPosition(wrap(current + 1));
      return;
    }
    if (swipe && offset > threshold) {
      setPosition(wrap(current - 1));
    }
  };

  const handleClickCapture = (event: MouseEvent<HTMLDivElement>) => {
    if (!suppressClickRef.current) return;
    suppressClickRef.current = false;
    event.preventDefault();
    event.stopPropagation();
  };

  // 페이지가 바뀌면(화살표·끌기 포함) 타이머를 다시 시작해, 넘긴 직후 바로 또 넘어가지 않게 한다.
  useEffect(() => {
    if (paused || dragging || pageCount < 2) return;
    const timer = setTimeout(goNext, AUTO_SLIDE_MS);
    return () => clearTimeout(timer);
  });

  const handleBlur = (event: FocusEvent<HTMLDivElement>) => {
    if (!event.currentTarget.contains(event.relatedTarget)) setPaused(false);
  };

  const arrows = pageCount > 1 && (
    <Flex gap="small">
      <Button
        size="small"
        icon={<LeftOutlined />}
        onClick={goPrev}
        aria-label="이전 할인 상품"
        className={styles.arrow}
      />
      <Button
        size="small"
        icon={<RightOutlined />}
        onClick={goNext}
        aria-label="다음 할인 상품"
        className={styles.arrow}
      />
    </Flex>
  );

  return (
    <SectionCard title="지금 뜨는 할인" extra={arrows} bracket={false}>
      {isPending && <Skeleton active paragraph={{ rows: 4 }} />}

      {isError && (
        <Flex vertical align="center" gap="small" className={styles.state}>
          <Typography.Text type="secondary">할인 상품 불러오기 실패</Typography.Text>
          <Button size="small" onClick={() => void refetch()}>
            다시 시도
          </Button>
        </Flex>
      )}

      {!isPending && !isError && sales.length === 0 && (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="진행 중인 할인 없음"
          className={styles.state}
        />
      )}

      {sales.length > 0 && (
        <div
          className={styles.viewport}
          onMouseEnter={() => setPaused(true)}
          onMouseLeave={() => setPaused(false)}
          onFocus={() => setPaused(true)}
          onBlur={handleBlur}
          onPointerDown={handlePointerDown}
          onPointerMove={handlePointerMove}
          onPointerUp={(event) => endDrag(event, true)}
          onPointerCancel={(event) => endDrag(event, false)}
          onClickCapture={handleClickCapture}
          // 링크·이미지를 끌면 브라우저 기본 끌어 놓기가 시작돼 넘기기가 멈추므로 막는다.
          onDragStart={(event) => event.preventDefault()}
        >
          <ul
            className={animating ? styles.track : `${styles.track} ${styles.instant}`}
            style={{ transform: `translateX(calc(${-current * 100}% + ${dragOffset}px))` }}
            onTransitionEnd={handleTransitionEnd}
            aria-label="지금 뜨는 할인"
          >
            {slides.map((items, index) => (
              // 화면 밖 페이지(복제본 포함)는 키보드 포커스와 스크린 리더에서 뺀다.
              <li key={index} className={styles.page} inert={index !== current}>
                {items.map((sale) => (
                  <SaleCard key={sale.productId} sale={sale} />
                ))}
              </li>
            ))}
          </ul>
        </div>
      )}
    </SectionCard>
  );
}
