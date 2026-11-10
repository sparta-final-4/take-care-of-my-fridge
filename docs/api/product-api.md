# 상품 API 명세

상태: 구현 완료 · 공통 규칙: [API 계약](api-spec.md#공통-규칙) · 자세한 동작: [상품 설계](../architecture/domains/product.md)

공통 응답·오류 코드·페이지·날짜 형식은 [공통 규칙](api-spec.md#공통-규칙)을 따릅니다. 이 문서의 경로는 `/api`를 뺀 값이고, 에러 표에는 공통 오류(401 `A003`, 403 `A004`, 400 `C001`)를 빼고 이 API만의 오류를 적습니다. 응답의 `sale`·`soldOut`은 [상품 할인·품절 필드](api-spec.md#상품-할인품절-필드)를 따릅니다.

## 목차

- [상품 목록](#상품-목록)
- [상품 상세](#상품-상세)
- [관리자 상품 목록](#관리자-상품-목록)
- [관리자 상품 상세](#관리자-상품-상세)
- [상품 등록](#상품-등록)
- [상품 수정](#상품-수정)
- [상품 삭제](#상품-삭제)
- [상품 이미지 업로드 URL](#상품-이미지-업로드-url)
- [상품 요청 DTO](#상품-요청-dto)
- [상품 응답 DTO](#상품-응답-dto)

## 상품 목록
`GET /products` · 공개

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| sort (query) | `LATEST`·`RELEVANCE`·`PRICE_ASC`·`PRICE_DESC` | N | 기본 `LATEST`(최신순). `RELEVANCE`는 이 API에서 `LATEST`와 같음 |
| keyword (query) | string | N | 50자 이하. 상품명에 들어 있는지 봄(대소문자 무시) |
| type (query) | `INGREDIENT`·`MEALKIT` | N | 비우면 둘 다 |
| minPrice (query) | int | N | 0 이상. 정가 기준, 이 값 포함 |
| maxPrice (query) | int | N | 0 이상. 정가 기준, 이 값 포함 |
| page (query) | int | N | 기본 1, 1 이상 |
| size (query) | int | N | 기본 20, 1~100 |

응답: `PageResponse<ProductListItemResponse>`

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | C001 | `minPrice`가 `maxPrice`보다 큼 |

주의 사항
- 판매 중지(`STOPPED`) 상품은 빼고 품절(`SOLD_OUT`) 상품은 보여 줍니다.
- MySQL로만 조회합니다. 관련도 순 검색은 [상품 검색](search-api.md#상품-검색)을 씁니다.

## 상품 상세
`GET /products/{productId}` · 공개

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| productId (path) | long | Y | 상품 ID |

응답: `ProductDetailResponse`

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | PRODUCT_001 | 없는 상품이거나 판매 중지 상품 |

## 관리자 상품 목록
`GET /admin/products` · ADMIN

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| keyword (query) | string | N | 50자 이하. 상품명에 들어 있는지 봄 |
| type (query) | `INGREDIENT`·`MEALKIT` | N | 비우면 둘 다 |
| status (query) | `ON_SALE`·`SOLD_OUT`·`STOPPED` | N | 비우면 세 상태 모두 |
| page (query) | int | N | 기본 1, 1 이상 |
| size (query) | int | N | 기본 20, 1~100 |

응답: `PageResponse<ProductListItemResponse>` (최신순)

## 관리자 상품 상세
`GET /admin/products/{productId}` · ADMIN

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| productId (path) | long | Y | 상품 ID |

응답: `ProductDetailResponse` (판매 중지 상품도 반환)

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | PRODUCT_001 | 없는 상품 |

## 상품 등록
`POST /admin/products` · ADMIN

요청: `ProductCreateRequest`

응답: `ProductDetailResponse` (201)

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | C001 | 상품 종류에 맞는 상세가 아니거나 둘 다 보냄 |
| 400 | PRODUCT_006 | 직접 입력용·밀키트용 기본 재료를 연결함 |
| 404 | INGREDIENT_002 | 없는 재료를 연결함 |

주의 사항
- 등록한 상품은 판매 중(`ON_SALE`)으로 시작합니다.

## 상품 수정
`PATCH /admin/products/{productId}` · ADMIN

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| productId (path) | long | Y | 상품 ID |

요청: `ProductUpdateRequest`

응답: `ProductDetailResponse`

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | C001 | 상품 종류와 다른 상세를 보냄 |
| 400 | PRODUCT_006 | 직접 입력용·밀키트용 기본 재료를 연결함 |
| 404 | PRODUCT_001 | 없는 상품 |
| 404 | INGREDIENT_002 | 없는 재료를 연결함 |
| 500 | PRODUCT_007 | 상세 행이 없는 상품에 상세를 보냄 |

주의 사항
- 보낸 칸만 바꾸고 null인 칸은 그대로 둡니다. `description`·`imageUrl`은 빈 문자열을 보내면 비웁니다.
- 판매 중지가 아니면 상태는 재고로 정합니다(재고 0이면 `SOLD_OUT`). 판매 중지 상품도 수정할 수 있습니다.

## 상품 삭제
`DELETE /admin/products/{productId}` · ADMIN

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| productId (path) | long | Y | 상품 ID |

응답: 없음(`ApiResponse.ok()`)

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | PRODUCT_001 | 없는 상품 |

주의 사항
- 행을 지우지 않고 판매 중지(`STOPPED`)로 바꿉니다. 이미 판매 중지된 상품을 다시 삭제해도 성공입니다.

## 상품 이미지 업로드 URL
`POST /admin/images/presign` · ADMIN

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| contentType | string | Y | `image/jpeg`·`image/png`·`image/webp` |
| fileSize | long | Y | 바이트, 1 이상 5MB 이하 |

응답: `ImageUploadUrlResponse`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| uploadUrl | string | N | 파일을 PUT할 Presigned URL. 5분 동안 유효 |
| imageUrl | string | N | 올린 뒤 상품 `imageUrl`에 저장할 조회 URL |

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | IMAGE_002 | JPG·PNG·WEBP가 아님 |
| 503 | IMAGE_001 | 이미지 업로드를 끈 환경. 이미지 URL을 직접 입력 |

주의 사항
- 요청한 Content-Type·크기 그대로 PUT해야 하며, 다르면 S3가 거부합니다.

## 상품 요청 DTO

`ProductCreateRequest`

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| productType | `INGREDIENT`·`MEALKIT` | Y | 상품 종류. 등록 뒤 바꿀 수 없음 |
| name | string | Y | 200자 이하 |
| description | string | N | 2,000자 이하 |
| imageUrl | string | N | 500자 이하, `http://`·`https://`로 시작. 빈 값은 null |
| price | int | Y | 정가. 1원 이상 1,000만 원 이하 |
| stock | int | Y | 0 이상 10만 이하 |
| storage | `ROOM_TEMP`·`REFRIGERATED`·`FROZEN` | Y | 상온·냉장·냉동 |
| shelfDays | int | Y | 권장 보관 일수, 1 이상 |
| ingredient | `IngredientDetail` | 종류가 `INGREDIENT`면 Y | 그 밖에는 보내면 안 됨 |
| ingredient.ingredientId | long | Y | 연결할 표준 재료 ID |
| ingredient.unitAmount | number | Y | 용량. 0보다 큼, 정수 6자리·소수 2자리 |
| ingredient.unit | string | Y | 용량 단위, 20자 이하 |
| ingredient.baseQuantity | number | Y | 재료 기본 단위 수량. 0보다 큼, 정수 8자리·소수 2자리 |
| mealkit | `MealkitDetail` | 종류가 `MEALKIT`면 Y | 그 밖에는 보내면 안 됨 |
| mealkit.servings | int | Y | 구성 인분, 1 이상 |

`ProductUpdateRequest`

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| name | string | N | 1자 이상 200자 이하 |
| description | string | N | 2,000자 이하. 빈 문자열이면 비움 |
| imageUrl | string | N | 500자 이하, `http://`·`https://`로 시작. 빈 문자열이면 비움 |
| price | int | N | 1원 이상 1,000만 원 이하 |
| stock | int | N | 0 이상 10만 이하 |
| storage | `ROOM_TEMP`·`REFRIGERATED`·`FROZEN` | N | |
| shelfDays | int | N | 1 이상 |
| status | `ON_SALE`·`SOLD_OUT`·`STOPPED` | N | `STOPPED`가 아니면 재고로 정함 |
| ingredient | `IngredientUpdate` | N | 재료 상품만. 칸은 등록과 같고 모두 선택 |
| mealkit | `MealkitUpdate` | N | 밀키트 상품만. `servings` 선택 |

## 상품 응답 DTO

`ProductListItemResponse`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| productId | long | N | 상품 ID |
| productType | `INGREDIENT`·`MEALKIT` | N | 상품 종류 |
| name | string | N | 상품명 |
| imageUrl | string | Y | 상품 사진 |
| price | int | N | 정가 |
| status | `ON_SALE`·`SOLD_OUT`·`STOPPED` | N | 상품 상태 |
| sale | `SaleResponse` | Y | 진행 중 할인 |
| soldOut | boolean | N | 지금 보이는 가격으로 살 수 없음 |

`ProductDetailResponse`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| productId | long | N | 상품 ID |
| productType | `INGREDIENT`·`MEALKIT` | N | 상품 종류 |
| name | string | N | 상품명 |
| description | string | Y | 상품 설명 |
| imageUrl | string | Y | 상품 사진 |
| price | int | N | 정가 |
| stock | int | N | 재고 |
| storage | `ROOM_TEMP`·`REFRIGERATED`·`FROZEN` | N | 보관 방법 |
| shelfDays | int | N | 권장 보관 일수 |
| status | `ON_SALE`·`SOLD_OUT`·`STOPPED` | N | 상품 상태 |
| sale | `SaleResponse` | Y | 진행 중 할인 |
| soldOut | boolean | N | 지금 보이는 가격으로 살 수 없음 |
| ingredient | `IngredientInfo` | Y | 재료 상품만. unitAmount, unit |
| mealkit | `MealkitInfo` | Y | 밀키트 상품만. servings |
