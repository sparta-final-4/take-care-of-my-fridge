# WORK-028: 관리자 회원 목록 API

상태: 검증 대기 · 작성일: 2026-10-17 · 담당: 관리자

## 목표와 완료 기준

- ADMIN이 이메일 검색·역할 필터·페이지 조회로 회원 목록을 읽는다.
- USER와 비로그인 요청을 거부하고 잘못된 페이지·역할 입력에 400을 반환한다.

## 근거와 범위

- docs/api/api-spec.md의 회원 목록 계약.
- 기존 /api/admin/** ADMIN 권한 규칙을 사용한다.

## 구현 결과

- GET /api/admin/members에 요청 DTO와 검색 서비스를 연결했다.
- page=1, size=20을 기본값으로 적용하고 둘 다 1 이상으로 검증한다.
- 회원 목록 명세에 검색 방식, 정렬, 응답 필드와 미연결 승격 정보를 명시했다.

## 검증

- AdminMemberControllerTest: 기본값, 검색·필터, 접근 거부, 잘못된 입력 확인.
- clean compileJava compileTestJava: 통과. 전체 test: 95개 중 94개 통과, contextLoads 1개는 로컬 MySQL의 fridge_user 인증 거부로 실패했다.

## 연결과 남은 작업

- 관리자 승격 기능에서 promotedAt·promotedBy를 실제 승격 기록과 연결한다.
