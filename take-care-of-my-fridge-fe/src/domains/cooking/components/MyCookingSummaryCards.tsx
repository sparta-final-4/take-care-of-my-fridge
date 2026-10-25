import { Button, Typography } from 'antd';

import { SectionCard } from '@/common/components/SectionCard';
import type { ThisMonthCookingSummary } from '../api';

import styles from './MyCookingSummaryCards.module.scss';

export interface MyCookingSummaryCardsProps {
  summary: ThisMonthCookingSummary;
  unreviewedCount: number;
  onGoToUnreviewed: () => void;
}

export function MyCookingSummaryCards({
  summary,
  unreviewedCount,
  onGoToUnreviewed,
}: MyCookingSummaryCardsProps) {
  const showReviewPromptCard = unreviewedCount > 0;

  return (
    <div className={styles.container}>
      {/* 1. 지금까지 요리 기록 카드 */}
      <div className={styles.cardItem}>
        <SectionCard title="지금까지 요리 기록">
          <div className={styles.summaryContent}>
            <div className={styles.summaryRow}>
              <span className={styles.summaryLabel}>요리 완료</span>
              <span className={styles.summaryValue}>{summary.completedCookCount}번</span>
            </div>
            <div className={styles.summaryRow}>
              <span className={styles.summaryLabel}>요리에 쓴 재료</span>
              <span className={styles.summaryValue}>{summary.consumedIngredientCount}종</span>
            </div>
            <div className={styles.summaryRow}>
              <span className={styles.summaryLabel}>가장 많이 만든 레시피</span>
              <span className={styles.summaryValue}>
                {summary.mostCookedRecipeName && summary.mostCookedRecipeName !== '-'
                  ? `${summary.mostCookedRecipeName} · ${summary.mostCookedRecipeCount}회`
                  : '-'}
              </span>
            </div>
          </div>
        </SectionCard>
      </div>

      {/* 2. 후기를 기다리는 레시피 카드 (미작성이 0개면 숨김) */}
      {showReviewPromptCard && (
        <div className={styles.cardItem}>
          <SectionCard title="후기를 기다리는 레시피">
            <div className={styles.promptContent}>
              <Typography.Text type="secondary" className={styles.promptSubtitle}>
                후기는 다른 사람의 요리 실패를 줄여줘요.
              </Typography.Text>
              <Button
                color="default"
                variant="solid"
                block
                size="large"
                className={styles.promptButton}
                onClick={onGoToUnreviewed}
              >
                후기 {unreviewedCount}개 쓰러 가기
              </Button>
            </div>
          </SectionCard>
        </div>
      )}
    </div>
  );
}
