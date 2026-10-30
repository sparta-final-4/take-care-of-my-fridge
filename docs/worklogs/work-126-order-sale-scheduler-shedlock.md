# WORK-126: 주문·할인 행사 스케줄러에 서버 간 잠금 걸기

상태: 검증 대기 · 작성일: 2026-10-30 · 담당: 준혁

## 목표와 완료 기준

- 서버가 여러 대여도 주문 만료, 자동 구매 확정, 할인 행사 열기·닫기를 한 서버만 실행한다.
- 번개 드롭이 열릴 때 시작 알림 요청이 한 번만 나간다.

## 근거와 범위

- 문제
  - 세 스케줄러는 서버마다 따로 돈다. `fixedDelay`는 한 서버 안의 실행끼리만 겹치지 않게 한다.
  - 할인 행사 열기는 열 번개 드롭 ID를 잠금 없이 먼저 읽고 일괄 UPDATE로 연다. 두 서버가 함께 돌면 둘 다 같은 ID를 읽어 시작 알림 요청을 두 번 발행한다. 알림 저장의 중복 방지 키가 저장은 막지만 발행은 두 번이다.
  - 주문 만료와 자동 구매 확정은 주문 행을 잠그고 상태를 확인하므로 함께 돌아도 결과는 같다. 같은 주문을 두 번 처리하는 일과 잠금 대기만 생긴다.
- 잠금 시간은 `lockAtMostFor` 30분만 두고 `lockAtLeastFor`는 두지 않는다. `ShedLockConfig`의 기본값과 같다.
  - 1분 작업을 돌던 서버가 죽으면 최대 30분 동안 주문 만료와 할인 행사 열기·닫기가 멈춘다. 진행 중인지는 조회가 시각으로 판단하므로 화면 동작은 같다.
  - 최소 잠금 시간이 없어 동시 실행만 막고, 두 서버가 시차를 두고 연달아 도는 것은 막지 않는다.

## 구현 결과

| 스케줄러 | 실행 주기 | 잠금 이름 |
|---|---|---|
| `OrderExpireScheduler.expireAbandonedOrders` | 이전 실행 1분 뒤 | `orderExpire` |
| `OrderAutoConfirmScheduler.confirmDueOrders` | 매시 정각 | `orderAutoConfirm` |
| `SaleEventScheduler.openAndCloseDueEvents` | 이전 실행 1분 뒤 | `saleEventOpenClose` |

- 세 메서드에 `@SchedulerLock(name = ..., lockAtMostFor = "30m")`을 붙였다. 잠금은 기존 Redis 잠금 설정을 그대로 쓴다.
- 주석에 잠금이 막는 것을 적었다. 할인 행사는 중복 알림, 주문 둘은 중복 처리다.
- API, DB, 팀 문서 변경은 없다.

## 검증

| 명령·시나리오 | 실행 환경·조건 | 통과/실패/미실행 | 근거 |
|---|---|---|---|
| `sh ./gradlew test --tests '*OrderSchedulerLockTest' --tests '*SaleEventSchedulerLockTest' --tests '*OrderExpireSchedulerTest' --tests '*OrderAutoConfirmSchedulerTest' --tests '*SaleEventSchedulerTest'` | 로컬(맥) | 통과 | 각 클래스 실패·오류·건너뜀 없음 |
| 잠금 설정 | 어노테이션 확인 | 통과 | 세 메서드에 잠금이 붙어 있고 이름이 위 표와 같다 |
| 기존 스케줄러 동작 | 목 | 통과 | 만료의 한 건 실패·다음 묶음 읽기, 자동 확정의 결제 7일 기준, 열기·닫기 호출과 실패 처리가 그대로다 |

Redis로 같은 이름의 두 번째 잠금이 거절되는 동작은 기존 잠금 통합 테스트가 확인한다.

## 연결과 남은 작업

- 주문번호 순번을 서버 메모리 대신 Redis에서 받는다.
