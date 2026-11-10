# 결제 API 명세

상태: 구현 완료 · 담당: 결제·환불 · 공통 규칙: [API 계약](api-spec.md#공통-규칙) · 자세한 동작: [결제 설계](../architecture/domains/payment.md)

공통 응답·오류 코드·날짜 형식은 [공통 규칙](api-spec.md#공통-규칙)을 따릅니다. 이 문서의 경로는 `/api`를 뺀 값이고, 에러 표에는 공통 오류(401 `A003`, 400 `C001`)를 빼고 이 API만의 오류를 적습니다. 회원 번호는 로그인 정보에서만 읽고 요청에는 받지 않습니다. 결제 금액은 요청으로 받지 않고 주문 금액을 씁니다.

## 목차

- [결제 준비](#결제-준비)
- [결제 확정](#결제-확정)
- [결제창 닫기](#결제창-닫기)
- [PortOne 웹훅](#portone-웹훅)
- [결제 응답 DTO](#결제-응답-dto)

## 결제 준비
`POST /orders/{orderId}/payments` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| orderId (path) | long | Y | 결제할 주문 ID |
| pgProvider | string | Y | 결제대행사. `KAKAOPAY`(카카오페이)·`TOSSPAY`(토스페이)·`KICC`(이지페이)·`INICIS`(KG이니시스)·`TOSSPAYMENTS`(토스페이먼츠). 없거나 모르는 값이면 400 `C001` |

응답: `PaymentPrepareResponse`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| orderId | long | N | 주문 ID |
| portonePaymentId | string | N | 결제창에 넘길 결제 ID(40자) |
| orderName | string | N | 결제창에 보일 주문 이름. 예: "대파 외 2건" |
| amount | long | N | 결제 금액(주문 금액) |
| expiresAt | datetime | N | 결제 대기 만료 시각. 준비 10분 뒤 |
| pgProvider | string | N | 고른 결제대행사 |
| channelKey | string | N | 그 결제대행사의 PortOne 채널 키 |
| payMethod | string | N | `EASY_PAY`(카카오페이·토스페이)·`CARD`(나머지) |
| easyPayProvider | string | Y | `KAKAOPAY`·`TOSSPAY`. 간편결제가 아니면 null |

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | ORDER_001 | 없는 주문 |
| 403 | ORDER_003 | 남의 주문 |
| 409 | PAYMENT_007 | 주문이 결제 대기가 아님(결제 완료·취소). 이전 결제가 PG에서 끝나 지금 승인된 경우 포함 |
| 409 | PAYMENT_008 | 같은 주문의 결제 준비가 동시에 들어와 다른 요청이 먼저 만듦 |
| 500 | COMMON_002 | 이전 결제 대기 건의 PG 조회 실패. 새 결제를 만들지 않음 |

주의 사항
- 결제하기를 누를 때마다 부릅니다. 다시 결제할 때 다른 결제대행사를 골라도 됩니다.
- 프론트는 응답의 channelKey·payMethod·easyPayProvider로 결제창을 엽니다. 채널 키는 서버 설정에만 있습니다.

## 결제 확정
`POST /payments/confirm` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| orderId | long | Y | 결제할 주문 ID. 결제의 주문과 다르면 400 `C001` |
| portonePaymentId | string | Y | 결제 준비에서 받은 결제 ID |

응답: `PaymentConfirmResponse`

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | PAYMENT_001 | 없는 결제 |
| 403 | ORDER_003 | 남의 결제 |
| 400 | PAYMENT_004 | PG에 결제가 없음·진행 중·실패, PG 응답에 금액·결제 시각 없음 |
| 400 | PAYMENT_002 | PG 금액과 결제 금액이 다름. 실패 처리 후 PG 취소 |
| 409 | PAYMENT_006 | 만료 시각 이후 결제됨. 실패 처리 후 PG 취소 |
| 409 | PAYMENT_007 | 주문이 이미 다른 결제로 결제 완료됐거나 취소됨. 실패 처리 후 PG 취소 |
| 409 | PAYMENT_005 | 이미 실패 처리된 결제(창 닫기·새 결제로 정리·PG 실패) |

주의 사항
- 결제창이 성공으로 끝나도 이 응답이 200이어야 결제 성공입니다. 서버가 PortOne에 결제 건을 다시 조회해 확인합니다.
- 이미 결제 완료된 결제를 다시 확정하면 PG 조회 없이 같은 200 응답을 줍니다.
- 웹훅·스케줄러가 먼저 실패 처리한 결제도 실패 사유에 맞는 오류(`PAYMENT_002`·`PAYMENT_006`·`PAYMENT_007`·`PAYMENT_005`)로 답합니다.

## 결제창 닫기
`POST /payments/{portonePaymentId}/fail` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| portonePaymentId (path) | string | Y | 닫은 결제창의 결제 ID |

응답: 없음(`ApiResponse.ok()`)

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | PAYMENT_001 | 없는 결제 |
| 403 | ORDER_003 | 남의 결제 |
| 409 | PAYMENT_005 | 결제 대기가 아님, PG에서 이미 결제됨 |

주의 사항
- 결제 대기 건만 실패로 바꾸고, 주문은 결제 대기로 남아 다시 결제할 수 있습니다.
- 프론트는 지금 이 API를 부르지 않습니다. 만료 스케줄러가 10분 뒤 정리합니다.

## PortOne 웹훅
`POST /webhooks/portone` · 공개(서명 검증), CSRF 제외

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| webhook-id (header) | string | Y | PortOne 웹훅 ID |
| webhook-timestamp (header) | string | Y | 보낸 시각 |
| webhook-signature (header) | string | Y | 서명 |
| (body) | string | Y | 서명 검증을 위해 원문 그대로 받음 |

응답: 없음(`ApiResponse.ok()`)

| HTTP | 코드 | 조건 |
|---|---|---|
| 401 | WEBHOOK_001 | 서명 헤더 없음·서명 불일치·5분이 지난 웹훅. 기록하지 않음 |
| 5xx | COMMON_002, PAYMENT_009 등 | 처리 실패(PG 조회 실패, 보상 취소 거절 등). `FAILED`로 기록하고 PortOne 재전송을 받음 |

주의 사항
- `Transaction.Paid`·`Failed`·`Cancelled`·`PartialCancelled`만 처리하고 나머지는 기록만 합니다. 본문 값을 믿지 않고 PG를 다시 조회합니다.
- 같은 `webhook-id`가 다시 오면 처리가 끝난 경우 200만 돌려줍니다.

## 결제 응답 DTO

`PaymentConfirmResponse`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| id | long | N | 결제 ID |
| orderId | long | N | 주문 ID |
| portonePaymentId | string | N | PortOne 결제 ID |
| amount | long | N | 결제 금액 |
| status | string | N | `PAID` |
| paidAt | datetime | N | 결제 완료 시각 |
| pgProvider | string | N | 실제로 결제된 결제대행사. PG에서 확인하지 못하면 고른 값 |

결제 상태 값: `READY`(결제 대기), `PAID`(결제 완료), `FAILED`(실패).
