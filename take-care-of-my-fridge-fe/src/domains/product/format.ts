import type { ProductListItemResponseProductTypeEnum } from '@/common/api/clients';

export const PRODUCT_TYPE_LABELS: Record<ProductListItemResponseProductTypeEnum, string> = {
  INGREDIENT: '재료',
  MEALKIT: '밀키트',
};

// 상품 종류 태그 색. 카드마다 같은 종류가 같은 색으로 나온다.
export const PRODUCT_TYPE_COLORS: Record<ProductListItemResponseProductTypeEnum, string> = {
  INGREDIENT: 'green',
  MEALKIT: 'purple',
};

// 가격대 칩
export const PRICE_RANGES = [
  { key: 'under3k', label: '3천원 이하', maxPrice: 3000 },
  { key: '3kto10k', label: '3천~1만원', minPrice: 3001, maxPrice: 9999 },
  { key: 'over10k', label: '1만원 이상', minPrice: 10000 },
] as const satisfies ReadonlyArray<{
  key: string;
  label: string;
  minPrice?: number;
  maxPrice?: number;
}>;

export type PriceRangeKey = (typeof PRICE_RANGES)[number]['key'];
