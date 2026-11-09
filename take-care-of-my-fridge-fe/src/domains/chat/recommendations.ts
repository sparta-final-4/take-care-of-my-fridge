import type { ProductCard, RecommendedMealkit, RecommendedRecipe } from '@/common/api/clients';

// AI 상담 추천 카드 SSE 이벤트(event: recommendations)의 data.
// SSE 이벤트 data는 OpenAPI 스펙에 나오지 않아 생성 타입이 없다. 그래서 백엔드 DTO
// ChatRecommendationsEventResponse(domain/chat/dto)와 같은 모양으로 여기에 둔다. 필드를 바꾸면 둘 다 고친다.
// 메시지 조회(ChatMessageResponse.recommendations)의 카드는 생성 타입 RecommendedRecipe로 오고,
// toChatRecommendedRecipes로 같은 모양으로 바꿔 그린다.
// 밀키트 카드(mealkits)도 같다: 이벤트는 parseChatRecommendations, 메시지 조회는 toChatRecommendedMealkits.
// 살 상품 카드(products)도 같다: 메시지 조회는 toChatProductCards.
// 냉장고 보기 카드(fridgeItems)는 이벤트에만 있다 (저장하지 않아 다시 불러오면 답 글만 보인다).

export interface ChatRecommendationImminentIngredient {
  name: string;
  // 오늘 기준 소비기한까지 남은 일수 (0이면 오늘까지)
  dDay: number;
}

// 장볼 재료로 담을 상품. 가격·품절은 카드를 만들 때(다시 불러올 때도) 기준이다
export interface ChatShoppingProduct {
  productId: number;
  name: string;
  imageUrl: string | null;
  // 정가(원)
  price: number;
  // 진행 중 할인의 판매가. 할인이 없으면 null
  salePrice: number | null;
  // 할인 구분 (SPECIAL·FLASH_DROP). 할인이 없으면 null
  saleType: string | null;
  // 지금 살 수 없음 (상품 품절·번개 드롭 남은 수량 0)
  soldOut: boolean;
  // 상품 용량 (예: 300 g). 없으면 null
  unitAmount: number | null;
  unit: string | null;
}

export interface ChatRecommendationMissingIngredient {
  ingredientId: number;
  name: string;
  // 이 재료로 담을 상품. 판매 상품이 없으면 null
  product: ChatShoppingProduct | null;
}

export interface ChatRecommendedRecipe {
  recipeId: number;
  name: string;
  imageUrl: string | null;
  category: string | null;
  cookTimeMin: number | null;
  // 쓰는 냉장고 재료 이름 (임박한 순)
  usedIngredients: string[];
  imminentIngredients: ChatRecommendationImminentIngredient[];
  // 겹친 맛 (한글)
  matchedTastes: string[];
  // 냉장고에 없는 재료 (기본 양념 제외). 재료마다 담을 상품이 있으면 product에 온다
  missingIngredients: ChatRecommendationMissingIngredient[];
  // 고른·말한 재료 중 냉장고에 없는 것의 이름 (missingIngredients에도 들어 있다)
  missingSelectedIngredients: string[];
  // 재료를 많이 사야 함
  lacksManyIngredients: boolean;
  // 추천 이유 한 줄 (예: "버섯·두부가 주재료예요"). 없으면 빈 문자열
  reason: string;
  // 카드 아래 AI 설명. 저장된 기록에만 있고, 없으면 null
  description: string | null;
}

// 추천 밀키트 카드. 누르면 상품 상세로 간다
export interface ChatRecommendedMealkit {
  // 담을 상품 (가격·할인가·품절은 카드를 만들 때 기준, 용량 칸은 null)
  product: ChatShoppingProduct;
  // 구성 인분
  servings: number;
  // 이 밀키트로 만드는 레시피. 연결이 없으면 null
  recipeId: number | null;
  recipeName: string | null;
  // 카드 아래 서버 설명 ("요리하기 번거로우면 …"). 밀키트만 보여 주는 답이면 null
  description: string | null;
  // 냉장고에 이 밀키트가 있음 ("냉장고에 있어요" 태그, 담기 버튼 숨김). 다시 불러올 때도 지금 냉장고 기준
  inFridge: boolean;
  // 냉장고에 있으면 소비기한까지 남은 일수 (D-3 이내면 임박 태그). 없으면 null
  fridgeDDay: number | null;
}

// 냉장고 보기 카드 (냉장고 줄 하나). 백엔드 ChatRecommendationsEventResponse.FridgeItemCard와 같은 모양
export interface ChatFridgeItem {
  fridgeId: number;
  ingredientId: number | null;
  // 보여 줄 이름 (일반 재료는 재료 이름, 밀키트·직접 입력은 등록 이름)
  name: string;
  rawName: string | null;
  quantity: number | null;
  unit: string | null;
  // 오늘 기준 소비기한까지 남은 일수 (0이면 오늘까지, 음수면 지남)
  dDay: number;
  // NORMAL·IMMINENT·EXPIRED
  status: string;
  mealkit: boolean;
  // 밀키트 줄이면 연결 상품 (상품 상세로 간다). 없으면 null
  productId: number | null;
  imageUrl: string | null;
}

// 살 상품 카드 (사기 답, 냉장고 보기에서 없는 재료). 누르면 상품 상세로 가고 담기로 1개를 담는다
export interface ChatProductCard {
  // 사용자가 말한 재료·상품 이름 ("당근")
  name: string;
  product: ChatShoppingProduct;
}

export interface ChatRecommendations {
  recipes: ChatRecommendedRecipe[];
  // 추천 밀키트. 밀키트를 말하면 최대 3개(레시피 카드 없음), 번거로워하는 말이면 레시피 카드 아래 1개
  mealkits: ChatRecommendedMealkit[];
  // 냉장고 보기 카드 (소비기한 순, 지난 줄은 맨 아래, 최대 10개)
  fridgeItems: ChatFridgeItem[];
  // 살 상품 카드
  products: ChatProductCard[];
  // 후보를 찾으려고 푼 조건 (아직 항상 빈 목록)
  relaxed: string[];
  // 보여 줄 카드가 없음
  empty: boolean;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function strings(value: unknown): string[] {
  return Array.isArray(value)
    ? value.filter((item): item is string => typeof item === 'string')
    : [];
}

function records(value: unknown): Record<string, unknown>[] {
  return Array.isArray(value) ? value.filter(isRecord) : [];
}

// 상품 id·이름·정가가 없으면 상품이 없는 것으로 본다
function toShoppingProduct(value: unknown): ChatShoppingProduct | null {
  if (!isRecord(value)) return null;
  const { productId, name, price } = value;
  if (typeof productId !== 'number' || typeof name !== 'string' || typeof price !== 'number')
    return null;
  return {
    productId,
    name,
    imageUrl: typeof value.imageUrl === 'string' ? value.imageUrl : null,
    price,
    salePrice: typeof value.salePrice === 'number' ? value.salePrice : null,
    saleType: typeof value.saleType === 'string' ? value.saleType : null,
    soldOut: value.soldOut === true,
    unitAmount: typeof value.unitAmount === 'number' ? value.unitAmount : null,
    unit: typeof value.unit === 'string' ? value.unit : null,
  };
}

function toRecipe(value: Record<string, unknown>): ChatRecommendedRecipe | null {
  const { recipeId, name } = value;
  if (typeof recipeId !== 'number' || typeof name !== 'string') return null;
  return {
    recipeId,
    name,
    imageUrl: typeof value.imageUrl === 'string' ? value.imageUrl : null,
    category: typeof value.category === 'string' ? value.category : null,
    cookTimeMin: typeof value.cookTimeMin === 'number' ? value.cookTimeMin : null,
    usedIngredients: strings(value.usedIngredients),
    imminentIngredients: records(value.imminentIngredients).flatMap((item) =>
      typeof item.name === 'string' && typeof item.dDay === 'number'
        ? [{ name: item.name, dDay: item.dDay }]
        : [],
    ),
    matchedTastes: strings(value.matchedTastes),
    missingIngredients: records(value.missingIngredients).flatMap((item) =>
      typeof item.ingredientId === 'number' && typeof item.name === 'string'
        ? [
            {
              ingredientId: item.ingredientId,
              name: item.name,
              product: toShoppingProduct(item.product),
            },
          ]
        : [],
    ),
    missingSelectedIngredients: strings(value.missingSelectedIngredients),
    lacksManyIngredients: value.lacksManyIngredients === true,
    reason: typeof value.reason === 'string' ? value.reason : '',
    description:
      typeof value.description === 'string' && value.description ? value.description : null,
  };
}

// 메시지 조회로 받은 카드(생성 타입)를 그릴 모양으로 바꾼다. id·이름이 없는 카드는 뺀다.
// 기록에서 다시 그린 카드는 "냉장고에 없는 선택 재료"를 다시 계산하지 않아 빈 목록으로 온다(그 태그만 안 보인다).
export function toChatRecommendedRecipes(
  cards: RecommendedRecipe[] | undefined,
): ChatRecommendedRecipe[] {
  return (cards ?? []).flatMap((card) => toRecipe({ ...card }) ?? []);
}

// 상품(id·이름·정가)이 없는 밀키트 카드는 뺀다
function toMealkit(value: Record<string, unknown>): ChatRecommendedMealkit | null {
  const product = toShoppingProduct(value.product);
  if (!product) return null;
  return {
    product,
    servings: typeof value.servings === 'number' ? value.servings : 0,
    recipeId: typeof value.recipeId === 'number' ? value.recipeId : null,
    recipeName: typeof value.recipeName === 'string' ? value.recipeName : null,
    description:
      typeof value.description === 'string' && value.description ? value.description : null,
    inFridge: value.inFridge === true,
    fridgeDDay: typeof value.fridgeDDay === 'number' ? value.fridgeDDay : null,
  };
}

// 메시지 조회로 받은 밀키트 카드(생성 타입)를 그릴 모양으로 바꾼다
export function toChatRecommendedMealkits(
  cards: RecommendedMealkit[] | undefined,
): ChatRecommendedMealkit[] {
  return (cards ?? []).flatMap((card) => toMealkit({ ...card }) ?? []);
}

// 냉장고 줄 id·이름·남은 일수가 없는 카드는 뺀다
function toFridgeItem(value: Record<string, unknown>): ChatFridgeItem | null {
  const { fridgeId, name, dDay } = value;
  if (typeof fridgeId !== 'number' || typeof name !== 'string' || typeof dDay !== 'number')
    return null;
  return {
    fridgeId,
    ingredientId: typeof value.ingredientId === 'number' ? value.ingredientId : null,
    name,
    rawName: typeof value.rawName === 'string' ? value.rawName : null,
    quantity: typeof value.quantity === 'number' ? value.quantity : null,
    unit: typeof value.unit === 'string' ? value.unit : null,
    dDay,
    status: typeof value.status === 'string' ? value.status : 'NORMAL',
    mealkit: value.mealkit === true,
    productId: typeof value.productId === 'number' ? value.productId : null,
    imageUrl: typeof value.imageUrl === 'string' ? value.imageUrl : null,
  };
}

// 이름·상품(id·이름·정가)이 없는 상품 카드는 뺀다
function toProductCard(value: Record<string, unknown>): ChatProductCard | null {
  const product = toShoppingProduct(value.product);
  if (!product || typeof value.name !== 'string') return null;
  return { name: value.name, product };
}

// 메시지 조회로 받은 상품 카드(생성 타입)를 그릴 모양으로 바꾼다
export function toChatProductCards(cards: ProductCard[] | undefined): ChatProductCard[] {
  return (cards ?? []).flatMap((card) => toProductCard({ ...card }) ?? []);
}

// 이벤트 data(JSON)를 읽는다. 모양이 다르면 null, 레시피 중 필수 값(id·이름)이 없는 것은 뺀다.
// 밀키트만 보여 주는 답은 recipes가 빈 목록이고 mealkits가 있다 (empty가 아니다).
export function parseChatRecommendations(data: string): ChatRecommendations | null {
  let parsed: unknown;
  try {
    parsed = JSON.parse(data);
  } catch {
    return null;
  }
  if (!isRecord(parsed) || !Array.isArray(parsed.recipes)) return null;
  const recipes = records(parsed.recipes).flatMap((recipe) => toRecipe(recipe) ?? []);
  const mealkits = records(parsed.mealkits).flatMap((mealkit) => toMealkit(mealkit) ?? []);
  const fridgeItems = records(parsed.fridgeItems).flatMap((item) => toFridgeItem(item) ?? []);
  const products = records(parsed.products).flatMap((card) => toProductCard(card) ?? []);
  return {
    recipes,
    mealkits,
    fridgeItems,
    products,
    relaxed: strings(parsed.relaxed),
    empty:
      parsed.empty === true ||
      (recipes.length === 0 &&
        mealkits.length === 0 &&
        fridgeItems.length === 0 &&
        products.length === 0),
  };
}

// 카드 설명 조각 SSE 이벤트(event: recommendation_text)의 data. 백엔드 ChatRecommendationTextEventResponse와 같은 모양
export interface ChatRecommendationText {
  // 카드 순서 (0부터)
  index: number;
  text: string;
}

export function parseChatRecommendationText(data: string): ChatRecommendationText | null {
  let parsed: unknown;
  try {
    parsed = JSON.parse(data);
  } catch {
    return null;
  }
  if (!isRecord(parsed) || typeof parsed.index !== 'number' || typeof parsed.text !== 'string')
    return null;
  return { index: parsed.index, text: parsed.text };
}

// 소비기한 임박 기준일 (백엔드 FridgeItemStatus.IMMINENT_DAYS와 같은 D-3)
export const IMMINENT_DAYS = 3;

// 임박 재료 태그 문구. 백엔드 추천 설명·냉장고 보기 답 글과 같은 말을 쓴다.
export function formatImminentLabel(dDay: number): string {
  if (dDay <= 0) return '오늘까지';
  if (dDay === 1) return '내일까지';
  if (dDay === 2) return '모레까지';
  return `${dDay}일 남음`;
}

// 가장 급한 날짜의 임박 재료만 한 줄로 ("고추·깻잎 내일까지"). 없으면 null
export function summarizeImminent(
  imminent: ChatRecommendationImminentIngredient[],
): { label: string; dDay: number } | null {
  if (imminent.length === 0) return null;
  const dDay = Math.min(...imminent.map((ingredient) => ingredient.dDay));
  const names = imminent
    .filter((ingredient) => ingredient.dDay === dDay)
    .map((ingredient) => ingredient.name);
  return { label: `${names.join('·')} ${formatImminentLabel(dDay)}`, dDay };
}
