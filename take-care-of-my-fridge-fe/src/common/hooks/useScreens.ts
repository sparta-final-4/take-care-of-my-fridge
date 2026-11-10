import { Grid } from 'antd';

// 화면(창) 폭 기준 분기. styles/_mixins.scss의 from()과 같은 기준($breakpoints)이다.
export function useScreens() {
  return Grid.useBreakpoint();
}
