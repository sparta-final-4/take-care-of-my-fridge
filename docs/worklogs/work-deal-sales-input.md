# 할인 분석 판매량 입력 조회

상태: 구현 · 작성일: 2026-10-29 · 담당: 관리자·분석

## 목표

24번 분석에서 대상 주 직전 4주의 같은 요일 판매량을 기준 시각으로 읽는다.

## 구현

`DealSalesInputService.getSameWeekdaySales(targetWeekStart, dayOfWeek, asOf)`가 주문 도메인의 `OrderSalesService.getDailyProductSales`를 한 번 호출하고 해당 요일 행만 반환한다. 주문 도메인의 확정 수량·기준 시각 정책을 그대로 사용한다. 평균과 판매 없는 날의 0 처리 정책은 이번 단계에서 정하지 않는다. API·DB 계약은 변경하지 않는다.

## 검증

`DealSalesInputServiceTest`로 4주 기간, 기준 시각 전달, 요일 필터를 확인한다. 전체 빌드 결과는 PR 검사에서 확인한다.
