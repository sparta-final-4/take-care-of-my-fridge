import { Typography } from 'antd';

import { MAIN_PHOTOS } from './mainPhotos';
import styles from './MainHero.module.scss';

// 메인 첫 화면. 냉장고 안 사진이 한 화면을 꽉 채우고, 왼쪽 아래에 제목·설명만 얹는다(버튼 없이 사진과 글로만).
// 오른쪽 아래의 스크롤 표시가 아래 띠로 이어진다는 것을 알려 준다.
export function MainHero() {
  const photo = MAIN_PHOTOS.hero;

  return (
    <section className={styles.hero} aria-labelledby="main-hero-title">
      <img
        src={photo.src}
        srcSet={photo.srcSet}
        sizes={photo.sizes}
        width={photo.width}
        height={photo.height}
        alt={photo.alt}
        fetchPriority="high"
        className={styles.image}
      />
      <div className={styles.inner}>
        <div className={styles.text}>
          <span className={styles.eyebrow}>Take care of my fridge</span>
          {/* 서비스 이름은 상단 로고가 맡고, 첫 화면 제목은 서비스가 하는 일을 말한다. */}
          <Typography.Title id="main-hero-title" level={1} className={styles.title}>
            남은 재료,
            <br />
            버리기 전에 요리로
          </Typography.Title>
          <Typography.Paragraph className={styles.lede}>
            재료를 적어 두면 소비기한을 챙기고, 그 재료로 만들 요리를 찾아 드려요.
          </Typography.Paragraph>
        </div>
        <span className={styles.scrollCue} aria-hidden>
          Scroll
        </span>
      </div>
    </section>
  );
}
