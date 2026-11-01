export type FallbackImageSize = 'default' | 'small';

// 보여 줄 이미지 주소. 비어 있거나 이미 불러오기에 실패한 주소면 undefined라 기본 이미지를 보여 준다.
// 실패한 주소를 기억해 두므로 같은 주소를 다시 요청하지 않고, 다른 주소로 바뀌면 새로 불러온다.
export function getImageSrc(src: string | null | undefined, failedSrc: string | undefined) {
  if (!src?.trim() || src === failedSrc) return undefined;
  return src;
}

// 작은 썸네일에는 글자를 넣지 않고 아이콘만 보여 준다.
export function getFallbackCaption(size: FallbackImageSize) {
  return size === 'small' ? undefined : '이미지 준비 중';
}
