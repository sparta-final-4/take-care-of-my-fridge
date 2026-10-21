# WORK-062: 할인 후보 검토 입력·상태 규칙

상태: 완료 · 작성일: 2026-10-20 · 담당: 관리자·분석

## 목표와 범위
- 검토 저장 요청은 후보 ID와 `SELECTED` 또는 `HELD` 상태를 받는다.
- 제외 상품, 이미 행사 요청한 후보, `UNREVIEWED` 입력을 거부한다.

## 구현 결과
- 검토 요청 DTO와 후보 상태 변경 메서드, D003·D004 오류 코드를 추가했다.
- 저장 서비스와 HTTP API는 후속 PR로 분리한다.

## 검증
- `gradlew.bat clean compileJava compileTestJava test --tests org.example.takecareofmyfridgebe.domain.deal.entity.DealCandidateReviewTest`: 통과.

## 남은 작업
- 복수 후보의 원자적 저장, 검토 건수와 작업 이력, 관리자 API.
