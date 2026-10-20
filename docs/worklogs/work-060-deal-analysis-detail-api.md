# WORK-060: 할인 분석 상세 조회 API

상태: 완료 · 작성일: 2026-10-20 · 담당: 관리자·분석

## 목표와 범위
- 관리자가 `GET /api/admin/deal-analyses/{analysisId}`로 저장된 결과를 조회한다.
- 기존 관리자 접근 제한과 공통 응답 형식을 적용한다.

## 구현 결과
- 특정 분석 조회 컨트롤러와 권한·404 MVC 테스트를 추가했다.

## 검증
- `gradlew.bat clean compileJava compileTestJava test --tests org.example.takecareofmyfridgebe.domain.deal.controller.AdminDealAnalysisControllerTest`: 통과.

## 남은 작업
- 관리자별 최근 분석 조회와 검토 저장.
