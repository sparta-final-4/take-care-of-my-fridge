import { describe, expect, it } from 'vitest';

import { formatImminentLabel, parseChatRecommendations } from './recommendations';

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
          missingIngredients: [{ ingredientId: 21, name: '두부' }],
          missingSelectedIngredients: ['두부'],
          lacksManyIngredients: true,
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
    });
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
