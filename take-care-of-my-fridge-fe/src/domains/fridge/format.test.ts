import { describe, expect, it } from 'vitest';

import { withObjectParticle } from './format';

describe('withObjectParticle', () => {
  it('받침이 있으면 을, 없으면 를을 붙인다', () => {
    expect(withObjectParticle('계란')).toBe('계란을');
    expect(withObjectParticle('두부')).toBe('두부를');
  });

  it('한글로 끝나지 않으면 을(를)을 붙인다', () => {
    expect(withObjectParticle('두부 300g')).toBe('두부 300g을(를)');
  });
});
