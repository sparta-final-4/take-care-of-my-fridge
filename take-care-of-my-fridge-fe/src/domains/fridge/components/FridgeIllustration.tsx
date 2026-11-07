import styles from './FridgeIllustration.module.scss';

interface FridgeIllustrationProps {
  // happy: 웃는 냉장고, worried: 임박·기한 지난 재료가 있을 때 땀 흘리는 냉장고,
  // empty: 입을 동그랗게 벌린 냉장고(비어 있을 때)
  mood?: 'happy' | 'worried' | 'empty';
  className?: string;
}

// 내 냉장고 화면의 장식용 냉장고 일러스트. 사이트 색(잉크 선·종이색 몸통·귤색 포인트)만 쓴다.
// 위칸(냉동)에 귤색 자석과 메모지, 아래칸(냉장) 문에 얼굴. 장식이라 화면 읽기에서 뺀다.
export function FridgeIllustration({ mood = 'happy', className }: FridgeIllustrationProps) {
  return (
    <svg
      viewBox="0 0 120 140"
      aria-hidden="true"
      focusable="false"
      className={className ? `${styles.svg} ${className}` : styles.svg}
    >
      {/* 몸통 */}
      <rect className={styles.body} x="20" y="8" width="80" height="120" rx="16" />
      {/* 위칸(냉동)과 아래칸(냉장) 사이 */}
      <line className={styles.line} x1="20" y1="48" x2="100" y2="48" />
      {/* 손잡이 */}
      <rect className={styles.ink} x="84" y="20" width="5" height="16" rx="2.5" />
      <rect className={styles.ink} x="84" y="58" width="5" height="26" rx="2.5" />
      {/* 위칸: 메모지와 귤색 자석 */}
      <rect
        className={styles.note}
        x="44"
        y="18"
        width="22"
        height="20"
        rx="2"
        transform="rotate(-6 55 28)"
      />
      <line className={styles.noteLine} x1="48" y1="26" x2="61" y2="24.6" />
      <line className={styles.noteLine} x1="48.5" y1="31" x2="58" y2="30" />
      <circle className={styles.accent} cx="55" cy="18" r="4" />
      {/* 아래칸: 얼굴 */}
      <ellipse className={styles.blush} cx="40" cy="92" rx="6" ry="3.5" />
      <ellipse className={styles.blush} cx="74" cy="92" rx="6" ry="3.5" />
      <circle className={styles.ink} cx="48" cy="82" r="3.5" />
      <circle className={styles.ink} cx="66" cy="82" r="3.5" />
      {mood === 'happy' && <path className={styles.line} d="M50 92 Q57 100 64 92" />}
      {mood === 'worried' && (
        <>
          {/* 물결 입과 이마의 땀방울 */}
          <path className={styles.line} d="M49 95 Q53 91 57 95 T65 95" />
          <path
            className={styles.sweat}
            d="M76 64 C76 64 71 71 71 74 A5 5 0 0 0 81 74 C81 71 76 64 76 64 Z"
          />
        </>
      )}
      {mood === 'empty' && <ellipse className={styles.mouth} cx="57" cy="95" rx="4" ry="5" />}
      {/* 발 */}
      <rect className={styles.ink} x="32" y="126" width="12" height="7" rx="3" />
      <rect className={styles.ink} x="76" y="126" width="12" height="7" rx="3" />
    </svg>
  );
}
