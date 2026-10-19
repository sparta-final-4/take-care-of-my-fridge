# 프론트엔드 개발 규칙

상태: 공유 규칙 기준안 · 출처: S1 · 최초 정리: 2026-10-14

## 적용 메모

- 아래 원문은 공유된 프론트 지침을 보존한 것입니다. 코드 경로와 명령의 기준 디렉터리는 frontend/입니다. 코드 예시는 예정 구조입니다.
- [공통 지침](../../AGENTS.md)과 [프론트 진입점](../../frontend/AGENTS.md)의 절차를 함께 적용합니다.
- API가 없는 초기에는 합의된 명세를 기준으로 합니다. 실제 백엔드가 생기면 DTO·직렬화·명세를 대조합니다.
- 최초 의존성 설치와 잠금 파일 생성 이후 frozen-lockfile 절차를 적용합니다.
- 30분 미사용 시 로그아웃은 결정됐습니다([Q-01](../requirements/project-plan.md#결정-대기-목록)). 401 재발급, 만료 화면, 로그인·로그아웃 후 이동은 Q-02·Q-08 해결 전 확정하지 않습니다.
- 필드별 오류 계약은 Q-10, 가입 정책은 Q-09입니다.
- 라이브러리 세부 버전은 초기 구성 시 호환성을 확인해 고정합니다.

---

## 공유된 프론트 규칙 원문

`frontend/` 개발 규칙. 이 문서와 기존 코드의 패턴을 따른다. 충돌하면 이 문서가 우선이며 그 사실을 보고한다.

## 0. 작업 절차

**시작 전**
1. `node_modules`가 없거나 `pnpm-lock.yaml`이 바뀌었을 때만 `pnpm install --frozen-lockfile`.
2. API를 다루는 작업이면 먼저 `../backend`의 컨트롤러·DTO를 읽고 `src/api/<domain>.ts`의 타입과 맞는지 확인한다. 다르면 백엔드 기준으로 먼저 맞춘다.

**완료 전** (모두 통과해야 완료)
```bash
pnpm lint && pnpm format:check && pnpm typecheck && pnpm build
pnpm test   # 테스트 설정이 있을 때
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
├─ main.tsx, App.tsx    # Provider 조립, initSession() 호출 (순서는 아래)
├─ api/                 # client.ts(axios), types.ts, errors.ts, 도메인별 API(pages.ts …)
├─ queries/             # queryClient.ts, 도메인별 queryOptions
├─ auth/                # sessionId.ts, session.ts, initSession.ts
├─ router/              # index.tsx, paths.ts, RequireAuth.tsx
├─ layouts/  pages/     # pages/Xxx/XxxPage.tsx (+ components/ 필요할 때만)
├─ components/          # 2개 이상 페이지에서 재사용되는 것만
├─ stores/  hooks/  utils/
├─ theme/theme.ts
└─ styles/              # global.scss, _variables.scss, _mixins.scss
```
- import는 `@/` alias(`src/`), 같은 폴더는 `./`. alias는 Vite `resolve.alias`와 `tsconfig` `paths`에 똑같이 설정한다. 배럴 파일(`index.ts` 재export) 금지.
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

| 상태 | 위치 |
|---|---|
| 서버 데이터 | TanStack Query |
| 컴포넌트 내부 UI 상태 | `useState` |
| 폼 값 | antd `Form` |
| 검색어·필터·페이지 번호 | URL 쿼리(`useSearchParams`) |
| 여러 화면이 공유하는 클라이언트 상태 | Zustand |

- 서버 데이터를 Zustand에 복사하지 않는다.
- Zustand는 selector로 필요한 값만 구독(여러 값은 `useShallow`). React 밖에서는 `getState()`.
- `persist`는 사용자 설정에만. 토큰·개인정보 저장 금지.

## 6. 라우팅

- `createBrowserRouter`·`generatePath`는 `react-router`, `RouterProvider`는 `react-router/dom`.
- 경로 문자열은 `src/router/paths.ts`의 `ROUTES`에만 두고, 파라미터 경로는 `generatePath`로 만든다.
- 레이아웃은 정적 import 후 `Component`로, **lazy는 페이지에만**. 로그인이 필요한 화면은 `RequireAuth` 라우트 아래에 둔다.
- 에러는 `errorElement`, 404·권한 없음은 antd `Result`.
- 배포 시 SPA 화면 경로에만 `index.html`을 응답한다. 정적 파일 404·API 오류까지 바꾸지 않는다.

```tsx
const page = (load: () => Promise<{ default: React.ComponentType }>) => async () => ({
  Component: (await load()).default,
});

export const router = createBrowserRouter([
  { path: ROUTES.login, lazy: page(() => import('@/pages/Login/LoginPage')) },
  {
    Component: MainLayout,
    errorElement: <RouteErrorPage />,
    children: [
      { path: ROUTES.home, lazy: page(() => import('@/pages/Home/HomePage')) },
      { element: <RequireAuth />, children: [{ path: ROUTES.orders, lazy: page(() => import('@/pages/OrderList/OrderListPage')) }] },
    ],
  },
  { path: '*', lazy: page(() => import('@/pages/NotFound/NotFoundPage')) },
]);
```

## 7. UI

### 7.1 원칙
- UI 요소는 antd로 먼저 해결한다. 간격은 `Flex`·`Space`·`Row/Col`, 텍스트는 `Typography`, 아이콘은 Outlined 계열.
- 새 화면은 비슷한 기존 화면의 구성을 따른다.
- 넉넉한 여백, 약한 그림자, primary 한 색 중심. 페이지 전체를 습관적으로 Card로 감싸지 않는다.
- `message`·`notification`·`modal`은 `App.useApp()`으로 쓴다. React 밖(queryClient 등)에서는 `FeedbackBridge`가 `useEffect`로 연결해 둔 `feedback` 객체를 쓴다.
- Primary 버튼은 같은 시각 영역(헤더·모달·카드·섹션)마다 하나. 위험 동작은 `danger`.
- 버튼 문구: 등록, 수정, 삭제, 저장, 취소, 검색, 초기화.

| 목적 | 패턴 |
|---|---|
| 사용자 화면 목록 | 카드 그리드(`Row/Col`) + `Pagination` |
| 관리자 목록 | `Table`(`rowKey` 필수, 서버 페이지네이션) |
| 입력 | `Form`(`rules`, 기본 `layout="vertical"`) |
| 생성/수정 팝업 | 단순하면 `Modal`, 항목이 많으면 `Drawer` |
| 로딩 / 빈 상태 / 에러 | `Skeleton`·`Spin` / `Empty` / `Result` |
| 위험 동작 확인 | `Popconfirm` |

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
- 색상·radius·폰트·간격의 기준은 `theme.ts`. `cssVar: true`로 노출된 `var(--ant-*)`를 SCSS에서도 쓴다.
- antd 스타일 변경 순서: ① `theme.components` ② props ③ SCSS 모듈 안 `:global`. 전역 오버라이드 금지.
- SCSS에 색상·간격·radius·폰트 크기를 하드코딩하지 않는다. 고정 치수는 `_variables.scss` 변수로. `1px`·`100%` 같은 값은 그대로 써도 된다.
- 전역 헬퍼 클래스 금지. 인라인 `style`은 런타임 계산 값에만.
- 폰트: `pretendard` 패키지의 `pretendard/dist/web/variable/pretendardvariable-dynamic-subset.css`를 `main.tsx`에서 import한다.

```ts
// src/theme/theme.ts
export const theme: ThemeConfig = {
  cssVar: true,
  token: {
    colorPrimary: '#4f46e5',
    borderRadius: 8,
    fontFamily: "'Pretendard Variable', Pretendard, -apple-system, BlinkMacSystemFont, sans-serif",
  },
};
```

```scss
// src/styles/_variables.scss
$breakpoints: (sm: 576px, md: 768px, lg: 992px, xl: 1200px, xxl: 1600px);

// src/styles/_mixins.scss
@use 'sass:map';
@use 'variables' as *;
@mixin from($bp) {
  @media (min-width: map.get($breakpoints, $bp)) { @content; }
}

// 사용 예 (컴포넌트 .module.scss)
@use '@/styles/mixins' as *;

.toolbar {
  display: flex;
  flex-direction: column;
  padding: var(--ant-padding-sm);
  @include from(md) { flex-direction: row; padding: var(--ant-padding); }
}
```

## 8. API · 인증

### 8.1 API 호출
- axios 인스턴스는 `api/client.ts`의 `apiClient` 하나. axios 호출은 `api/<domain>.ts`에서만 한다.
- API 함수는 일반 함수로 export한다(`getPages`, `updatePage`). 서비스 클래스 등 추가 레이어 금지.
- 응답 래퍼(`ApiResponse<T>`)는 API 함수 안에서 벗겨 실제 데이터를 반환한다.
- 조회 함수는 `signal?: AbortSignal`을 받아 axios에 넘긴다.
- 경로 변수는 `encodeURIComponent`, 쿼리스트링은 `params`로.
- **URL 규칙**: `VITE_API_BASE_URL`에 API prefix까지 넣고(`/api`), 함수에는 prefix를 뺀 경로만 쓴다(`'/pages'`). 로컬은 Vite `server.proxy`로 `/api`를 백엔드에 넘긴다.

**타입**
- 요청·응답 타입은 도메인 파일에 백엔드 DTO 이름 그대로 정의하고 다른 곳에 중복 정의하지 않는다. 공통 타입은 `api/types.ts`.
- 백엔드 컨트롤러·DTO·직렬화 설정을 확인해 맞춘다. **추측으로 필드를 만들지 않고**, 확인할 수 없으면 묻는다.
- 날짜는 `string`, enum은 문자열 리터럴 유니온, `?`와 `| null`은 백엔드 기준으로 구분.
- 타입은 런타임 검증이 아니다. 새 API는 실제 응답을 한 번 확인하고, 못 했으면 보고한다.

```ts
// src/api/pages.ts
export interface PageDto { id: number; slug: string; title: string; status: 'DRAFT' | 'PUBLISHED' }

export async function getPage(slug: string, signal?: AbortSignal) {
  const { data } = await apiClient.get<ApiResponse<PageDto>>(`/pages/${encodeURIComponent(slug)}`, { signal });
  return data.data;
}
```

### 8.2 TanStack Query
- 도메인별 `queries/<domain>.ts`에 queryKey와 `queryOptions`를 모은다. 이를 감싸는 훅은 만들지 않는다.
- 목록 queryKey에는 결과에 영향을 주는 값(검색어·필터·정렬·페이지·페이지 크기)을 모두 넣는다.
- `queryFn`은 `signal`을 API 함수에 넘긴다.
- 첫 로딩 실패는 화면에서 `Result`로, 백그라운드 재조회 실패는 알리지 않는다.
- mutation 실패 알림은 `MutationCache.onError`에서 한 번만. 화면에서 직접 처리하면 `meta: { skipGlobalError: true }`.
- mutation 후에는 `setQueryData`보다 `invalidateQueries`.
- **콜백 위치**: 캐시 무효화는 `useMutation` 옵션에, 메시지·화면 이동은 `mutate(값, { onSuccess })`에 둔다(언마운트되면 실행되지 않음).

```ts
export const pageQueries = {
  all: ['pages'] as const,
  detail: (slug: string) =>
    queryOptions({ queryKey: [...pageQueries.all, 'detail', slug], queryFn: ({ signal }) => getPage(slug, signal) }),
};

const { mutate } = useMutation({
  mutationFn: (input: UpdatePageRequest) => updatePage(slug, input),
  onSuccess: () => queryClient.invalidateQueries({ queryKey: pageQueries.all }),
});
const handleSave = (values: UpdatePageRequest) => mutate(values, { onSuccess: () => message.success('저장했습니다.') });
```

### 8.3 에러 처리
에러 분류와 메시지 변환은 `api/errors.ts`에서만 한다.

| 상황 | 처리 |
|---|---|
| 로그인 API 401 | 로그인 실패로 폼에 표시 |
| 사용자 정보 조회 401 | 비로그인(`anonymous`) |
| 그 외 401 | 로그인 상태이고 현재 세션의 요청일 때만 세션 만료(알림 없이 로그인으로) |
| 403 | 권한 없음 안내(로그아웃하지 않음) |
| 400 / 422 | `form.setFields`로 필드에 표시 |
| 5xx · 네트워크 · 타임아웃 | 구분된 안내 |

### 8.4 인증
- HttpOnly 쿠키 기반(`withCredentials: true`). 토큰을 브라우저 저장소에 두지 않는다. CSRF·CORS는 백엔드와 함께 정한다.
- 상태(`authStore.status`): `initializing` / `authenticated` / `anonymous` / `error`.
- **초기화**: `App.tsx`에서 `initSession()` 호출. 성공 → `authenticated`, 401 → `anonymous`, 그 외 오류 → `error`. 진행 중인 Promise를 재사용해 중복 요청을 막는다(StrictMode를 끄지 않는다).
- **`RequireAuth`**: `initializing`이면 로딩, `error`면 재시도 버튼, `anonymous`면 현재 경로를 `redirect`로 붙여 로그인으로 이동.
- **세션 전환**(`session.ts`): 로그인·로그아웃·만료 시 `cancelQueries` 후 `clear`. 화면 이동은 하지 않고 상태만 바꾼다(이동은 `RequireAuth`).
- 전환마다 `sessionId`를 올리고, `await` 후 상태를 바꾸기 전에 `sessionId`가 그대로인지 확인한다.
- 요청마다 시작 시점의 `sessionId`를 기록해, 이전 세션 요청의 401로 현재 세션을 끝내지 않는다. 이전 세션 mutation의 실패도 알리지 않는다.
- 세션이 바뀌어도 계속 마운트되는 컴포넌트(헤더 등)의 mutation은 `onMutate`에서 `sessionId`를 기록하고 콜백에서 비교한다.
- **의존 방향**: `sessionId.ts`는 의존성 없음. `session.ts`는 `api/*`·`router`를 import하지 않는다. 순환 import를 만들지 않는다.
- 로그인 후 `redirect`는 `safeRedirect()`로 검증한다. 문자열 검사가 아니라 `new URL()`로 파싱한 origin이 같을 때만 허용한다.

## 9. 코드 품질

- ESLint: `typescript-eslint`, `react-hooks`, `react-refresh`, `jsx-a11y`, `eslint-config-prettier`. 포맷은 Prettier.
- `eslint-disable` 금지. 불가피하면 해당 줄에만 사유와 함께.
- import 순서: 외부 → `@/` → `./` → `*.module.scss`.
- Prettier: `semi`, `singleQuote`, `trailingComma: "all"`, `printWidth: 100`, `tabWidth: 2`.
- scripts: `dev`, `build`(`tsc -b && vite build`), `typecheck`(`tsc -b --noEmit`), `lint`, `format`, `format:check`, `test`(`vitest run`). CI에서도 0장의 완료 명령을 실행한다.
