import dayjs from 'dayjs';
import 'dayjs/locale/ko';
import { beforeAll, describe, expect, it } from 'vitest';

import { formatChatDateTime, formatChatTime, formatEndedLabel } from './format';

describe('formatChatDateTime', () => {
  beforeAll(() => {
    dayjs.locale('ko');
  });

  it('MM.DD 오전/오후 h:mm로 보여 준다', () => {
    expect(formatChatDateTime('2026-10-28 14:05:00')).toBe('10.28 오후 2:05');
    expect(formatChatDateTime('2026-01-03 09:30:00')).toBe('01.03 오전 9:30');
  });

  it('값이 없으면 -를 보여 준다', () => {
    expect(formatChatDateTime(undefined)).toBe('-');
  });
});

describe('formatChatTime', () => {
  it('HH:mm로 보여 주고, 값이 없으면 비운다', () => {
    expect(formatChatTime('2026-10-28 14:05:00')).toBe('14:05');
    expect(formatChatTime('2026-10-28 09:05:00')).toBe('09:05');
    expect(formatChatTime(undefined)).toBe('');
  });
});

describe('formatEndedLabel', () => {
  it('요리 완료로 끝났으면 요리 이름을 붙이고, 아니면 끝났다고만 한다', () => {
    expect(formatEndedLabel('두부조림')).toBe('두부조림 요리 완료로 상담이 끝났어요');
    expect(formatEndedLabel(undefined)).toBe('상담이 끝났어요');
    expect(formatEndedLabel('')).toBe('상담이 끝났어요');
  });
});
