# WORK-BE-ADMIN-PRODUCT-READ: 관리자 상품 목록·상세 조회

상태: 검증 대기 · 작성일: 2026-10-26 · 담당: 관리자·상품 연동

## 목표와 완료 기준

- ADMIN이 판매 중지 상품을 포함한 목록을 검색·상태 필터로 조회한다.
- ADMIN이 판매 중지 상품의 상세 정보를 조회한다.
- 공개 상품 조회의 판매 중지 제외 동작은 유지한다.

## 근거와 범위

- `docs/requirements/screens.md` A-01, A-02
- `AdminProductController`, `ProductService`, `ProductRepositoryCustom`, `ProductDetailResponse`
- 상품 도메인 안에 관리자 전용 읽기 API를 추가한다.

## 구현 결과

- `GET /api/admin/products`: keyword, type, status, page, size. 상태 생략 시 ON_SALE·SOLD_OUT·STOPPED 모두 포함한다.
- `GET /api/admin/products/{productId}`: STOPPED 상품도 기존 상세 응답 형식으로 반환한다.
- 기존 공개 GET의 조회 상태는 변경하지 않았다.

## 검증

- ProductServiceTest: 통과. 관리자 상태 필터·STOPPED 상세·공개 조회 상태를 확인했다.
- `clean compileJava compileTestJava`: 통과.
- 전체 `test`: 600개 중 30개가 로컬 Docker 미연결로 초기화에 실패했다. 변경한 상품 서비스 테스트는 통과했다.

## 연결과 남은 작업

- 프론트 A-01/A-02는 새 관리자 GET 계약으로 연결해야 한다.
- OpenAPI 생성 클라이언트는 백엔드가 실행되는 환경에서 재생성해야 한다.
