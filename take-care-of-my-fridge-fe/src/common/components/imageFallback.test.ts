import { describe, expect, it } from 'vitest';

import { getFallbackCaption, getImageSrc } from './imageFallback';

describe('getImageSrc', () => {
  it.each([undefined, null, '', '   '])('주소가 없으면(%j) 처음부터 기본 이미지', (src) => {
    expect(getImageSrc(src, undefined)).toBeUndefined();
  });

  it('주소가 있으면 그 주소를 보여 준다', () => {
    expect(getImageSrc('https://example.com/a.jpg', undefined)).toBe('https://example.com/a.jpg');
  });

  it('불러오기에 실패한 주소는 다시 요청하지 않고 기본 이미지', () => {
    const src = 'https://example.com/broken.jpg';
    expect(getImageSrc(src, src)).toBeUndefined();
  });

  it('실패한 뒤 다른 주소로 바뀌면 새 주소를 불러온다', () => {
    expect(getImageSrc('https://example.com/b.jpg', 'https://example.com/a.jpg')).toBe(
      'https://example.com/b.jpg',
    );
  });
});

describe('getFallbackCaption', () => {
  it('기본 크기는 "이미지 준비 중"을 보여 준다', () => {
    expect(getFallbackCaption('default')).toBe('이미지 준비 중');
  });

  it('작은 크기는 글자 없이 아이콘만', () => {
    expect(getFallbackCaption('small')).toBeUndefined();
  });
});
