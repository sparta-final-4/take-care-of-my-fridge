import { Typography } from 'antd';

import { MAIN_PHOTOS } from './mainPhotos';
import styles from './MainHero.module.scss';

// 메인 첫 화면. 냉장고 안 사진이 화면 폭을 다 덮고, 왼쪽 아래에 제목·설명을 얹는다. 화면 이동은 헤더 메뉴가 맡는다.
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
      </div>
    </section>
  );
}
