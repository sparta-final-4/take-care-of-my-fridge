# WORK-059: 할인 분석 상세 조회 서비스

상태: 완료 · 작성일: 2026-10-20 · 담당: 관리자·분석

## 목표와 범위
- 분석 ID로 저장된 분석과 후보·제외 상품을 읽는다.
- 없는 분석은 API 명세의 D002로 처리한다.

## 구현 결과
- `DealAnalysisReadService.getById`와 D002 오류 코드를 추가했다.
- HTTP 경로는 후속 PR에서 연결한다.

## 검증
- `gradlew.bat clean compileJava compileTestJava test --tests org.example.takecareofmyfridgebe.domain.deal.service.DealAnalysisReadServiceTest`: 통과.

## 남은 작업
- 관리자 상세 조회 API와 관리자별 최근 분석 조회.
