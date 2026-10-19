# WORK-054: 최신 주 예측 조회

상태: 완료 · 작성일: 2026-10-19 · 담당: 관리자·판매 예측

## 목표와 완료 기준
- 관리자가 가장 최근 대상 주의 예측을 조회한다.
- 예측이 하나도 없으면 F001 404를 반환한다.

## 근거와 범위
- 근거: 판매 예측 API 5-2.
- 범위: 최신 주 리포지토리 쿼리, 서비스, 관리자 API, 대상 테스트.
- 가정: 최신은 생성 시각이 아닌 target_week_start의 최댓값이다.

## 계획
| 단계 | 변경 | 검증 |
|---|---|---|
| 최신 조회 | 대상 주 내림차순 첫 기록 선택 | 서비스 테스트 |
| API | /latest 고정 경로 | MVC 테스트 |

## AI 활용과 개발자 판단
- AI가 19번의 getByWeek와 응답 구조를 재사용했다.
- 조회 쿼리 한 번이 추가되지만 공통 조립 경로를 유지했다.

## 구현 결과
- GET /api/admin/forecasts/latest를 추가했다.

## 검증
| 명령·시나리오 | 실행 환경·조건 | 결과 | 근거 |
|---|---|---|---|
| .\gradlew.bat test --tests "*ForecastReadServiceTest" --tests "*AdminForecastControllerTest" | 로컬 | 통과 | BUILD SUCCESSFUL, 12개 테스트 |
| ForecastReadRepositoryTest | 로컬 | 미실행 | Docker 연결 불가, compileTestJava 통과 |

## 연결과 남은 작업
- 예측 생성 배치·실적 확정 배치가 데이터를 저장하기 전까지 조회는 F001이다.
