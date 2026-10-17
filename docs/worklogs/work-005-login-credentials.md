# WORK-005: 로그인 자격 증명 검사

상태: 검증 대기 · 작성일: 2026-10-16 · 담당: 회원·인증

## 목표와 범위

- 이메일과 비밀번호가 맞는 활성 회원만 로그인 후속 처리로 전달한다.
- 없는 이메일과 틀린 비밀번호는 동일한 `A001`로, 비밀번호가 맞는 정지 계정은 `A002`로 거부한다.
- 이번 변경은 HTTP API, 토큰, 세션을 발급하지 않는다.

## 구현 결과

- `MemberRepository.findByEmail`과 `LoginCredentialService.authenticate`를 추가했다.
- 오류 코드와 API 명세를 맞췄다.

## 검증

- `./gradlew.bat clean compileJava compileTestJava test --tests '*LoginCredentialServiceTest'`: 통과.
- `./gradlew.bat test`: 17개 중 16개 통과, 기존 `contextLoads`는 로컬 MySQL `fridge_user` 인증 거부로 실패.

## 연결과 남은 작업

- 후속 변경에서 세션 저장, JWT 발급, 로그인 API를 연결한다.
