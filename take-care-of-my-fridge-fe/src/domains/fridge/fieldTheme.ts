import type { ThemeConfig } from 'antd';

// 재료 추가·수정 팝업의 입력칸 높이. 사이트 기본 입력칸(app/theme.ts, 44px)보다 낮춰 팝업을 가볍게 한다.
const FIELD_HEIGHT = 40;

export const COMPACT_FIELD_THEME: ThemeConfig = {
  components: {
    Input: { controlHeight: FIELD_HEIGHT },
    InputNumber: { controlHeight: FIELD_HEIGHT },
    Select: { controlHeight: FIELD_HEIGHT },
    DatePicker: { controlHeight: FIELD_HEIGHT },
  },
};
