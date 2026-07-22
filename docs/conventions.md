# 읽어볼까 백엔드 구현 컨벤션

이 문서는 Java/Spring 구현에서 반복되는 패키지 배치, 이름, 계층 책임을 정합니다. 제품 정책이나
도메인 용어를 새로 결정하는 문서가 아니며, 아래 정본과 충돌하면 임의로 맞추지 말고 사용자에게
어느 문서를 갱신할지 확인합니다.

## 문서 라우팅

| 확인하려는 내용 | 정본 |
| --- | --- |
| 에이전트의 공통 행동과 작업 경계 | [`AGENTS.md`](../AGENTS.md) |
| 독자·열람 확정·포인트 등 도메인 용어 | [`CONTEXT.md`](../CONTEXT.md) |
| MVP 범위와 기능 요구사항 | [`docs/prd.md`](./prd.md) |
| 핵심 불변식과 필수 검증 시나리오 | [`docs/test-strategy.md`](./test-strategy.md) |
| 되돌리기 비싼 기술·구조 결정과 근거 | [`docs/adr/`](./adr/) |
| 환경별 DB 연결과 민감정보 주입 | [`docs/deployment.md`](./deployment.md) |
| Java/Spring 구현 방식 | 이 문서 |

## 패키지 구조

- 기본 패키지는 `com.example.ilgeobolkka`입니다.
- 기능은 기술 계층 전체를 한곳에 모으지 않고 `reading`, `point`, `book`처럼 도메인별로 묶습니다.
- 한 도메인 안에서는 필요한 계층만 `controller`, `facade`, `service`, `repository`, `dto`, `entity`로
  나눕니다. 도메인의 API를 구현할 때 Facade를 함께 만들되, 아직 구현하지 않는 도메인의 빈 패키지는
  미리 만들지 않습니다.
- 설정·예외 처리·보안처럼 실제로 여러 도메인이 함께 쓰는 코드만 `global`에 둡니다. 한 도메인에서만
  쓰는 코드를 재사용 가능성만으로 `global`에 올리지 않습니다.
- 파일 저장소나 외부 API 같은 외부 시스템 연동은 `infra`에 둡니다. 연동 대상이 정해지기 전에는
  인터페이스나 어댑터를 미리 만들지 않습니다.

예를 들어 열람 확정 API를 구현한다면 다음 범위에서 시작합니다.

```text
com.example.ilgeobolkka
└── reading
    ├── controller
    ├── facade
    ├── service
    ├── repository
    ├── dto
    └── entity
```

도메인 우선 패키지와 Facade 경계를 선택한 근거는
[`ADR-0006`](./adr/0006-organize-backend-packages-by-domain.md)에 기록합니다.

## 이름

| 대상 | 규칙 | 읽어볼까 예시 |
| --- | --- | --- |
| Controller | `{Domain}Controller` | `ReadingController` |
| Facade | `{Domain}Facade` | `ReadingFacade` |
| Service | `{Domain}Service` | `PointService` |
| Repository | `{Entity}Repository` | `ConfirmedPageRepository` |
| Entity | 도메인 명사 단수형 | `ReadingSession`, `PointLedger` |
| Enum | `{Domain}Status`, `{Domain}Type` | `ReadingSessionStatus` |
| Request DTO | `{Action}{Domain}Request` | `ConfirmReadingRequest` |
| Response DTO | `{Action}{Domain}Response` | `ConfirmReadingResponse` |
| Exception | 실패한 규칙이 드러나는 이름 | `InsufficientPointException` |

- 메서드는 `confirmReading()`, `deductPoint()`, `findBooks()`처럼 행위와 대상을 드러냅니다.
  `process()`, `handle()`, `updateData()`, `check()`처럼 문맥 없이는 의미를 알 수 없는 이름은 피합니다.
- 변수는 `readerId`, `currentPointBalance`처럼 도메인 용어를 그대로 쓰고, `uid`, `amt` 같은 임의
  축약어를 만들지 않습니다.
- 구매·상품처럼 [`CONTEXT.md`](../CONTEXT.md)의 `_Avoid_`에 있는 표현을 코드 이름에도 쓰지 않습니다.

## Controller

Controller는 HTTP 요청과 응답의 경계만 담당합니다.

- Request DTO 검증, 인증된 독자 식별, 같은 도메인의 Facade 호출, Response DTO 변환까지만 수행합니다.
- 비즈니스 규칙, 트랜잭션, Entity 직접 조립, Repository 접근, 외부 시스템 호출을 두지 않습니다.
- Entity를 응답으로 직접 반환하지 않습니다.
- 의존성은 `private final` 필드와 생성자로 주입합니다. Lombok을 사용한다면
  `@RequiredArgsConstructor`까지만 사용하고 `@Autowired` 필드 주입은 사용하지 않습니다.

## Facade와 트랜잭션

- 도메인마다 `{Domain}Facade`를 두고 Controller가 호출하는 유스케이스 진입점으로 사용합니다.
- public 메서드는 `confirmReading(readerId, request)`처럼 하나의 유스케이스를 표현합니다.
- 상태 변경 트랜잭션은 Facade에서 시작하고 `@Transactional`을 사용합니다. 조회 전용 유스케이스는
  `@Transactional(readOnly = true)`로 의도를 드러냅니다.
- 포인트 차감, 내역 생성, 열람 확정, 마지막 열람 위치 갱신은
  [`INV-003`](./test-strategy.md#inv-003-차감과-열람-확정의-원자성)에 따라 하나의 트랜잭션에서 처리합니다.
- Facade는 필요한 도메인 Service의 public 메서드를 조합하지만 Repository를 직접 참조하거나 도메인
  규칙을 구현하지 않습니다.
- Facade끼리는 호출하지 않습니다. 다른 도메인의 행위가 필요하면 해당 도메인 Service의 public
  메서드를 호출해 흐름과 트랜잭션의 주인이 하나만 남게 합니다.
- Facade는 Spring `@Service`와 생성자 주입을 사용합니다.

## Service

- Service는 자기 도메인의 규칙을 수행하고 자기 도메인의 Repository만 직접 참조합니다.
- 다른 도메인의 Service나 Facade를 호출하지 않습니다. 도메인 횡단 조합은 호출한 Facade가 담당합니다.
- Service 메서드는 Facade가 시작한 트랜잭션 안에서 실행합니다. 독립적인 트랜잭션을 새로 열어 원자적
  유스케이스를 분리하지 않습니다.
- 외부 API나 파일 저장소 호출은 별도 Client로 분리하고, 긴 DB 트랜잭션 안에서 호출하지 않습니다.
- 즉시 반영이 계약상 필요한 경우가 아니라면 `saveAndFlush()`를 반복하지 않고 JPA 더티 체킹을
  사용합니다.

## Repository와 스키마

- Repository는 Spring Data JPA 인터페이스로 두고 조회·저장 책임만 맡깁니다. 비즈니스 규칙을
  Repository 메서드 안에 숨기지 않습니다.
- 사용자·도서·페이지별 최초 1회 차감처럼 중복이 허용되지 않는 규칙은 애플리케이션 검사만이 아니라
  MySQL 고유 제약으로도 보장합니다.
- 테이블, 컬럼, 인덱스, 제약조건을 바꾸는 작업에는 Flyway migration을 함께 추가합니다. 이미 적용된
  migration은 수정하지 않고 다음 번호의 migration으로 보정합니다.
- JPA Entity 변경만으로 운영 스키마 변경을 대신하지 않습니다.

## Entity와 DTO

- Entity와 API Request/Response DTO를 분리합니다.
- Entity에는 넓은 범위의 setter 대신 `confirm()`, `deduct()`처럼 유효한 상태 변경만 허용하는 메서드를
  둡니다.
- Lombok의 `@Data`, 클래스 전체 `@Setter`, Entity의 public 전체 필드 생성자는 사용하지 않습니다.
- Controller용 DTO를 Service 내부 `record`로 숨기지 않고 용도별 파일로 분리합니다. 한 계층에서만 쓰는
  타입은 필요해질 때 그 계층 패키지에 둡니다.

## 검증과 오류 응답

- 문자열 형식, 빈 값, 수량 범위 같은 입력 형식은 Request DTO의 Bean Validation과 `@Valid`로 검증합니다.
- 잔액 부족, 중복 차감 금지, 열람 시간 같은 도메인 규칙은 Service와 Entity에서 검증합니다.
- 동시 요청에서 반드시 지켜야 하는 불변식은 DB 제약과 트랜잭션으로 다시 보장합니다.
- `@Valid`나 도메인 메서드가 이미 검증한 조건을 Controller의 `try-catch`로 반복하지 않습니다.
- 도메인 예외는 실패한 규칙이 이름에 드러나게 하고 `@RestControllerAdvice`에서 HTTP 오류로 변환합니다.
- 공통 성공 응답 래퍼는 API 계약이 정해지기 전에 만들지 않습니다. HTTP 상태 코드와 오류 형식은
  이후 API 계약에서 먼저 합의합니다.

## 설정, 시간, 로그

- 접속 주소, 자격 증명처럼 환경에 따라 달라지는 값은 `application.yaml`과 환경변수로 주입하고
  [`AGENTS.md`](../AGENTS.md)의 민감정보 정책을 따릅니다.
- 페이지 단가 50P와 열람 확정 6초는 단순 운영 설정이 아니라 제품·도메인 계약입니다. 의미 있는 이름으로
  표현하고 값을 바꿀 때 PRD, 테스트 전략, 관련 ADR을 함께 검토합니다.
- 애플리케이션과 DB의 저장 시각은 UTC로 통일합니다. 만료와 경과 시간은 `Instant`와 `Duration`을
  우선 사용합니다.
- 토큰, 비밀번호, DB 접속 정보와 같은 민감값은 로그나 오류 응답에 남기지 않습니다.

## 테스트

- 테스트 범위와 실행 명령은 `test` 스킬, 필수 시나리오는
  [`docs/test-strategy.md`](./test-strategy.md)를 따릅니다.
- 테스트 메서드는 `같은_페이지를_재열람하면_포인트를_차감하지_않는다()`처럼 조건과 결과가 드러나는
  한글 이름을 사용합니다. 프레임워크가 생성한 기본 smoke test 이름은 예외로 둡니다.
- Facade 테스트는 조합된 유스케이스의 정상·실패 흐름과 트랜잭션 원자성을 검증합니다.
- 도메인 규칙은 빠른 단위 테스트로, JPA·트랜잭션·동시성·고유 제약은 MySQL 통합 테스트로 검증합니다.
- 시간 경계 테스트에는 고정된 `Instant`나 주입한 `Clock`을 사용하고 `now()`에 결과가 흔들리지 않게
  합니다.

## 주석과 과한 설계 제한

- Javadoc은 열람 확정의 부수효과, 원자성, 던지는 예외처럼 코드 이름만으로 알 수 없는 "왜"를 설명할
  때만 작성합니다. getter, 단순 위임, 자명한 Controller 매핑에는 달지 않습니다.
- 중복 차감 문제는 우선 MySQL 트랜잭션과 고유 제약으로 해결합니다. 증거 없이 Redis 분산 락이나
  메시지 브로커를 더하지 않으며, 새 인프라가 필요해지면 실패 시나리오와 트레이드오프를 ADR로 먼저
  남깁니다.
