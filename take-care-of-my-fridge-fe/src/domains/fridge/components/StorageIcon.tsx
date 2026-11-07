import type { StorageType } from '../api';
import styles from './StorageIcon.module.scss';

interface StorageIconProps {
  storage: StorageType;
}

// 보관 탭 앞의 작은 아이콘(장식): 냉장 = 냉장고, 냉동 = 눈송이, 상온 = 바구니. 잉크 선 하나로 그린다.
export function StorageIcon({ storage }: StorageIconProps) {
  return (
    <svg viewBox="0 0 16 16" aria-hidden="true" focusable="false" className={styles.icon}>
      {storage === 'REFRIGERATED' && (
        <>
          <rect x="3.5" y="1.5" width="9" height="13" rx="2" />
          <path d="M3.5 6h9M10.5 3.5v1M10.5 8v2.5" />
        </>
      )}
      {storage === 'FROZEN' && (
        <path d="M8 1.5v13M2.4 4.75l11.2 6.5M2.4 11.25l11.2-6.5M6.5 2.5 8 4l1.5-1.5M6.5 13.5 8 12l1.5 1.5" />
      )}
      {storage === 'ROOM_TEMP' && (
        <>
          <path d="M2 6.5h12l-1.4 7a1 1 0 0 1-1 .8H4.4a1 1 0 0 1-1-.8z" />
          <path d="M5 6.5 7 2M11 6.5 9 2" />
        </>
      )}
    </svg>
  );
}
