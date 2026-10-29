# 할인 분석 검색 클릭 입력 조회

상태: 구현 · 작성일: 2026-10-29 · 담당: 관리자·분석

## 목표

24번 분석에서 대상 주 직전 4주의 같은 요일 검색 결과 상품 클릭 수를 기준 시각으로 읽는다.

## 구현

`DealClickInputService.getSameWeekdayClicks(targetWeekStart, dayOfWeek, asOf)`가 검색 도메인의 상품별 클릭 수를 해당 요일의 4일에 대해 조회하고 상품별로 합친다. 클릭이 없는 상품은 결과에 없으며 분석 단계에서 0으로 처리한다. API·DB 계약은 변경하지 않는다.

## 검증

`DealClickInputServiceTest`로 조회일 4개, 기준 시각 전달, 상품별 합산을 확인한다.
