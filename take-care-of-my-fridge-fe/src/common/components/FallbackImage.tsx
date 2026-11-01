import Icon, { ShoppingOutlined } from '@ant-design/icons';
import { useState, type ImgHTMLAttributes } from 'react';

import { FadeImage } from './FadeImage';
import { getFallbackCaption, getImageSrc, type FallbackImageSize } from './imageFallback';
import styles from './FallbackImage.module.scss';

interface FallbackImageProps extends Omit<ImgHTMLAttributes<HTMLImageElement>, 'src' | 'alt'> {
  src?: string | null;
  alt: string;
  kind: 'recipe' | 'product';
  size?: FallbackImageSize;
}

// antd에 포크·나이프 아이콘이 없어 직접 그린다. antd 아이콘처럼 글자색·글자 크기를 따른다.
function ForkKnifeSvg() {
  return (
    <svg
      viewBox="0 0 24 24"
      width="1em"
      height="1em"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.6}
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M5 3v5a2 2 0 0 0 4 0V3M7 3v18" />
      <path d="M18 21V3c-2.2 1.4-3.5 4-3.5 7.2 0 1.6 1.2 2.8 3.5 2.8" />
    </svg>
  );
}

const ICONS = {
  recipe: <Icon component={ForkKnifeSvg} />,
  product: <ShoppingOutlined />,
};

// 이미지 주소가 없거나 불러오지 못하면 그 자리에 기본 이미지를 그린다.
// className은 이미지와 기본 이미지에 똑같이 붙여 원래 자리의 크기·비율·모서리를 그대로 따른다.
export function FallbackImage({
  src,
  alt,
  kind,
  size = 'default',
  className,
  onError,
  ...props
}: FallbackImageProps) {
  const [failedSrc, setFailedSrc] = useState<string>();
  const imageSrc = getImageSrc(src, failedSrc);

  if (imageSrc) {
    return (
      <FadeImage
        {...props}
        src={imageSrc}
        alt={alt}
        className={className}
        onError={(event) => {
          setFailedSrc(imageSrc);
          onError?.(event);
        }}
      />
    );
  }

  const caption = getFallbackCaption(size);
  return (
    <span
      // 대체 텍스트가 비어 있으면(옆에 이름이 따로 있는 자리) 화면 낭독기에서 건너뛴다.
      role={alt ? 'img' : undefined}
      aria-label={alt || undefined}
      aria-hidden={alt ? undefined : true}
      className={className ? `${styles.fallback} ${className}` : styles.fallback}
    >
      <span className={styles.icon}>{ICONS[kind]}</span>
      {caption && <span className={styles.caption}>{caption}</span>}
    </span>
  );
}
