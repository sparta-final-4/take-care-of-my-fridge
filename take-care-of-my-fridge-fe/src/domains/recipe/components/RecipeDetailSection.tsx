import { LeftOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Button, Result, Skeleton } from 'antd';
import { useLocation, useNavigate } from 'react-router';

import { ROUTES } from '@/app/paths';
import { isNotFound } from '@/common/api/errors';
import { RecipeCookingStats } from '@/domains/cooking/components/RecipeCookingStats';
import { useRecipeFridgeMatch } from '@/domains/recipe/hooks/useRecipeFridgeMatch';
import { recipeQueries } from '@/domains/recipe/queries';
import { RecipeReviewSection } from '@/domains/review/components/RecipeReviewSection';

import { RecipeCookPanel } from './RecipeCookPanel';
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
  const location = useLocation();
  const {
    data: recipe,
    isPending,
    isError,
    error,
    refetch,
  } = useQuery(recipeQueries.detail(recipeId));
  const fridgeMatch = useRecipeFridgeMatch(recipe?.ingredients ?? []);

  // 앱 안에서 이동해 왔으면 이전 화면(필터가 남은 목록 등)으로 돌아간다.
  // 주소로 바로 들어와 앱 안의 이전 화면이 없으면(location.key === 'default') 목록으로 간다.
  // window.history.length는 외부 사이트에서 들어온 기록도 세므로 쓰지 않는다.
  const handleGoBack = () => (location.key === 'default' ? navigate(ROUTES.recipes) : navigate(-1));

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
        {/* 왼쪽: 사진·이름 → 재료 → 조리 순서 → 영양 성분 */}
        <div className={styles.column}>
          <RecipeOverview recipe={recipe} fridgeMatch={fridgeMatch} />
          <RecipeIngredientSection
            ingredients={recipe.ingredients ?? []}
            fridgeMatch={fridgeMatch}
          />
          <RecipeStepSection steps={recipe.steps ?? []} tip={recipe.tip} />
          <RecipeNutritionSection nutrition={recipe.nutrition} />
        </div>

        {/* 오른쪽: [내 냉장고 재료] + [요리 완료] → 요리 통계 → 후기 */}
        <div className={styles.column}>
          <RecipeCookPanel recipeId={recipeId} recipe={recipe} fridgeMatch={fridgeMatch} />
          <RecipeCookingStats recipeId={recipeId} />
          <RecipeReviewSection recipeId={recipeId} />
        </div>
      </div>
    </div>
  );
}
