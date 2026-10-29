import styles from './RouteFallback.module.scss';

// 화면 코드·로그인 상태를 기다리는 동안 본문 자리에 보이는 로딩.
// index.html의 첫 로딩과 같은 모양으로, 금방 끝나는 로딩에서는 보이지 않도록 잠시 뒤에 나타난다.
export function RouteFallback() {
  return (
    <div className={styles.fallback} role="status" aria-label="불러오는 중">
      <img className={styles.logo} src="/favicon.svg" alt="" />
    </div>
  );
}
