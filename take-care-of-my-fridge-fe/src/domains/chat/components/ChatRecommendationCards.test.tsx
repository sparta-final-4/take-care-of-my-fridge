import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ComponentProps } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';

import type { ChatRecommendedMealkit, ChatRecommendedRecipe } from '../recommendations';
import { ChatRecommendationCards } from './ChatRecommendationCards';

function recipe(overrides: Partial<ChatRecommendedRecipe>): ChatRecommendedRecipe {
  return {
    recipeId: 631,
    name: '감자느타리버섯국',
    imageUrl: null,
    category: '국',
    cookTimeMin: 25,
    usedIngredients: ['고추', '대파'],
    imminentIngredients: [],
    matchedTastes: [],
    missingIngredients: [],
    missingSelectedIngredients: [],
    lacksManyIngredients: false,
    reason: '감자를 쓸 수 있어요',
    description: null,
    ...overrides,
  };
}

const mealkit: ChatRecommendedMealkit = {
  product: {
    productId: 40,
    name: '된장국',
    imageUrl: null,
    price: 9000,
    salePrice: null,
    saleType: null,
    soldOut: false,
    unitAmount: null,
    unit: null,
  },
  servings: 2,
  recipeId: 544,
  recipeName: '된장국',
  description: null,
  inFridge: false,
  fridgeDDay: null,
};

function render(
  recipes: ChatRecommendedRecipe[],
  extra: Partial<ComponentProps<typeof ChatRecommendationCards>> = {},
) {
  return renderToStaticMarkup(
    <MemoryRouter>
      <ChatRecommendationCards recipes={recipes} {...extra} />
    </MemoryRouter>,
  );
}

describe('ChatRecommendationCards', () => {
  it('첫 줄에 이름과 분류·조리 시간을 보여 주고, 카드를 누르면 레시피 상세로 간다', () => {
    const html = render([
      recipe({}),
      recipe({ recipeId: 139, name: '참치김치찌개', cookTimeMin: null }),
    ]);

    expect(html).toContain('감자느타리버섯국');
    expect(html).toContain('국 · 25분');
    expect(html).toContain('국 · 시간 정보 없음');
    expect(html).toContain('href="/recipes/631"');
    expect(html).toContain('aria-label="참치김치찌개 레시피 보기"');
  });

  it('둘째 줄에 추천 이유 한 줄을 보여 주고, 이유가 없으면 그 줄을 그리지 않는다', () => {
    expect(render([recipe({ reason: '버섯·두부가 주재료예요' })])).toContain(
      '버섯·두부가 주재료예요',
    );
    expect(render([recipe({ reason: '' })])).not.toContain('감자를 쓸 수 있어요');
  });

  it('셋째 줄에 가장 급한 임박 재료 하나, 냉장고에 없는 선택 재료, 장볼 재료 수, 장보기 필요를 보여 준다', () => {
    const html = render([
      recipe({
        imminentIngredients: [
          { name: '고추', dDay: 1 },
          { name: '깻잎', dDay: 1 },
          { name: '대파', dDay: 2 },
        ],
        missingSelectedIngredients: ['두부'],
        missingIngredients: [
          { ingredientId: 1, name: '장어', product: null },
          { ingredientId: 2, name: '생강', product: null },
        ],
        lacksManyIngredients: true,
      }),
    ]);

    expect(html).toContain('고추·깻잎 내일까지');
    expect(html).not.toContain('대파 모레까지');
    expect(html).toContain('두부 없음');
    expect(html).toContain('장볼 재료 2');
    expect(html).toContain('장보기 필요');
  });

  it('장볼 재료 이름과 기본 양념 안내는 펼쳐야 보인다', () => {
    const html = render([
      recipe({ missingIngredients: [{ ingredientId: 1, name: '장어', product: null }] }),
    ]);

    expect(html).toContain('aria-expanded="false"');
    expect(html).not.toContain('기본 양념은 빼고 셌어요');
  });

  it('장볼 재료가 없으면 장볼 재료 버튼을 그리지 않는다', () => {
    expect(render([recipe({})])).not.toContain('장볼 재료');
  });

  it('추천 레시피가 없으면 아무것도 그리지 않는다', () => {
    expect(render([])).toBe('');
  });

  it('카드 아래에 설명을 보여 주고, 설명이 있으면 카드 안 이유 한 줄은 숨긴다', () => {
    const html = render([
      recipe({ description: '두부를 졸여 짭짤하게 먹어요.', reason: '두부가 주재료예요' }),
    ]);

    expect(html).toContain('두부를 졸여 짭짤하게 먹어요.');
    expect(html).not.toContain('두부가 주재료예요');
  });

  it('받는 중인 설명이 있으면 저장된 설명 대신 그 글을 보여 준다', () => {
    const html = renderToStaticMarkup(
      <MemoryRouter>
        <ChatRecommendationCards
          recipes={[recipe({ description: null, reason: '이유' })]}
          descriptions={['받는 중인 설명']}
        />
      </MemoryRouter>,
    );

    expect(html).toContain('받는 중인 설명');
    expect(html).not.toContain('이유');
  });

  it('기록에서 다시 그린 카드는 냉장고에 없는 선택 재료가 비어 있어도 그 태그 없이 그린다', () => {
    const html = render([
      recipe({
        missingSelectedIngredients: [],
        missingIngredients: [{ ingredientId: 1, name: '두부', product: null }],
      }),
    ]);

    expect(html).not.toContain('없음');
    expect(html).toContain('장볼 재료 1');
  });

  it('밀키트만 있는 답이면 레시피 카드 없이 밀키트 카드를 보여 준다', () => {
    const html = renderToStaticMarkup(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <ChatRecommendationCards recipes={[]} mealkits={[mealkit]} />
        </MemoryRouter>
      </QueryClientProvider>,
    );

    expect(html).not.toContain('추천 레시피');
    expect(html).toContain('밀키트로 간편하게');
    expect(html).toContain('aria-label="된장국 장바구니에 담기"');
  });

  it('사기 답이면 "살 수 있는 상품" 아래 말한 이름과 상품, 담기 버튼을 보여 준다', () => {
    const html = renderToStaticMarkup(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <ChatRecommendationCards
            recipes={[]}
            products={[
              { name: '당근', product: { ...mealkit.product, productId: 7, name: '흙당근 1kg' } },
            ]}
          />
        </MemoryRouter>
      </QueryClientProvider>,
    );

    expect(html).toContain('살 수 있는 상품');
    expect(html).not.toContain('밀키트로 간편하게');
    expect(html).toContain('당근');
    expect(html).toContain('href="/products/7"');
    expect(html).toContain('aria-label="흙당근 1kg 장바구니에 담기"');
  });

  it('냉장고 보기 답이면 냉장고 카드를 보여 준다', () => {
    const html = render([], {
      fridgeItems: [
        {
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
        },
      ],
    });

    expect(html).toContain('냉장고에 있어요');
    expect(html).toContain('두부');
    expect(html).toContain('내일까지');
  });

  it('레시피·밀키트가 모두 없으면 아무것도 그리지 않는다', () => {
    expect(render([])).toBe('');
  });
});
