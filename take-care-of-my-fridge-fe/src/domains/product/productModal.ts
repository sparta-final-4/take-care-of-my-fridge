import { generatePath, type To } from 'react-router';

import { ROUTES } from '@/app/paths';

// 상품 모달 주소(/products/:productId). 이 주소는 항상 "상품 목록 + 그 위의 상품 모달"이다(ProductsPage).
// search는 뒤에 깔릴 상품 목록의 조건(?type=…·?q=…)이다.
export function productModalPath(productId: number, search = ''): To {
  return {
    pathname: generatePath(ROUTES.productDetail, { productId: String(productId) }),
    search,
  };
}

// 상품 목록에서 카드를 눌러 모달을 열었다는 표시(주소 이동의 state).
// 있으면 닫을 때 뒤로 가기로 목록 기록에 돌아가고, 없으면(링크·새로고침·로그인 후 복귀) 목록 주소로 바꾼다.
export const PRODUCT_MODAL_STATE = { productModal: true } as const;

export function isProductModalFromList(state: unknown): boolean {
  return typeof state === 'object' && state !== null && 'productModal' in state;
}
