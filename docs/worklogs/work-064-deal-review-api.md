# WORK-064: 할인 후보 검토 저장 API

상태: 완료 · 작성일: 2026-10-20 · 담당: 관리자·분석

## 목표와 범위
- `PUT /api/admin/deal-analyses/{analysisId}/reviews`로 일부 후보의 검토 상태를 저장한다.
- 성공은 저장 서비스에서, 비즈니스 오류는 facade에서 `DEAL_REVIEW` 작업 이력에 기록한다.

## 구현 결과
- 관리자 검토 저장 컨트롤러와 비즈니스 실패·권한 거부 작업 이력 연결을 추가했다.
- 보낸 후보만 변경하고 검토 건수를 응답한다.

## 검증
- `gradlew.bat clean compileJava compileTestJava test --tests org.example.takecareofmyfridgebe.domain.deal.facade.DealReviewFacadeTest --tests org.example.takecareofmyfridgebe.domain.deal.controller.AdminDealAnalysisControllerTest`: 통과.

## 남은 작업
- 실제 후보 분석 실행은 상품·재고·검색 지표 협의 후 연결한다.
