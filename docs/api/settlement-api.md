# 정산 API 명세

상태: 구현 완료 · 담당: 결제·환불 · 공통 규칙: [API 계약](api-spec.md#공통-규칙) · 자세한 동작: [정산 설계](../architecture/domains/settlement.md)

공통 응답·오류 코드·날짜 형식은 [공통 규칙](api-spec.md#공통-규칙)을 따릅니다. 이 문서의 경로는 `/api`를 뺀 값이고, 에러 표에는 공통 오류(401 `A003`, 403 `A004`, 400 `C001`)를 빼고 이 API만의 오류를 적습니다. 모두 관리자(ADMIN)만 부릅니다. 날짜는 `YYYY-MM-DD`이고 한국 시간 기준이며, 시작일과 종료일을 모두 포함합니다.

## 목차

- [정산 조회](#정산-조회)
- [결제 건별 조회](#결제-건별-조회)
- [오늘 매출](#오늘-매출)
- [정산 응답 DTO](#정산-응답-dto)

## 정산 조회
`GET /admin/settlements?unit=&startDate=&endDate=` · ADMIN

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| unit (query) | string | Y | `DAY`·`WEEK`(월~일)·`MONTH`(1일~말일) |
| startDate (query) | date | Y | 시작일 |
| endDate (query) | date | Y | 종료일. 시작일 이후·오늘까지, 시작일 + 3개월 전날까지 |

응답: `SettlementResponse`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| summary | `SalesAmountResponse` | N | 조회 기간 전체 합계 |
| periods | `SettlementPeriodResponse[]` | N | 단위별 행. 오래된 기간부터 |
| methods | `SettlementMethodResponse[]` | N | 조회 기간의 결제 수단별 합계. 결제 금액이 큰 순, 같으면 등록 카드 먼저 |

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | C001 | 종료일이 시작일보다 빠름, 종료일이 내일 이후, 조회 기간 3개월 초과 |

주의 사항
- 어제까지는 일별 집계를 읽고, 집계되지 않은 날은 그 자리에서 집계해 채운 뒤 응답합니다. 기간에 오늘이 있으면 오늘 지금까지의 매출을 붙입니다.
- 순매출은 환불일 기준이라 음수일 수 있습니다.

## 결제 건별 조회
`GET /admin/settlements/payments?startDate=&endDate=&page=&size=` · ADMIN

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| startDate (query) | date | Y | 시작일 |
| endDate (query) | date | Y | 종료일. 정산 조회와 같은 규칙 |
| page (query) | int | N | 1부터. 기본 1, 최대 10000 |
| size (query) | int | N | 기본 20, 1~100 |

응답: `PageResponse<SettlementPaymentResponse>`. 기간 안에 결제·환불이 있었던 결제 한 건이 한 행이고, 마지막 결제·환불이 최근인 결제가 먼저입니다.

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| paymentId | long | N | 결제 ID |
| paidAt | datetime | N | 결제 완료 시각 |
| orderNumber | string | N | 주문번호 |
| buyerName | string | Y | 회원 닉네임. 회원 행이 없으면 null |
| registeredCard | boolean | N | 등록 카드 결제면 true |
| pgProvider | string | N | 실제로 승인한 결제대행사 |
| paidAmount | long | N | 기간 안의 결제 금액. 기간 전에 결제하고 기간 안에 환불했으면 0 |
| refundedAmount | long | N | 기간 안의 환불 금액 |
| netAmount | long | N | paidAmount − refundedAmount. 음수 가능 |

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | C001 | 기간 규칙 위반, page·size 범위 밖 |

주의 사항
- 금액은 기간 안에서 일어난 것만 세므로 모든 행을 더하면 정산 조회의 summary와 같습니다.

## 오늘 매출
`GET /admin/settlements/today` · ADMIN

요청: 없음

응답: `TodaySalesResponse`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| salesDate | date | N | 오늘(한국 시간) |
| sales | `SalesAmountResponse` | N | 오늘 0시부터 조회 시점까지의 매출. 저장하지 않고 바로 셈 |

## 정산 응답 DTO

`SalesAmountResponse`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| paidAmount | long | N | 결제 금액 |
| refundedAmount | long | N | 환불 금액(PG 콘솔 취소 포함) |
| netAmount | long | N | 순매출. 음수 가능 |
| paidCount | int | N | 결제 건수 |
| refundedCount | int | N | 환불 건수. 취소 이력 한 건이 1건(같은 결제를 두 번 부분 환불하면 2건) |

`SettlementPeriodResponse`: `SalesAmountResponse`의 필드에 아래 두 필드가 더 있습니다.

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| startDate | date | N | 기간 시작일 |
| endDate | date | N | 기간 종료일. 일 단위면 시작일과 같고, 조회 기간에서 잘린 주·월은 잘린 날짜 |

`SettlementMethodResponse`: `SalesAmountResponse`의 필드에 아래 두 필드가 더 있습니다.

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| registeredCard | boolean | N | 등록 카드 결제면 true. 결제창으로 낸 같은 결제대행사 결제와 따로 셈 |
| pgProvider | string | N | 실제로 승인한 결제대행사 |
