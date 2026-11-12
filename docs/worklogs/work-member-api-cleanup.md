# 회원·인증·관리자 API 문서와 api-spec.md 정리

상태: 완료 · 작성일: 2026-11-11 · 담당: cookiez

브랜치 `docs/member-api-cleanup`(dev 4b99afbf 위)에서 한 작업이다. 커밋은 로컬에만 있고 push·PR은 하지 않았다.
이 영역 담당(OdinAhn)이 빠져 cookiez가 넘겨받았다. 냉장고·상담 API 정리와 같은 원칙으로 나눴다.

## 목표와 완료 기준
- 사용자 문제: api-spec.md에 회원·인증·관리자 명세와 정책이 섞여 있었다. member-auth-admin-api.md는 인증이 "Q-08에서 정함" 상태로 남아 api-spec.md보다 오래됐고, 판매 예측·할인 후보 명세까지 한 파일에 있었다.
- 완료 조건
  - api-spec.md는 공통 규칙, 도메인별 API 목차, 공유 응답, 페이지 응답, 작성 템플릿만.
  - ○○-api.md는 명세만(한 줄 → 요청 표 → 응답 표 → 오류 표 → 주의 3줄 이내), 정책·흐름은 architecture/domains/○○.md.
  - 코드(컨트롤러·DTO·ErrorCode·검증)와 /v3/api-docs에 맞춘다.
  - 이번에 바꾼 링크·앵커가 깨지지 않는다.

## 근거와 범위
- 기준: 코드 > api-spec.md "회원 API 제안" > member-auth-admin-api.md.
- 다른 담당 문서 변경: product-api.md(상품 할인·품절 필드 절을 문구 그대로 옮김, 목차·자기 링크), sales-forecast.md(API 링크, 조회 기준, 남은 결정). 링크만 고친 곳: cart-api.md, sale-event-api.md, README.md.
- 프론트 폴더(take-care-of-my-fridge-fe)에서 이 문서들을 거는 곳은 없어 프론트 파일은 고치지 않았다.

## 실제 기능 빠짐 (팀 공유)
- 관리자 작업 이력 action에 `PRODUCT_CREATE`·`PRODUCT_DELETE`·`PRICE_UPDATE`가 있지만, 상품 등록·삭제·정상가 수정에서 이력을 남기는 코드가 없다(`AdminAuditLog.Action.PRODUCT_*`를 쓰는 곳이 상품 도메인에 없음).
- 그래서 A-09 작업 이력과 A-02 상품 변경 이력에 상품 작업이 나오지 않는다. 누가 기록할지는 [회원 설계의 남은 결정](../architecture/domains/member.md#남은-결정)에 적었다.

## 코드와 달라서 고친 것
| # | 문서에 있던 내용 | 코드 기준 | 고친 곳 |
|---|---|---|---|
| 1 | 로그인 요청 `keepLoggedIn` | `LoginRequest`는 email·password뿐 | 삭제 |
| 2 | 로그인·내 정보 응답에 phone 없음 | `MyInfoResponse`에 `phone` | 회원 응답 DTO |
| 3 | 맛 선호 변경 "중복 값 400"(member 문서) | 변경은 `HashSet`으로 한 번만 저장, 가입만 중복 시 400 | 맛 선호 변경 주의 |
| 4 | 비밀번호 변경 후 현재 세션 "Q-08" | 현재 세션 유지, 다른 기기만 폐기 | 비밀번호 변경 주의 |
| 5 | 갱신 "토큰 회전 Q-08", api-spec "이전 토큰 재사용 감지" | 교체된 이전 토큰은 세션을 못 찾아 A005. 세션을 폐기하지는 않음 | member.md 인증 절 |
| 6 | 오류 코드 이름·메시지(A001·A002·A003·A004·A005·M003·M006) | `ErrorCode` 이름·메시지 | 오류 코드 표 |
| 7 | 작업 이력 action 목록 | `DEAL_ANALYSIS` 추가 | 작업 이력 조회 |
| 8 | 분석 실행 "작업 이력에 남기지 않음" | `DEAL_ANALYSIS`로 기록 | deal-analysis-api.md, deal-analysis.md |
| 9 | 분석 실행 `discountRate` 10·20·30·40, `minStock` 선택값 | 1~99, `minStock` 요청 없음(서버 고정 50, 응답에는 있음) | 후보 분석 실행 |
| 10 | 후보 순서 "예상 증가가 큰 순" | 검색 클릭 − 구매가 큰 순, 같으면 후보 ID | 응답 DTO, deal-analysis.md |
| 11 | D001(503) | 코드에 없음 | 삭제 |
| 12 | D005 400, D006 503 "요청 실패" | D005 409 미선택·이미 등록, D006 409 가상 데이터 분석 | 할인 행사 등록 |
| 13 | D007·D008 없음(member 문서) | D007 422, D008 500 | 후보 분석 실행, 오류 코드 |
| 14 | 닉네임 "세부 규칙 Q-09" | `@NotBlank`·`strip`·30자 | 닉네임 중복 확인 |
| 15 | 7-1 "A-02 상품 변경 이력" | 상품 작업은 기록되지 않음 | member.md 남은 결정 |
| 16 | 가상 데이터 후보는 행사 등록 불가(api-spec) | dev(`APP_ENV=dev`)에서는 허용 | deal-analysis.md |

## /v3/api-docs 대조
- local 프로필로 서버를 띄워 `/v3/api-docs`를 받았다(서버는 확인 후 내림).
- 회원·인증·작업 이력·판매 예측·할인 후보 경로 23개와 요청·응답 스키마 필드 이름이 명세와 같다. 프론트 `openapi/fridge-api.json`과도 같다.
- 문서와 다르게 보이는 두 가지는 springdoc 표시 문제라 명세에 따르지 않았다(코드 변경 없음).
  - `@ModelAttribute` 쿼리(`/members/email-availability`, `/admin/members`, `/admin/audit-logs` 등)가 파라미터 하나(`request`)로 나온다. 실제로는 email·keyword·page 같은 개별 쿼리다.
  - `DealEventRequest` 스키마에 `endsAfterStarts`·`limitedQuantityValid`가 필드로 나온다. `@AssertTrue` 검증 메서드라 요청에 보내지 않는다.

## 대조표

### api-spec.md "필요한 API 기능 목록"에서 지운 사실
| 사실 | 새 위치 / 삭제 이유 |
|---|---|
| 가입: 이메일·닉네임 확인, 계정 생성, USER 고정, 닉네임·전화번호 필수·중복 불가, 서버 최종 검증 | member-auth-admin-api.md 회원가입, member.md 기능·정책 |
| 가입: 약관 없음 | member.md 기능·정책 |
| 인증: 로그인·갱신·로그아웃·현재 사용자, 정지 처리 | member-auth-admin-api.md 인증·내 정보 |
| 인증: 30분 미사용 만료, 세션 폐기·경합 | member.md 인증 쿠키·토큰·세션 |
| 내 정보: 닉네임·비밀번호·맛 선호 변경, 다른 기기 세션 처리 | member-auth-admin-api.md 내 정보, member.md |
| 회원 탈퇴 API 없음 | member.md 기능·정책 |
| 회원 권한: 목록·승격, ADMIN, 정지 회원 제외, 이력 기록 | member-auth-admin-api.md 회원 목록·관리자 승격, member.md 관리자 작업 이력 |
| 상품 목록·상세·등록·논리 삭제(상품 도메인 제공) | 삭제: product-api.md에 구현된 명세가 있음 |
| 상품 정상가 수정, 기존 주문 가격 불변 | 삭제: product-api.md 상품 수정. 변경 이력 기록은 member.md 남은 결정(실제 기능 빠짐) |
| 분석: 예측·실제 판매량·기준선 조회, 버전·시점·가상 데이터 표시 | forecast-api.md |
| 할인 후보: 분석 조회·검토 저장·행사 등록, 선택한 후보만 기존 행사 생성 기능으로 | deal-analysis-api.md, deal-analysis.md |
| 할인 후보 실행 경로·요청 필드(targetWeekStart·dayOfWeek·discountRate 1~99) | deal-analysis-api.md 후보 분석 실행 |
| 대상일은 오늘 이후 | deal-analysis-api.md, deal-analysis.md (코드 기준 "오늘이거나 그 뒤") |
| 일반 특가 이력 부족 시 D007(422), 메시지에 부족 건수·할인율 종류 | deal-analysis-api.md 오류 표, deal-analysis.md 기능·정책 |
| 학습·예측 자료 값 오류 D008(500) | deal-analysis-api.md, deal-analysis.md |
| 최신 조회 없음 D002 메시지 내용(대상 상품 수, 종료 특가 건수 최소 3, 할인율 종류 최소 2) | deal-analysis-api.md(요약), deal-analysis.md(내용) |
| 행사 등록 경로·요청 필드(candidateId·type·startsAt·endsAt·limitedQuantity) | deal-analysis-api.md 할인 행사 등록 |
| 할인율은 분석 결과 값 사용 | deal-analysis-api.md 주의, deal-analysis.md |
| 가상 데이터가 아닌 결과만 등록 | deal-analysis.md (dev 예외로 고침, 위 16번) |
| 행사 생성과 후보 상태·행사 ID 기록은 한 트랜잭션 | deal-analysis.md |
| 성공 201, candidateId·saleEventId·APPROVED | deal-analysis-api.md 응답 |
| 행사 생성 실패 시 후보 상태 불변 | deal-analysis-api.md 주의 |
| 관리자 작업 이력: 기간·동작·결과·대상 검색, 수정·삭제 API 없음 | member-auth-admin-api.md 작업 이력 조회, member.md |
| 연동: 후기용 회원 정보 | member.md 연동 계약 |
| 연동: 재고·판매·검색 지표 | deal-analysis.md 연동 계약, sales-forecast.md 집계 기준 |

### api-spec.md "회원 API 제안"에서 지운 사실
| 사실 | 새 위치 / 삭제 이유 |
|---|---|
| 상태 "제안·팀 합의 전" | 삭제: 구현 완료로 바뀜 |
| 요약 표 13줄(경로·권한·요청·응답·오류) | member-auth-admin-api.md 각 API 절 |
| 회원 목록 회원 ID 내림차순, MemberSummary 필드, 비밀번호 비노출, page·size 1 이상 | member-auth-admin-api.md 회원 목록·DTO |
| promotedAt·promotedBy는 최근 성공 ROLE_CHANGE에서, 없으면 null | member-auth-admin-api.md DTO, member.md 관리자 작업 이력 |
| 승격 성공은 역할 변경과 이력을 한 트랜잭션, 실패 FAILED, 일반 회원 시도 DENIED | member.md 관리자 작업 이력 |
| 이력 조회 기본 최근 7일, from·to 포함, from>to 400, to만 주면 7일 | member-auth-admin-api.md(요청 표·오류), member.md(규칙) |
| result=FAILED는 FAILED·DENIED, 최신 시각·ID 내림차순, 기본 page 1·size 20 | member-auth-admin-api.md, member.md |
| actorRole·민감 값 제외 before·after | member-auth-admin-api.md DTO, member.md |
| 로그인 실패는 없는 이메일·틀린 비밀번호 구분 안 함, 정지는 비밀번호가 맞을 때만 403 | member-auth-admin-api.md 로그인 |
| A001(401)·A002(403) | member-auth-admin-api.md 로그인·오류 코드 |
| JWT HS256, `JWT_SECRET` UTF-8 32바이트 이상 | member.md 인증 쿠키·토큰·세션 |
| 액세스 15분, 갱신 최대 7일, 토큰 종류 교환 불가, 30분 미사용은 서버 세션에서 | member-auth-admin-api.md 인증 쿠키(수명), member.md(동작) |
| 로그인 요청 email·password, 응답 필드 | member-auth-admin-api.md 로그인·DTO (phone 추가, 위 2번) |
| 쿠키 이름·Path·수명·HttpOnly·SameSite=Lax·Secure, `AUTH_COOKIE_SECURE=false` | member-auth-admin-api.md 인증 쿠키 |
| 로그인 POST는 익명·CSRF 예외 | member-auth-admin-api.md 로그인, member.md |
| `GET /auth/csrf` → `XSRF-TOKEN` 쿠키, `X-XSRF-TOKEN` 헤더, 다시 받는 때 | member-auth-admin-api.md CSRF 토큰·인증 쿠키 |
| 보호 API의 서명·만료·세션 확인, 폐기·만료·30분 미사용이면 401 A003, 마지막 사용 시각 갱신 | member.md |
| 갱신 토큰 매번 교체, 동시 갱신은 행 잠금으로 하나만 성공, 이전 토큰 A005, 정지 A006, 7일 비연장 | member.md (재사용 문구 고침, 위 5번), A005·A006은 명세 오류 표 |
| 갱신은 만료된 액세스 없이 가능, 갱신 쿠키·CSRF 헤더 필요, 쿠키 재발급, Max-Age 남은 기간, 동시 갱신 모으기 | member-auth-admin-api.md 토큰 갱신(요구 조건), member.md(동작) |
| 로그아웃 세션 선택(액세스 유효 → 그 세션, 아니면 갱신 쿠키 세션) | member.md |
| 로그아웃 쿠키 없거나 이미 폐기여도 200·쿠키 삭제, CSRF 실패 403 | member-auth-admin-api.md 로그아웃 |
| tastes 값 5개 | member-auth-admin-api.md `Taste` |
| 가입 role 무시, /api/admin ADMIN 전용, 익명 401 A003·USER 403 A004 | member-auth-admin-api.md 머리말·회원가입, member.md 권한 |
| 403 AUTH_002(CSRF·메서드 권한 거부)와 프론트 1회 재시도 | member-auth-admin-api.md 인증 쿠키, member.md |
| 가입 비밀번호 규칙 (2026-10-16 확정) | member-auth-admin-api.md 회원가입, member.md(날짜는 변경 이력) |
| 가입 nickname 앞뒤 공백 제거 1~30자, tastes 빈 목록 허용·중복 거부 | member-auth-admin-api.md 회원가입 |
| phone 형식·필수·숫자 11자리 저장·M009, 기존 회원·초기 관리자 null 가능 | member-auth-admin-api.md 회원가입·DTO, member.md |
| 닉네임 변경 공백 제거·1~30자, 본인 닉네임 200, 중복 M002, 성공 `ApiResponse.ok()` | member-auth-admin-api.md 닉네임 변경 ("응답: 본문 없음"으로 표기) |
| 비밀번호 변경 규칙·M003·현재 세션 유지·다른 기기 폐기 (2026-10-17 결정) | member-auth-admin-api.md, member.md(날짜는 변경 이력) |
| 맛 선호 PUT 전체 교체, 빈 목록이면 모두 지움, 중복 허용·한 번 저장, 누락·null·허용 밖 값 400 | member-auth-admin-api.md 맛 선호 변경 |
| 가입 성공 `{memberId}`, M001·M002 | member-auth-admin-api.md 회원가입 |
| `POST /members` 익명·CSRF 예외 | member-auth-admin-api.md 회원가입, member.md |

### member-auth-admin-api.md에서 옮기거나 지운 사실
| 사실 | 새 위치 / 삭제 이유 |
|---|---|
| 상태·담당 범위 문단 | 새 머리말 (담당 cookiez) |
| 목차 표의 화면 ID | 삭제: 화면은 [화면 목록](../requirements/screens.md)에서 관리 |
| 공통 사항: 경로 prefix·`ApiResponse` | 머리말, api-spec 공통 규칙 |
| 공통 사항: 인증 Q-08 | 삭제: 인증 쿠키 절로 정해짐 |
| 공통 사항: 권한 표기 | 각 API 한 줄(공개·로그인·ADMIN) |
| 공통 사항: 401·403 구분, 업무 오류에 401 안 씀 | api-spec 공통 규칙 오류 표, 로그인 주의 |
| 공통 사항: 날짜 "시간대 미정"(Q-07) | 삭제: api-spec 공통 규칙 날짜 형식 |
| 공통 사항: 페이지 1부터·기본 20 | api-spec 페이지 응답, 각 요청 표 |
| 공통 사항: 입력 오류 Q-10 | 삭제: project-plan Q-10에서 관리 |
| 공통 사항: 관리자 쓰기·권한 거부 이력, 민감 값 제외 | member.md 관리자 작업 이력 |
| 공통 값 Role·MemberStatus·Taste | 회원 응답 DTO |
| 공통 값 DayOfWeek | deal-analysis-api.md |
| 1-1 결과 참고용 | 이메일 중복 확인 주의 |
| 1-2 본인 닉네임 true | 닉네임 중복 확인 주의 |
| 1-3 비밀번호 확인 칸은 화면에서만 | 삭제: 화면 동작이라 명세 범위 밖 |
| 1-3 가입만 하고 로그인 안 함 | 회원가입 주의, member.md 대표 흐름 |
| 1-3 회원 생성·맛 선호 한 트랜잭션 | member.md 구현 방향 |
| 2-1 keepLoggedIn(Q-01) | 삭제: 코드에 없음 (위 1번) |
| 2-1 ADMIN이면 관리자 화면, 401·403은 폼 오류 | 로그인 주의 |
| 2-2 401이면 1회 자동 호출, A005·A006, Q-08 | 토큰 갱신 오류 표, member.md |
| 2-3 이미 만료돼도 200 | 로그아웃 주의 |
| 3-1 앱 시작 시 로그인 확인, 401이면 비로그인 | 내 정보 조회 주의 |
| 3-1 role은 마이페이지에 표시 안 함 | 삭제: 화면 동작 |
| 3-3 M003·M004, 다른 기기 세션 폐기 | 비밀번호 변경 |
| 3-4 중복 값 400 | 고침 (위 3번) |
| 3-4 후기에 바뀐 맛이 보임 | member.md 연동 계약(후기 조회 시 최신 값) |
| 3-5 로그인 기기 관리(제안) | member.md 제안 기능 |
| 4-1 promotedBy는 작업 이력에서 | member.md 관리자 작업 이력 |
| 4-2 오류 표, 한 트랜잭션, 즉시 적용·다음 로그인 때 화면 전환, 권한 해제 없음 | 관리자 승격, member.md 구현 방향 |
| 5장 판매 예측 명세 | forecast-api.md |
| 5장 배치 시각, 실제 판매량 정의, F001 메시지 항목, 시연 결과 저장 규칙, 기준선 오차 0 처리 | sales-forecast.md 집계 기준·조회 기준 |
| 5장 전주 순위(`previousActualRank`) 결정 대기 | sales-forecast.md 남은 결정 |
| 6장 할인 후보 명세 | deal-analysis-api.md (위 8~13번 고침) |
| 6장 가격 불변, 분석마다 새로 저장, 제외 사유, 최근 4주 같은 요일, dev 시연 등록 | deal-analysis.md |
| 6-3 보낸 후보만, 미검토 남아도 저장, DEAL_REVIEW | 검토 결과 저장, deal-analysis.md |
| 6-4 EVENT_REQUEST, SALE_001 등 행사 쪽 오류 | 할인 행사 등록 주의, deal-analysis.md |
| 7-1 쿼리·응답·수정 불가 | 작업 이력 조회 |
| 7-2 상품 작업 이력 기록 주체(결정 대기) | member.md 남은 결정 (실제 기능 빠짐) |
| 8장 상품 관리자 기능 요청 표 | 삭제: product-api.md에 구현됨 |
| 8장 주문·재고·검색·행사·후기 데이터 | 삭제: 같은 백엔드 내부 호출. sales-forecast.md·deal-analysis.md 연동 계약, member.md 연동 계약 |
| 9장 A·M 코드 | 오류 코드 표 (이름·메시지 코드 기준, 위 6번) |
| 9장 F·D 코드 | forecast-api.md·deal-analysis-api.md 오류 코드 |
| 10장 쿠키·회전·CSRF·CORS(Q-08) | 반영: 인증 쿠키 절, member.md (CORS 없음) |
| 10장 401 재발급·비밀번호 변경 후 세션(Q-02·Q-08) | 반영: member.md |
| 10장 로그인 상태 유지(Q-01) | 반영: 30분, 선택지 없음 |
| 10장 비밀번호·닉네임 규칙(Q-09) | 반영. 이메일 인증은 member.md 남은 결정 |
| 10장 필드별 오류 구조(Q-10) | 삭제: project-plan Q-10에서 관리 |
| 10장 페이지 1부터 | 반영: api-spec 페이지 응답 |
| 10장 시간대(Q-07) | 반영: api-spec 날짜 형식 |
| 10장 전주 순위 | sales-forecast.md 남은 결정 |
| 10장 행사 요청·결과 전달(Q-05) | 반영: 기존 행사 생성 서비스 직접 호출 |
| 10장 상품 작업 이력 기록 주체 | member.md 남은 결정 |
| 10장 로그인 기기 관리 | member.md 제안 기능 |

### api-spec.md에서 그 밖에 바꾼 것
- 공통 규칙의 "아래 회원 API 절" → [인증 쿠키](../api/member-auth-admin-api.md#인증-쿠키) 링크.
- 검증 실패 문장의 "(필드별 구조는 Q-10)" 삭제. Q-10은 project-plan 결정 대기 목록에 그대로 있다.
- 상품 할인·품절 필드 → product-api.md (문구 그대로). 거는 곳 cart-api.md, sale-event-api.md, product-api.md 링크를 고쳤다.
- 도메인별 API 표에 판매 예측·할인 후보 추가. 클릭 수 기반 할인 상품·할인 행사 줄의 앵커가 이미 없는 제목을 가리켜 고쳤다.
- 엔드포인트 작성 템플릿을 명세 모양으로 바꿨다.

## 크기
| 파일 | 전 줄 | 후 줄 | 전 글자 | 후 글자 |
|---|---|---|---|---|
| api/api-spec.md | 179 | 102 | 9,936 | 2,948 |
| api/member-auth-admin-api.md | 743 | 337 | 18,433 | 8,484 |
| api/forecast-api.md (새로) | 0 | 105 | 0 | 2,927 |
| api/deal-analysis-api.md (새로) | 0 | 191 | 0 | 5,515 |
| api/product-api.md | 229 | 243 | 6,513 | 7,177 |
| domains/member.md | 76 | 121 | 3,157 | 5,914 |
| domains/deal-analysis.md (새로) | 0 | 62 | 0 | 2,312 |
| domains/sales-forecast.md | 24 | 38 | 1,333 | 1,958 |
| domains/deal-analysis-input-discussion.md | 37 | 39 | 1,997 | 2,055 |

## 검증
| 명령·시나리오 | 결과 | 근거 |
|---|---|---|
| 저장소 전체 md 링크·앵커 검사 (스크립트) | 이번에 바꾼 파일 깨짐 0 | 아래 "이미 깨져 있던 링크" 13개는 이 작업 전부터 있던 것 |
| `/v3/api-docs`(local 서버)와 필드 이름 대조 | 같음 | 위 대조 절 |
| 애플리케이션 테스트 | 미실행 | 문서만 바꿈 |

## 연결과 남은 작업
- 실제 커밋: 로컬 `docs/member-api-cleanup` 4개(push 전).
- 이미 깨져 있던 링크(다른 담당 문서, 이번에 고치지 않음): AGENTS.md → README#문서-안내(제목이 "11. 문서 안내"), domains/cart·notification·order·payment-card·sale-event·settlement.md의 erd.md·data-dictionary.md 앵커 9개, sale-event.md의 sale-event-api.md 앵커 2개, work-141 작업 기록 1개.
- 상품 작업 이력 기록(위 "실제 기능 빠짐")은 팀이 담당을 정해야 한다.
