# 결제 API 명세

상태: 구현 완료 · 담당: 최용범 · 공통 규칙: [API 계약](api-spec.md#공통-규칙) · 자세한 동작: [결제 설계](../architecture/domains/payment.md)

공통 응답·오류 코드·날짜 형식은 [공통 규칙](api-spec.md#공통-규칙)을 따릅니다. 이 문서의 경로는 `/api`를 뺀 값이고, 에러 표에는 공통 오류(401 `A003`, 400 `C001`)를 빼고 이 API만의 오류를 적습니다. 회원 번호는 로그인 정보에서만 읽고 요청에는 받지 않습니다. 결제 금액은 요청으로 받지 않고 주문 금액을 씁니다.

## 목차

- [결제 준비](#결제-준비)
- [결제 확정](#결제-확정)
- [결제창 닫기](#결제창-닫기)
- [PortOne 웹훅](#portone-웹훅)
- [등록 카드 결제](#등록-카드-결제)
- [카드 등록 준비](#카드-등록-준비)
- [카드 등록 확정](#카드-등록-확정)
- [카드 목록](#카드-목록)
- [대표 카드 지정](#대표-카드-지정)
- [카드 삭제](#카드-삭제)
- [결제 비밀번호 상태](#결제-비밀번호-상태)
- [결제 비밀번호 설정](#결제-비밀번호-설정)
- [결제 비밀번호 변경](#결제-비밀번호-변경)
- [결제 비밀번호 재설정](#결제-비밀번호-재설정)
- [결제 응답 DTO](#결제-응답-dto)

등록 카드와 결제 비밀번호의 정책은 [등록 카드 결제 설계](../architecture/domains/payment-card.md)에 있습니다.

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

## 등록 카드 결제
`POST /orders/{orderId}/payments/billing-key` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| orderId (path) | long | Y | 결제할 주문 ID |
| cardId | long | Y | 결제할 등록 카드 ID |
| pin | string | Y | 결제 비밀번호. 숫자 6자리 |

응답: `PaymentConfirmResponse`(결제 확정과 같음)

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | ORDER_001 | 없는 주문 |
| 403 | ORDER_003 | 남의 주문 |
| 409 | PAYMENT_007 | 주문이 결제 대기가 아님. 승인 때 주문이 이미 다른 결제로 결제 완료됐거나 취소됐으면 실패 처리 후 PG 취소 |
| 404 | BILLING_004 | 없는 카드, 남의 카드, 삭제한 카드 |
| 409 | PIN_002 | 결제 비밀번호를 설정하지 않음 |
| 409 | PIN_001 | 결제 비밀번호가 잠김. 5번째로 틀린 요청도 이 오류 |
| 400 | PIN_003 | 결제 비밀번호가 틀림. 메시지에 남은 횟수가 붙음 |
| 409 | PAYMENT_008 | 같은 주문의 등록 카드 결제가 PG에서 아직 끝나지 않음, 또는 결제가 동시에 들어옴 |
| 409 | PAYMENT_010 | 카드사가 결제를 거절함(한도 초과·정지 카드 등). 주문은 결제 대기로 남음 |
| 409 | PAYMENT_011 | 결제 결과를 아직 모름. 웹훅·만료 스케줄러가 마무리 |
| 500 | BILLING_006 | 저장된 빌링키를 풀지 못함(서버 키 설정·저장 값 문제). 결제를 만들지 않고 이전 결제 대기도 그대로 둠 |
| 400 | PAYMENT_004 | 청구는 성공했지만 PG 조회에서 결제 완료를 확인하지 못함 |
| 400 | PAYMENT_002 | PG 금액과 결제 금액이 다름. 실패 처리 후 PG 취소 |
| 409 | PAYMENT_006 | 만료 시각 이후 결제됨. 실패 처리 후 PG 취소 |
| 500 | COMMON_002 | 이전 결제 대기 건의 PG 조회 실패. 새 결제를 만들지 않음 |

## 카드 등록 준비
`POST /billing-keys/prepare` · 로그인

요청: 없음

응답: `BillingKeyPrepareResponse`. PortOne 카드 등록 창(`requestIssueBillingKey`)에 넘기는 값입니다.

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| channelKey | string | N | 카드 등록용(토스페이먼츠 정기결제) PortOne 채널 키 |
| issueId | string | N | 이번 발급 요청 ID. `billing-` + 하이픈 없는 UUID |
| issueName | string | N | 카드 등록 창에 보이는 제목("카드 등록") |
| customerId | string | N | PortOne 고객 ID. `member-{회원 ID}` |

| HTTP | 코드 | 조건 |
|---|---|---|
| 409 | BILLING_002 | 등록 카드가 이미 5장 |

## 카드 등록 확정
`POST /billing-keys` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| billingKey | string | Y | 카드 등록 창이 돌려준 빌링키 |

응답: `BillingKeyResponse`. 201입니다. 같은 빌링키로 다시 보내면 처음 저장한 카드를 201로 돌려줍니다.

| HTTP | 코드 | 조건 |
|---|---|---|
| 409 | BILLING_003 | PortOne 조회 실패, 발급 상태가 아님, 남의 빌링키, 다른 회원이나 삭제한 카드로 이미 저장된 빌링키 |
| 409 | BILLING_005 | 이미 등록한 카드(마스킹 번호와 발급사가 같음) |
| 409 | BILLING_002 | 등록 카드가 이미 5장 |

## 카드 목록
`GET /billing-keys` · 로그인

요청: 없음

응답: `List<BillingKeyResponse>`. 대표 카드가 먼저이고, 그다음 최근 등록순입니다. 삭제한 카드는 빠집니다.

## 대표 카드 지정
`PATCH /billing-keys/{cardId}/default` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| cardId (path) | long | Y | 대표로 정할 카드 ID |

응답: `List<BillingKeyResponse>`. 바뀐 카드 목록입니다. 이미 대표 카드여도 같은 응답을 줍니다.

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | BILLING_004 | 없는 카드, 남의 카드, 삭제한 카드 |

## 카드 삭제
`DELETE /billing-keys/{cardId}` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| cardId (path) | long | Y | 지울 카드 ID |

응답: 없음(`ApiResponse.ok()`)

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | BILLING_004 | 없는 카드, 남의 카드, 이미 삭제한 카드 |

## 결제 비밀번호 상태
`GET /payment-pin` · 로그인

요청: 없음

응답: `PaymentPinStatusResponse`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| isSet | boolean | N | 설정했으면 true |
| isLocked | boolean | N | 5번 틀려 잠겼으면 true |
| remainingAttempts | int | N | 더 틀릴 수 있는 횟수. 설정하지 않았으면 5 |

## 결제 비밀번호 설정
`POST /payment-pin` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| pin | string | Y | 숫자 6자리 |

응답: 없음(`ApiResponse.ok()`). 201입니다.

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | PIN_004 | 같은 숫자만 쓰거나(111111) 1씩 오르내리는 숫자(123456, 654321) |
| 409 | PIN_005 | 이미 설정함. 변경이나 재설정을 씀 |

## 결제 비밀번호 변경
`PATCH /payment-pin` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| currentPin | string | Y | 현재 결제 비밀번호. 숫자 6자리 |
| newPin | string | Y | 새 결제 비밀번호. 숫자 6자리 |

응답: 없음(`ApiResponse.ok()`)

| HTTP | 코드 | 조건 |
|---|---|---|
| 409 | PIN_002 | 설정하지 않음 |
| 409 | PIN_001 | 잠김. 현재 비밀번호를 5번째로 틀린 요청도 이 오류 |
| 400 | PIN_004 | 새 비밀번호가 쉬움 |
| 400 | PIN_003 | 현재 비밀번호가 틀림. 결제 때와 같이 틀린 횟수에 들어감 |
| 400 | PIN_008 | 새 비밀번호가 현재 비밀번호와 같음 |

## 결제 비밀번호 재설정
`POST /payment-pin/reset` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| loginPassword | string | Y | 로그인 비밀번호 |
| newPin | string | Y | 새 결제 비밀번호. 숫자 6자리 |

응답: 없음(`ApiResponse.ok()`)

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | PIN_004 | 새 비밀번호가 쉬움. 시도 횟수에 넣지 않음 |
| 429 | PIN_007 | 로그인 비밀번호를 30분 안에 5번 넘게 시도함 |
| 503 | PIN_009 | 시도 횟수를 셀 수 없음(Redis 장애). 재설정을 막음 |
| 400 | PIN_006 | 로그인 비밀번호가 틀림 |

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

`BillingKeyResponse`. 빌링키 값은 내려주지 않습니다.

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| cardId | long | N | 카드 ID |
| cardName | string | Y | 카드 상품명. PortOne이 주지 않으면 null |
| cardLast4 | string | Y | 카드 번호 끝 4자리 |
| cardType | string | Y | `CREDIT`(신용)·`DEBIT`(체크)·`GIFT`(기프트) |
| isDefault | boolean | N | 대표 카드면 true |
| createdAt | datetime | N | 등록 시각 |
