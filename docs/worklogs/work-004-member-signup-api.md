# WORK-004: 공개 회원가입 API

상태: 검증 대기 · 작성일: 2026-10-16 · 담당: 회원 도메인

## 목표와 완료 기준
- 익명 사용자가 CSRF 토큰 없이 `POST /api/members`로 회원가입을 요청할 수 있다.
- 유효한 입력은 HTTP 201과 회원 ID를, 잘못된 입력은 400을, 중복은 409를 반환한다.

## 근거와 범위
- [API 명세](../api/api-spec.md), [회원가입 내부 동작](work-003-member-signup-service.md).
- 컨트롤러, 정확한 가입 POST 경로의 공개·CSRF 예외 설정, HTTP·보안 테스트만 추가한다.
- 공통 응답 형태는 PR #9, 저장 구조는 #10, 가입 서비스는 #11이 선행한다.

## 계획
| 단계 | 변경 | 검증 |
|---|---|---|
| 1 | 가입 컨트롤러 | 201·400·409 HTTP 테스트 |
| 2 | 가입 POST 공개 접근 | 익명·CSRF 경계 테스트 |
| 3 | 계약 문서 갱신 | 경로·권한·상태 비교 |

## AI 활용과 개발자 판단
- 기존 회원가입 구현에서 HTTP·보안 코드만 분리했다.
- 비밀번호 인코더는 앞선 PR의 설정을 재사용하여 중복 빈을 만들지 않았다.
- DTO의 세부 검증은 앞선 PR에서 테스트하므로 여기서는 HTTP 경계만 검증했다.

## 구현 결과
- `MemberController`: `POST /api/members`, 성공 시 201과 `memberId`.
- `SecurityConfig`: 가입 POST만 익명·CSRF 예외로 처리하고 다른 요청은 인증 대상으로 둔다.
- 요청·응답 DTO와 ErrorCode 변경 없음. 프론트는 가입 API 호출 시 응답의 `data.memberId`를 사용한다.

## 검증
| 명령·시나리오 | 실행 환경·조건 | 결과 | 근거 |
|---|---|---|---|
| `.\gradlew.bat clean compileJava compileTestJava test --tests 'org.example.takecareofmyfridgebe.domain.member.controller.MemberControllerTest'` | Windows, JDK/Gradle | 통과 | HTTP·보안 테스트 3건 |
| `.\gradlew.bat test --tests 'org.example.takecareofmyfridgebe.domain.member.controller.MemberControllerTest'` | 다른 POST의 CSRF 테스트 추가 후 | 통과 | HTTP·보안 테스트 4건 |
| `.\gradlew.bat test` | 로컬 MySQL | 실패 | 기존 `contextLoads`에서 `fridge_user` 인증 거부, 나머지 12건 통과 |

## 연결과 남은 작업
- 선행 PR: #9 공통 응답, #10 회원 저장, #11 가입 서비스.
- 로컬 DB 인증 해결 후 실제 회원·맛 선호 저장 통합 검증 필요.
- 익명 요청의 성공·실패 JSON 구조는 #9 병합 후 다시 확인한다.
