import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';

import type { ChatRecommendedMealkit, ChatShoppingProduct } from '../recommendations';
import { ChatMealkitCards } from './ChatMealkitCards';

function mealkit(
  overrides: Partial<ChatRecommendedMealkit> = {},
  product: Partial<ChatShoppingProduct> = {},
): ChatRecommendedMealkit {
  return {
    product: {
      productId: 41,
      name: '황태해장국',
      imageUrl: null,
      price: 12900,
      salePrice: null,
      saleType: null,
      soldOut: false,
      unitAmount: null,
      unit: null,
      ...product,
    },
    servings: 2,
    recipeId: 543,
    recipeName: '황태해장국',
    description: null,
    inFridge: false,
    fridgeDDay: null,
    ...overrides,
  };
}

function render(mealkits: ChatRecommendedMealkit[], pendingProductId?: number) {
  return renderToStaticMarkup(
    <MemoryRouter>
      <ChatMealkitCards mealkits={mealkits} onAdd={() => {}} pendingProductId={pendingProductId} />
    </MemoryRouter>,
  );
}

describe('ChatMealkitCards', () => {
  it('"밀키트로 간편하게" 아래 이름·인분·가격과 상품 상세 링크, 장바구니 아이콘 버튼을 보여 준다', () => {
    const html = render([mealkit()]);

    expect(html).toContain('밀키트로 간편하게');
    expect(html).toContain('황태해장국');
    expect(html).toContain('2인분');
    expect(html).toContain('12,900원');
    expect(html).toContain('href="/products/41"');
    expect(html).toContain('aria-label="황태해장국 상품 보기"');
    expect(html).toContain('aria-label="황태해장국 장바구니에 담기"');
    expect(html).toContain('shopping-cart');
  });

  it('할인 중이면 할인가와 줄 그은 정가를, 상품 이름과 다른 레시피 이름은 인분 옆에 보여 준다', () => {
    const html = render([
      mealkit({ recipeName: '된장국' }, { name: '구수한 된장국 세트', salePrice: 9900 }),
    ]);

    expect(html).toContain('9,900원');
    expect(html).toMatch(/<del>12,900원<\/del>/);
    expect(html).toContain('2인분 · 된장국');
  });

  it('품절이면 담기 버튼을 막고 품절로 보여 준다', () => {
    const html = render([mealkit({}, { soldOut: true })]);

    const button = html.match(/<button[^>]*aria-label="황태해장국 품절"[^>]*>.*?<\/button>/)?.[0];
    expect(button).toContain('disabled');
    expect(button).toContain('품절');
    expect(html).not.toContain('장바구니에 담기');
  });

  it('서버 설명이 있으면 카드 아래에 보여 주고, 담는 중이면 버튼에 로딩을 보여 준다', () => {
    const html = render(
      [mealkit({ description: '요리하기 번거로우면 황태해장국 밀키트로 간편하게 드셔 보세요.' })],
      41,
    );

    expect(html).toContain('요리하기 번거로우면 황태해장국 밀키트로 간편하게 드셔 보세요.');
    expect(html).toContain('ant-btn-loading');
  });

  it('냉장고에 있는 밀키트는 "냉장고에 있어요"와 임박이면 남은 날 태그를 붙이고 담기 버튼을 숨긴다', () => {
    const html = render([mealkit({ inFridge: true, fridgeDDay: 1 })]);

    expect(html).toContain('냉장고에 있어요');
    expect(html).toContain('내일까지');
    expect(html).not.toContain('장바구니에 담기');
    expect(html).toContain('href="/products/41"');
  });

  it('냉장고에 있어도 D-3보다 여유가 있으면 남은 날 태그는 붙이지 않는다', () => {
    const html = render([mealkit({ inFridge: true, fridgeDDay: 10 })]);

    expect(html).toContain('냉장고에 있어요');
    expect(html).not.toContain('10일 남음');
  });

  it('밀키트가 없으면 아무것도 그리지 않는다', () => {
    expect(render([])).toBe('');
  });
});
