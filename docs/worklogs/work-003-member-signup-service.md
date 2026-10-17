# WORK-003: 회원가입 내부 동작

상태: 검증 대기 · 작성일: 2026-10-16 · 담당: 회원 도메인

## 목표와 완료 기준
- 가입 입력을 검증하고, 비밀번호를 해시로 저장하며 이메일·닉네임 중복을 구분한다.
- 회원가입 서비스와 요청 검증의 집중 테스트를 통과한다.

## 근거와 범위
- [API 명세](../api/api-spec.md), [회원 도메인](../architecture/domains/member.md), [회원 저장 구조](work-002-member-persistence.md).
- 요청·응답 DTO, 서비스, 비밀번호 인코더, 중복 오류 코드와 테스트만 포함한다. 컨트롤러와 공개 접근 설정은 다음 PR 범위다.
- 비밀번호 규칙은 2026-10-16 사용자 확정 정책(8~30자, ASCII 영문·숫자·특수문자 필수)을 따른다.

## 계획
| 단계 | 변경 | 검증 |
|---|---|---|
| 1 | DTO·인코더·중복 코드 | 컴파일·입력 검증 테스트 |
| 2 | 가입 서비스·경합 처리 | 서비스 단위 테스트 |
| 3 | 계약 문서 갱신 | 요청·응답·오류 코드 비교 |

## AI 활용과 개발자 판단
- 검증된 기존 회원가입 코드에서 내부 동작만 분리하고, HTTP·보안 설정은 제외했다.
- DB 유일 제약 충돌을 서비스 테스트로 확인했으며 실제 DB 저장 검증은 남겼다.

## 구현 결과
- `SignUpRequest`: 이메일·비밀번호·닉네임·맛 선호 입력과 검증.
- `MemberService`: 중복 확인, 비밀번호 인코딩, 회원·맛 선호 저장, 유일 제약 경합 처리.
- `SignUpResponse`: 저장된 회원 ID. 중복 오류 `M001`·`M002`.
- 프론트는 가입 성공 시 `memberId`를 읽고, 409 응답의 두 코드를 구분할 수 있다. HTTP API는 다음 PR에서 공개한다.

## 검증
| 명령·시나리오 | 실행 환경·조건 | 결과 | 근거 |
|---|---|---|---|
| `.\gradlew.bat clean compileJava compileTestJava test --tests 'org.example.takecareofmyfridgebe.domain.member.service.MemberServiceTest' --tests 'org.example.takecareofmyfridgebe.domain.member.dto.SignUpRequestTest'` | Windows, JDK/Gradle | 통과 | 변경 전 서비스 5건·DTO 2건 |
| `.\gradlew.bat test --tests 'org.example.takecareofmyfridgebe.domain.member.service.MemberServiceTest' --tests 'org.example.takecareofmyfridgebe.domain.member.dto.SignUpRequestTest'` | 동시 이메일 충돌 테스트 추가 후 | 통과 | 서비스 6건·DTO 2건 |
| `.\gradlew.bat test` | 로컬 MySQL | 실패 | 기존 `contextLoads`에서 `fridge_user` 인증 거부 |

## 연결과 남은 작업
- 선행 PR: #9 공통 응답, #10 회원 저장 구조. 이번 PR은 #10의 커밋을 기준으로 한다.
- 로컬 DB 인증 해결 후 전체 테스트·실제 DB 저장 재검증 필요.
- 컨트롤러, 익명 가입 허용, HTTP 응답 테스트는 다음 PR에서 구현한다.
