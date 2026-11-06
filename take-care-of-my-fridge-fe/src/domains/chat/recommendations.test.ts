import { describe, expect, it } from 'vitest';

import {
  formatImminentLabel,
  parseChatRecommendations,
  summarizeImminent,
  toChatRecommendedRecipes,
} from './recommendations';

describe('parseChatRecommendations', () => {
  it('recommendations 이벤트 data를 읽어 카드 정보로 바꾼다', () => {
    const data = JSON.stringify({
      recipes: [
        {
          recipeId: 631,
          name: '감자느타리버섯국',
          imageUrl: null,
          category: '국',
          cookTimeMin: null,
          usedIngredients: ['고추', '대파'],
          imminentIngredients: [{ name: '고추', dDay: 1 }],
          matchedTastes: ['감칠맛'],
          missingIngredients: [{ ingredientId: 21, name: '두부' }],
          missingSelectedIngredients: ['두부'],
          lacksManyIngredients: true,
          reason: '느타리버섯·두부가 주재료예요',
          description: null,
        },
      ],
      relaxed: [],
      empty: false,
    });

    expect(parseChatRecommendations(data)).toEqual({
      recipes: [
        {
          recipeId: 631,
          name: '감자느타리버섯국',
          imageUrl: null,
          category: '국',
          cookTimeMin: null,
          usedIngredients: ['고추', '대파'],
          imminentIngredients: [{ name: '고추', dDay: 1 }],
          matchedTastes: ['감칠맛'],
          missingIngredients: [{ ingredientId: 21, name: '두부', product: null }],
          missingSelectedIngredients: ['두부'],
          lacksManyIngredients: true,
          reason: '느타리버섯·두부가 주재료예요',
          description: null,
        },
      ],
      relaxed: [],
      empty: false,
    });
  });

  it('후보가 없으면 빈 목록과 empty=true를 돌려준다', () => {
    expect(parseChatRecommendations('{"recipes":[],"relaxed":[],"empty":true}')).toEqual({
      recipes: [],
      relaxed: [],
      empty: true,
    });
  });

  it('id·이름이 없는 레시피는 빼고, 빠진 목록 칸은 빈 목록으로 채운다', () => {
    const result = parseChatRecommendations(
      JSON.stringify({
        recipes: [{ name: '이름만' }, { recipeId: 1, name: '두부조림' }],
        empty: false,
      }),
    );

    expect(result?.recipes).toHaveLength(1);
    expect(result?.recipes[0]).toMatchObject({
      recipeId: 1,
      usedIngredients: [],
      missingIngredients: [],
      missingSelectedIngredients: [],
      lacksManyIngredients: false,
      reason: '',
      description: null,
    });
  });

  it('장볼 재료의 담을 상품을 읽고, 상품이 없거나 id·이름·정가가 없으면 null로 둔다', () => {
    const result = parseChatRecommendations(
      JSON.stringify({
        recipes: [
          {
            recipeId: 1,
            name: '두부조림',
            missingIngredients: [
              {
                ingredientId: 21,
                name: '두부',
                product: {
                  productId: 22,
                  name: '부침용 두부 1모',
                  imageUrl: null,
                  price: 2000,
                  salePrice: 1500,
                  saleType: 'SPECIAL',
                  soldOut: false,
                  unitAmount: 1,
                  unit: '모',
                },
              },
              { ingredientId: 5, name: '배추', product: null },
              { ingredientId: 3, name: '대파', product: { name: '대파 1단' } },
            ],
          },
        ],
        empty: false,
      }),
    );

    expect(result?.recipes[0].missingIngredients).toEqual([
      {
        ingredientId: 21,
        name: '두부',
        product: {
          productId: 22,
          name: '부침용 두부 1모',
          imageUrl: null,
          price: 2000,
          salePrice: 1500,
          saleType: 'SPECIAL',
          soldOut: false,
          unitAmount: 1,
          unit: '모',
        },
      },
      { ingredientId: 5, name: '배추', product: null },
      { ingredientId: 3, name: '대파', product: null },
    ]);
  });

  it('JSON이 아니거나 recipes가 없으면 null을 돌려준다', () => {
    expect(parseChatRecommendations('not json')).toBeNull();
    expect(parseChatRecommendations('{"empty":true}')).toBeNull();
  });
});

describe('formatImminentLabel', () => {
  it('D-0 오늘까지, D-1 내일까지, D-2 모레까지, D-3부터 n일 남음으로 쓴다', () => {
    expect(formatImminentLabel(0)).toBe('오늘까지');
    expect(formatImminentLabel(1)).toBe('내일까지');
    expect(formatImminentLabel(2)).toBe('모레까지');
    expect(formatImminentLabel(3)).toBe('3일 남음');
  });
});

describe('summarizeImminent', () => {
  it('가장 급한 날짜의 재료만 모아 한 줄로 쓰고 그 날짜를 돌려준다', () => {
    expect(
      summarizeImminent([
        { name: '고추', dDay: 1 },
        { name: '대파', dDay: 2 },
        { name: '깻잎', dDay: 1 },
      ]),
    ).toEqual({ label: '고추·깻잎 내일까지', dDay: 1 });
  });

  it('임박 재료가 없으면 null을 돌려준다', () => {
    expect(summarizeImminent([])).toBeNull();
  });
});

describe('toChatRecommendedRecipes', () => {
  it('메시지 조회의 카드를 그릴 모양으로 바꾸고, 설명을 담고, 빠진 목록은 빈 목록으로 채운다', () => {
    const cards = toChatRecommendedRecipes([
      {
        recipeId: 30,
        name: '두부조림',
        category: '조림',
        description: '두부를 졸여요',
        reason: '이유',
      },
      { name: 'id 없음' },
    ]);

    expect(cards).toHaveLength(1);
    expect(cards[0]).toMatchObject({
      recipeId: 30,
      description: '두부를 졸여요',
      cookTimeMin: null,
      missingSelectedIngredients: [],
      imminentIngredients: [],
    });
  });

  it('카드가 없으면 빈 목록을 돌려준다', () => {
    expect(toChatRecommendedRecipes(undefined)).toEqual([]);
  });
});
