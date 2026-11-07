import type { ThemeConfig } from 'antd';

// 로그인·회원가입 화면만: 입력칸과 버튼을 모두 큰 크기(48px)로 맞춘다(Furrow 테마의 48px 키).
// 화면 전체 테마(app/theme.ts)는 그대로 두고 이 화면 안에서만 덮어쓴다.
export const AUTH_THEME: ThemeConfig = {
  token: {
    controlHeightLG: 48,
  },
};
