# WORK-026: 공통 페이지 응답

상태: 검증 대기 · 작성일: 2026-10-17 · 담당: 관리자

## 목표와 완료 기준

- 회원 목록에서 사용할 공통 PageResponse를 제공한다.
- API 페이지 번호는 1부터, Spring Data Page 번호는 0부터 시작하도록 변환한다.

## 근거와 범위

- docs/api/api-spec.md의 페이지 응답 구조와 회원 목록 명세.
- 회원 검색 저장소·HTTP API는 후속 작은 작업으로 나눈다.

## 구현 결과

- content, pageNumber, pageSize, totalElements, totalPages, isLast를 담는 불변 record를 추가했다.
- Spring Page를 변환하는 of 메서드를 추가했다. 신규 오류 코드와 DB 변경은 없다.

## 검증

- PageResponseTest: 통과.
- 전체 컴파일·테스트는 회원 목록 API 완료 후 재실행한다.

## 연결과 남은 작업

- 회원 검색 서비스와 컨트롤러에서 이 응답을 사용한다.
