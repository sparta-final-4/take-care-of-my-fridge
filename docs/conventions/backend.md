# 백엔드 개발 규칙

상태: 공유 규칙 기준안 · 출처: S1·S3 · 최초 정리: 2026-10-14

## 적용 메모

- 아래 원문은 공유된 백엔드 지침을 보존한 것입니다. 경로·명령의 기준은 backend/입니다. 패키지와 코드는 예시이며 컴파일된 구현이 아닙니다.
- [공통 지침](../../AGENTS.md), [백엔드 진입점](../../backend/AGENTS.md), [DB 규칙](database.md)을 함께 적용합니다.
- S3 추가: JPAQueryFactory·RedisTemplate·KafkaTemplate은 등록된 빈을 주입하고 중복 생성하지 않습니다.
- S3는 같은 클래스명 충돌 시 FQCN 예외를 허용하지만 S1은 일괄 금지입니다. 와일드카드 import도 금지/지양이 다릅니다. 합의 전 금지 기준을 유지하고 충돌 상황을 보고합니다.
- Lombok 허용 목록과 Builder 사용 규칙이 다릅니다. 합의 전 명시적 생성자·정적 팩토리를 사용하면 충돌을 피할 수 있습니다.
- 단일 Config가 전체 설정 한 클래스인지, 기술별 중복 방지인지 Q-17에서 확정합니다.
- 감사 시각의 JPA/DB 관리 주체는 Q-06입니다. 중복 자동 갱신을 임의로 구성하지 않습니다.
- S3의 BusinessException은 ErrorCode 보유 및 하위 예외를 허용합니다. 공통 처리기의 ErrorCode 접근 방식까지 구현 시 확인합니다.
- 필드·메시지 반환 요구는 API 필드 오류 구조(Q-10)로 구체화합니다.
- Gradle build --dry-run은 작업 계획 확인이며 의존성 해석·컴파일 성공의 증거가 아닙니다.
- AFTER_COMMIT과 메시지 전달 보장은 별도 검토합니다. [시스템 설계](../architecture/system-design.md)에 재시도·복구 요구를 기록합니다.
- 원문의 내용은 바꾸지 않고, 제목·목록·코드 블록 등 Markdown 표시 형식만 정리했습니다.

---

## 공유된 백엔드 규칙 원문

backend/ 개발 규칙. 이 문서와 기존 코드의 패턴을 따른다. 충돌하면 이 문서가 우선이며 그 사실을 보고한다.

### 0. 작업 절차

**시작 전**

1. API 변경 또는 추가 작업 시, `domain/{도메인}/dto` 및 `global/response` 구조를 확인한다.
2. 프론트엔드 연동 작업이면 먼저 `frontend/src/api/<domain>.ts`의 타입과 맞는지 확인한다. 다르면 백엔드 DTO 기준으로 싱크를 맞추거나 변경 사항을 기록한다.
3. Gradle 의존성 변경이 있거나 최초 세팅 시에만 `./gradlew build --dry-run` 또는 의존성 갱신을 확인한다.

**완료 전** (모두 통과해야 완료)

```sh
./gradlew clean compileJava compileTestJava
./gradlew test
```

- 컴파일 에러, 미사용 import, 테스트 실패가 없어야 완료로 인정한다.
- 작업 보고: 변경 파일 목록, 신규/수정된 API 명세(Request/Response DTO), 신규 ErrorCode, 프론트엔드에 전달해야 할 변경 사항, 검증하지 못한 항목.

### 1. 기술 스택

Java 17 · Spring Boot 3.x · Gradle · Spring Data JPA · QueryDSL 5.x · Redis & Redisson · Apache Kafka · Spring Security · JUnit 5 + Mockito

- Java 17 최신 문법(record, pattern matching, switch expressions)을 적극 사용한다.
- 롬복은 최소화한다: `@Getter`, `@RequiredArgsConstructor`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)`만 허용.
- 금지: `@Setter`, `@Data`, `@AllArgsConstructor`, 필드 인젝션(`@Autowired`), FQCN(코드 본문 풀 패키지 경로 작성), 와일드카드 import(`import java.util.*`).
- 새 라이브러리 추가는 사용자 확인 후 `build.gradle`에 반영한다.

### 2. 기본 원칙 (Core Principles)

- 도메인 중심 패키지 구조: 계층형(Layered) 분리가 아닌 비즈니스 도메인 단위(`domain/{domain_name}`)로 격리한다.
- 불변성과 안정성: DTO 및 이벤트 메시지는 Java record를 사용하고, 엔티티는 `@Setter` 없이 도메인 메서드로만 상태를 변경한다.
- 공통 응답 및 에러 표준화: 모든 HTTP API 응답은 `ApiResponse<T>`, 모든 비즈니스 예외는 ErrorCode를 품은 BusinessException으로 통일한다.
- 관심사 분리 및 트랜잭션 경계: 다중 도메인 조율 및 분산 락 제어는 facade 계층을 통하고, 외부 I/O 및 비동기 작업은 트랜잭션 커밋 후(AFTER_COMMIT) 이벤트로 분리한다.

### 3. 구조

```text
org.example.{project}
├── domain/{domain_name}
│   ├── controller       # REST API 엔드포인트
│   ├── service          # 단일 도메인 비즈니스 로직 및 트랜잭션 단위
│   ├── facade           # 다중 서비스 오케스트레이션 / 분산 락 / 트랜잭션 경계 분리
│   ├── repository       # Spring Data JPA 및 QueryDSL Custom 구현체
│   ├── entity           # JPA Entity
│   ├── dto              # Request / Response records
│   └── event            # 이벤트 발행(Producer), 리스너(Listener), 컨슈머(Consumer)
├── infra                # 외부 연동 모듈 (PG, SMS, 외부 메시징 등)
└── global
    ├── config           # Security, Redis, Kafka, QueryDSL, Jackson, Auditing 설정
    ├── error            # ErrorCode, BusinessException, GlobalExceptionHandler
    ├── response         # ApiResponse, PageResponse
    ├── filter           # JWT 인증/인가 필터
    └── entity           # BaseTimeEntity (createdAt, updatedAt 등 공통 엔티티)
```

- 모든 인프라성 설정(JPA Auditing, QueryDSL, Redis, Redisson, Kafka 등)은 `global/config/`의 단일 Config 클래스로 관리한다. 도메인 패키지 내부에서 `@Configuration`을 중복 선언하지 않는다.

### 4. 레이어별 구현 규칙

#### 4.1 Controller

- 반환 타입은 항상 `ResponseEntity<ApiResponse<T>>` 형태를 유지한다. 원시값, 단순 DTO, Entity 직접 반환 금지.
- 의존성 주입은 `@RequiredArgsConstructor` + `private final`을 사용한다.
- 입력값 검증은 `@Valid`를 필수로 적용하고, 요청 파라미터는 Request record로 수신한다.

#### 4.2 Service & Transaction

- 클래스 상단에 `@Transactional(readOnly = true)`를 기본 선언하고, CUD 메서드에만 `@Transactional`을 명시한다.
- 더티 체킹(Dirty Checking): 엔티티 수정 시 `repository.save()`를 명시적으로 호출하지 않고, 영속 상태 엔티티의 비즈니스 메서드를 호출한다.
- 외부 API 통신이나 카프카 메시지 발행은 DB 트랜잭션과 묶지 않으며, `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`를 활용한다.

#### 4.3 Entity

- 클래스 선언: `@Entity`, `@Table(name = "...")`, `@Getter`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)`.
- 기본키(PK): `@Id`, `@GeneratedValue(strategy = GenerationType.IDENTITY)`.
- 공통 감사 필드: BaseTimeEntity를 상속해 createdAt, updatedAt을 관리한다.
- 연관관계: 모든 연관관계 매핑(`@ManyToOne`, `@OneToOne` 등)은 `fetch = FetchType.LAZY` 필수.
- 생성자/빌더: 생성자와 `@Builder`는 private 또는 protected로 제한하고, 정적 팩토리 메서드(`create(...)`, `of(...)`)로만 엔티티를 생성한다.
- `@Setter` 절대 금지. 상태 전이 및 필드 변경은 도메인 행위 중심의 엔티티 메서드로 캡슐화한다.

#### 4.4 DTO (Data Transfer Object)

- 모든 DTO는 불변 객체인 Java record로 작성한다 (Lombok 사용 금지).
- Request -> Entity: Request DTO 내부에 변환 로직을 두지 않고, Service에서 파라미터를 꺼내 `Entity.create(...)` 팩토리 메서드를 호출한다.
- Entity -> Response: Response record 내부에 `public static Response from(Entity entity)` 정적 팩토리 메서드를 구현해 DTO가 조립 책임을 가진다.
- 유효성 검증: Request 필드에 `@NotBlank`, `@NotNull`, `@Min` 등을 선언하고 상세한 message를 작성한다.
- 날짜/시간 필드는 ISO-8601 문자열 기준 직렬화를 기본으로 하며, 필요 시 `@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")`를 명시한다.

#### 4.5 Repository & QueryDSL

- 기본 CRUD는 `JpaRepository<Entity, Long>`을 상속한다.
- 동적 쿼리, 복잡한 조인은 `{Domain}RepositoryCustom` 인터페이스와 `{Domain}RepositoryImpl` 구현체로 분리하여 JPAQueryFactory로 구현한다.
- N+1 문제 방지를 위해 연관 엔티티 조회 시 `join().fetchJoin()`을 적극 사용한다.
- 대량 배치 수정/삭제는 건당 쿼리 방지를 위해 Bulk 연산(`execute()` 또는 `@Modifying`)을 적용하고 영속성 컨텍스트 초기화를 처리한다.

#### 4.6 Redis & Redisson 분산 락

- Key 네이밍: 콜론(`:`) 계층형 구조 통일 (`{도메인}:{목적}:{식별자}`).
- TTL 필수: 캐시 및 임시 데이터 저장 시 만료 시간(TTL)을 반드시 지정한다.
- Redisson 분산 락 원칙: 트랜잭션과 락의 생명주기 충돌을 방지하기 위해 락 획득/해제는 반드시 Facade 레이어에서 수행한다.
  - 구조: Facade(Lock 획득) -> Service(`@Transactional` 비즈니스 로직 실행) -> Facade(Lock 해제)

#### 4.7 Kafka 이벤트

- 토픽 네이밍: 케밥 케이스(`-`) 및 행위 중심 (`order-created`, `payment-completed`).
- Payload: 불변 Java record로 정의하며, 식별자(ID)와 상태 변경 추적에 필요한 최소 필드만 담는다.
- 컨슈머 멱등성(Idempotency): 네트워크 재전송에 대비하여 동일한 메시지가 2회 이상 유입되어도 시스템 상태가 왜곡되지 않도록 중복 처리 검증 로직을 구현한다.

### 5. API 응답 및 에러 표준

#### 5.1 응답 규격 (`ApiResponse<T>` & `PageResponse<T>`)

```java
// global/response/ApiResponse.java
public record ApiResponse<T>(
    boolean success,
    T data,
    ErrorResponse error
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(true, null, null);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, null, new ErrorResponse(code, message));
    }
}

// global/response/ErrorResponse.java
public record ErrorResponse(
    String code,
    String message
) {}

// global/response/PageResponse.java (antd Table 1-based index 호환)
public record PageResponse<T>(
    List<T> content,
    int pageNumber,
    int pageSize,
    long totalElements,
    int totalPages,
    boolean isLast
) {
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
            page.getContent(),
            page.getNumber() + 1,
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.isLast()
        );
    }
}
```

#### 5.2 Controller 반환 패턴

```java
// 단건 조회
@GetMapping("/{id}")
public ResponseEntity<ApiResponse<ProductResponse>> getProduct(@PathVariable Long id) {
    ProductResponse response = productService.getProduct(id);
    return ResponseEntity.ok(ApiResponse.ok(response));
}

// 반환 데이터가 없는 CUD
@DeleteMapping("/{id}")
public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable Long id) {
    productService.deleteProduct(id);
    return ResponseEntity.ok(ApiResponse.ok());
}

// 페이징 목록 조회
@GetMapping
public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> getProducts(Pageable pageable) {
    PageResponse<ProductResponse> response = productService.getProducts(pageable);
    return ResponseEntity.ok(ApiResponse.ok(response));
}
```

#### 5.3 예외 처리 아키텍처

- 비즈니스 예외는 ErrorCode enum을 가진 BusinessException을 throw한다.
- Controller에서 `ApiResponse.error(...)`를 직접 호출하지 않는다.
- `@RestControllerAdvice`(GlobalExceptionHandler)에서 에러를 일괄 가로채 처리한다:
  - BusinessException: `errorCode.getHttpStatus()`에 맞춰 `ApiResponse.error(code, message)` 응답.
  - MethodArgumentNotValidException: 400 Bad Request와 함께 `@NotBlank` 등의 유효성 검증 실패 메시지를 추출해 응답.
  - 미처리 Exception: 500 에러 코드(S001)로 마스킹하고, 서버 콘솔/로그에만 상세 `log.error` 스택트레이스를 남긴다.

```java
// global/error/ErrorCode.java 예시
@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "적절하지 않은 요청 값입니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "C002", "해당 리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "S001", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
```
