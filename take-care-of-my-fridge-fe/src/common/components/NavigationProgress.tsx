import { useNavigation } from 'react-router';

import styles from './NavigationProgress.module.scss';

// 다음 화면 코드를 받는 동안 상단에 진행 막대를 보여 준다.
export function NavigationProgress() {
  const navigation = useNavigation();

  if (navigation.state === 'idle') return null;
  return <div className={styles.bar} role="progressbar" aria-label="화면을 불러오는 중" />;
}
