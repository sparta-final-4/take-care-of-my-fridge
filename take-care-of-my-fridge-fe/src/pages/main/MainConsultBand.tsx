import { Button, Typography } from 'antd';

import { useChatModalStore } from '@/domains/chat/model/useChatModalStore';

import { MAIN_PHOTOS } from './mainPhotos';
import styles from './MainConsultBand.module.scss';

// 한 화면을 꽉 채우는 잉크색 띠. 가운데 본문 폭 안에서 왼쪽은 밤에 열어 본 냉장고 사진, 오른쪽은 AI 상담 소개와
// 기존 상담 창을 여는 버튼. 휴대폰은 사진(화면 끝까지) 위, 글 아래.
export function MainConsultBand() {
  const openChat = useChatModalStore((state) => state.openChat);
  const photo = MAIN_PHOTOS.consult;

  return (
    <section className={styles.band} aria-labelledby="main-consult-title">
      <div className={styles.inner}>
        <div className={styles.media}>
          <img
            src={photo.src}
            srcSet={photo.srcSet}
            sizes={photo.sizes}
            width={photo.width}
            height={photo.height}
            alt={photo.alt}
            loading="lazy"
            className={styles.image}
          />
        </div>
        <div className={styles.text}>
          <span className={styles.eyebrow}>AI Consult</span>
          <Typography.Title id="main-consult-title" level={2} className={styles.title}>
            오늘 저녁,
            <br />
            남은 재료로 뭘 만들까요?
          </Typography.Title>
          <Typography.Paragraph className={styles.description}>
            AI 상담에 냉장고 재료를 고르면 그 재료로 만들 요리를 추천해 드려요.
          </Typography.Paragraph>
          <div>
            <Button type="primary" size="large" onClick={() => openChat()}>
              AI 상담 시작하기
            </Button>
          </div>
        </div>
      </div>
    </section>
  );
}
