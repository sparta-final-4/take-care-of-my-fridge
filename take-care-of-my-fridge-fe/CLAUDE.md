# 프론트엔드 개발 규칙

상태: 공유 규칙 기준안 · 최초 정리: 2026-10-14 · 구조 정리: 2026-10-16

## 적용 메모

- 이 문서가 프론트 규칙의 기준본이다. 경로·명령의 기준 디렉터리는 이 폴더(`take-care-of-my-fridge-fe/`)이고, `../docs` 폴더는 참고하지 않는다. 기존 코드와 충돌하면 이 문서가 우선이며 그 사실을 보고한다.
- [공통 지침](../AGENTS.md)의 절차를 함께 적용한다.
- 백엔드는 [../take-care-of-my-fridge-be](../take-care-of-my-fridge-be). 명세보다 실제 컨트롤러·DTO·직렬화가 우선이며, 다르면 프론트를 백엔드에 맞추고 보고한다.
- `(예정)`이 붙은 파일·폴더는 아직 없다. 처음 필요할 때 이 문서의 위치와 이름으로 만든다.
- 30분 미사용 시 자동 로그아웃한다. 현재 프론트는 보호 API 401에서 재발급 후 한 번 재시도하고, 만료·정지 시 로그인으로 이동한다. 가입 비밀번호 규칙은 백엔드 DTO를 따른다. 이메일 인증은 현재 API에 없다.
- API 클라이언트는 `openapi-generator-cli`로 생성한다(8.1). 생성 결과는 스펙과 함께 커밋하며, 생성·협업 절차는 [README](README.md#api-클라이언트-생성)를 따른다.

## 0. 작업 절차

**시작 전**

1. `node_modules`가 없거나 `pnpm-lock.yaml`이 바뀌었을 때만 `pnpm install --frozen-lockfile`.
2. API를 다루는 작업이면 먼저 `../take-care-of-my-fridge-be`의 컨트롤러·DTO·`global/response/ApiResponse.java`를 읽고 `openapi/fridge-api.json`과 맞는지 확인한다. 다르면 스펙을 손으로 고치지 않고 사용자에게 알린다(백엔드를 띄워 `pnpm oag:generate`로 갱신해야 한다).

**완료 전** (모두 통과해야 완료)

```bash
pnpm lint && pnpm format:check && pnpm typecheck && pnpm build
pnpm test
```

**UI 검증**: 브라우저 도구가 있으면 375 / 768 / 1440px에서 실제로 확인한다. 없으면 확인했다고 보고하지 않는다.

**작업 보고**: 변경 파일, 백엔드에 맞춰 바꾼 타입, 검증하지 못한 항목, 사용자 확인이 필요한 사항.

## 1. 기술 스택

pnpm · Vite · TypeScript(strict) · React · Ant Design · `@ant-design/icons` · SCSS Modules · React Router v7(Data 모드) · Zustand · TanStack Query · axios · dayjs · ESLint + Prettier · Vitest

- **pnpm만** 사용한다(`npm`·`yarn`·`npx` 금지). 설치된 CLI는 `pnpm exec`. pnpm은 `packageManager`, Node는 `.node-version`·`engines`로 버전 고정.
- 새 라이브러리는 사용자 확인 후 `pnpm add`. 기존 버전은 임의로 올리지 않는다.
- 금지: Tailwind 등 유틸리티 클래스, CSS-in-JS, 클래스 컴포넌트, 다른 상태관리·UI 라이브러리, `react-router-dom` import.

## 2. 구조

```
src/
├─ main.tsx                  # 폰트·전역 스타일·dayjs 로케일 import, App 마운트
├─ app/                      # 라우터, 전역 설정
│   ├─ App.tsx               # Provider 구성
│   ├─ router.tsx            # createBrowserRouter
│   ├─ paths.ts              # ROUTES
│   ├─ theme.ts              # antd 테마
│   ├─ queryClient.ts        # QueryClient, mutation 전역 실패 알림
│   ├─ feedback.ts           # React 밖에서 쓰는 message·notification·modal
│   └─ FeedbackBridge.tsx
├─ common/                   # 모든 도메인이 같이 쓰는 것
│   ├─ components/           # 공통 UI (RouteErrorPage 등)
│   ├─ api/
│   │   ├─ axiosInstance.ts  # 유일한 axios 인스턴스, 인터셉터
│   │   ├─ clients.ts        # 생성 API 클래스 인스턴스, 생성 모델 타입 재export
│   │   ├─ unwrap.ts         # 생성 응답에서 data만 꺼냄
│   │   ├─ errors.ts         # 에러 분류·메시지 변환
│   │   ├─ types.ts          # ApiResponse<T>, PageResponse<T> 등 공통 응답 타입
│   │   └─ generated/        # openapi-generator-cli 생성 결과(커밋함), 직접 수정 금지
│   └─ auth/                 # 8.4 인증: authStore, initialize, session, sessionId, RequireAuth 등
├─ layout/                   # MainLayout, Header
├─ pages/                    # XxxPage.tsx: 섹션을 배치만 함 (MainPage, NotFoundPage)
├─ styles/                   # global.scss, _variables.scss, _mixins.scss
└─ domains/                  # 백엔드 도메인과 같은 이름
    └─ <domain>/             # fridge(재료 입력·내 냉장고), review(후기), notification(헤더 알림),
        │                    # chat(AI 상담), recipe(레시피 추천), product(밀키트 큐레이션)
        ├─ api.ts            # API 함수와 요청·응답 타입 (8.1)
        ├─ queries.ts        # queryKey·queryOptions (8.2)
        ├─ components/
        └─ hooks/            # 도메인 UI 로직 훅 (쿼리를 감싸기만 하는 훅은 두지 않음)
```

- import는 `@/` alias(`src/`), 같은 폴더는 `./`. alias는 Vite `resolve.alias`와 `tsconfig` `paths`에 똑같이 설정한다. 배럴 파일(`index.ts` 재export) 금지. 예외: `src/common/api/clients.ts`는 생성 모델 타입을 재export한다(도메인이 `generated` 경로를 직접 import하지 않게 하기 위해).
- 컴포넌트와 `.module.scss`는 같은 폴더, 같은 이름.
- `App.tsx` Provider 순서: `ConfigProvider`(theme, koKR) → `AntdApp` → [`FeedbackBridge`, `QueryClientProvider` → `RouterProvider`]. `FeedbackBridge`는 반드시 `AntdApp` 안에 둔다.

## 3. 구현 원칙 — 단순하게

1. 만들기 전에 `src/`를 검색해 기존 것을 재사용·확장한다. 중복 구현 금지.
2. antd를 감싸기만 하는 래퍼 컴포넌트 금지.
3. 컴포넌트 분리는 재사용되거나 책임이 섞여 읽기 어려울 때만.
4. 지금 필요 없는 옵션·props·제네릭·추상화 레이어를 만들지 않는다.
5. 요청받지 않은 리팩터링·파일 이동은 하지 않고 제안만 한다.

## 4. React / TypeScript

- 함수형 컴포넌트(`function` 선언) + Hooks. props는 같은 파일의 `interface XxxProps`.
- 페이지만 `default export`(lazy 로딩), 나머지는 named export.
- 네이밍: 컴포넌트·파일 `PascalCase`, 페이지 `~Page`, 레이아웃 `~Layout`, 훅 `useXxx`, 스토어 `useXxxStore`, 핸들러 `handleXxx` / 콜백 props `onXxx`.
- `any` 금지(`unknown` 후 좁히기).
- 서버 데이터 로딩에 `useEffect`+`useState`를 쓰지 않는다(TanStack Query). `useEffect`는 외부 시스템 동기화에만.
- `useMemo`/`useCallback`은 필요할 때만.
- 날짜는 `dayjs`. 표기 `YYYY-MM-DD`, `YYYY-MM-DD HH:mm`.

## 5. 상태 관리

| 상태                                 | 위치                                                                          |
| ------------------------------------ | ----------------------------------------------------------------------------- |
| 서버 데이터                          | TanStack Query(조회는 `queryOptions`, 변경은 `useMutation`) + 도메인 API 함수 |
| 컴포넌트 내부 UI 상태                | `useState`                                                                    |
| 폼 값                                | antd `Form`                                                                   |
| 검색어·필터·페이지 번호              | URL 쿼리(`useSearchParams`)                                                   |
| 여러 화면이 공유하는 클라이언트 상태 | Zustand                                                                       |

- 컴포넌트에서 API 함수를 직접 호출하지 않는다. 버튼 등 이벤트로 서버를 바꾸는 동작도 `useMutation`으로 한다.
- 서버 데이터를 Zustand에 복사하지 않는다.
- Zustand는 selector로 필요한 값만 구독(여러 값은 `useShallow`). React 밖에서는 `getState()`.
- `persist`는 사용자 설정에만. 토큰·개인정보 저장 금지.

## 6. 라우팅

- `createBrowserRouter`·`generatePath`는 `react-router`, `RouterProvider`는 `react-router/dom`.
- 경로 문자열은 `src/app/paths.ts`의 `ROUTES`에만 두고, 파라미터 경로는 `generatePath`로 만든다.
- 페이지는 `src/pages/XxxPage.tsx`(폴더 없이)로 만들고 `src/app/router.tsx`에 등록한다. 추가 절차는 [README](README.md#새-페이지-추가).
- 레이아웃은 정적 import 후 `Component`로, **lazy는 페이지에만**. 로그인이 필요한 화면은 `RequireAuth` 라우트 아래에 둔다.
- 에러는 `errorElement`, 404·권한 없음은 antd `Result`.
- 배포 시 SPA 화면 경로에만 `index.html`을 응답한다. 정적 파일 404·API 오류까지 바꾸지 않는다.

## 7. UI

### 7.1 원칙

- UI 요소는 antd로 먼저 해결한다. 간격은 `Flex`·`Space`·`Row/Col`, 텍스트는 `Typography`, 아이콘은 Outlined 계열.
- 새 화면은 비슷한 기존 화면의 구성을 따른다.
- 넉넉한 여백, 약한 그림자, primary 한 색 중심. 페이지 전체를 습관적으로 Card로 감싸지 않는다.
- `message`·`notification`·`modal`은 `App.useApp()`으로 쓴다. React 밖(queryClient 등)에서는 `src/app/feedback.ts`의 `getFeedback()`을 쓴다(`FeedbackBridge`가 `useEffect`로 연결).
- Primary 버튼은 같은 시각 영역(헤더·모달·카드·섹션)마다 하나. 위험 동작은 `danger`.
- 버튼 문구: 등록, 수정, 삭제, 저장, 취소, 검색, 초기화.

| 목적                  | 패턴                                      |
| --------------------- | ----------------------------------------- |
| 사용자 화면 목록      | 카드 그리드(`Row/Col`) + `Pagination`     |
| 관리자 목록           | `Table`(`rowKey` 필수, 서버 페이지네이션) |
| 입력                  | `Form`(`rules`, 기본 `layout="vertical"`) |
| 생성/수정 팝업        | 단순하면 `Modal`, 항목이 많으면 `Drawer`  |
| 로딩 / 빈 상태 / 에러 | `Skeleton`·`Spin` / `Empty` / `Result`    |
| 위험 동작 확인        | `Popconfirm`                              |

### 7.2 반응형 (모든 화면 필수)

- 모바일(360px~)부터 데스크톱까지. 관리자 화면도 모바일에서 깨지지 않아야 한다.
- 브레이크포인트는 antd와 동일: sm 576 · md 768 · lg 992 · xl 1200 · xxl 1600.
- SCSS는 **모바일 우선**(기본이 모바일, 큰 화면은 `@include from(md)`). `max-width` 미디어쿼리를 섞지 않는다.
- 스타일 변화는 CSS로, 렌더링할 컴포넌트가 바뀔 때만 `Grid.useBreakpoint()`.
- 모바일 처리: 사이드 메뉴는 `lg` 미만에서 `Drawer`, `Table`은 `scroll={{ x: 'max-content' }}`, 주요 버튼은 `block`, 제목+액션은 `Flex wrap`.
- 확인 항목: 페이지 가로 스크롤 없음, 텍스트 겹침·잘림 없음(`ellipsis`), 클릭 영역 44px 이상, 모달이 화면 안에 들어옴.

### 7.3 접근성

- 키보드로 조작 가능, 포커스 표시 유지. 클릭 요소는 `button`/`a` 또는 antd(`div onClick` 금지). 이미지에는 `alt`.

### 7.4 테마·SCSS

- 색상·radius·폰트·간격의 기준은 `src/app/theme.ts`. SCSS에서는 antd가 노출하는 `var(--ant-*)`를 쓴다.
- antd v6는 CSS 변수가 기본으로 켜져 있어 `cssVar` 설정이 필요 없다. 변수는 `:root`가 아니라 ConfigProvider 범위에만 생기므로 `body` 같은 전역 선택자에서는 쓸 수 없다.
- antd 스타일 변경 순서: ① `theme.components` ② props ③ SCSS 모듈 안 `:global`. 전역 오버라이드 금지.
- SCSS에 색상·간격·radius·폰트 크기를 하드코딩하지 않는다. 고정 치수는 `_variables.scss` 변수로. `1px`·`100%` 같은 값은 그대로 써도 된다.
- 전역 헬퍼 클래스 금지. 인라인 `style`은 런타임 계산 값에만.
- 폰트: `pretendard` 패키지의 `pretendard/dist/web/variable/pretendardvariable-dynamic-subset.css`를 `main.tsx`에서 import한다.
- 실제 기준 파일: `src/app/theme.ts`, `src/styles/_variables.scss`, `src/styles/_mixins.scss`(`from($bp)`), 사용 예는 `src/layout/*.module.scss`.

## 8. API · 인증

### 8.1 API 호출

- axios 인스턴스는 `src/common/api/axiosInstance.ts`의 `axiosInstance` 하나. HTTP 호출은 `src/domains/<domain>/api.ts`에서만 한다.
- API 함수는 일반 함수로 export한다(`getPages`, `updatePage`). 서비스 클래스 등 추가 레이어 금지.
- 응답 래퍼(`ApiResponse<T>`)는 API 함수 안에서 벗겨 실제 데이터를 반환한다.
- 조회 함수는 `signal?: AbortSignal`을 받아 axios에 넘긴다.
- 경로 변수는 `encodeURIComponent`, 쿼리스트링은 `params`로.
- **URL 규칙**: `VITE_API_BASE_URL`은 로컬·운영 모두 빈 값이다. 로컬은 Vite `server.proxy`, 운영은 CloudFront `/api/*` behavior가 `/api` 요청을 백엔드로 넘긴다. `/api` prefix는 백엔드 컨트롤러 경로에 포함돼 있으므로 경로에 그대로 쓴다(`'/api/pages'`).
- **OpenAPI 생성 클라이언트**: 생성 클래스 인스턴스는 `src/common/api/clients.ts`에서만 `new XxxApi(undefined, import.meta.env.VITE_API_BASE_URL, axiosInstance)`로 만든다(basePath를 빼면 `http://localhost`로 요청된다). `api.ts`의 일반 함수가 생성 메서드를 호출하고 `unwrap`으로 래퍼를 벗겨 반환한다. 화면·쿼리에서는 생성 클래스를 직접 쓰지 않는다.

**타입**

- 요청·응답 타입은 `@/common/api/clients`에서 가져오는 생성 모델 타입을 쓰고 직접 정의하지 않는다. 아직 generated로 전환하지 않은 도메인의 직접 정의 타입은 전환할 때 지운다. 공통 타입은 `src/common/api/types.ts`에 둔다.
- 백엔드 공통 응답은 `ApiResponse.java` 기준 `{ success, data, error }`이다. 오류는 `error: { code, message }`로 전달된다. 필드별 오류 구조는 없다.
- 백엔드 컨트롤러·DTO·직렬화 설정을 확인해 맞춘다. **추측으로 필드를 만들지 않고**, 확인할 수 없으면 묻는다.
- 날짜는 `string`, enum은 문자열 리터럴 유니온. 현재 생성 타입은 응답 필드도 `?`로 나오므로(백엔드에 required·nullable 정보 없음) `!`·`as`로 우회하지 않고 `??`·조건문으로 처리한다.
- 타입은 런타임 검증이 아니다. 새 API는 실제 응답을 한 번 확인하고, 못 했으면 보고한다.

```ts
// src/domains/member/api.ts
import { memberApi, type MyInfoResponse } from '@/common/api/clients';
import { unwrap } from '@/common/api/unwrap';

export async function getMyInfo(signal?: AbortSignal): Promise<MyInfoResponse> {
  return unwrap(memberApi.getMyInfo({ signal }));
}
```

### 8.2 TanStack Query

- 도메인별 `src/domains/<domain>/queries.ts`에 queryKey와 `queryOptions`를 모은다. 이를 감싸는 훅은 만들지 않는다.
- 목록 queryKey에는 결과에 영향을 주는 값(검색어·필터·정렬·페이지·페이지 크기)을 모두 넣는다.
- `queryFn`은 `signal`을 API 함수에 넘긴다.
- 첫 로딩 실패는 화면에서 `Result`로, 백그라운드 재조회 실패는 알리지 않는다.
- mutation 실패 알림은 `MutationCache.onError`(`src/app/queryClient.ts`)에서 한 번만. 화면에서 직접 처리하면 `meta: { skipGlobalError: true }`.
- mutation 후에는 `setQueryData`보다 `invalidateQueries`.
- **콜백 위치**: 캐시 무효화는 `useMutation` 옵션에, 메시지·화면 이동은 `mutate(값, { onSuccess })`에 둔다(언마운트되면 실행되지 않음).

```ts
export const pageQueries = {
  all: ['pages'] as const,
  detail: (slug: string) =>
    queryOptions({
      queryKey: [...pageQueries.all, 'detail', slug],
      queryFn: ({ signal }) => getPage(slug, signal),
    }),
};

const { mutate } = useMutation({
  mutationFn: (input: UpdatePageRequest) => updatePage(slug, input),
  onSuccess: () => queryClient.invalidateQueries({ queryKey: pageQueries.all }),
});
const handleSave = (values: UpdatePageRequest) =>
  mutate(values, { onSuccess: () => message.success('저장했습니다.') });
```

### 8.3 에러 처리

에러 분류와 메시지 변환은 `src/common/api/errors.ts`에서만 한다.

| 상황                      | 처리                                                                   |
| ------------------------- | ---------------------------------------------------------------------- |
| 로그인 API 401            | 로그인 실패로 폼에 표시                                                |
| 사용자 정보 조회 401      | A006은 정지 안내. 그 외에는 재발급 후 재조회하고 실패 시 비로그인 처리 |
| 그 외 401                 | 현재 세션의 보호 API면 재발급 후 한 번 재시도. 실패 시 로그인으로 이동 |
| 403                       | 권한 없음 안내. 로그인 A002는 정지 안내                                |
| 400 / 422                 | 확인된 오류 코드는 해당 필드에 표시하고 나머지는 공통 메시지로 안내    |
| 5xx · 네트워크 · 타임아웃 | 구분된 안내                                                            |

### 8.4 인증

인증 구현은 `src/common/auth/`의 `authStore.ts`, `initialize.ts`, `interceptors.ts`, `session.ts`, `sessionId.ts`, `RequireAuth.tsx`, `IdleLogout.tsx`에 있다. 로그인 복귀 경로 검증은 `LoginPage.tsx`에서 한다.

- HttpOnly 쿠키 기반(`withCredentials: true`). 토큰을 브라우저 저장소에 두지 않는다. CSRF·CORS는 백엔드와 함께 정한다.
- 상태(`useAuthStore`의 `status`): `initializing` / `authenticated` / `anonymous` / `error`.
- **초기화**: `src/app/App.tsx`에서 `initSession()` 호출. 성공 → `authenticated`, 401 → `anonymous`, 그 외 오류 → `error`. 진행 중인 Promise를 재사용해 중복 요청을 막는다(StrictMode를 끄지 않는다).
- **`RequireAuth`**: `initializing`이면 로딩, `error`면 재시도 버튼, `anonymous`면 현재 경로를 `redirect`로 붙여 로그인으로 이동.
- **세션 전환**(`session.ts`): 로그인·로그아웃·만료 시 `cancelQueries` 후 `clear`. 화면 이동은 하지 않고 상태만 바꾼다(이동은 `RequireAuth`).
- 전환마다 `sessionId`를 올리고, `await` 후 상태를 바꾸기 전에 `sessionId`가 그대로인지 확인한다.
- 요청마다 시작 시점의 `sessionId`를 기록해, 이전 세션 요청의 401로 현재 세션을 끝내지 않는다. 이전 세션 mutation의 실패도 알리지 않는다.
- 세션이 바뀌어도 계속 마운트되는 컴포넌트(헤더 등)의 mutation은 `onMutate`에서 `sessionId`를 기록하고 콜백에서 비교한다.
- **의존 방향**: `sessionId.ts`는 의존성 없음. `session.ts`는 `domains/*/api.ts`·`app/router.tsx`를 import하지 않는다. 순환 import를 만들지 않는다.
- 로그인 후 `redirect`는 `safeRedirect()`로 검증한다. 문자열 검사가 아니라 `new URL()`로 파싱한 origin이 같을 때만 허용한다.

## 9. 코드 품질

- ESLint(`eslint.config.js`): `typescript-eslint`, `react-hooks`, `react-refresh`, `jsx-a11y`, `eslint-config-prettier`. 포맷은 Prettier(`.prettierrc.json`), 이 문서의 코드 예시도 같은 규칙을 따른다.
- `eslint-disable` 금지. 불가피하면 해당 줄에만 사유와 함께.
- import 순서: 외부 → `@/` → `./` → `*.module.scss`.
- CI에서도 0장의 완료 명령을 실행한다.
