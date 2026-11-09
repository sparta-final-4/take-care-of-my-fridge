import { GithubOutlined } from '@ant-design/icons';
import { Typography } from 'antd';

import { MAIN_PHOTOS } from '@/pages/main/mainPhotos';

import styles from './SiteFooter.module.scss';

const GITHUB_URL = 'https://github.com/sparta-final-4/take-care-of-my-fridge';

// 모든 화면 맨 아래 흰 띠(MainLayout). 서비스 소개 한 줄과 GitHub 저장소, 사진 출처(CC BY-SA 라이선스 조건). 화면 바로가기는 헤더 메뉴에 있어 두지 않는다.
export function SiteFooter() {
  return (
    <footer className={styles.band}>
      <div className={styles.inner}>
        <div className={styles.about}>
          <div className={styles.intro}>
            <Typography.Text className={styles.wordmark}>냉장고를 부탁해</Typography.Text>
            <Typography.Paragraph className={styles.description}>
              남은 재료를 기록하고, 소비기한을 챙기고, 그 재료로 요리하도록 돕는 냉장고 관리
              서비스예요.
            </Typography.Paragraph>
          </div>
          <a
            href={GITHUB_URL}
            target="_blank"
            rel="noreferrer"
            aria-label="GitHub 저장소 (새 창)"
            className={styles.github}
          >
            <GithubOutlined />
          </a>
        </div>

        <div className={styles.credits}>
          <Typography.Text type="secondary" className={styles.creditsHeading}>
            사진 출처: Wikimedia Commons, Unsplash
          </Typography.Text>
          <ul className={styles.creditList}>
            {Object.values(MAIN_PHOTOS).map((photo) => (
              <li key={photo.src}>
                <a href={photo.page} target="_blank" rel="noreferrer" className={styles.creditLink}>
                  {photo.author}
                </a>
                , {photo.license}
                {'modification' in photo && ` (${photo.modification})`}
              </li>
            ))}
          </ul>
        </div>
      </div>
    </footer>
  );
}
