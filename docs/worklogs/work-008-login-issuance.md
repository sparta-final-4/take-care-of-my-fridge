# WORK-008: 로그인 성공 시 세션·토큰 발급

상태: 검증 대기 · 작성일: 2026-10-16 · 담당: 회원·인증

## 목표와 범위

- 자격 증명이 맞으면 세션을 저장하고 액세스·갱신 JWT를 발급한다.
- 갱신 토큰 원문은 DB에 저장하지 않는다.

## 구현 결과

- `LoginService`가 회원 검증, 세션 저장, 토큰 발급을 한 트랜잭션으로 연결한다.
- 응답용 회원 정보는 트랜잭션 안에서 `LoginMember`로 변환한다.
- HTTP 쿠키·인증 필터는 후속 변경에서 연결한다.

## 검증

- `./gradlew.bat clean compileJava compileTestJava test --tests '*LoginServiceTest'`: 통과.
- 전체 테스트는 로컬 MySQL 계정 인증 문제로 검증 대기.
