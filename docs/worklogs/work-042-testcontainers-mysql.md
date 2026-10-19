# WORK-042: 테스트 DB를 Testcontainers MySQL로 통일

상태: 검증 완료 · 작성일: 2026-10-19 · 담당: 장바구니

## 목표와 완료 기준

- 리포지토리 쿼리를 실제 MySQL에서 돌려 DB 상태로 검증할 수 있게 한다.
- 장바구니 조회·담기 쿼리의 회원 간 격리를 자동 테스트로 확인한다.

## 근거와 범위

- 선행 작업: WORK-041 장바구니 조회.
- 기존 테스트는 서비스 계층에서 리포지토리를 mock으로 두어, 파생 쿼리가 실제로 맞는지와 다른 회원의 줄이 섞이지 않는지 검증하지 못한다.
- 테스트 DB는 CI와 같은 `mysql:8.4` 컨테이너로 한다. H2는 MySQL과 방언이 달라 이후 락·동시성 테스트에서 결과가 어긋날 수 있다. 로컬에 떠 있는 개발 DB를 직접 쓰면 테스트가 개발 데이터를 건드린다.
- 범위는 의존성과 장바구니 리포지토리 테스트 하나로 한정한다.

## 구현 결과

- `build.gradle`: `spring-boot-testcontainers`, `testcontainers-junit-jupiter`, `testcontainers-mysql`을 `testImplementation`으로 추가했다. Testcontainers 버전(2.0.5)은 Spring Boot가 관리한다.
- `CartItemRepositoryTest`: `@DataJpaTest`에 `@AutoConfigureTestDatabase(replace = NONE)`와 `@ServiceConnection` MySQL 컨테이너를 붙였다. 컨테이너 접속 정보는 `@ServiceConnection`이 연결한다.
- `@DataJpaTest`는 `@EnableJpaAuditing`이 있는 `JpaAuditingConfig`를 읽지 않아 `created_at`이 비어 저장에 실패한다. 테스트에 `@Import(JpaAuditingConfig.class)`를 붙여 해결했다.
- 테스트마다 저장 뒤 `flush`와 `clear`로 영속성 컨텍스트를 비우고 DB에서 다시 읽는다.
- 테스트 4개:
  - `findByCartMemberIdOrderByIdAsc`: 회원 A의 줄이 있을 때 회원 B는 빈 목록, A는 자기 줄만 받는다.
  - `findByCartMemberIdOrderByIdAsc`: 장바구니가 없는 회원은 빈 목록이다.
  - `findByCartMemberIdOrderByIdAsc`: 담은 순서(id 오름차순)로 읽는다.
  - `findByCartAndProductId`: 같은 상품이 다른 회원의 장바구니에 있어도 내 장바구니의 줄만 찾는다.
- 운영 코드, 신규 오류 코드, API 변경은 없다.

## 검증

| 명령·시나리오 | 실행 환경·조건 | 통과/실패/미실행 | 근거 |
|---|---|---|---|
| `./gradlew test` | 로컬(맥), Docker 실행 중, `JWT_SECRET`은 32바이트 이상 임시값 | 통과 | 전체 테스트 통과, 실패 없음. `CartItemRepositoryTest` 포함 |

## 연결과 남은 작업

- 이 테스트를 돌리려면 로컬에 Docker가 필요하다. Docker 없이 `./gradlew test`를 하면 `CartItemRepositoryTest`가 실패한다.
- WORK-041의 Postman 확인(다른 회원으로 조회하면 빈 목록)은 격리와 원래 비어 있음을 구분하지 못했다. 이 작업의 첫 테스트가 A의 줄이 있는 상태에서 B가 조회하는 경우로 보완한다.
- 수량 변경, 삭제 API의 "내 장바구니의 줄인지" 소유 확인 테스트도 이 환경에서 DB 상태로 확인한다.
