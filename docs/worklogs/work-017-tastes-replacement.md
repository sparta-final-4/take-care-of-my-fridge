# WORK-017: 맛 선호 목록 교체 저장

상태: 검증 대기 · 작성일: 2026-10-17 · 담당: 회원

## 목표와 완료 기준

- 로그인 회원의 맛 선호 목록을 요청한 목록으로 교체한다.
- 중복 입력은 허용하며 같은 맛은 한 번만 저장한다. 빈 목록은 모든 선호를 제거한다.

## 근거와 범위

- 회원 도메인과 member_tastes의 (member_id, taste) 고유 제약.
- 사용자 결정: 맛 선호 변경 요청의 중복 값을 허용한다. 회원가입 요청의 중복 금지 규칙은 유지한다.

## 구현 결과

- Member.replaceTastes가 빠진 맛만 제거하고 새 맛만 추가한다. 유지되는 행은 다시 만들지 않는다.
- MemberService.changeTastes는 회원 ID로 대상을 찾아 한 트랜잭션에서 교체한다.
- HTTP 경로와 요청 검증은 다음 API 작업으로 분리한다.

## 검증

- 서비스 브랜치에서 Gradle MemberServiceTest: 통과.
- Gradle clean compileJava compileTestJava: 통과.
- 서비스 브랜치 전체 테스트 64개 중 63개 통과. contextLoads()는 로컬 MySQL fridge_user 인증 거부로 실패했다.
- 실제 DB orphanRemoval 및 동시 변경은 MySQL 접속이 가능할 때 확인한다.

## 연결과 남은 작업

- 기준 브랜치: member-nickname-update-api. HTTP API 변경과 별도 PR로 올린다.
