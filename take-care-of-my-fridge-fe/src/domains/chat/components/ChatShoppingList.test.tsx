import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';

import type { ChatRecommendationMissingIngredient, ChatShoppingProduct } from '../recommendations';
import { ChatShoppingList } from './ChatShoppingList';

function product(overrides: Partial<ChatShoppingProduct>): ChatShoppingProduct {
  return {
    productId: 16,
    name: '대파 1단',
    imageUrl: null,
    price: 3000,
    salePrice: null,
    saleType: null,
    soldOut: false,
    unitAmount: 1,
    unit: '단',
    ...overrides,
  };
}

function render(ingredients: ChatRecommendationMissingIngredient[], pendingProductId?: number) {
  return renderToStaticMarkup(
    <ChatShoppingList
      ingredients={ingredients}
      onAdd={() => {}}
      pendingProductId={pendingProductId}
    />,
  );
}

describe('ChatShoppingList', () => {
  it('상품이 있으면 재료·상품 이름, 정가와 장바구니 아이콘 버튼을 보여 준다', () => {
    const html = render([{ ingredientId: 3, name: '대파', product: product({}) }]);

    expect(html).toContain('대파');
    expect(html).toContain('대파 1단');
    expect(html).toContain('3,000원');
    expect(html).toContain('aria-label="대파 1단 장바구니에 담기"');
    expect(html).toContain('shopping-cart');
    expect(html).not.toContain('disabled');
  });

  it('할인 중이면 할인가와 줄 그은 정가를 함께 보여 준다', () => {
    const html = render([
      {
        ingredientId: 21,
        name: '두부',
        product: product({
          productId: 22,
          name: '부침용 두부 1모',
          price: 2000,
          salePrice: 1500,
          saleType: 'SPECIAL',
        }),
      },
    ]);

    expect(html).toContain('1,500원');
    expect(html).toMatch(/<del>2,000원<\/del>/);
  });

  it('품절이면 버튼을 막고 품절로 보여 준다', () => {
    const html = render([{ ingredientId: 3, name: '대파', product: product({ soldOut: true }) }]);

    const button = html.match(/<button[^>]*aria-label="대파 품절"[^>]*>.*?<\/button>/)?.[0];
    expect(button).toContain('disabled');
    expect(button).toContain('품절');
    expect(html).not.toContain('장바구니에 담기');
  });

  it('살 수 있는 상품 → 품절 → 판매 상품 없음 순서로 보여 준다', () => {
    const html = render([
      { ingredientId: 1, name: '양배추', product: null },
      { ingredientId: 2, name: '대파', product: product({ soldOut: true }) },
      { ingredientId: 3, name: '두부', product: product({ productId: 22, name: '두부 1모' }) },
    ]);

    const buyable = html.indexOf('두부 1모 장바구니에 담기');
    const soldOut = html.indexOf('대파 품절');
    const none = html.indexOf('판매 상품 없음');
    expect(buyable).toBeGreaterThan(-1);
    expect(buyable).toBeLessThan(soldOut);
    expect(soldOut).toBeLessThan(none);
  });

  it('판매 상품이 없는 재료는 한 줄로 묶어 "판매 상품 없음"으로 보여 주고 버튼을 그리지 않는다', () => {
    const html = render([
      { ingredientId: 1, name: '양배추', product: null },
      { ingredientId: 2, name: '미나리', product: null },
      { ingredientId: 3, name: '느타리버섯', product: null },
    ]);

    expect(html).toContain('양배추 · 미나리 · 느타리버섯');
    expect(html.match(/판매 상품 없음/g)).toHaveLength(1);
    expect(html.match(/<li/g)).toHaveLength(1);
    expect(html).not.toContain('<button');
  });

  it('담는 중인 상품의 버튼에 로딩을 보여 준다', () => {
    const html = render([{ ingredientId: 3, name: '대파', product: product({}) }], 16);

    expect(html).toContain('ant-btn-loading');
  });
});
