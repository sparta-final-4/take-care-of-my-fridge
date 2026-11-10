import { describe, expect, it } from 'vitest';

import { DEFAULT_CONSULT_TEXT, imminentConsultText, selectedConsultText } from './consultText';

describe('selectedConsultText', () => {
  it('세 개까지는 이름을 모두 쓴다', () => {
    expect(selectedConsultText(['배추', '대파'])).toBe('배추, 대파로 만들 요리를 추천해 드려요.');
    expect(selectedConsultText(['대파', '감자', '양송이버섯'])).toBe(
      '대파, 감자, 양송이버섯으로 만들 요리를 추천해 드려요.',
    );
  });

  it('세 개를 넘으면 외 N개로 줄인다', () => {
    expect(selectedConsultText(['배추', '대파', '무', '두부', '김치'])).toBe(
      '배추, 대파, 무 외 2개로 만들 요리를 추천해 드려요.',
    );
  });

  it('넘길 재료가 없으면 기본 문장', () => {
    expect(selectedConsultText([])).toBe(DEFAULT_CONSULT_TEXT);
  });
});

describe('imminentConsultText', () => {
  it('가장 급한 재료를 앞세우고 나머지는 외 N개', () => {
    expect(
      imminentConsultText([
        { rawName: '대파', dDay: 3 },
        { rawName: '서울우유', dDay: 0 },
        { rawName: '애호박', dDay: 1 },
        { rawName: '달걀', dDay: 2 },
      ]),
    ).toBe('오늘까지인 서울우유 외 3개로 만들 요리를 추천해 드려요.');
  });

  it('하나뿐이면 이름에 조사를 붙이고, 3일 남으면 "3일 남은"', () => {
    expect(imminentConsultText([{ rawName: '대파', dDay: 3 }])).toBe(
      '3일 남은 대파로 만들 요리를 추천해 드려요.',
    );
    expect(imminentConsultText([{ rawName: '양송이버섯', dDay: 1 }])).toBe(
      '내일까지인 양송이버섯으로 만들 요리를 추천해 드려요.',
    );
  });

  it('임박 재료가 없으면 기본 문장', () => {
    expect(imminentConsultText([])).toBe('냉장고 재료로 만들 요리를 추천해 드려요.');
  });
});
