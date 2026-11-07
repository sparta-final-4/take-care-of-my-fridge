import { Typography } from 'antd';

import type { StepItem } from '@/common/api/clients';
import { FadeImage } from '@/common/components/FadeImage';

import styles from './RecipeStepSection.module.scss';

interface RecipeStepSectionProps {
  steps: StepItem[];
  tip?: string;
}

// 레퍼런스 레시피 글의 조리법 칸: 큰 번호(01, 02 …)와 문단, 단계 팁은 아래 작은 글씨, 전체 팁은 맨 아래 메모.
export function RecipeStepSection({ steps, tip }: RecipeStepSectionProps) {
  return (
    <section className={styles.section} aria-label="조리 순서">
      <Typography.Title level={2} className={styles.title}>
        조리 순서
      </Typography.Title>
      <ol className={styles.list}>
        {steps.map((step) => (
          <li key={step.stepNo} className={styles.step}>
            <span className={styles.number} aria-hidden="true">
              {String(step.stepNo ?? '').padStart(2, '0')}
            </span>
            <div className={styles.body}>
              <Typography.Paragraph className={styles.description}>
                {step.description}
              </Typography.Paragraph>
              {step.tip && (
                <Typography.Text type="secondary" className={styles.stepTip}>
                  {step.tip}
                </Typography.Text>
              )}
              {step.imageUrl && (
                <FadeImage
                  src={step.imageUrl}
                  alt={`${step.stepNo}단계`}
                  className={styles.image}
                  loading="lazy"
                />
              )}
            </div>
          </li>
        ))}
      </ol>
      {tip && (
        <aside className={styles.tip} aria-label="조리 팁">
          <Typography.Text strong>조리 팁</Typography.Text>
          <Typography.Paragraph className={styles.tipText}>{tip}</Typography.Paragraph>
        </aside>
      )}
    </section>
  );
}
