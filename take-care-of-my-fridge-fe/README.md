# take-care-of-my-fridge-fe

냉장고를 부탁해 프론트엔드. 개발 규칙은 [CLAUDE.md](CLAUDE.md)를 따릅니다.

## 요구 사항

- Node 24.10 이상(`.node-version`), pnpm 10.26.2(`packageManager`)
- `pnpm oag:generate*` 실행 시 Java 11 이상(openapi-generator가 JAR로 실행됨)

## 시작

```bash
pnpm install --frozen-lockfile
cp .env.example .env
pnpm dev
```

| 환경               | `VITE_API_BASE_URL` | 파일              |
| ------------------ | ------------------- | ----------------- |
| 로컬(`pnpm dev`)   | (빈 값)             | `.env`            |
| 운영(`pnpm build`) | (빈 값)             | `.env.production` |

로컬에서는 Vite proxy가 `/api` 요청을 `VITE_PROXY_TARGET`(기본 `http://localhost:8080`)으로, 운영에서는 CloudFront가 백엔드로 넘깁니다(dev.imhungry.click → api-dev, imhungry.click → api). 브라우저 입장에서 항상 같은 출처라 CORS 설정이 필요 없고, CSRF 쿠키도 axios가 자동으로 헤더에 붙입니다.

## 새 페이지 추가

`paths.ts`에 경로 추가 → `pages/`에 페이지 생성 → `router.tsx`에 등록, 세 단계입니다.

**1. 경로 추가: `src/app/paths.ts`.** 경로 문자열은 이 파일에만 둡니다.

```ts
export const ROUTES = {
  home: '/',
  fridge: '/fridge',
  recipeDetail: '/recipes/:recipeId', // 파라미터 경로
} as const;
```

**2. 페이지 만들기: `src/pages/XxxPage.tsx`.** 페이지는 지연 로딩하므로 `default export`합니다. 스타일은 같은 폴더의 `XxxPage.module.scss`에 둡니다.

```tsx
import { Typography } from 'antd';
import { useParams } from 'react-router';

export default function RecipeDetailPage() {
  const { recipeId } = useParams();

  return <Typography.Title level={2}>레시피 {recipeId}</Typography.Title>;
}
```

페이지는 섹션을 배치만 합니다. 실제 기능(재료 입력, 레시피 목록 등)은 `src/domains/<도메인>/components/`에 만들어 가져다 씁니다.

**3. 라우터 등록: `src/app/router.tsx`.** 헤더가 있는 화면은 `MainLayout`의 `children`에 추가합니다.

```tsx
children: [
  { path: ROUTES.home, lazy: page(() => import('@/pages/MainPage')) },
  { path: ROUTES.fridge, lazy: page(() => import('@/pages/FridgePage')) },
  { path: ROUTES.recipeDetail, lazy: page(() => import('@/pages/RecipeDetailPage')) },
],
```

- 레이아웃 없는 화면(예: 로그인)은 `MainLayout` 블록 밖, `'*'` 항목 위에 둡니다.
- `'*'`(404)는 항상 마지막입니다.
- 로그인이 필요한 화면은 `RequireAuth` 아래에 둘 예정입니다. 인증 정책이 정해지기 전이라 아직 없으므로 지금은 일반 페이지로 추가합니다.

**링크·이동.** 경로는 `ROUTES`로 쓰고, 파라미터 경로는 `generatePath`로 만듭니다. import는 `react-router`에서 합니다(`react-router-dom`은 ESLint 오류).

```tsx
import { generatePath, Link, useNavigate } from 'react-router';

import { ROUTES } from '@/app/paths';

<Link to={ROUTES.fridge}>내 냉장고</Link>;

const navigate = useNavigate();
navigate(generatePath(ROUTES.recipeDetail, { recipeId: String(id) }));
```

추가한 뒤에는 아래 [검증](#검증) 명령을 실행하고, 화면을 375 / 768 / 1440px 너비에서 확인합니다.

## API 클라이언트 생성

`@openapitools/openapi-generator-cli`(typescript-axios)로 `src/common/api/generated/`에 생성합니다. 생성 결과는 스펙(`openapi/fridge-api.json`)과 함께 커밋하고 직접 수정하지 않습니다. 둘 다 dev 백엔드 코드와 같아야 합니다.

| 명령                        | 동작                                                                | 누가                     |
| --------------------------- | ------------------------------------------------------------------- | ------------------------ |
| `pnpm oag:generate`         | 실행 중인 백엔드(`OPENAPI_SPEC_URL`)에서 스펙을 받아 갱신한 뒤 생성 | 백엔드 API를 바꾼 사람   |
| `pnpm oag:generate:offline` | 커밋된 스펙으로 generated만 다시 생성                               | 충돌 해결·실험 되돌릴 때 |

### 협업 절차

- **API를 바꿀 때**: 최신 dev를 머지한 상태에서 백엔드를 띄우고 `pnpm oag:generate`를 실행한 뒤, 백엔드 코드·`openapi/`·`src/common/api/generated/`를 같은 PR에 커밋합니다.
- **API를 쓰기만 할 때**: dev를 머지하면 끝입니다. 생성할 필요가 없습니다.
- **커밋 전 실험**: 로컬 백엔드로 `pnpm oag:generate`를 실행해 붙여 봐도 됩니다. 버릴 때는 `git checkout -- openapi` 후 `pnpm oag:generate:offline`으로 되돌립니다.
- **충돌**: 스펙·generated를 손으로 고치지 않습니다. 백엔드 충돌을 해결하고 `pnpm oag:generate`로 다시 만들어 커밋합니다. 스펙은 깔끔히 합쳐졌고 generated만 충돌했다면 `pnpm oag:generate:offline`이면 됩니다.

스펙 키는 백엔드 `springdoc.writer-with-order-by-keys`로 이름순 정렬돼, 서로 다른 API를 추가한 PR은 대부분 git이 자동으로 합칩니다. 커밋된 스펙·generated가 백엔드와 다르면 `openapi-drift.yml`이 PR에 빨간 X로 경고합니다. 필수 체크가 아니라 머지를 막지는 않으니, 뜨면 oag를 최신 백엔드로 돌렸는지 확인하세요.

`VITE_API_BASE_URL`은 서버 주소만 갖고, `/api` prefix는 백엔드 경로(스펙)에 들어 있습니다. 생성된 API 클래스는 `basePath`와 `axiosInstance`를 넘겨 만듭니다. 로컬은 `VITE_API_BASE_URL`이 빈 값이라, `basePath`를 넘기지 않으면 생성 코드가 `http://localhost`로 요청합니다.

```ts
new FridgeControllerApi(undefined, import.meta.env.VITE_API_BASE_URL, axiosInstance);
```

`openapi/templates/baseApi.mustache`는 `erasableSyntaxOnly`와 맞추기 위해 생성기 7.24.0 원본을 수정한 것이므로, `openapitools.json`의 생성기 버전을 올리면 원본과 다시 비교합니다.

## 검증

```bash
pnpm lint && pnpm format:check && pnpm typecheck && pnpm build
pnpm test
```
