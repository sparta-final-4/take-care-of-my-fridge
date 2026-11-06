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
  it('상품이 있으면 재료·상품 이름, 정가와 [담기]를 보여 준다', () => {
    const html = render([{ ingredientId: 3, name: '대파', product: product({}) }]);

    expect(html).toContain('대파');
    expect(html).toContain('대파 1단');
    expect(html).toContain('3,000원');
    expect(html).toContain('aria-label="대파 1단 장바구니에 담기"');
    expect(html).toContain('담기');
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

  it('품절이면 [담기]를 막고 품절로 보여 준다', () => {
    const html = render([{ ingredientId: 3, name: '대파', product: product({ soldOut: true }) }]);

    expect(html).toContain('품절');
    expect(html).toContain('aria-label="대파 품절"');
    expect(html).toContain('disabled');
    expect(html).not.toContain('장바구니에 담기');
  });

  it('판매 상품이 없으면 재료 이름만 흐리게 "판매 상품 없음"으로 보여 주고 [담기]를 그리지 않는다', () => {
    const html = render([{ ingredientId: 5, name: '배추', product: null }]);

    expect(html).toContain('배추');
    expect(html).toContain('판매 상품 없음');
    expect(html).not.toContain('<button');
  });

  it('담는 중인 상품의 버튼에 로딩을 보여 준다', () => {
    const html = render([{ ingredientId: 3, name: '대파', product: product({}) }], 16);

    expect(html).toContain('ant-btn-loading');
  });
});
