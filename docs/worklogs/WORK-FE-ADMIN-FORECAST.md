# WORK-FE-ADMIN-FORECAST: 관리자 판매 예측

상태: 검증 대기 · 작성일: 2026-10-21 · 담당: 프론트엔드

## 목표와 완료 기준

- ADMIN이 최신 주 또는 특정 주의 판매 예측 TOP 10을 조회한다.
- 실적이 확정된 주에는 예측·실적·기준선과 TOP 10 일치·진입·이탈·MAE를 비교한다.

## 근거와 범위

- `docs/requirements/screens.md` A-04, A-05
- 백엔드 `AdminForecastController`, `ForecastResponse`, `ForecastReadService`

## 구현 결과

- `/admin/forecast`를 ADMIN 전용 경로와 관리자 메뉴에 추가했다.
- 최신 주·월요일 기준 특정 주·이전/다음 주 조회를 연결했다.
- 예측 TOP 10과 확정 실적 비교 탭, 모델·집계 시각·가상 데이터 표시를 구현했다.
- 예측 없음(F001), 미확정 실적, 일반 조회 오류를 각각 표시한다.

## 검증

- ESLint, TypeScript, Vite 빌드, Vitest(8개): 통과.
- 변경 파일 Prettier 검사와 `git diff --check`: 통과.
- 전체 Prettier 검사는 기존 파일 106개의 포맷 차이로 실패한다.
- 관리자 인증이 필요한 실제 데이터 화면과 API 왕복: 사용 가능한 관리자 세션이 없어 미검증. 로컬 화면 접근 시 인증 상태 확인 오류가 표시됐다.
