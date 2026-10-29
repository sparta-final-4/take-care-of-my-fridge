import { useState, type ImgHTMLAttributes } from 'react';

import styles from './FadeImage.module.scss';

interface FadeImageProps extends ImgHTMLAttributes<HTMLImageElement> {
  src: string;
  alt: string;
}

// 사진을 받는 동안 그 자리에 냉장고 로고 로딩을 보여 주고, 다 받으면 사진으로 부드럽게 바꾼다.
// 이미 캐시에 있는 사진은 로딩 없이 바로 보인다.
export function FadeImage({ src, alt, className, ...props }: FadeImageProps) {
  const [shown, setShown] = useState<{ src: string; cached: boolean }>();
  const handleLoad = () =>
    setShown((current) => (current?.src === src ? current : { src, cached: false }));
  const visible = shown?.src === src;
  const classNames = [
    styles.image,
    visible && styles.shown,
    visible && !shown.cached && styles.appeared,
    className,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <img
      {...props}
      ref={(img) => {
        if (img?.complete && img.naturalWidth > 0 && shown?.src !== src) {
          setShown({ src, cached: true });
        }
      }}
      src={src}
      alt={alt}
      className={classNames}
      decoding="async"
      onLoad={handleLoad}
      // 실패해도 숨겨 두지 않고 브라우저 기본 표시(대체 텍스트)를 보여 준다.
      onError={handleLoad}
    />
  );
}
