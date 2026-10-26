# WORK-086: 할인 행사 리뷰 반영

상태: 검증 완료 · 작성일: 2026-10-23 · 담당: 준혁

## 목표와 완료 기준

- 할인 행사 스택(#229~#235) 리뷰에서 나온 정리 항목 네 개를 반영한다. 동작은 바뀌지 않는다.

## 근거와 범위

- 선행 작업: WORK-080~085.
- 진행 중 상태 목록(`예정`, `진행 중`)이 등록 서비스, QueryDSL 구현, 진행 중 할인 조회에 따로 있어 한 곳으로 모았다.
- 번개 드롭 구매 한도 1이 장바구니 담기·수량 변경, 주문하기 세 곳에 숫자로 있어 상수 하나로 모았다.
- `SaleType.from`이 `valueOf(name())`이라 행사 구분이 늘면 런타임에 실패한다. `switch`로 바꿔 컴파일 때 알 수 있게 했다.
- 스케줄러가 예외를 삼키는 분기에 테스트가 없어 추가했다.
- 리뷰의 나머지 항목(환불 경로, 한도와 재고 검사 순서, 동시 담기 등)은 현재 흐름에서 일어나지 않거나 의도라서 바꾸지 않았다.

## 구현 결과

- `SaleEventStatus.OPEN`(예정·진행 중): `SaleEventCreateService`, `SaleEventItemRepositoryImpl`, `SaleEventReader`, `SaleEventStatusService`가 쓴다.
- `SaleEvent.FLASH_DROP_PURCHASE_LIMIT`(1): `CartService`, `OrderCreateService`가 쓴다.
- `SaleType.from`: `switch`로 명시 매핑한다.
- `SaleEventSchedulerTest`(신규): 지금 시각으로 상태 정리를 부르는지, 상태 정리가 실패해도 예외를 밖으로 내지 않는지 확인한다.

## 검증

| 명령·시나리오 | 실행 환경·조건 | 통과/실패/미실행 | 근거 |
|---|---|---|---|
| `./gradlew test` | 로컬(맥), dev c18edca 기준 | 통과 | 전체 테스트 실패 없음. 새 테스트 포함 |

## 다음 작업

스택 머지 뒤 docs 일괄 갱신(`api-spec.md`, 주문 도메인 설계 문서)을 한다.
