# WORK-058: 할인 분석 상세 응답 형식

상태: 완료 · 작성일: 2026-10-20 · 담당: 관리자·분석

## 목표와 범위
- 저장된 분석을 명세 6장의 응답 형식으로 바꾼다.
- 후보는 예상 판매 증가량 순서로, 제외 상품은 별도 목록으로 반환한다.

## 구현 결과
- `DealAnalysisResponse`에 분석 조건, 검토 건수, 후보·제외 상품 스냅샷을 담았다.
- API에 있는 `syntheticData`를 분석 엔티티·데이터 사전·SQL에 추가했다.
- 조회 서비스와 HTTP 경로는 후속 PR에서 연결한다.

## 검증
- `gradlew.bat clean compileJava compileTestJava test --tests org.example.takecareofmyfridgebe.domain.deal.dto.DealAnalysisResponseTest`: 통과.

## 남은 작업
- 특정 분석 조회, 관리자별 최근 분석 조회, 검토 결과 저장.
