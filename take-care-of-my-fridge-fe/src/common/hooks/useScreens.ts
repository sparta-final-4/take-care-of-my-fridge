import { Grid } from 'antd';
import { useSyncExternalStore } from 'react';

// 마우스를 쓰는 PC. styles/_variables.scss의 $desktop-pointer와 같은 조건이다.
const DESKTOP_POINTER_QUERY = '(hover: hover) and (pointer: fine)';

function subscribe(onChange: () => void) {
  const media = window.matchMedia(DESKTOP_POINTER_QUERY);
  media.addEventListener('change', onChange);
  return () => media.removeEventListener('change', onChange);
}

const isDesktopPointer = () => window.matchMedia(DESKTOP_POINTER_QUERY).matches;

// Grid.useBreakpoint()와 같은 모양. 마우스를 쓰는 PC는 창을 줄여도 PC 화면(xl, 1200px)까지의 분기를 늘 켠다
// (SCSS from() 믹스인과 같은 기준). xxl은 PC에서도 실제 창 폭으로 정한다. 휴대폰·터치 기기는 그대로 폭을 따른다.
export function useScreens() {
  const screens = Grid.useBreakpoint();
  const desktop = useSyncExternalStore(subscribe, isDesktopPointer, () => false);
  if (!desktop) return screens;
  return { ...screens, xs: false, sm: true, md: true, lg: true, xl: true };
}
