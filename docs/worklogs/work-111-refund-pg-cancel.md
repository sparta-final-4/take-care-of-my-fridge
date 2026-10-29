# WORK-111: 환불 PG 부분 취소와 환불 신청 API

상태: 완료 · 작성일: 2026-10-23 · 담당: 결제·환불

## 목표와 완료 기준

- 환불 신청을 받으면 PG에서 환불 금액만큼 부분 취소하고, 성공하면 완료로 바꾼다.
- 완료되면 환불 수량만큼 상품 재고를 되돌린다.
- 같은 환불을 여러 번 마무리하려 해도 재고는 한 번만 돌아온다.

## 근거와 범위

- PR: #225(PG 부분 취소·완료·재고 복구), #227(환불 신청 API). 모두 10/23.
- PG 호출은 트랜잭션 밖(`RefundFacade`)에서 하고, 저장·완료는 각각 짧은 트랜잭션(`RefundCommandService`)으로 한다.
- PG 거절·결과 모름 처리는 이 작업에서 TODO로 남기고 WORK-113에서 채웠다.

## 구현 결과

- `RefundFacade.request`: 요청 상태로 저장 → PG 부분 취소(`PaymentGateway.cancelPayment`, 금액 지정) → 완료 처리.
- `RefundCommandService.complete`: 환불 행을 잠그고 `refresh`로 다시 읽은 뒤 요청 상태일 때만 완료한다. 이미 처리된 환불이면 현재 상태를 돌려준다. 환불 수량만큼 재고를 되돌릴 때는 주문하기와 같은 순서(상품 ID 순)로 상품 행을 잠근다.
- `PaymentGateway.cancelPayment`가 PG 취소 건 ID를 돌려주게 바꿨다. 환불에 `portone_cancellation_id`로 남긴다.
- `RefundReasonType`에 PG 취소 사유로 보낼 설명을 더했다.
- API `POST /api/refunds`: 201, `RefundResponse`(refundId, status, refundAmount, refundedAt). 요청에 `orderId`를 받고 회원 번호는 로그인 정보에서 읽는다.
- 리뷰 반영: 이미 취소된 결제에 부분 취소를 요청하면 PortOne이 "이미 취소됨"으로 답한다. 이것을 성공으로 보면 환불되지 않았는데 완료·재고 복구가 일어나므로 부분 취소에서는 거절로 본다(전액 취소에서만 성공으로 봄).

## 검증

| 명령·시나리오 | 실행 환경·조건 | 통과/실패/미실행 | 근거 |
|---|---|---|---|
| `RefundFacadeTest`, `RefundCommandServiceTest`, `RefundControllerTest`, `PortOneClientTest` | 로컬(Windows), MySQL 컨테이너, 2026-10-29 dev 기준 | 통과 | 각각 16건, 16건, 15건, 12건 실패 0 |
| 환불 완료 | 같은 환경 | 통과 | 취소 ID·환불 시각이 남고 환불 수량만큼 재고 복구 |
| 이미 완료된 환불을 다시 완료 | 같은 환경 | 통과 | 바꾸지 않고 현재 상태, 재고는 한 번만 복구 |
| 이미 취소된 결제의 부분 취소 | 같은 환경 | 통과 | 거절로 던져 완료 처리 안 함 |

## 연결과 남은 작업

- 재고 복구는 환불·주문 취소·할인 행사가 각자 상품 재고를 직접 바꾼다. 상품 도메인 서비스로 모을지 결정이 남았다.
