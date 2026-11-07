// 내 냉장고 화면의 재료 입력 섹션 id
export const INGREDIENT_INPUT_SECTION_ID = 'ingredient-input';

// 재료 입력 섹션으로 내려가 첫 입력칸(재료명)에 포커스한다. 화면 머리의 [재료 추가]와 빈 냉장고 안내가 쓴다.
export function scrollToIngredientInput() {
  const section = document.getElementById(INGREDIENT_INPUT_SECTION_ID);
  section?.scrollIntoView({ block: 'start' });
  section?.querySelector('input')?.focus({ preventScroll: true });
}
