import { BulbOutlined } from '@ant-design/icons';
import { Alert, Flex, Typography } from 'antd';

import type { StepItem } from '@/common/api/clients';
import { FadeImage } from '@/common/components/FadeImage';
import { SectionCard } from '@/common/components/SectionCard';

import styles from './RecipeStepSection.module.scss';

interface RecipeStepSectionProps {
  steps: StepItem[];
  tip?: string;
}

export function RecipeStepSection({ steps, tip }: RecipeStepSectionProps) {
  return (
    <SectionCard title="조리 순서">
      <Flex vertical gap="middle">
        <ol className={styles.list}>
          {steps.map((step) => (
            <li key={step.stepNo} className={styles.step}>
              <span className={styles.number} aria-hidden="true">
                {step.stepNo}
              </span>
              <Flex vertical gap={4} className={styles.body}>
                <Typography.Text>{step.description}</Typography.Text>
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
              </Flex>
            </li>
          ))}
        </ol>
        {tip && (
          <Alert type="info" showIcon icon={<BulbOutlined />} title="조리 팁" description={tip} />
        )}
      </Flex>
    </SectionCard>
  );
}
