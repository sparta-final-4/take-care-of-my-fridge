import { describe, expect, it } from 'vitest';

import { onlyDigits } from './paymentPin';

describe('onlyDigits', () => {
  it('숫자가 아닌 글자를 지우고 6자리까지만 남긴다', () => {
    expect(onlyDigits('12a3 4-56')).toBe('123456');
    expect(onlyDigits('12345678')).toBe('123456');
  });

  it('값이 없으면 빈 문자열이다', () => {
    expect(onlyDigits(undefined)).toBe('');
  });
});
