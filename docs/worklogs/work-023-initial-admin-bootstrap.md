# WORK-023: 첫 관리자 계정 자동 생성

상태: 검증 대기 · 작성일: 2026-10-17 · 담당: 회원·인증

## 목표와 완료 기준

- 운영자가 지정한 환경변수로 전용 ACTIVE·ADMIN 계정 1개를 첫 시작에 생성한다.
- ADMIN이 이미 있으면 재시작 때 추가 계정을 만들거나 기존 비밀번호를 바꾸지 않는다.
- 공개 회원가입은 계속 USER로 고정한다.

## 근거와 범위

- 사용자 요청 12번과 2026-10-17 사용자 결정: 환경변수로 앱 시작 시 영구 관리자 계정 자동 생성.
- 회원 저장소·엔티티·시작 시드 작업과 운영 절차 문서를 변경한다.

## 구현 결과

- ADMIN_BOOTSTRAP_EMAIL, ADMIN_BOOTSTRAP_PASSWORD, ADMIN_BOOTSTRAP_NICKNAME 세 값을 설정하면 시작 시 회원가입 규칙을 검증하고 첫 ADMIN을 저장한다.
- 환경변수가 모두 없으면 작업을 건너뛴다. 일부 누락, 입력 오류, 기존 USER 이메일·닉네임과 충돌하면 서버 시작을 실패시킨다.
- 관리자 계정은 members 테이블에 저장되며 재시작해도 유지된다. 기존 ADMIN이 있으면 덮어쓰지 않는다.
- 신규 HTTP API, 오류 코드, DB 스키마 변경은 없다.

## 검증

- InitialAdminSeederTest, AdminAccessSecurityTest, MemberServiceTest: 통과.
- Gradle clean compileJava compileTestJava: 통과.
- 전체 테스트 83개 중 82개 통과. contextLoads()는 로컬 MySQL fridge_user 인증 거부로 실패했다.
- 실제 MySQL에서 초기 ADMIN 생성은 검증하지 못했다.

## 연결과 남은 작업

- 설정·실행 순서는 docs/operations/initial-admin.md에 기록했다.
- 관리자 권한 거부 이력 기록은 후속 기능 13번 범위다.
