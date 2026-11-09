import { LeftOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Anchor, Button, Result, Skeleton } from 'antd';
import { useState, type KeyboardEvent } from 'react';
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

// 좁은 화면에서 재료·조리 순서를 바꿔 보는 탭(레퍼런스 INGREDIENTS / DIRECTIONS 버튼)
const METHOD_TABS = [
  { key: 'ingredients', label: '재료' },
  { key: 'steps', label: '조리 순서' },
] as const;

type MethodTab = (typeof METHOD_TABS)[number]['key'];

// 오른쪽 화면 바로가기. 세트의 section-nav가 꺼진 세트에서는 CSS로 숨긴다.
// 머리 높이(_variables.scss $header-height)만큼 띄워 묶음 위끝이 헤더에 가리지 않게 한다.
// 맨 위에서는 소개 묶음이 아직 헤더 밑까지 올라오지 않아 antd가 아무 항목도 고르지 않으므로, 그때는 첫 항목(소개)을 표시한다.
const HEADER_HEIGHT = 64;
const SECTION_LINKS = [
  { key: 'overview', href: '#recipe-overview', title: '소개' },
  { key: 'method', href: '#recipe-method', title: '만드는 법' },
  { key: 'more', href: '#recipe-more', title: '영양과 후기' },
];

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
  const [methodTab, setMethodTab] = useState<MethodTab>('ingredients');

  // 앱 안에서 이동해 왔으면 이전 화면(필터가 남은 목록 등)으로 돌아간다.
  // 주소로 바로 들어와 앱 안의 이전 화면이 없으면(location.key === 'default') 목록으로 간다.
  // window.history.length는 외부 사이트에서 들어온 기록도 세므로 쓰지 않는다.
  const handleGoBack = () => (location.key === 'default' ? navigate(ROUTES.recipes) : navigate(-1));

  // 탭 사이는 왼쪽·오른쪽 화살표로도 옮긴다(탭이 둘이라 어느 쪽이든 다른 탭으로).
  const handleTabKeyDown = (event: KeyboardEvent<HTMLButtonElement>) => {
    if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
    const next: MethodTab = methodTab === 'ingredients' ? 'steps' : 'ingredients';
    setMethodTab(next);
    document.getElementById(`recipe-tab-${next}`)?.focus();
  };

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
      <nav className={styles.sectionNav} aria-label="레시피 화면 바로가기">
        <Anchor
          affix={false}
          replace
          targetOffset={HEADER_HEIGHT}
          items={SECTION_LINKS}
          getCurrentAnchor={(activeLink) => activeLink || SECTION_LINKS[0].href}
        />
      </nav>

      <Button type="link" icon={<LeftOutlined />} onClick={handleGoBack} className={styles.back}>
        이전으로
      </Button>

      {/* 머리: 글 + 사진(레퍼런스 레시피 글 머리) */}
      <RecipeOverview recipe={recipe} fridgeMatch={fridgeMatch} />

      {/* 본문: lg 이상은 재료 | 조리 순서 두 칸, 좁은 화면은 탭 버튼으로 하나씩 */}
      <section id="recipe-method" className={styles.method} aria-label="재료와 조리 순서">
        <div role="tablist" aria-label="레시피 내용" className={styles.tabs}>
          {METHOD_TABS.map((tab) => {
            const selected = methodTab === tab.key;
            return (
              <button
                key={tab.key}
                type="button"
                role="tab"
                id={`recipe-tab-${tab.key}`}
                aria-selected={selected}
                aria-controls={`recipe-panel-${tab.key}`}
                tabIndex={selected ? 0 : -1}
                onClick={() => setMethodTab(tab.key)}
                onKeyDown={handleTabKeyDown}
                className={selected ? `${styles.tab} ${styles.tabActive}` : styles.tab}
              >
                {tab.label}
              </button>
            );
          })}
        </div>
        <div className={styles.viewport}>
          <div
            className={
              methodTab === 'steps' ? `${styles.track} ${styles.trackSteps}` : styles.track
            }
          >
            <div
              role="tabpanel"
              id="recipe-panel-ingredients"
              aria-labelledby="recipe-tab-ingredients"
              className={
                methodTab === 'ingredients'
                  ? `${styles.panel} ${styles.panelIngredients} ${styles.panelActive}`
                  : `${styles.panel} ${styles.panelIngredients}`
              }
            >
              <RecipeIngredientSection
                ingredients={recipe.ingredients ?? []}
                fridgeMatch={fridgeMatch}
              />
            </div>
            <div
              role="tabpanel"
              id="recipe-panel-steps"
              aria-labelledby="recipe-tab-steps"
              className={
                methodTab === 'steps'
                  ? `${styles.panel} ${styles.panelSteps} ${styles.panelActive}`
                  : `${styles.panel} ${styles.panelSteps}`
              }
            >
              <RecipeStepSection steps={recipe.steps ?? []} tip={recipe.tip} />
            </div>
          </div>
        </div>
      </section>

      {/* 아래: 왼쪽 영양 정보 → 후기, 오른쪽 [내 냉장고 재료] + [요리 완료] → 요리 통계 */}
      <div id="recipe-more" className={styles.row}>
        <div className={styles.column}>
          <RecipeNutritionSection nutrition={recipe.nutrition} />
          <RecipeReviewSection recipeId={recipeId} tipSummary={recipe.tipSummary} />
        </div>
        <div className={`${styles.column} ${styles.aside}`}>
          <RecipeCookPanel recipeId={recipeId} recipe={recipe} fridgeMatch={fridgeMatch} />
          <RecipeCookingStats recipeId={recipeId} />
        </div>
      </div>
    </div>
  );
}
