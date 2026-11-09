// 메인 화면 사진. Wikimedia Commons 자유 이용 사진과 Unsplash License 사진이며, 화면 맨 아래에 출처를 표시한다
// (Wikimedia CC BY-SA는 출처 표시가 조건, Unsplash는 조건은 아니지만 함께 표시).
// 파일은 public/photos/main/에 크기별로 있다(파일 이름 끝 숫자가 가로 px). 브라우저가 화면 폭(sizes)에 맞는 파일만 받는다.
// /images/는 CloudFront가 상품 이미지 버킷으로 보내므로 프론트 정적 파일을 두지 않는다.
// 서비스 사진이 생기면 src·srcSet·width·height만 바꾸고, 출처 줄은 지운다.
export interface MainPhoto {
  // srcSet을 모르는 브라우저가 받는 기본 파일
  src: string;
  srcSet: string;
  // 실제로 그려지는 폭. 브라우저가 이 폭에 맞는 파일을 고른다.
  sizes: string;
  // 가장 큰 파일의 크기(비율 계산용)
  width: number;
  height: number;
  alt: string;
  author: string;
  license: string;
  page: string;
  // 원본을 고쳐 쓴 경우 출처 줄에 함께 표시한다(CC BY-SA는 변경 사항 표시가 조건).
  modification?: string;
}

// 바로가기 3칸: 모바일은 화면 폭, md 이상은 본문(1200px)의 1/3
const TILE_SIZES = '(min-width: 768px) 400px, 100vw';

export const MAIN_PHOTOS = {
  hero: {
    src: '/photos/main/hero-fridge-1920.jpg',
    srcSet: '/photos/main/hero-fridge-960.jpg 960w, /photos/main/hero-fridge-1920.jpg 1920w',
    sizes: '100vw',
    width: 1920,
    height: 1372,
    alt: '달걀, 채소, 과일, 반찬 통이 칸칸이 정리된 냉장고 안',
    author: 'USDA',
    license: 'Public domain',
    page: 'https://commons.wikimedia.org/wiki/File:Food_Safety_-_Cut_Waste_in_Refrigerators_(20200605-FSIS-LSC-0059).jpg',
  },
  fridge: {
    src: '/photos/main/tile-fridge-960.jpg',
    srcSet: '/photos/main/tile-fridge-480.jpg 480w, /photos/main/tile-fridge-960.jpg 960w',
    sizes: TILE_SIZES,
    width: 960,
    height: 720,
    alt: '',
    author: 'Onur Burak Akın',
    license: 'Unsplash License',
    page: 'https://unsplash.com/photos/XIMl4Crm1GQ',
  },
  recipes: {
    src: '/photos/main/tile-recipes-960.jpg',
    srcSet: '/photos/main/tile-recipes-480.jpg 480w, /photos/main/tile-recipes-960.jpg 960w',
    sizes: TILE_SIZES,
    width: 960,
    height: 720,
    alt: '',
    author: 'Nadine Primeau',
    license: 'Unsplash License',
    page: 'https://unsplash.com/photos/KD3XqquHlcc',
  },
  products: {
    src: '/photos/main/tile-products-960.jpg',
    srcSet: '/photos/main/tile-products-480.jpg 480w, /photos/main/tile-products-960.jpg 960w',
    sizes: TILE_SIZES,
    width: 960,
    height: 720,
    alt: '',
    author: 'Tom Brunberg',
    license: 'Unsplash License',
    page: 'https://unsplash.com/photos/D4ZtZX1UeAI',
  },
  consult: {
    // 원본 사진에서 냉장고만 오려 낸(배경 투명) WebP. 잉크색 띠 위에 바로 얹는다.
    src: '/photos/main/story-night-fridge-cutout-898.webp',
    srcSet:
      '/photos/main/story-night-fridge-cutout-500.webp 500w, /photos/main/story-night-fridge-cutout-898.webp 898w',
    // lg 이상은 본문의 절반 칸(약 560px), 그보다 좁으면 화면 폭
    sizes: '(min-width: 992px) 560px, 100vw',
    width: 898,
    height: 1185,
    alt: '불이 켜진 채 열려 있는 냉장고',
    author: 'W.carter',
    license: 'CC BY-SA 4.0',
    page: 'https://commons.wikimedia.org/wiki/File:Open_refrigerator_with_food_at_night.jpg',
    modification: '배경 제거',
  },
} satisfies Record<string, MainPhoto>;
