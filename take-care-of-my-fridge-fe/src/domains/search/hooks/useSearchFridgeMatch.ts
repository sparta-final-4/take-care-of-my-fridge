import { useQuery } from '@tanstack/react-query';

import { useAuthStore } from '@/common/auth/authStore';
import { searchQueries } from '@/domains/search/queries';

import { useSearchKeyword } from './useSearchKeyword';

// 로그인 회원이면 지금 보이는 레시피 결과를 냉장고와 비교한다. 레시피 섹션과 상단 안내가 같은 요청을 나눠 쓴다.
// 실패하면 비교 표시 없이 결과만 보여 준다.
export function useSearchFridgeMatch() {
  const { keyword, recipeRequest } = useSearchKeyword();
  const authenticated = useAuthStore((state) => state.status) === 'authenticated';
  const { data: recipes, isPlaceholderData } = useQuery(searchQueries.recipes(recipeRequest));
  const recipeIds = (recipes?.content ?? []).flatMap((recipe) =>
    recipe.recipeId === undefined ? [] : [recipe.recipeId],
  );

  const { data } = useQuery({
    ...searchQueries.fridgeMatch({ recipeIds, keyword }),
    enabled: authenticated && recipeIds.length > 0 && !isPlaceholderData,
  });

  return {
    matchById: new Map((data?.recipes ?? []).map((match) => [match.recipeId, match])),
    keywordItems: data?.keywordItems ?? [],
  };
}
