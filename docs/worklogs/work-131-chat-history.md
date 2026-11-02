# WORK-131: 마이페이지 AI 상담 내역 목록

상태: 검증 대기 · 작성일: 2026-11-02 · 담당: zcookiez

## 목표와 완료 기준

- 마이페이지 "AI 상담 내역"(화면 7-2)의 "준비 중" 자리에 내 상담 목록을 보여 준다.
- 진행 중 상담은 채팅창을 열고, 끝난 상담(종료·만료)은 읽기 전용 팝업으로 대화를 본다.

## 근거와 범위

- 요구사항: `docs/requirements/screens.md` 7-2. 이번 범위는 목록·읽기 전용 대화. 기간 필터·메뉴 상세·추천 메뉴 칩은 범위 밖.
- 변경 영역: 백엔드 chat 도메인, 프론트 chat 도메인·마이페이지 상담 화면, 생성 API 클라이언트.
- 가정: 요청 page는 프로젝트 공통 규칙대로 1부터(요청서의 `page=0` 예시 대신). 상태 태그 문구는 상담 중 / 종료 / 만료.

## 구현 결과

- 백엔드 `GET /api/chat-sessions`: `ChatSessionListRequest`(page 1부터, size 기본 10), `ChatSessionSummaryResponse`. `ChatSessionRepositoryImpl`이 메시지가 있는 본인 세션을 최근 시작순으로 페이지 조회하고, `ChatMessageRepositoryImpl`이 세션 ID 목록의 메시지 수·첫 USER 메시지를 한 번에 조회한다. 첫 질문은 코드 포인트 기준 50자로 자른다.
- 기존 `GET /api/chat-sessions/{id}/messages`는 그대로 쓴다. 종료·만료 세션도 본인 것이면 조회되는 것을 테스트로 확인했다.
- 프론트: `ChatHistorySection`(목록·페이지네이션·빈 상태), `ChatHistoryDialog`(읽기 전용, `ChatMessageBubble` 재사용), `format.ts`. 상담 종료·새 상담 시작·메시지 응답 완료 시 `['chat','sessions']`를 무효화한다.
- 채팅창 열기는 기존 `useChatModalStore`의 `openChat`을 쓴다(`useChatModal().open()`이라는 훅은 없다).
- DB 변경 없음.

## 검증

| 명령·시나리오 | 실행 환경·조건 | 통과/실패/미실행 | 근거 |
|---|---|---|---|
| `ChatSessionHistoryTest` | 로컬(Testcontainers MySQL) | 통과 | 최근순·통계, 빈 상담 제외, 타 회원 제외, 50자 자르기, 페이지, 종료·만료 메시지 조회 (6건) |
| `ChatSessionControllerTest` | 로컬 | 통과 | 비로그인 401, 기본 page·size와 응답 형식, page=0 400 |
| `./gradlew test --tests '*domain.chat.*'` | 로컬 | 통과 | chat 도메인 전체 |
| `pnpm oag:generate` | 로컬 백엔드 | 통과 | 스펙 변경은 새 엔드포인트·모델뿐 |
| `pnpm lint`·`typecheck`·`build`·`test` | 로컬 | 통과 | 125 tests |
| `pnpm format:check` | 로컬 Windows | 실패(기존) | 변경 전에도 같은 36개 파일(CRLF 체크아웃). 이번 변경 파일은 해당 없음 |
| `./gradlew clean compileJava compileTestJava`, `./gradlew test` | 로컬(Docker) | 통과 | 1252개, 실패 0 |
| 로그인 상태 실제 응답·브라우저 375/768/1440 확인 | - | 미실행 | 비로그인 401만 실제 서버로 확인 |

## 후속: 마이페이지 레이아웃·상담 목록 정렬 (2026-11-02)

- 마이페이지 공통(`MyPageLayout.module.scss`): md부터 내용 칸을 줄 높이까지 늘리고(`align-self: stretch`) 화면 최상위 카드가 채운다. 메뉴는 위쪽 정렬 그대로라 sticky 유지. 모바일(가로 탭)은 미적용. 내 레시피 관리는 바깥 div 안의 목록 카드가 채우도록 `MyPageRecipesPage.module.scss`에 한 줄 추가. 내 정보 수정은 카드 4개 격자라 늘릴 단일 카드가 없어 사실상 변화 없음.
- 상담 목록 한 줄을 grid로: 날짜 14ch(tabular-nums) / 상태 4.5em(태그 가운데) / 첫 질문 minmax(0, 1fr) 말줄임 / 메시지 수 11ch 오른쪽 정렬. 모바일은 첫 질문만 아랫줄. 줄 hover는 연한 배경 + 손가락 커서, 밑줄 없음.
- 태그는 모두 `variant="filled"`. 상담 중만 브랜드색 클래스(antd `processing`은 colorInfo 기본 파랑이라 쓰지 않음).
- 페이지네이션은 2페이지 이상일 때만: 상담 내역·주문 내역. 내 레시피 관리는 이미 같은 기준.
- 검증: lint·typecheck·build·test 통과. 375/768/1440 브라우저 확인은 미실행.

## 후속: 목록 + 대화 두 칸 (2026-11-02)

- 지난 상담 팝업(`ChatHistoryDialog`)이 오른쪽 아래 채팅창과 함께 떠서 어색해 삭제하고, 같은 화면 안 두 칸으로 바꿨다.
- lg(992px, antd 기준)부터 왼쪽 목록 + 오른쪽 대화(`ChatSessionConversation`). 두 칸 모두 560px 고정, 대화 칸만 안에서 스크롤하고 상담을 고르면 맨 아래로. 처음엔 첫 상담 선택. 선택 줄은 메뉴 선택과 같은 연한 브랜드색.
- 한 페이지 6개(줄 약 73px × 6 + 페이지네이션이 560px 안에 들어감). 두 칸 화면의 목록은 폭이 좁아 첫 질문을 아랫줄로 내리고 칸 너비는 그대로.
- 진행 중 상담은 입력창 없이 [채팅창에서 이어서 상담하기]로 채팅창을 연다(같은 상담이 두 곳에서 열리지 않게). 종료·만료는 "지난 상담은 읽기만 할 수 있어요".
- lg 미만은 목록만, 누르면 대화 화면 + [← 목록].
- 상태 태그는 목록·대화 머리가 함께 쓰도록 `ChatSessionStatusTag`로 뺐다.
- 채팅 입력 글자 수 한도를 200 → 500(백엔드 `@Size(max = 500)`)으로 맞췄다.
- 검증: lint·typecheck·build·test 통과. 브라우저에서 높이·줄 수 실측은 미실행.

## 후속: 진행 중 상담을 오른쪽 칸에서 바로 대화 (2026-11-02)

- 진행 중 상담을 고르면 오른쪽 칸에 `ChatSessionLiveConversation`이 채팅창과 같은 `ChatRoomSection`을 넣는다(스트리밍, 500자, 하루 제한·만료 안내, 상담 끝내기 그대로). `ChatRoomSection`에는 헤더 제목을 바꾸는 `title` prop만 추가했다.
- `ChatRoomSection`은 회원의 진행 중 상담(activeSession)을 다루므로 목록의 상담과 id가 같을 때만 입력창을 보여 준다. 다르면 activeSession과 목록을 다시 받아 맞춘다. 상담을 끝내면 activeSession이 먼저 COMPLETED가 되어 그 자리에서 읽기 전용으로 바뀌고, 목록 무효화로 왼쪽 상태도 "종료"가 된다. 메시지를 보내면 기존 목록 무효화로 메시지 수가 갱신된다.
- 같은 상담이 두 곳에서 열리지 않게 `useChatModalStore`에 `inlineHost`를 뒀다. 상담 내역 화면이 떠 있으면 채팅창을 닫고, `openChat`은 채팅창 대신 이 화면의 진행 중 상담(목록에 없으면 새 상담 대화 칸)을 연다. 빈 상태의 [AI 상담 시작하기]도 이 경로로 화면 안에서 시작한다.
- 목록 줄을 모든 폭에서 두 줄로: 1줄 날짜 + 상태 태그 / 2줄 첫 질문(말줄임) + 메시지 수(글자만큼, 오른쪽). 좁은 목록 칸에서 "메시지 N개"가 넘치던 문제 해결. 선택 줄의 첫 질문은 기본 글자색.
- 검증: lint·typecheck·build·test(128) 통과, 스토어 열기 경로 테스트 3건 추가. 브라우저 확인은 미실행.

## 후속: 상태 탭 + 무한 스크롤 목록 (2026-11-02)

- 목록 구조·배치는 Murder-Help 관리자 채팅 목록(`AdminChatDashboard.tsx`)을 참고하고, 색·글자는 antd 변수·12px 이상으로 다시 그렸다. 앞선 두 줄 grid 목록 디자인은 이걸로 대체.
- 백엔드 `GET /api/chat-sessions`: `status`(ALL/ACTIVE/ENDED) 필터, 응답을 `ChatSessionListResponse { counts, sessions }`로 바꿈(내 레시피 `MyRecipeListResponse`와 같은 감싸기). 요약에 `lastMessagePreview`(마지막 메시지 앞 50자)를 첫 질문과 같은 쿼리에서 함께 조회. 탭 건수는 조건 합계 쿼리 1회.
- 결정: 시간 제한이 지났는데 아직 ACTIVE인 상담은 목록에서 만료로 보이고 ENDED·종료 건수에 들어간다. 그대로 두면 "상담 중" 탭에 있지만 열면 이미 끝난 상담이 된다.
- 테스트 정리: `ChatSessionHistoryTest`가 남긴 메시지가 `chat_sessions`만 지우는 다른 테스트(`ChatSessionUniqueConstraintTest`)를 외래 키로 깨뜨려, 앞뒤로 두 표를 비우게 했다.
- 프론트: `useInfiniteQuery`(`sessionListInfiniteQuery`), 목록 끝이 보이면 다음 페이지(IntersectionObserver, 받는 중에만 작은 로딩). 탭은 `Segmented` + 건수, URL `?status=`. 줄은 button 카드(간격 8px, 선택 시 연한 회색 + 테두리), 3줄 구성. 상태 태그는 상담 중 = 초록 점 + success, 종료·만료 = 테두리만.
- 오른쪽 대화: `ChatRoomSection`에 `hideStatusBanner`·`onSessionCompleted` prop 추가. 이 화면에서만 "● 상담 중" 줄을 숨기고, "상담 중" 탭에서 상담을 끝내면 "종료" 탭으로 옮겨 같은 상담을 계속 보여 준다.
- 밑줄: 앱 SCSS·전역 스타일·테마·antd(Typography의 `u`/`ins`만 밑줄)에서 목록 줄·말풍선에 text-decoration을 주는 곳을 찾지 못했다. 브라우저로 재현하지 못해 원인은 미확정.
- 검증: 백엔드 `./gradlew clean compileJava compileTestJava`·`./gradlew test` 전체 통과, 프론트 lint·typecheck·build·test(129) 통과. `pnpm oag:generate`는 작업 트리 백엔드를 8081로 따로 띄워 실행(`OPENAPI_SPEC_URL`). 브라우저 375/768/1440 확인은 미실행(브라우저 도구 없음).

## 연결과 남은 작업

- 실제 커밋·PR: 아직 없음
- 남은 작업: 추천 메뉴 칩(추천 단계), 기간 필터
