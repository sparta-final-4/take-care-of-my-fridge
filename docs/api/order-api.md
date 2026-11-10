# 주문 API 명세

상태: 구현 완료 · 담당: 장준혁 · 공통 규칙: [API 계약](api-spec.md#공통-규칙) · 자세한 동작: [주문 설계](../architecture/domains/order.md)

공통 응답·오류 코드·페이지·날짜 형식은 [공통 규칙](api-spec.md#공통-규칙)을 따릅니다. 이 문서의 경로는 `/api`를 뺀 값이고, 에러 표에는 공통 오류(401 `A003`, 400 `C001`)를 빼고 이 API만의 오류를 적습니다. 회원 번호는 로그인 정보에서만 읽고 요청에는 받지 않습니다. 남의 주문은 없는 주문과 같은 404 `ORDER_001`입니다.

## 목차

- [주문하기](#주문하기)
- [주문 확인 조회](#주문-확인-조회)
- [주문 취소](#주문-취소)
- [주문 목록](#주문-목록)
- [주문 상세](#주문-상세)
- [구매 확정](#구매-확정)
- [주문 응답 DTO](#주문-응답-dto)

## 주문하기
`POST /orders` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| Idempotency-Key (header) | string | Y | 주문하기 클릭마다 새로 만든 값. 64자 이하 |
| items | `OrderCreateItemRequest[]` | Y | 주문할 장바구니 항목. 1~50개 |
| items[].cartItemId | long | Y | 장바구니 항목 ID |
| items[].unitPrice | int | Y | 장바구니에서 본 단가 |
| items[].saleType | `SPECIAL`·`FLASH_DROP` | N | 장바구니에서 본 할인 구분. 할인이 없었으면 비움 |

응답: `OrderCheckoutResponse`. 새 주문이면 201, 같은 키로 이미 만든 주문이면 200입니다.

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | C001 | `Idempotency-Key`가 없거나 64자를 넘음 |
| 404 | CART_001 | 없는 장바구니 항목이거나 남의 항목 |
| 404 | PRODUCT_001 | 없는 상품 |
| 409 | PRODUCT_002 | 재고 부족·품절(번개 드롭 남은 수량 0 포함) |
| 409 | PRODUCT_005 | 판매 중지 상품 |
| 409 | ORDER_010 | 보낸 단가나 할인 구분이 주문 시점과 다름. 문구는 `가격 변경`·`할인 시작`·`할인 종료` 중 하나와 상품 이름 |
| 409 | CART_003 | 번개 드롭 상품을 1개보다 많이 주문하거나, 이미 산 번개 드롭을 다시 주문함 |
| 409 | ORDER_006 | 같은 키로 처음과 다른 상품·수량을 보냄 |
| 409 | ORDER_005 | 주문번호가 겹침. 다시 주문하면 됨 |
| 503 | ORDER_013 | 주문번호를 만들 수 없음. 다시 주문하면 됨 |

주의 사항
- 재고와 번개 드롭 남은 수량은 주문을 만들 때 줄고, 장바구니 항목은 주문하기에서 지우지 않습니다.
- 결제를 시도하지 않은 주문은 만든 지 5분 뒤 서버가 지웁니다.

## 주문 확인 조회
`GET /orders/{orderId}/checkout` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| orderId (path) | long | Y | 주문 ID |

응답: `OrderCheckoutResponse`

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | ORDER_001 | 없는 주문(지운 주문 포함)이거나 남의 주문 |
| 409 | ORDER_007 | 결제 대기가 아님(결제 완료·구매 확정·전액 환불) |
| 409 | ORDER_008 | 취소된 주문 |

## 주문 취소
`PATCH /orders/{orderId}/cancel` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| orderId (path) | long | Y | 주문 ID |

응답: 없음(`ApiResponse.ok()`)

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | ORDER_001 | 없는 주문(이미 취소해 지운 주문 포함)이거나 남의 주문 |
| 409 | ORDER_007 | 결제 대기가 아님(결제 완료·구매 확정·전액 환불) |
| 409 | ORDER_008 | 취소된 주문 |
| 409 | ORDER_012 | 결제를 시도한 주문. 결제가 실패했어도 해당 |

주의 사항
- 재고와 번개 드롭 남은 수량을 되돌린 뒤 주문을 지웁니다. 다시 취소하면 404이고 재고는 한 번만 돌아옵니다.
- 결제를 시도한 결제 대기 주문은 진행 중 결제가 없고 마지막 결제 뒤 10분이 지나면 서버가 취소 상태로 바꿉니다. 그 전에는 같은 주문으로 다시 결제할 수 있습니다.

## 주문 목록
`GET /orders` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| page (query) | int | N | 기본 1, 1 이상 |
| size (query) | int | N | 기본 10, 1~100 |

응답: `PageResponse<OrderSummaryResponse>` (최신 주문순. 취소된 주문은 뺌)

## 주문 상세
`GET /orders/{orderId}` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| orderId (path) | long | Y | 주문 ID |

응답: `OrderDetailResponse`

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | ORDER_001 | 없는 주문이거나 남의 주문 |

## 구매 확정
`PATCH /orders/{orderId}/confirm` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| orderId (path) | long | Y | 주문 ID |

응답: `OrderConfirmResponse`

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | ORDER_001 | 없는 주문이거나 남의 주문 |
| 409 | ORDER_009 | 결제 완료 전 |
| 409 | ORDER_008 | 취소된 주문 |
| 409 | ORDER_011 | 요청 상태 환불이 있음. 실패한 환불은 막지 않음 |
| 409 | ORDER_014 | 전액 환불된 주문 |

주의 사항
- 이미 구매 확정된 주문에 다시 보내면 같은 200 응답을 줍니다.
- 결제 완료 시각부터 7일이 지난 결제 완료 주문은 서버가 매시간 구매 확정합니다. 요청 상태 환불이 있으면 다음 실행으로 넘깁니다.

## 주문 응답 DTO

주문 상태(`status`)는 `PENDING`(결제 대기), `PAID`(결제 완료), `CANCELLED`(취소), `CONFIRMED`(구매 확정), `REFUNDED`(전액 환불)입니다.

`OrderCheckoutResponse` (주문하기·주문 확인 조회)

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| orderId | long | N | 주문 ID |
| orderNumber | string | N | 주문번호(숫자 20자리) |
| status | `OrderStatus` | N | 주문 상태 |
| orderedAt | date-time | N | 주문 시각 |
| listPriceAmount | long | N | 정가 합 |
| discountAmount | long | N | 할인 금액(정가 합 − 결제할 금액) |
| payAmount | long | N | 결제할 금액 |
| items | `OrderItemResponse[]` | N | 주문 상품 |

`OrderDetailResponse` (주문 상세)

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| orderId | long | N | 주문 ID |
| orderNumber | string | N | 주문번호 |
| status | `OrderStatus` | N | 주문 상태 |
| orderedAt | date-time | N | 주문 시각 |
| confirmedAt | date-time | Y | 구매 확정 시각. 확정 전 null |
| listPriceAmount | long | N | 정가 합 |
| discountAmount | long | N | 할인 금액 |
| payAmount | long | N | 결제 금액 |
| items | `OrderDetailItemResponse[]` | N | 주문 상품 |

`OrderSummaryResponse` (주문 목록)

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| orderId | long | N | 주문 ID |
| orderNumber | string | N | 주문번호 |
| orderedAt | date-time | N | 주문 시각 |
| payAmount | long | N | 결제 금액 |
| status | `OrderStatus` | N | 주문 상태 |

`OrderConfirmResponse` (구매 확정)

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| orderId | long | N | 주문 ID |
| status | `OrderStatus` | N | 주문 상태(`CONFIRMED`) |

`OrderItemResponse`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| productId | long | N | 상품 ID |
| productName | string | N | 주문 당시 상품 이름 |
| productImageUrl | string | Y | 주문 당시 상품 이미지 |
| quantity | int | N | 주문 수량 |
| listPrice | int | N | 주문 당시 정가 |
| unitPrice | int | N | 실제 단가 |
| lineAmount | long | N | 단가 × 수량 |
| saleType | `NONE`·`SPECIAL`·`FLASH_DROP` | N | 주문 당시 할인 구분 |

`OrderDetailItemResponse`는 `OrderItemResponse`의 필드에 아래 하나를 더합니다.

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| confirmedQuantity | int | Y | 확정 수량(주문 수량 − 완료된 환불 수량). 확정 전 null |

주문 상품의 이름·이미지·가격은 주문 당시 값을 저장한 것이라 상품이 바뀌어도 바뀌지 않습니다.
