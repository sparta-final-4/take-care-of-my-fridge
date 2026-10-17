# WORK-035: 관리자 작업 이력 조회 API

상태: 검증 대기 · 작성일: 2026-10-17 · 담당: 관리자

## 목표와 완료 기준

- ADMIN이 GET /api/admin/audit-logs에서 기간·동작·결과·대상으로 이력을 검색한다.
- 잘못된 입력과 USER·비로그인 접근을 거부한다.

## 구현 결과

- 날짜·동작·결과·대상·페이지 쿼리 DTO를 추가했다.
- 기본 page=1, size=20이며 1 이상으로 검증한다.
- API 명세에 기본 기간과 FAILED 필터의 의미를 반영했다.

## 검증

- AdminAuditControllerTest: 기본값, 전체 필터, 접근 거부, 잘못된 입력 통과.
- clean compileJava compileTestJava 통과. 전체 test는 109개 중 108개 통과했고, contextLoads 1개는 로컬 MySQL fridge_user 인증 거부로 실패했다.
