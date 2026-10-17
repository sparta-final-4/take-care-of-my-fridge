# WORK-006: 로그인 세션 저장 모델

상태: 검증 대기 · 작성일: 2026-10-16 · 담당: 회원·인증

## 목표와 범위

- 갱신 토큰 원문 대신 해시를 저장할 `refresh_sessions` JPA 모델을 추가한다.
- 30분 미사용 또는 로그인 후 7일에 세션을 만료한다.
- `keepLoggedIn` 입력은 현재 로그인 범위에서 제외하고 저장값은 `false`로 둔다.

## 구현 결과

- 세션 생성·사용 시각 갱신·폐기·유효성 검사와 해시 조회 저장소를 추가했다.
- API나 실제 토큰 발급은 후속 변경에서 연결한다.

## 검증

- `./gradlew.bat clean compileJava compileTestJava test --tests '*RefreshSessionTest'`: 통과.
- 전체 테스트는 로컬 MySQL 계정 인증 문제로 검증 대기.
