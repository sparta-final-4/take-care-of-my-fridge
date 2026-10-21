# WORK-063: 할인 후보 검토 결과 저장

상태: 완료 · 작성일: 2026-10-20 · 담당: 관리자·분석

## 목표와 범위
- 보낸 후보만 `SELECTED`·`HELD`로 변경하고 다른 후보는 유지한다.
- 분석과 후보 소속을 확인하고, 중복 ID·제외 상품·요청 완료 후보는 거부한다.
- 검토 저장 시각과 성공 작업 이력을 같은 트랜잭션에 남긴다.

## 구현 결과
- 분석 행 잠금, 일괄 검증·저장 서비스, 검토 건수 응답을 추가했다.
- HTTP API와 실패 작업 이력 연결은 후속 PR에서 처리한다.

## 검증
- `gradlew.bat clean compileJava compileTestJava test --tests org.example.takecareofmyfridgebe.domain.deal.service.DealReviewServiceTest`: 통과.

## 남은 작업
- 관리자 검토 저장 API.
