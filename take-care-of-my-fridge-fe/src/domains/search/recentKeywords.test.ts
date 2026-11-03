import { beforeEach, describe, expect, it } from 'vitest';

import {
  addRecentKeyword,
  clearRecentKeywords,
  getRecentKeywords,
  removeRecentKeyword,
} from './recentKeywords';

describe('recentKeywords', () => {
  beforeEach(() => {
    clearRecentKeywords();
  });

  it('최근에 검색한 말을 맨 앞에 두고 같은 말은 한 번만 남긴다', () => {
    addRecentKeyword('김치');
    addRecentKeyword('두부');
    addRecentKeyword(' 김치 ');

    expect(getRecentKeywords()).toEqual(['김치', '두부']);
  });

  it('10개를 넘으면 가장 오래된 검색어를 버린다', () => {
    for (let i = 1; i <= 11; i += 1) addRecentKeyword(`검색어${i}`);

    expect(getRecentKeywords()).toHaveLength(10);
    expect(getRecentKeywords()[0]).toBe('검색어11');
    expect(getRecentKeywords()).not.toContain('검색어1');
  });

  it('빈 검색어는 저장하지 않는다', () => {
    addRecentKeyword('   ');

    expect(getRecentKeywords()).toEqual([]);
  });

  it('하나씩 지우거나 모두 지울 수 있다', () => {
    addRecentKeyword('김치');
    addRecentKeyword('두부');

    removeRecentKeyword('김치');
    expect(getRecentKeywords()).toEqual(['두부']);

    clearRecentKeywords();
    expect(getRecentKeywords()).toEqual([]);
  });
});
