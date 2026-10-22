import { Button, Result } from 'antd';
import { useNavigate, useParams } from 'react-router';

import { ROUTES } from '@/app/paths';
import { RecipeDetailSection } from '@/domains/recipe/components/RecipeDetailSection';

export default function RecipeDetailPage() {
  const navigate = useNavigate();
  const recipeId = Number(useParams().recipeId);

  if (!Number.isInteger(recipeId) || recipeId <= 0) {
    return (
      <Result
        status="404"
        title="레시피를 찾을 수 없습니다"
        extra={<Button onClick={() => navigate(ROUTES.recipes)}>목록으로</Button>}
      />
    );
  }
  return <RecipeDetailSection key={recipeId} recipeId={recipeId} />;
}
