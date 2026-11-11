# 장바구니 API 명세

상태: 구현 완료 · 담당: 장준혁 · 공통 규칙: [API 계약](api-spec.md#공통-규칙) · 자세한 동작: [장바구니 설계](../architecture/domains/cart.md)

공통 응답·오류 코드·날짜 형식은 [공통 규칙](api-spec.md#공통-규칙)을 따릅니다. 이 문서의 경로는 `/api`를 뺀 값이고, 에러 표에는 공통 오류(401 `A003`, 400 `C001`)를 빼고 이 API만의 오류를 적습니다. 회원 번호는 로그인 정보에서만 읽고 요청에는 받지 않습니다. 응답의 `sale`·`soldOut`은 [상품 할인·품절 필드](product-api.md#상품-할인품절-필드)를 따릅니다.

## 목차

- [장바구니 조회](#장바구니-조회)
- [장바구니 담기](#장바구니-담기)
- [수량 변경](#수량-변경)
- [항목 삭제](#항목-삭제)
- [장바구니 응답 DTO](#장바구니-응답-dto)

## 장바구니 조회
`GET /cart-items` · 로그인

요청: 없음

응답: `CartItemsResponse`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| items | `CartItemResponse[]` | N | 담은 순서. 담은 것이 없으면 빈 목록 |

주의 사항
- 담아 둔 뒤 판매 중지된 상품도 목록에 나오며 `soldOut=true`입니다.

## 장바구니 담기
`POST /cart-items` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| productId | long | Y | 담을 상품 ID |
| quantity | int | N | 1 이상. 비우면 1. 이미 담긴 상품이면 기존 수량에 더함 |

응답: `AddCartItemResponse`. 새 항목이면 201, 이미 담긴 상품의 수량만 더했으면 200입니다.

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| productId | long | N | 담은 상품 ID |
| quantity | int | N | 더한 뒤의 수량 |

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | PRODUCT_001 | 없는 상품 |
| 409 | PRODUCT_005 | 판매 중지 상품 |
| 409 | CART_002 | 기존 수량과 담을 수량의 합이 재고보다 많음(품절 포함) |
| 409 | CART_003 | 진행 중인 번개 드롭 상품을 합쳐 1개보다 많이 담음 |
| 409 | CART_004 | 같은 회원의 담기가 동시에 와 서버가 한 번 다시 시도해도 충돌 |

## 수량 변경
`PATCH /cart-items/{cartItemId}` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| cartItemId (path) | long | Y | 바꿀 장바구니 항목 ID |
| quantity | int | Y | 1 이상. 더할 값이 아니라 바꾼 뒤의 수량 |

응답: `ChangeCartItemQuantityResponse`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| cartItemId | long | N | 장바구니 항목 ID |
| quantity | int | N | 바꾼 뒤의 수량 |

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | CART_001 | 없는 항목이거나 남의 항목 |
| 404 | PRODUCT_001 | 없는 상품 |
| 409 | PRODUCT_005 | 판매 중지 상품 |
| 409 | CART_002 | 지금 수량보다 올리는데 재고보다 많음 |
| 409 | CART_003 | 진행 중인 번개 드롭 상품을 1개보다 많게 올림 |

주의 사항
- 담은 뒤 재고가 줄었거나 번개 드롭이 시작되어 한도를 넘었어도, 수량을 내리는 것은 허용합니다.

## 항목 삭제
`DELETE /cart-items` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| cartItemIds (query) | long[] | Y | 지울 장바구니 항목 ID. 1개 이상 |

응답: 없음(`ApiResponse.ok()`)

주의 사항
- 내 항목만 지우고, 남의 항목이나 없는 ID는 오류 없이 넘어갑니다. 같은 요청을 다시 보내도 결과가 같습니다.

## 장바구니 응답 DTO

`CartItemResponse`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| cartItemId | long | N | 장바구니 항목 ID |
| productId | long | N | 상품 ID |
| productName | string | N | 상품 이름 |
| imageUrl | string | Y | 상품 이미지 |
| quantity | int | N | 담은 수량 |
| listPrice | int | N | 정가 |
| unitPrice | int | N | 지금 단가. 할인이 없으면 정가. 조회할 때마다 계산하므로 할인이 끝나면 다음 조회부터 정가 |
| sale | `SaleResponse` | Y | 진행 중인 할인. 없으면 null |
| soldOut | boolean | N | 상품이 품절·판매 중지이거나 번개 드롭 남은 수량이 0이면 true |
