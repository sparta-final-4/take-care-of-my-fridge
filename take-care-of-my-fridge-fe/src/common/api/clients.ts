import { axiosInstance } from './axiosInstance';
import {
  AdminAuditControllerApi,
  AdminDealAnalysisControllerApi,
  AdminForecastControllerApi,
  AdminMemberControllerApi,
  AdminProductControllerApi,
  AdminSaleEventControllerApi,
  AuthControllerApi,
  CartControllerApi,
  CookingLogControllerApi,
  CookingStatsControllerApi,
  CsrfControllerApi,
  FridgeControllerApi,
  MemberControllerApi,
  MyRecipeControllerApi,
  OrderControllerApi,
  PaymentControllerApi,
  ProductControllerApi,
  RecipeControllerApi,
  RecipeReviewControllerApi,
} from './generated';

// 생성된 API 클래스의 인스턴스는 여기서만 만든다. 도메인 api.ts에서만 import한다.
// basePath를 빼면 생성 코드 기본값(http://localhost)으로 요청되므로 반드시 넘긴다.
// 새 컨트롤러가 생기면 사용자·관리자 그룹 안에 이름순으로 추가한다.
// WebhookControllerApi는 PortOne 서버가 호출하는 API라 프론트에서 만들지 않는다.

const basePath = import.meta.env.VITE_API_BASE_URL ?? '';

export const authApi = new AuthControllerApi(undefined, basePath, axiosInstance);
export const cartApi = new CartControllerApi(undefined, basePath, axiosInstance);
export const cookingLogApi = new CookingLogControllerApi(undefined, basePath, axiosInstance);
export const cookingStatsApi = new CookingStatsControllerApi(undefined, basePath, axiosInstance);
export const csrfApi = new CsrfControllerApi(undefined, basePath, axiosInstance);

export const fridgeApi = new FridgeControllerApi(undefined, basePath, axiosInstance);
export const memberApi = new MemberControllerApi(undefined, basePath, axiosInstance);
export const myRecipeApi = new MyRecipeControllerApi(undefined, basePath, axiosInstance);
export const orderApi = new OrderControllerApi(undefined, basePath, axiosInstance);
export const paymentApi = new PaymentControllerApi(undefined, basePath, axiosInstance);
export const productApi = new ProductControllerApi(undefined, basePath, axiosInstance);
export const recipeApi = new RecipeControllerApi(undefined, basePath, axiosInstance);
export const recipeReviewApi = new RecipeReviewControllerApi(undefined, basePath, axiosInstance);

export const adminAuditApi = new AdminAuditControllerApi(undefined, basePath, axiosInstance);
export const adminDealAnalysisApi = new AdminDealAnalysisControllerApi(
  undefined,
  basePath,
  axiosInstance,
);
export const adminForecastApi = new AdminForecastControllerApi(undefined, basePath, axiosInstance);
export const adminMemberApi = new AdminMemberControllerApi(undefined, basePath, axiosInstance);
export const adminProductApi = new AdminProductControllerApi(undefined, basePath, axiosInstance);
export const adminSaleEventApi = new AdminSaleEventControllerApi(
  undefined,
  basePath,
  axiosInstance,
);

export * from './generated/models';
