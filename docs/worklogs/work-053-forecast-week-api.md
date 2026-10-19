# WORK-053: 특정 주 예측 관리자 API

상태: 완료 · 작성일: 2026-10-19 · 담당: 관리자·판매 예측

## 목표와 완료 기준
- GET /api/admin/forecasts/{weekStart}에서 지정 주의 예측을 반환한다.
- 잘못된 날짜, 없는 예측, USER·미인증 접근을 명세대로 처리한다.

## 근거와 범위
- 근거: 판매 예측 API 5-1.
- 범위: 관리자 컨트롤러와 MVC 테스트.
- 가정: 기존 SecurityConfig의 /api/admin/** 규칙을 재사용한다.

## 계획
| 단계 | 변경 | 검증 |
|---|---|---|
| HTTP API | 날짜 파싱과 ApiResponse 응답 | MVC 테스트 |

## AI 활용과 개발자 판단
- AI가 기존 관리자 컨트롤러·보안 테스트 패턴을 따랐다.
- 저장·계산 로직은 선행 PR에 두어 HTTP 변경을 작게 유지했다.

## 구현 결과
- 주간 예측 관리자 조회 엔드포인트를 추가했다.

## 검증
| 명령·시나리오 | 실행 환경·조건 | 결과 | 근거 |
|---|---|---|---|
| .\gradlew.bat test --tests "*AdminForecastControllerTest" | 로컬 | 통과 | BUILD SUCCESSFUL, 4개 테스트 |

## 연결과 남은 작업
- 최신 주 조회는 후속 PR.
