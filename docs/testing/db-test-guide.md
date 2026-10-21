# DB 테스트 작성 지침

상태: 제안 · 작성일: 2026-10-21 · 팀 검토 전

실제 MySQL이 필요한 백엔드 테스트를 사람이나 AI가 새로 쓸 때 따르는 기준입니다. 무엇을 검증할지는 [테스트 전략](test-strategy.md)이 정하고, 이 문서는 DB 테스트를 어떻게 구성하는지만 다룹니다.

## 언제 MySQL 테스트를 쓰는가

| 검증 대상 | 테스트 형태 |
|---|---|
| 정책·계산, 협력 객체 호출 | 단위 테스트(Mockito). DB를 띄우지 않습니다. |
| 요청 검증·응답 형식·권한 | `@WebMvcTest`. 서비스는 대역으로 둡니다. |
| 쿼리(QueryDSL·네이티브), DB 제약, 트랜잭션 경계, 동시성 | `@DataJpaTest` + `MySqlContainerSupport` 상속 |

H2 같은 내장 DB로 바꾸지 않습니다. 락·인덱스·네이티브 쿼리가 MySQL과 다르게 동작할 수 있습니다.

## 구성

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
class CartItemRepositoryTest extends MySqlContainerSupport {
```

- `support/MySqlContainerSupport`를 상속합니다. MySQL 컨테이너는 JVM에서 한 번만 뜨고, 모든 DB 테스트가 함께 씁니다.
- 테스트 클래스에 `@Testcontainers`, `@Container`, `new MySQLContainer(...)`를 직접 선언하지 않습니다. 클래스마다 컨테이너가 새로 뜨면서 CI 시간이 늘어납니다.
- 베이스 클래스에도 `@Testcontainers`/`@Container`를 붙이지 않습니다. JUnit 확장이 테스트 클래스가 끝날 때마다 컨테이너를 멈춥니다. 그러면 캐시된 Spring 컨텍스트가 멈춘 컨테이너를 가리키게 되어, 다음 클래스에서 연결이 실패합니다.
- 실행에는 Docker가 필요합니다. Docker가 없으면 DB 테스트는 건너뛰지 않고 실패합니다.

## 데이터 격리

컨테이너와 스키마를 모든 DB 테스트가 공유하므로, 앞 클래스가 남긴 데이터가 뒤 클래스에 보일 수 있습니다.

- 기본 `@DataJpaTest`는 테스트마다 롤백되므로 별도 정리가 필요 없습니다.
- 서비스의 트랜잭션 경계나 동시성을 확인하려고 `@Transactional(propagation = Propagation.NOT_SUPPORTED)`로 실제 커밋하는 테스트는 `@AfterEach`에서 만든 데이터를 모두 지웁니다. 자식 테이블부터 지웁니다. 예: `orderItemRepository.deleteAll()` → `orderRepository.deleteAll()`.
- `count()`나 `findAll()`로 전체 개수를 검증하는 테스트는 위 정리가 되어 있다는 전제에서만 씁니다.
- 자동 증가 ID 값(1, 2 …)을 기대값으로 쓰지 않습니다. 다른 클래스가 먼저 행을 넣었다 지웠으면 번호가 이어집니다. 저장한 엔티티의 `getId()`를 씁니다.

## 컨텍스트 재사용

Spring은 설정이 같은 테스트끼리 컨텍스트를 재사용합니다. `@Import` 목록이나 `@MockitoBean` 구성이 다르면 컨텍스트를 새로 만듭니다. 테스트에 필요한 빈만 `@Import`하는 지금 방식은 유지하되, 같은 대상을 검증하는 클래스끼리는 구성을 맞추면 재사용됩니다.

## 확인 방법

DB 테스트를 추가·수정하면 클래스 실행 순서를 바꿔도 통과하는지 한 번 확인합니다. 커밋하지 않는 `src/test/resources/junit-platform.properties`에 아래 한 줄을 넣고 `./gradlew test`를 몇 번 돌린 뒤 파일을 지웁니다.

```properties
junit.jupiter.testclass.order.default=org.junit.jupiter.api.ClassOrderer$Random
```

## 미정

- 이 문서를 백엔드 `CLAUDE.md`와 `docs/conventions/backend.md`에서 가리킬지는 팀 합의 뒤 정합니다.
