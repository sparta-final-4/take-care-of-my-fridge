import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';

import type { ChatRecommendedRecipe } from '../recommendations';
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
    ...overrides,
  };
}

function render(recipes: ChatRecommendedRecipe[]) {
  return renderToStaticMarkup(
    <MemoryRouter>
      <ChatRecommendationCards recipes={recipes} />
    </MemoryRouter>,
  );
}

describe('ChatRecommendationCards', () => {
  it('카드마다 이름·분류·조리 시간을 보여 주고 누르면 레시피 상세로 간다', () => {
    const html = render([
      recipe({}),
      recipe({ recipeId: 139, name: '참치김치찌개', cookTimeMin: null }),
    ]);

    expect(html).toContain('감자느타리버섯국');
    expect(html).toContain('국 · 25분');
    expect(html).toContain('국 · 시간 정보 없음');
    expect(html).toContain('href="/recipes/631"');
    expect(html).toContain('href="/recipes/139"');
  });

  it('임박 재료는 오늘까지·내일까지로, 냉장고에 없는 선택 재료는 "없음"으로, 많이 모자라면 장보기 필요로 보여 준다', () => {
    const html = render([
      recipe({
        imminentIngredients: [
          { name: '고추', dDay: 1 },
          { name: '대파', dDay: 0 },
        ],
        missingSelectedIngredients: ['두부'],
        lacksManyIngredients: true,
      }),
    ]);

    expect(html).toContain('고추 내일까지');
    expect(html).toContain('대파 오늘까지');
    expect(html).toContain('두부 없음');
    expect(html).toContain('장보기 필요');
  });

  it('부족한 재료는 개수만 보여 주고 이름은 펼쳐야 보인다', () => {
    const html = render([
      recipe({
        missingIngredients: [
          { ingredientId: 1, name: '멸치' },
          { ingredientId: 2, name: '다시마' },
        ],
      }),
    ]);

    expect(html).toContain('부족한 재료 2개');
    expect(html).toContain('aria-expanded="false"');
    expect(html).not.toContain('멸치, 다시마');
  });

  it('추천 레시피가 없으면 아무것도 그리지 않는다', () => {
    expect(render([])).toBe('');
  });
});
