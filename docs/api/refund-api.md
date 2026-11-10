# 환불 API 명세

상태: 구현 완료 · 담당: 결제·환불 · 공통 규칙: [API 계약](api-spec.md#공통-규칙) · 자세한 동작: [환불 설계](../architecture/domains/refund.md)

공통 응답·오류 코드·날짜 형식은 [공통 규칙](api-spec.md#공통-규칙)을 따릅니다. 이 문서의 경로는 `/api`를 뺀 값이고, 에러 표에는 공통 오류(401 `A003`, 400 `C001`)를 빼고 이 API만의 오류를 적습니다. 회원 번호는 로그인 정보에서만 읽고 요청에는 받지 않습니다. 환불 금액은 요청으로 받지 않고 주문 당시 단가 × 수량으로 계산합니다.

## 목차

- [환불 신청](#환불-신청)
- [환불 내역](#환불-내역)
- [환불 요약](#환불-요약)
- [환불 가능 수량](#환불-가능-수량)

## 환불 신청
`POST /refunds` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| orderId | long | Y | 환불할 주문 ID |
| items | object[] | Y | 환불할 상품. 1개 이상. 같은 상품을 두 번 넣으면 400 `C001` |
| items[].orderItemId | long | Y | 주문 상품 ID |
| items[].quantity | int | Y | 환불 수량. 1 이상 |
| reasonType | string | Y | `CHANGE_OF_MIND`(단순 변심)·`ORDER_MISTAKE`(주문 실수)·`NO_LONGER_NEEDED`(더 이상 필요 없음)·`ETC`(기타) |
| reasonDetail | string | N | 상세 사유. 500자 이하 |

응답: `RefundResponse`. 완료(`COMPLETED`)든 요청 상태(`REQUESTED`)든 201입니다.

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| refundId | long | N | 환불 ID |
| status | string | N | `COMPLETED`(완료)·`REQUESTED`(PG 결과를 확정하지 못함) |
| refundAmount | long | N | 환불 금액 |
| refundedAt | datetime | Y | 환불 완료 시각. 완료 전 null |

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | ORDER_001 | 없는 주문이거나 남의 주문 |
| 409 | REFUND_010 | 구매 확정된 주문 |
| 400 | REFUND_003 | 결제 완료가 아닌 주문(전액 환불 포함) |
| 429 | REFUND_007 | 같은 주문에 진행 중(요청 상태) 환불이 있음 |
| 404 | REFUND_004 | 그 주문의 상품이 아님 |
| 400 | REFUND_011 | 특가·번개 드롭 상품 |
| 400 | REFUND_005 | 남은 환불 가능 수량 초과 |
| 409 | REFUND_012 | PG가 취소를 거절함. 환불은 실패로 기록 |

## 환불 내역
`GET /refunds?orderId=` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| orderId (query) | long | Y | 대상 주문 ID |

응답: `List<RefundHistoryResponse>`. 상태와 관계없이 최신 환불부터입니다.

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| refundId | long | N | 환불 ID |
| status | string | N | `REQUESTED`(요청)·`COMPLETED`(완료)·`FAILED`(실패) |
| reasonType | string | N | 환불 사유 |
| reasonDetail | string | Y | 상세 사유 |
| refundAmount | long | N | 환불 금액 |
| requestedAt | datetime | N | 신청 시각 |
| refundedAt | datetime | Y | 완료 시각. 완료 전·실패면 null |
| items | `RefundHistoryItemResponse[]` | N | 환불 상품 |

`RefundHistoryItemResponse`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| orderItemId | long | N | 주문 상품 ID |
| productName | string | N | 주문 당시 상품 이름 |
| productImageUrl | string | Y | 주문 당시 상품 이미지 |
| unitPrice | int | N | 환불 신청 때 저장한 단가 |
| quantity | int | N | 환불 수량 |
| amount | long | N | 단가 × 수량 |

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | ORDER_001 | 없는 주문이거나 남의 주문 |

## 환불 요약
`GET /refunds/summaries?orderIds=` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| orderIds (query) | long[] | Y | 주문 ID 목록(쉼표 구분). 빈 값·중복을 뺀 고유 주문 1~50건 |

응답: `List<RefundSummaryResponse>`. 요청한 순서대로이고, 없는 주문과 남의 주문은 뺍니다.

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| orderId | long | N | 주문 ID |
| refundStatus | string | N | `IN_PROGRESS`(요청 상태 환불 있음)·`FULL`(환불 가능한 상품을 모두 환불)·`PARTIAL`(완료된 환불 있음)·`NONE`(그 밖). 앞의 것부터 판단 |
| refundedAmount | long | N | 완료된 환불 금액 합 |

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | REFUND_013 | 고유 주문이 없거나 50건 초과 |

## 환불 가능 수량
`GET /refunds/refundable-items?orderId=` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| orderId (query) | long | Y | 대상 주문 ID |

응답: `List<RefundableItemResponse>`. 주문 상품 순서대로입니다.

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| orderItemId | long | N | 주문 상품 ID |
| productId | long | N | 상품 ID |
| productName | string | N | 주문 당시 상품 이름 |
| productImageUrl | string | Y | 주문 당시 상품 이미지 |
| unitPrice | int | N | 주문 당시 단가 |
| orderedQuantity | int | N | 주문 수량 |
| refundedQuantity | int | N | 요청·완료 상태 환불 수량 합 |
| refundableQuantity | int | N | 남은 환불 가능 수량. 특가·번개 드롭 상품은 0 |
| saleType | string | N | `NONE`·`SPECIAL`·`FLASH_DROP` |
| refundable | boolean | N | 환불 가능 수량이 남았으면 true |

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | ORDER_001 | 없는 주문이거나 남의 주문 |
| 409 | REFUND_010 | 구매 확정된 주문 |
| 400 | REFUND_003 | 결제 완료가 아닌 주문 |
