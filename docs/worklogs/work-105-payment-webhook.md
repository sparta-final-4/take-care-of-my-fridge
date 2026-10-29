# WORK-105: PortOne 결제 웹훅 수신과 멱등 처리

상태: 완료 · 작성일: 2026-10-20 · 담당: 결제·환불

## 목표와 완료 기준

- 사용자가 결제 직후 창을 닫아 확정 요청이 오지 않아도 PortOne 웹훅으로 결제를 승인한다.
- 서명이 맞는 웹훅만 받고, 같은 웹훅이 여러 번(재전송·동시 수신) 와도 한 번만 처리한다.
- 처리에 실패하면 PortOne이 다시 보내게 한다.

## 근거와 범위

- PR: #161(10/20).
- 웹훅 본문의 상태를 믿지 않고, 받을 때마다 PG를 다시 조회해 결제 상태를 맞춘다(`syncWithPg`). 웹훅 순서가 뒤바뀌어도 PG의 현재 상태로 판단한다.
- 결제 완료 뒤의 취소·부분 취소(환불)는 이때 처리하지 않고 기록만 했다. 환불 도메인 작업 때 연결했다(#281).

## 구현 결과

- `POST /api/webhooks/portone`: 공개 경로, CSRF 제외. 서명 검증을 위해 본문을 원문 그대로 받는다.
- `PortOneWebhookVerifier`: PortOne server-sdk 0.24.0 `WebhookVerifier`로 서명 검증. 실패하면 401(`WEBHOOK_001`)이고 기록하지 않는다. SDK가 모르는 type도 원문 type을 그대로 쓴다.
- `webhook_events` 테이블과 `WebhookEventService`: `webhook_id` UNIQUE. 처리가 끝난(`PROCESSED`·`IGNORED`) 웹훅이 다시 오면 건너뛰고, `RECEIVED`·`FAILED`는 다시 처리한다. 수신 기록은 결제 처리와 다른 트랜잭션이라 처리가 실패해도 남는다.
- `WebhookHandler`: `Transaction.Paid`·`Failed`·`Cancelled`만 처리하고 나머지는 `IGNORED`. 결제를 못 찾으면 `IGNORED`. 처리 중 오류는 `FAILED`로 기록하고 예외를 다시 던져 오류로 응답한다.
- `PaymentFacade.syncWithPg`
  - 우리 결제 대기 + PG 결제 완료: 금액 확인 후 승인, 다르면 실패 처리 후 PG 취소
  - 우리 실패 + PG 결제 완료: 보상 취소(결제창 닫기와 PG 승인이 겹친 경우)
  - 우리 결제 대기 + PG 실패·취소: `PG_FAILED`
- 리뷰 반영
  - 같은 `webhook_id`가 동시에 들어와 UNIQUE 위반이 나면, 다른 요청이 실제로 저장한 경우만 건너뛰고 그 외 제약 위반은 다시 던진다.
  - 실패 기록 자체가 실패해도 원래 예외를 던지고 기록 오류는 suppressed로 남긴다.
  - 결제 잠금 뒤 `refresh`로 DB 값을 다시 읽는다. OSIV로 같은 요청에서 먼저 읽은 엔티티가 재사용돼, 잠근 뒤에도 예전 상태로 판단하는 문제(실패한 결제를 결제 완료로 덮어씀)를 고쳤다. `refresh` 전에 `flush`해 반영 전 변경을 지킨다.

## 검증

| 명령·시나리오 | 실행 환경·조건 | 통과/실패/미실행 | 근거 |
|---|---|---|---|
| `./gradlew test --tests '*Webhook*'` | 로컬(Windows), 2026-10-29 dev 기준 | 통과 | `WebhookHandlerTest` 11건, `WebhookEventServiceTest` 5건, `PortOneWebhookVerifierTest` 5건, `WebhookControllerTest` 2건 |
| 서명 불일치·헤더 없음·5분 지난 웹훅 | 같은 환경 | 통과 | 검증 실패, 기록 안 함 |
| 같은 웹훅 재수신·동시 수신 | 같은 환경 | 통과 | 처리가 끝났으면 건너뜀, 동시 저장 충돌은 한쪽만 처리 |
| 처리 중 오류 | 같은 환경 | 통과 | `FAILED` 기록 후 예외, 재전송 때 다시 처리 |

## 연결과 남은 작업

- 보상 취소 실패의 재시도는 웹훅 재전송에만 기대다가 WORK-108에서 스케줄러를 더했다.
- 취소·부분 취소 웹훅의 환불 연동은 환불 작업 기록에서 다룬다.
