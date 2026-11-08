import { CloseOutlined, LeftOutlined, ReloadOutlined } from '@ant-design/icons';
import { Button, Modal, Progress, Typography } from 'antd';
import { useEffect, useState } from 'react';

import type { StepItem } from '@/common/api/clients';
import { FadeImage } from '@/common/components/FadeImage';

import styles from './RecipeStepFullscreen.module.scss';

interface RecipeStepFullscreenProps {
  open: boolean;
  steps: StepItem[];
  tip?: string;
  onClose: () => void;
}

// 휴대폰을 옆에 두고 요리할 때 보는 조리 순서 전체보기. 화면 아무 곳이나 누르면 다음 단계, 마지막 다음은 끝 화면.
// 브라우저가 지원하면 실제 전체 화면으로 바꾸고, 보는 동안 화면이 꺼지지 않게 한다(아이폰 등 미지원 기기는 화면을 덮기만 한다).
export function RecipeStepFullscreen({ open, steps, tip, onClose }: RecipeStepFullscreenProps) {
  const [index, setIndex] = useState(0);
  const finished = index >= steps.length;
  const step = steps[index];

  const handleNext = () => setIndex((current) => Math.min(current + 1, steps.length));
  const handlePrev = () => setIndex((current) => Math.max(current - 1, 0));

  // 키보드: 오른쪽·왼쪽 화살표로 넘긴다(Enter·Space는 포커스된 화면 버튼이 받는다, Esc는 Modal이 닫는다).
  useEffect(() => {
    if (!open) return;
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'ArrowRight' || event.key === 'PageDown') {
        setIndex((current) => Math.min(current + 1, steps.length));
      }
      if (event.key === 'ArrowLeft' || event.key === 'PageUp') {
        setIndex((current) => Math.max(current - 1, 0));
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [open, steps.length]);

  // 브라우저 전체 화면. 열 때 들어가고 닫을 때(또는 화면을 떠날 때) 나온다.
  useEffect(() => {
    if (!open || !document.fullscreenEnabled) return;
    document.documentElement.requestFullscreen().catch(() => undefined);
    return () => {
      if (document.fullscreenElement) document.exitFullscreen().catch(() => undefined);
    };
  }, [open]);

  // 보는 동안 화면 꺼짐 방지. 다른 앱에 다녀오면 브라우저가 풀어 버리므로 돌아올 때 다시 요청한다.
  useEffect(() => {
    if (!open || !('wakeLock' in navigator)) return;
    let lock: WakeLockSentinel | undefined;
    let cancelled = false;
    const request = () => {
      if (document.visibilityState !== 'visible') return;
      navigator.wakeLock
        .request('screen')
        .then((sentinel) => {
          if (cancelled) void sentinel.release();
          else lock = sentinel;
        })
        .catch(() => undefined);
    };
    request();
    document.addEventListener('visibilitychange', request);
    return () => {
      cancelled = true;
      document.removeEventListener('visibilitychange', request);
      void lock?.release();
    };
  }, [open]);

  return (
    <Modal
      open={open}
      onCancel={onClose}
      afterOpenChange={(opened) => !opened && setIndex(0)}
      footer={null}
      closable={false}
      width="100%"
      title="조리 순서 전체보기"
      className={styles.modal}
      classNames={{ container: styles.container, body: styles.body, header: styles.header }}
    >
      <div className={styles.screen}>
        {/* 화면 전체를 덮는 "다음" 버튼. 글·사진은 그 위에 얹되 클릭은 통과시켜 어디를 눌러도 넘어간다. */}
        {!finished && (
          <button
            type="button"
            className={styles.stage}
            onClick={handleNext}
            aria-label="다음 단계"
          />
        )}

        <div className={styles.top}>
          <Typography.Text strong className={styles.counter}>
            {finished ? '완료' : `${index + 1} / ${steps.length}`}
          </Typography.Text>
          <Button
            type="text"
            size="large"
            icon={<CloseOutlined />}
            onClick={onClose}
            aria-label="전체보기 닫기"
            className={styles.control}
          />
        </div>
        <Progress
          percent={(Math.min(index + 1, steps.length) / Math.max(steps.length, 1)) * 100}
          showInfo={false}
          size="small"
          className={styles.progress}
        />

        <div className={styles.content} aria-live="polite">
          {finished ? (
            <div className={styles.finish}>
              <span className={styles.finishTitle}>조리 끝!</span>
              {tip && (
                <aside className={styles.tip} aria-label="조리 팁">
                  <Typography.Text strong>조리 팁</Typography.Text>
                  <Typography.Paragraph className={styles.tipText}>{tip}</Typography.Paragraph>
                </aside>
              )}
              <div className={styles.finishActions}>
                <Button size="large" icon={<ReloadOutlined />} onClick={() => setIndex(0)}>
                  처음부터
                </Button>
                <Button type="primary" size="large" onClick={onClose}>
                  닫기
                </Button>
              </div>
            </div>
          ) : (
            step && (
              <>
                <span className={styles.number} aria-hidden="true">
                  {String(step.stepNo ?? index + 1).padStart(2, '0')}
                </span>
                <p className={styles.description}>{step.description}</p>
                {step.tip && (
                  <Typography.Text type="secondary" className={styles.stepTip}>
                    {step.tip}
                  </Typography.Text>
                )}
                {step.imageUrl && (
                  <FadeImage
                    src={step.imageUrl}
                    alt={`${step.stepNo ?? index + 1}단계`}
                    className={styles.image}
                  />
                )}
              </>
            )
          )}
        </div>

        <div className={styles.bottom}>
          <Button
            size="large"
            icon={<LeftOutlined />}
            onClick={handlePrev}
            disabled={index === 0}
            className={styles.control}
          >
            이전
          </Button>
          {!finished && (
            <Typography.Text type="secondary" className={styles.hint}>
              화면을 누르면 다음 단계로 넘어갑니다
            </Typography.Text>
          )}
        </div>
      </div>
    </Modal>
  );
}
