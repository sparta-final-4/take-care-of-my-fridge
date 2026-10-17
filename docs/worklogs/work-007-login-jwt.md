# WORK-007: 로그인 JWT 발급·검증

상태: 검증 대기 · 작성일: 2026-10-16 · 담당: 회원·인증

## 목표와 범위

- 액세스·갱신 JWT를 서버 키로 서명하고 만료·발급자·종류를 검증한다.
- 원문 갱신 토큰은 SHA-256 해시로 세션에 저장할 수 있게 한다.

## 구현 결과

- Spring Security JOSE 의존성과 `JwtTokenService`를 추가했다.
- `JWT_SECRET` 환경 변수는 UTF-8 32바이트 이상이어야 한다. 실제 비밀값은 저장소에 넣지 않는다.
- HTTP 쿠키와 인증 필터는 후속 변경에서 연결한다.

## 검증

- `./gradlew.bat build --dry-run`: 통과.
- `./gradlew.bat clean compileJava compileTestJava test --tests '*JwtTokenServiceTest'`: 통과.
- 전체 테스트는 로컬 MySQL 계정 인증 문제로 검증 대기.
