# WORK-020: 비밀번호 변경 후 다른 기기 세션 폐기

상태: 검증 대기 · 작성일: 2026-10-17 · 담당: 회원·인증

## 목표와 완료 기준

- 비밀번호 변경이 성공하면 같은 회원의 다른 활성 세션을 폐기한다.
- 현재 요청의 세션은 유지한다. 현재 비밀번호가 틀리면 세션을 폐기하지 않는다.

## 근거와 범위

- docs/architecture/domains/member.md의 세션 폐기 흐름.
- 2026-10-17 사용자 결정: 현재 기기 유지, 다른 기기 로그아웃.

## 구현 결과

- 인증 주체에 검증된 세션 ID를 포함한다.
- 회원 서비스의 비밀번호 변경 트랜잭션에서 다른 활성 세션만 조회해 revoked_at을 기록한다.
- 신규 API·오류 코드·DB 스키마 변경은 없다.

## 검증

- Gradle MemberServiceTest, AuthSessionServiceTest, JwtCookieFilterTest, MemberControllerTest: 통과.
- 전체 컴파일과 전체 테스트는 최종 API 작업 후 재실행한다.

## 연결과 남은 작업

- PATCH API와 요청 검증은 다음 작업에서 연결한다.
