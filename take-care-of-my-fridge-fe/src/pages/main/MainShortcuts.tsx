import { Link } from 'react-router';

import { ROUTES } from '@/app/paths';

import { MAIN_PHOTOS, type MainPhoto } from './mainPhotos';
import styles from './MainShortcuts.module.scss';

// 서비스의 핵심 세 가지. 메인에서는 사진과 한 줄 설명만 보여 주고, 누르면 그 화면으로 들어간다.
const SHORTCUTS: { label: string; description: string; to: string; photo: MainPhoto }[] = [
  {
    label: '내 냉장고',
    description: '재료와 소비기한을 한곳에서 확인해요.',
    to: ROUTES.fridge,
    photo: MAIN_PHOTOS.fridge,
  },
  {
    label: '레시피',
    description: '가진 재료로 만들 수 있는 요리를 찾아요.',
    to: ROUTES.recipes,
    photo: MAIN_PHOTOS.recipes,
  },
  {
    label: '재료/밀키트',
    description: '모자란 재료와 밀키트를 주문해요.',
    to: ROUTES.products,
    photo: MAIN_PHOTOS.products,
  },
];

export function MainShortcuts() {
  return (
    <nav aria-label="바로가기" className={styles.band}>
      <ul className={styles.list}>
        {SHORTCUTS.map((item) => (
          <li key={item.to}>
            <Link to={item.to} className={styles.tile}>
              <img
                src={item.photo.src}
                srcSet={item.photo.srcSet}
                sizes={item.photo.sizes}
                width={item.photo.width}
                height={item.photo.height}
                alt={item.photo.alt}
                loading="lazy"
                className={styles.image}
              />
              <span className={styles.label}>{item.label}</span>
              <span className={styles.description}>{item.description}</span>
            </Link>
          </li>
        ))}
      </ul>
    </nav>
  );
}
