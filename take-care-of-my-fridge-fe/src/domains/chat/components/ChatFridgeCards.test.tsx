import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';

import type { ChatFridgeItem } from '../recommendations';
import { ChatFridgeCards } from './ChatFridgeCards';

function item(overrides: Partial<ChatFridgeItem> = {}): ChatFridgeItem {
  return {
    fridgeId: 1,
    ingredientId: 11,
    name: '두부',
    rawName: '두부',
    quantity: 1,
    unit: '모',
    dDay: 1,
    status: 'IMMINENT',
    mealkit: false,
    productId: null,
    imageUrl: null,
    ...overrides,
  };
}

function render(items: ChatFridgeItem[]) {
  return renderToStaticMarkup(
    <MemoryRouter>
      <ChatFridgeCards items={items} />
    </MemoryRouter>,
  );
}

describe('ChatFridgeCards', () => {
  it('"냉장고에 있어요" 아래 이름·수량을 보여 주고, D-3 이내면 남은 날 태그를 붙인다', () => {
    const html = render([
      item(),
      item({ fridgeId: 2, name: '양파', quantity: 3, unit: '개', dDay: 10 }),
    ]);

    expect(html).toContain('냉장고에 있어요');
    expect(html).toContain('두부');
    expect(html).toContain('1모');
    expect(html).toContain('내일까지');
    expect(html).toContain('양파');
    expect(html).not.toContain('10일 남음');
  });

  it('소비기한이 지났으면 "지났어요" 태그를 붙인다', () => {
    expect(render([item({ dDay: -2, status: 'EXPIRED' })])).toContain('지났어요');
  });

  it('밀키트 줄은 상품 이미지와 상품 상세 링크를 붙이고, 일반 재료는 링크가 없다', () => {
    const html = render([
      item({
        fridgeId: 3,
        name: '된장국 밀키트',
        mealkit: true,
        productId: 24,
        imageUrl: 'img.png',
        dDay: 5,
      }),
      item(),
    ]);

    expect(html).toContain('href="/products/24"');
    expect(html).toContain('aria-label="된장국 밀키트 상품 보기"');
    expect(html.match(/href="\/products\//g)).toHaveLength(1);
  });

  it('카드가 없으면 아무것도 그리지 않는다', () => {
    expect(render([])).toBe('');
  });
});
