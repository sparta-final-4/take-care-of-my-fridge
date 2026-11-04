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

  it('둘째 줄에 가장 급한 임박 재료 하나, 냉장고에 없는 선택 재료, 장볼 재료 수, 장보기 필요를 보여 준다', () => {
    const html = render([
      recipe({
        imminentIngredients: [
          { name: '고추', dDay: 1 },
          { name: '깻잎', dDay: 1 },
          { name: '대파', dDay: 2 },
        ],
        missingSelectedIngredients: ['두부'],
        missingIngredients: [
          { ingredientId: 1, name: '장어' },
          { ingredientId: 2, name: '생강' },
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
    const html = render([recipe({ missingIngredients: [{ ingredientId: 1, name: '장어' }] })]);

    expect(html).toContain('aria-expanded="false"');
    expect(html).not.toContain('기본 양념은 빼고 셌어요');
  });

  it('장볼 재료가 없으면 장볼 재료 버튼을 그리지 않는다', () => {
    expect(render([recipe({})])).not.toContain('장볼 재료');
  });

  it('추천 레시피가 없으면 아무것도 그리지 않는다', () => {
    expect(render([])).toBe('');
  });
});
