# WORK-016: 본인 닉네임 변경 API

상태: 검증 대기 · 작성일: 2026-10-17 · 담당: 회원

## 목표와 완료 기준

- 로그인 회원이 `PATCH /api/members/me/nickname`으로 본인 닉네임을 변경한다.
- 앞뒤 공백을 제거한 뒤 1~30자로 검증하고, 다른 회원과 중복되면 409 `M002`를 반환한다.
- 중복 요청이 동시에 들어와도 DB 고유 제약 위반을 같은 오류로 처리한다.

## 근거와 범위

- `docs/api/api-spec.md`의 닉네임 변경 계약과 기존 회원가입·중복 확인 규칙.
- 현재 회원 엔티티, 서비스, 컨트롤러와 요청 DTO만 변경한다. 다른 회원 정보와 인증 정책은 변경하지 않는다.

## 구현 결과

- 인증 주체의 회원 ID로 대상을 조회하며 요청 본문에 회원 ID를 받지 않는다.
- 같은 닉네임이면 변경 없이 200을 반환한다. 다른 닉네임은 중복을 확인하고 변경 후 즉시 flush해 DB 경합을 감지한다.
- 성공 응답은 기존 `ApiResponse.ok()` 형식이다. 신규 오류 코드는 없다.

## 검증

| 명령·시나리오 | 결과 | 근거 |
|---|---|---|
| `./gradlew.bat test --tests '*MemberServiceTest' --tests '*MemberControllerTest'` | 통과 | 본인 변경, 중복, 입력 오류, 인증·CSRF |
| `./gradlew.bat clean compileJava compileTestJava` | 통과 | 전체 소스·테스트 소스 재컴파일 |
| `./gradlew.bat test` | 61개 중 60개 통과, 1개 실패 | `contextLoads()`에서 로컬 MySQL `fridge_user` 인증 거부 |

## 연결과 남은 작업

- 작업 브랜치: `member-nickname-update-api`.
- 실제 MySQL 고유 제약과 동시 변경 경합은 DB 접속이 가능할 때 확인한다.
- 프론트엔드는 PATCH 요청에 CSRF 헤더를 포함한다.
