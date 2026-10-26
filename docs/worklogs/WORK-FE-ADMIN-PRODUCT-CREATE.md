# WORK-FE-ADMIN-PRODUCT-CREATE: 관리자 상품 등록

상태: 검증 대기 · 작성일: 2026-10-26 · 담당: 프론트엔드

## 목표와 완료 기준

- ADMIN이 재료·밀키트 상품을 현재 백엔드 계약으로 등록한다.
- 상품 종류별 상세와 가격·재고·보관 값을 검증하고 등록 성공을 안내한다.

## 근거와 범위

- `docs/requirements/screens.md` A-03, `docs/requirements/project-plan.md` Q-04·Q-12
- 백엔드 `AdminProductController`, `ProductCreateRequest`
- 사용자 결정: 파일 업로드·유통기한 대신 현재 API의 이미지 URL·권장 보관 일수를 사용한다.

## 구현 결과

- `/admin/products/new`를 ADMIN 경로와 메뉴에 추가했다.
- 생성 API에 연결하고 재료·밀키트별 필수 입력과 API 범위를 검증한다.
- 재료 목록 API가 없어 기존 재료 ID는 숫자로 입력한다.

## 검증

- TypeScript, ESLint, Vite 빌드, Vitest 35개: 통과.
- 변경 파일 Prettier 검사와 `git diff --check`: 통과.
- 전체 Prettier 검사는 변경 범위 밖 기존 파일 79개의 포맷 차이로 실패한다.
- 관리자 세션에서 실제 등록 API 왕복과 화면 크기별 브라우저 표시는 미검증.

## 연결과 남은 작업

- 관리자 목록·상세 GET이 없어 A-01/A-02는 별도 백엔드 API가 필요하다. 공개 GET은 판매 중지 상품을 제외한다.
- 관리자 세션 실제 등록과 화면 크기별 브라우저 검증은 별도 확인이 필요하다.
