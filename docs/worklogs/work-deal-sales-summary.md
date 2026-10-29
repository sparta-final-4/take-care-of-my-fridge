# 할인 분석 순판매 수량과 요일 평균

상태: 구현 · 작성일: 2026-10-29 · 담당: 관리자·분석

## 목표

24번 분석의 `purchases`를 구매 확정 후 환불을 뺀 상품 수량으로 채우고, 판매 없는 날도 포함해 직전 4주 같은 요일의 평균을 계산한다.

## 구현

`DealSalesSummaryService.getByProduct`가 기존 판매량 입력의 상품별 수량을 합친다. `purchases`는 합계이고 `avgSalesOnDay`는 4일 평균을 반올림한 정수다. 기록이 없는 상품은 결과에 없으며 분석 단계에서 0으로 처리한다. API·DB 구조는 변경하지 않는다.

## 검증

`DealSalesSummaryServiceTest`로 수량 합계와 기록이 없는 날을 포함한 4일 평균을 확인한다.
