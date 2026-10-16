# API 계약 초안

상태: 초안 · 회원가입 컨트롤러 구현, DB 통합 검증 대기 · 다른 회원 API는 제안 단계 · 근거: S1, S3, S4, S5

## 공통 규칙

- 프론트 baseURL은 /api prefix를 포함하고, 도메인 API 함수에는 prefix를 반복하지 않습니다.
- 성공·실패는 ApiResponse<T>로 통일합니다.
- HTTP 인증 실패는 401, 인증된 사용자의 권한 부족은 403으로 구분합니다.
- 업무 오류는 ErrorCode·BusinessException·공통 처리기를 사용합니다. 인증 필터의 응답도 같은 규격으로 맞출 방법을 정합니다.
- 쿠키 인증·withCredentials는 공유 프론트 규칙입니다. 실제 쿠키 이름·속성·재발급·CSRF·CORS 계약은 미정입니다.
- 날짜·시간은 기본 ISO-8601. 시간대·날짜 전용 값·null 허용을 필드별로 확정합니다.
- 경로 변수 인코딩, 조회 요청 취소 신호, 요청·응답 타입 단일 정의는 프론트 규칙을 따릅니다.

## 공유된 응답 형태

아래 값은 예시이며 실제 API 응답이 아닙니다.

```json
{
  "success": true,
  "data": {},
  "error": null
}
```

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "C001",
    "message": "적절하지 않은 요청 값입니다."
  }
}
```

필드별 검증 실패를 담을 구조는 Q-10에서 확정합니다. 기존 두 필드만으로 form.setFields 매핑이 가능하다고 가정하지 않습니다.

## 페이지 응답

공유 구조: content, pageNumber, pageSize, totalElements, totalPages, isLast. 응답 pageNumber는 1부터 시작하는 제안입니다. Spring Pageable 요청의 번호 변환은 아직 정의되지 않았으므로 요청·응답을 함께 확정합니다.

## 필요한 API 기능 목록

HTTP 메서드·경로·DTO 이름은 구현 전 합의합니다.

| 영역 | 기능 | 권한·주요 조건 |
|---|---|---|
| 가입 | 이메일·닉네임 확인, 계정 생성 | 공개 가입 USER 고정, 닉네임 필수·중복 불가, 약관 없음, 서버 최종 중복 검증 |
| 인증 | 로그인·갱신·로그아웃·현재 사용자 | 정지 상태 처리, 30분 미사용 만료, 세션 폐기·경합 |
| 내 정보 | 닉네임·비밀번호·맛 선호 변경 | 본인, 다른 기기 세션 처리. 회원 탈퇴 API 없음 |
| 회원 권한 | 목록·승격 | ADMIN, 정지 회원 제외, 작업 이력 기록 |
| 상품(상품 도메인 제공) | 목록·상세·등록·논리 삭제 | 쓰기 ADMIN, 유통기한·보관 방법 필수, 주문 스냅샷 보존 |
| 상품 가격(상품 도메인 제공) | 정상가 수정 | ADMIN, 1원 이상, 변경 전후 작업 이력 기록, 기존 주문 가격 불변 |
| 분석 | 예측·실제 판매량·기준선 조회 | ADMIN, 버전·시점·가상 데이터 표시 |
| 할인 후보 | 분석·검토 저장·행사 요청 | ADMIN, 실제 가격 변경 없음, 요청 계약 미정 |
| 관리자 작업 이력 | 이력 조회 | ADMIN, 기록 수정·삭제 API 없음 |
| 연동 | 후기용 회원 정보, 재고·판매·검색 지표 | 제공 주체·전달 방식·집계 시점 합의 |

## 회원 API 제안

상태: **제안** · 팀 합의 전 · 근거: [회원 도메인 설계](../architecture/domains/member.md)

정책이 결정된 회원 기능만 경로를 제안합니다. 경로는 prefix(/api)를 뺀 값입니다. 인증 쿠키·재발급 세부(Q-02, Q-08)와 필드 오류 구조(Q-10)가 정해지면 각 항목을 아래 템플릿 수준으로 채웁니다.

| 기능 | Method / Path | 권한 | 요청 | 성공 응답 | 주요 오류 |
|---|---|---|---|---|---|
| 이메일 중복 확인 | GET /members/email-availability?email= | 공개 | email | `{ available: boolean }` | 400 형식 오류 |
| 닉네임 중복 확인 | GET /members/nickname-availability?nickname= | 공개 | nickname | `{ available: boolean }` | 400 형식 오류 |
| 회원가입 | POST /members | 공개 | email, password, nickname, tastes[] | 201, 회원 ID | 400 입력 오류, 409 이메일·닉네임 중복 |
| 로그인 | POST /auth/login | 공개 | email, password | 200, 쿠키 발급, 내 정보 | 401 공통 실패 문구, 403 정지 계정 |
| 로그아웃 | POST /auth/logout | 로그인 | 없음 | 200, 세션 폐기 | 401 |
| 내 정보 | GET /members/me | 로그인 | 없음 | 이메일, 닉네임, 역할, 상태, 맛 선호, 가입일 | 401 |
| 닉네임 변경 | PATCH /members/me/nickname | 로그인 | nickname | 200 | 400, 409 중복 |
| 비밀번호 변경 | PATCH /members/me/password | 로그인 | currentPassword, newPassword | 200, 다른 기기 세션 폐기 | 400, 401 현재 비밀번호 불일치 |
| 맛 선호 변경 | PUT /members/me/tastes | 로그인 | tastes[] (0개 이상) | 200 | 400 허용하지 않는 값 |
| 회원 목록 | GET /admin/members?keyword=&role=&page= | ADMIN | 검색어, 역할, 페이지 | PageResponse | 403 |
| 관리자 승격 | POST /admin/members/{memberId}/promotion | ADMIN | 없음 | 200, 작업 이력 기록 | 403, 404, 409 정지·이미 ADMIN |

- 로그인 실패는 없는 이메일과 틀린 비밀번호를 구분하지 않습니다. 정지 계정은 비밀번호가 맞을 때만 403으로 알립니다.
- 로그인 자격 증명 오류는 401 `A001`, 비밀번호가 일치하는 정지 계정은 403 `A002`입니다.
- tastes 값: SALTY, SWEET, SPICY, UMAMI, MILD.
- 가입 요청에 role이 들어와도 무시하고 USER로 만듭니다.
- 가입 비밀번호는 8~30자이며 ASCII 영문·숫자·특수문자를 각각 하나 이상 포함합니다. 공백·한글은 허용하지 않습니다(2026-10-16 사용자 확정).
- 가입 요청의 nickname은 앞뒤 공백을 제거하며 1~30자입니다. tastes는 빈 목록을 허용하지만 중복 값은 거부합니다.
- 가입 성공 데이터는 `{ memberId: number }`입니다. 이메일 중복은 `M001`, 닉네임 중복은 `M002`로 응답합니다.
- `POST /members`는 익명 요청을 허용합니다. 다른 POST 경로의 CSRF 보호는 유지합니다.

## 엔드포인트 작성 템플릿

### <기능명>

- 상태·담당:
- 관련 화면·요구사항:
- Method / Path:
- 인증·권한·리소스 소유권:
- 경로·쿼리·본문 필드(타입, 필수, null, 범위):
- 성공 HTTP 상태·응답 DTO:
- 오류 HTTP 상태·ErrorCode·필드 오류:
- 페이지·정렬 규칙:
- 동시 요청·중복 요청·재시도 동작:
- 트랜잭션 및 외부 연동:
- 예시 요청·응답(예시임을 표기):
- 관련 테스트·실제 응답 확인 결과:
- 변경 영향·합의한 담당자:
