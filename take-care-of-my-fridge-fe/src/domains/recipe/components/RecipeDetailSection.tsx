import { LeftOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Button, Result, Skeleton } from 'antd';
import { useNavigate } from 'react-router';

import { ROUTES } from '@/app/paths';
import { isNotFound } from '@/common/api/errors';
import { useRecipeFridgeMatch } from '@/domains/recipe/hooks/useRecipeFridgeMatch';
import { recipeQueries } from '@/domains/recipe/queries';

import { RecipeCookPanel } from './RecipeCookPanel';
import { RecipeFailureNotice } from './RecipeFailureNotice';
import { RecipeIngredientSection } from './RecipeIngredientSection';
import { RecipeNutritionSection } from './RecipeNutritionSection';
import { RecipeOverview } from './RecipeOverview';
import { RecipeStepSection } from './RecipeStepSection';
import styles from './RecipeDetailSection.module.scss';

interface RecipeDetailSectionProps {
  recipeId: number;
}

export function RecipeDetailSection({ recipeId }: RecipeDetailSectionProps) {
  const navigate = useNavigate();
  const {
    data: recipe,
    isPending,
    isError,
    error,
    refetch,
  } = useQuery(recipeQueries.detail(recipeId));
  const fridgeMatch = useRecipeFridgeMatch(recipe?.ingredients ?? []);

  // 목록에서 들어왔으면 필터가 남은 목록으로 돌아간다.
  const handleGoBack = () => (window.history.length > 1 ? navigate(-1) : navigate(ROUTES.recipes));

  if (isPending) return <Skeleton active paragraph={{ rows: 10 }} />;
  if (isError) {
    return isNotFound(error) ? (
      <Result
        status="404"
        title="레시피를 찾을 수 없습니다"
        extra={<Button onClick={() => navigate(ROUTES.recipes)}>목록으로</Button>}
      />
    ) : (
      <Result
        status="error"
        title="레시피를 불러오지 못했습니다."
        extra={<Button onClick={() => refetch()}>다시 시도</Button>}
      />
    );
  }

  return (
    <div className={styles.page}>
      <Button type="link" icon={<LeftOutlined />} onClick={handleGoBack} className={styles.back}>
        이전으로
      </Button>
      <div className={styles.row}>
        <div className={styles.column}>
          <RecipeOverview recipe={recipe} fridgeMatch={fridgeMatch} />
          <RecipeIngredientSection
            ingredients={recipe.ingredients ?? []}
            fridgeMatch={fridgeMatch}
          />
          <RecipeStepSection steps={recipe.steps ?? []} tip={recipe.tip} />
          <RecipeNutritionSection nutrition={recipe.nutrition} />
        </div>
        <div className={styles.column}>
          <RecipeCookPanel recipe={recipe} fridgeMatch={fridgeMatch} />
          <RecipeFailureNotice recipeId={recipeId} />
        </div>
      </div>
    </div>
  );
}
