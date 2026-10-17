# WORK-012: 갱신 토큰 교체

상태: 검증 대기 · 작성일: 2026-10-16 · 담당: 회원·인증

## 목표와 범위

- 유효한 갱신 JWT를 한 번 사용하면 새 토큰으로 교체하고 이전 토큰은 거부한다.
- 동시 갱신에서는 세션 행 잠금으로 한 요청만 성공한다.
- 최초 로그인 후 7일 최대 만료는 연장하지 않는다.

## 구현 결과

- `RefreshService`와 잠금 조회를 추가하고 세션 해시를 교체한다.
- HTTP 엔드포인트와 CSRF 연동은 후속 변경에서 연결한다.

## 검증

- `./gradlew.bat clean compileJava compileTestJava test --tests '*RefreshServiceTest' --tests '*JwtTokenServiceTest'`: 통과.
- DB 동시성 통합 테스트는 로컬 MySQL 계정 인증 문제로 검증 대기.
