# 판매 예측 API 명세

상태: 구현 완료 · 담당: cookiez · 공통 규칙: [API 계약](api-spec.md#공통-규칙) · 자세한 동작: [판매 예측 설계](../architecture/domains/sales-forecast.md)

공통 응답·오류 코드·페이지·날짜 형식은 [공통 규칙](api-spec.md#공통-규칙)을 따릅니다. 이 문서의 경로는 `/api`를 뺀 값이고, 오류 표에는 공통 오류(401 `A003`, 403 `A004`, 400 `C001`)를 빼고 이 API만의 오류를 적습니다. 모든 API는 ADMIN 전용 조회입니다. 예측 생성과 실제 판매량 확정은 서버 배치가 합니다.

## 목차

- [주간 예측 조회](#주간-예측-조회)
- [최신 예측 조회](#최신-예측-조회)
- [시연 예측 조회](#시연-예측-조회)
- [예측 응답 DTO](#예측-응답-dto)
- [오류 코드](#오류-코드)

## 주간 예측 조회
`GET /admin/forecasts/{weekStart}` · ADMIN

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| weekStart (path) | date | Y | 대상 주의 월요일. 월요일이 아니면 400 `C001` |

응답: `ForecastResponse` ([예측 응답 DTO](#예측-응답-dto))

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | F001 | 그 주의 실제 예측이 없음 |

주의 사항
- 가상 데이터(`syntheticData=true`) 결과는 돌려주지 않습니다.

## 최신 예측 조회
`GET /admin/forecasts/latest` · ADMIN

요청: 없음

응답: `ForecastResponse` (가상 데이터를 뺀 가장 최근 대상 주)

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | F001 | 실제 예측이 하나도 없음. `error.message`에 없는 원인을 담음 |

## 시연 예측 조회
`GET /admin/forecasts/demo` · ADMIN

요청: 없음

응답: `ForecastResponse` (`syntheticData=true`인 저장 결과)

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | F001 | 시연 결과가 없음 |

주의 사항
- 시연 응답의 `previousWeekStart`·`nextWeekStart`는 null입니다.

## 예측 응답 DTO

`ForecastResponse`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| weekStart | date | N | 대상 주 월요일 |
| weekEnd | date | N | 대상 주 일요일 |
| modelVersion | string | N | 예측 모델 버전 |
| dataCutoffAt | datetime | N | 입력 마감 시각 |
| createdAt | datetime | N | 예측 생성 시각 |
| confirmed | boolean | N | 실제 판매량이 확정됐으면 true |
| confirmedAt | datetime | Y | 실제 판매량 확정 시각 |
| syntheticData | boolean | N | 가상 데이터 결과면 true |
| previousWeekStart | date | Y | 실제 예측이 있는 이전 주. 없으면 null |
| nextWeekStart | date | Y | 실제 예측이 있는 다음 주. 없으면 null |
| summary | `ForecastSummary` | Y | 확정 전이면 null |
| items | `ForecastItem[]` | N | 예상 TOP 10(예상 순위순) 뒤에 실제 TOP 10에 새로 든 상품 |

`ForecastSummary`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| top10MatchCount | int | N | 예상 TOP 10 중 실제 TOP 10에 든 수 |
| baselineTop10MatchCount | int | N | 기준선 TOP 10 중 실제 TOP 10에 든 수 |
| meanAbsoluteError | double | N | 예상 TOP 10 상품의 \|예상 − 실제\| 평균 |
| baselineMeanAbsoluteError | double | N | 같은 상품의 \|기준선 − 실제\| 평균 |
| enteredCount | int | N | 실제 TOP 10에 새로 든 상품 수 |
| droppedCount | int | N | 예상 TOP 10에서 빠진 상품 수 |

`ForecastItem`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| productId | long | N | 상품 ID |
| productName | string | N | 상품명 |
| predictedRank | int | Y | 예상 순위. 새로 든 상품은 null |
| predictedQty | int | Y | 예상 수량. 새로 든 상품은 null |
| baselineQty | int | N | 기준선 수량 |
| actualRank | int | Y | 실제 순위. 확정 전 null |
| actualQty | int | Y | 실제 수량. 확정 전 null, 판매 없으면 0 |
| rankChange | int | Y | 예상 순위 − 실제 순위 |
| qtyError | int | Y | 예상 − 실제 |
| status | `MATCHED`·`DROPPED`·`ENTERED` | Y | 일치·이탈·진입. 확정 전 null |

## 오류 코드

| 코드 | HTTP | 이름 | 메시지 |
|---|---|---|---|
| F001 | 404 | FORECAST_NOT_FOUND | 해당 주의 예측이 없습니다. |
