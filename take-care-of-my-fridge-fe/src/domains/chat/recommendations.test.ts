import { describe, expect, it } from 'vitest';

import {
  formatImminentLabel,
  parseChatRecommendations,
  summarizeImminent,
  toChatProductCards,
  toChatRecommendedMealkits,
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
      mealkits: [],
      fridgeItems: [],
      products: [],
      relaxed: [],
      empty: false,
    });
  });

  it('후보가 없으면 빈 목록과 empty=true를 돌려준다', () => {
    expect(parseChatRecommendations('{"recipes":[],"relaxed":[],"empty":true}')).toEqual({
      recipes: [],
      mealkits: [],
      fridgeItems: [],
      products: [],
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

  it('밀키트 카드를 읽고, 레시피 카드가 없어도 밀키트가 있으면 empty가 아니며 상품이 없는 밀키트는 뺀다', () => {
    const result = parseChatRecommendations(
      JSON.stringify({
        recipes: [],
        mealkits: [
          {
            product: {
              productId: 41,
              name: '황태해장국',
              imageUrl: null,
              price: 12900,
              salePrice: 9900,
              saleType: 'SPECIAL',
              soldOut: false,
              unitAmount: null,
              unit: null,
            },
            servings: 2,
            recipeId: 543,
            recipeName: '황태해장국',
            description: null,
          },
          { product: null, servings: 2 },
        ],
        relaxed: [],
        empty: false,
      }),
    );

    expect(result?.empty).toBe(false);
    expect(result?.recipes).toEqual([]);
    expect(result?.mealkits).toEqual([
      {
        product: {
          productId: 41,
          name: '황태해장국',
          imageUrl: null,
          price: 12900,
          salePrice: 9900,
          saleType: 'SPECIAL',
          soldOut: false,
          unitAmount: null,
          unit: null,
        },
        servings: 2,
        recipeId: 543,
        recipeName: '황태해장국',
        description: null,
        inFridge: false,
        fridgeDDay: null,
      },
    ]);
  });

  it('냉장고 보기 카드와 살 상품 카드를 읽고, 밀키트의 냉장고 칸을 읽는다. 필수 값이 없는 카드는 뺀다', () => {
    const product = {
      productId: 7,
      name: '흙당근 1kg',
      imageUrl: null,
      price: 3000,
      salePrice: null,
      saleType: null,
      soldOut: false,
      unitAmount: 1,
      unit: 'kg',
    };
    const result = parseChatRecommendations(
      JSON.stringify({
        recipes: [],
        mealkits: [{ product, servings: 2, inFridge: true, fridgeDDay: 1 }],
        fridgeItems: [
          {
            fridgeId: 3,
            ingredientId: 99,
            name: '된장국 밀키트',
            rawName: '된장국 밀키트',
            quantity: 1,
            unit: '개',
            dDay: -1,
            status: 'EXPIRED',
            mealkit: true,
            productId: 24,
            imageUrl: 'img.png',
          },
          { name: 'id 없음', dDay: 1 },
        ],
        products: [
          { name: '당근', product },
          { name: '상품 없음', product: null },
        ],
        empty: false,
      }),
    );

    expect(result?.fridgeItems).toEqual([
      {
        fridgeId: 3,
        ingredientId: 99,
        name: '된장국 밀키트',
        rawName: '된장국 밀키트',
        quantity: 1,
        unit: '개',
        dDay: -1,
        status: 'EXPIRED',
        mealkit: true,
        productId: 24,
        imageUrl: 'img.png',
      },
    ]);
    expect(result?.products).toEqual([{ name: '당근', product }]);
    expect(result?.mealkits[0]).toMatchObject({ inFridge: true, fridgeDDay: 1 });
    expect(result?.empty).toBe(false);
  });

  it('냉장고 보기 카드만 있어도 empty가 아니다', () => {
    const result = parseChatRecommendations(
      JSON.stringify({
        recipes: [],
        fridgeItems: [{ fridgeId: 1, name: '두부', dDay: 2 }],
        empty: false,
      }),
    );

    expect(result?.empty).toBe(false);
    expect(result?.fridgeItems[0]).toMatchObject({
      status: 'NORMAL',
      mealkit: false,
      productId: null,
    });
  });

  it('레시피·밀키트가 모두 없으면 empty다', () => {
    expect(parseChatRecommendations('{"recipes":[],"mealkits":[],"empty":false}')?.empty).toBe(
      true,
    );
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

describe('toChatRecommendedMealkits', () => {
  it('메시지 조회의 밀키트 카드를 그릴 모양으로 바꾸고, 상품이 없는 카드는 빼며 없으면 빈 목록이다', () => {
    const mealkits = toChatRecommendedMealkits([
      {
        product: { productId: 40, name: '된장국', price: 9000, soldOut: true },
        servings: 2,
        recipeId: 544,
        recipeName: '된장국',
        description: '요리하기 번거로우면 된장국 밀키트로 간편하게 드셔 보세요.',
      },
      { servings: 1 },
    ]);

    expect(mealkits).toHaveLength(1);
    expect(mealkits[0]).toMatchObject({
      product: { productId: 40, salePrice: null, soldOut: true, imageUrl: null },
      servings: 2,
      description: '요리하기 번거로우면 된장국 밀키트로 간편하게 드셔 보세요.',
    });
    expect(toChatRecommendedMealkits(undefined)).toEqual([]);
  });
});

describe('toChatProductCards', () => {
  it('메시지 조회의 상품 카드를 그릴 모양으로 바꾸고, 이름·상품이 없는 카드는 빼며 없으면 빈 목록이다', () => {
    const cards = toChatProductCards([
      { name: '당근', product: { productId: 7, name: '흙당근 1kg', price: 3000, soldOut: false } },
      { name: '상품 없음' },
      { product: { productId: 8, name: '이름 없음', price: 1000 } },
    ]);

    expect(cards).toHaveLength(1);
    expect(cards[0]).toMatchObject({
      name: '당근',
      product: { productId: 7, name: '흙당근 1kg', salePrice: null, unit: null },
    });
    expect(toChatProductCards(undefined)).toEqual([]);
  });
});
