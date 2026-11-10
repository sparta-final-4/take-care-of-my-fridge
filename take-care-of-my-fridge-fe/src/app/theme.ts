import type { ThemeConfig } from 'antd';

// antd v6는 CSS 변수(--ant-*)가 기본으로 켜져 있어 cssVar 설정이 필요 없다.
// 색·모서리는 Hallmark Furrow 테마(OKLCH)를 hex로 옮긴 값이다. 기준과 사유는 DESIGN-ROLE.md.
// 바탕은 따뜻한 마닐라지, 글자는 거의 검정, 신호색은 귤색 하나(글자색으로 쓰지 않고 잉크 글자를 얹는 채움으로만),
// 초록은 "아직 괜찮은 것"에만 쓴다.
const PAPER = '#f5f1ea'; // oklch(96% 0.011 85) 화면 바탕
const PAPER_2 = '#fffdfa'; // oklch(99.5% 0.005 85) 입력칸·떠 있는 면
const PAPER_3 = '#e9e4d9'; // oklch(92% 0.016 85) 옅은 칸(well)
const INK = '#14110f'; // oklch(18% 0.006 60)
const INK_2 = '#2c2825'; // oklch(28% 0.008 60)
const MUTED = '#58514c'; // oklch(44% 0.012 60) 보조 글자, 바탕 대비 6.9:1
const TERTIARY = '#7a736e'; // oklch(56% 0.012 60) 3차 글자, 4.5:1 이상
const FIELD_EDGE = '#8c857f'; // oklch(62% 0.012 60) 입력칸 테두리, 3:1 이상
const RULE = '#dbd7ce'; // oklch(88% 0.013 85) 줄 사이 얇은 선
const HOVER = '#eeeae2'; // oklch(94% 0.012 85) 바탕 위 마우스 올림
const TANGERINE = '#fc6b33'; // oklch(70% 0.19 40) 잉크 글자와 6.5:1
const TANGERINE_HOVER = '#ee5e23'; // oklch(66% 0.19 40) 마우스 올림: 조금 진하게
const TANGERINE_ACTIVE = '#d84a00'; // oklch(60% 0.19 40) 누름
// 입력 중 고리: 귤색 번짐 대신 옅은 종이색 3px 고리(잉크 테두리와 함께 포커스를 보인다)
const FOCUS_RING = `0 0 0 3px ${PAPER_3}`;
const GREEN = '#246e3a'; // oklch(48% 0.11 150)
const RED = '#c22826'; // oklch(53% 0.19 27)
const AMBER = '#8a5500'; // 옅은 경고 바탕 위 글자 4.5:1 이상
// 경고 안내 바탕·테두리. antd가 진한 AMBER에서 계산하면 탁한 회갈색(#c9c5b5)이 나와 직접 정한다.
// AMBER 글자와 5.4:1
const AMBER_BG = '#f8ecd4';
const AMBER_BORDER = '#e3c690';

export const theme: ThemeConfig = {
  token: {
    colorPrimary: TANGERINE,
    colorSuccess: GREEN,
    colorWarning: AMBER,
    colorWarningBg: AMBER_BG,
    colorWarningBorder: AMBER_BORDER,
    colorError: RED,
    colorInfo: INK_2,
    // 귤색은 글자색으로 쓰지 않는다. 링크는 잉크, 마우스를 올리면 보조색.
    colorLink: INK,
    colorLinkHover: MUTED,
    colorLinkActive: INK,
    colorText: INK,
    colorTextHeading: INK,
    colorTextSecondary: MUTED,
    colorTextTertiary: TERTIARY,
    colorTextQuaternary: FIELD_EDGE,
    colorBgLayout: PAPER,
    colorBgContainer: PAPER_2,
    colorBgElevated: PAPER_2,
    colorBorder: FIELD_EDGE,
    colorBorderSecondary: RULE,
    colorFillSecondary: PAPER_3,
    colorFillTertiary: PAPER_3,
    colorFillQuaternary: HOVER,
    borderRadius: 6,
    borderRadiusLG: 10,
    borderRadiusSM: 4,
    controlHeight: 40,
    fontWeightStrong: 600,
    fontFamily: "'Pretendard Variable', Pretendard, -apple-system, BlinkMacSystemFont, sans-serif",
  },
  components: {
    Layout: {
      bodyBg: PAPER,
      headerBg: PAPER,
    },
    // 버튼(레퍼런스 row7seeds 버튼 구성 + Hallmark 컨트롤 원칙): 44px 알약 모양, 굵은 글자, 넉넉한 좌우 여백.
    // 효과는 하나만: 귤색 버튼은 마우스를 올리면 조금 진해지고, 테두리 버튼은 잉크색으로 채워진다(레퍼런스의 채워지는 버튼).
    Button: {
      // 귤색 버튼 위 글자는 잉크(흰 글자는 대비가 모자란다).
      primaryColor: INK,
      primaryShadow: 'none',
      defaultShadow: 'none',
      fontWeight: 600,
      controlHeight: 44,
      controlHeightLG: 52,
      controlHeightSM: 32,
      borderRadius: 999,
      borderRadiusLG: 999,
      borderRadiusSM: 999,
      paddingInline: 20,
      paddingInlineLG: 28,
      colorPrimaryHover: TANGERINE_HOVER,
      colorPrimaryActive: TANGERINE_ACTIVE,
      defaultBg: 'transparent',
      defaultColor: INK,
      defaultBorderColor: INK,
      defaultHoverBg: INK,
      defaultHoverColor: PAPER,
      defaultHoverBorderColor: INK,
      defaultActiveBg: INK_2,
      defaultActiveColor: PAPER,
      defaultActiveBorderColor: INK_2,
    },
    // 입력칸·선택칸·날짜칸(레퍼런스 입력칸 구성): 44px, 둥근 10px. 마우스를 올리면 테두리가 잉크색,
    // 입력 중에는 잉크 테두리 + 옅은 종이색 고리(귤색 번짐 대신). 테두리 기본색은 3:1 대비(FIELD_EDGE) 유지.
    Input: {
      controlHeight: 44,
      borderRadius: 10,
      paddingInline: 14,
      hoverBorderColor: INK,
      activeBorderColor: INK,
      activeShadow: FOCUS_RING,
    },
    InputNumber: {
      controlHeight: 44,
      borderRadius: 10,
      hoverBorderColor: INK,
      activeBorderColor: INK,
      activeShadow: FOCUS_RING,
    },
    Select: {
      controlHeight: 44,
      borderRadius: 10,
      hoverBorderColor: INK,
      activeBorderColor: INK,
      activeOutlineColor: PAPER_3,
    },
    DatePicker: {
      controlHeight: 44,
      borderRadius: 10,
      hoverBorderColor: INK,
      activeBorderColor: INK,
      activeShadow: FOCUS_RING,
    },
    Tag: {
      defaultBg: PAPER_3,
    },
    // 귤색을 글자·가는 선으로 쓰지 않는 규칙에 맞춘다. 현재 페이지는 귤색 채움 위 잉크 숫자(6.5:1).
    Pagination: {
      itemActiveBg: TANGERINE,
      itemActiveColor: INK,
      itemActiveColorHover: INK,
    },
    // 정렬한 열의 본문을 다른 칸과 같은 바탕으로 둔다. 기본값은 마우스를 올린 행과 같은 색이라 둘이 겹쳐 십자 모양이 된다.
    // 어느 열로 정렬했는지는 헤더 칸 색과 정렬 화살표가 보여 준다.
    Table: {
      bodySortBg: PAPER_2,
    },
    // 체크한 칸은 귤색 채움 그대로, 체크 표시를 흰색 대신 잉크로(흰색은 귤색 위 3:1 정도).
    Checkbox: {
      colorWhite: INK,
    },
  },
};
