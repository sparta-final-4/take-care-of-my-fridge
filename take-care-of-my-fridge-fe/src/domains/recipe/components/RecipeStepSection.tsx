import { ExpandOutlined } from '@ant-design/icons';
import { Button, Typography } from 'antd';
import { useState } from 'react';

import type { StepItem } from '@/common/api/clients';
import { FadeImage } from '@/common/components/FadeImage';

import { RecipeStepFullscreen } from './RecipeStepFullscreen';
import styles from './RecipeStepSection.module.scss';

interface RecipeStepSectionProps {
  steps: StepItem[];
  tip?: string;
}

// 레퍼런스 레시피 글의 조리법 칸: 큰 번호(01, 02 …)와 문단, 단계 팁은 아래 작은 글씨, 전체 팁은 맨 아래 메모.
// 제목 옆 [전체보기]는 한 단계씩 크게 보는 화면을 연다(RecipeStepFullscreen).
export function RecipeStepSection({ steps, tip }: RecipeStepSectionProps) {
  const [fullscreenOpen, setFullscreenOpen] = useState(false);

  return (
    <section className={styles.section} aria-label="조리 순서">
      <div className={styles.head}>
        <Typography.Title level={2} className={styles.title}>
          조리 순서
        </Typography.Title>
        {steps.length > 0 && (
          <Button
            icon={<ExpandOutlined />}
            onClick={() => setFullscreenOpen(true)}
            className={styles.fullscreenButton}
          >
            전체보기
          </Button>
        )}
      </div>
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
      <RecipeStepFullscreen
        open={fullscreenOpen}
        steps={steps}
        tip={tip}
        onClose={() => setFullscreenOpen(false)}
      />
    </section>
  );
}
