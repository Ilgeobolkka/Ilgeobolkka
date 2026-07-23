# 0010. 스키마 변경은 Flyway로만 관리하고 Hibernate ddl-auto는 끈다

- 상태: 승인됨
- 날짜: 2026-07-23
- 결정자: 사용자

## 맥락

ADR-0005는 "로컬과 운영의 스키마는 같은 Flyway 마이그레이션으로 관리합니다"를 이미 결정했고 결과
절에 "Flyway 의존성 추가가 필요합니다"를 예고했습니다. ADR-0007은 ERD를 확정하면서도 "Flyway
migration 파일 자체는 이 ADR의 범위가 아니며 후속 구현 작업에서 결정합니다"라고 범위를 분리해
두었습니다. `docs/conventions.md`의 "Repository와 스키마" 절도 "테이블·컬럼·인덱스·제약조건을
바꾸는 작업에는 Flyway migration을 함께 추가한다"와 "JPA Entity 변경만으로 운영 스키마 변경을
대신하지 않는다"를 이미 컨벤션으로 못박아 두었습니다.

즉 "Flyway를 쓴다"는 방향 자체는 이미 세 문서에서 합의되어 있지만, Hibernate의
`spring.jpa.hibernate.ddl-auto`를 어떤 값으로 둘지, Flyway와 어떻게 병행 또는 배타적으로 동작하게
할지는 아직 어느 문서에도 명시되어 있지 않습니다. 이 값은 스키마가 실제로 어떻게 생성·검증되는지를
좌우하는 되돌리기 비싼 결정이고, 엔티티가 늘어난 뒤 바꾸면 비용이 커지므로 별도 ADR로 근거와
대안을 명시적으로 남깁니다. 현재 `src/main`에는 `@Entity`가 하나도 없고(테스트 전용
`QuerydslTestEntity`만 존재), `build.gradle`에는 Flyway 의존성이 없는 골격 단계입니다.

## 고려한 대안

- `ddl-auto=update` 단독 사용 — 엔티티만 작성하면 스키마가 자동으로 따라와 초기 개발 속도는 빠르지만,
  운영에서 예측 불가능한 컬럼 변경이 발생할 수 있고 conventions.md가 이미 금지한 "Entity 변경만으로
  운영 스키마 변경을 대신"하는 방식이라 제외합니다.
- `ddl-auto=create-drop`/`create` 단독 사용 — 테스트 격리에는 유용하지만 기동마다 데이터를 잃고
  로컬-운영 스키마 일치를 보장할 수단이 없어 제외합니다.
- Flyway + `ddl-auto=update` 병행 — Flyway로 스키마를 만든 뒤에도 Hibernate가 추가로 컬럼을 자동
  변경할 수 있어, 리뷰 가능한 마이그레이션 파일이 실제 스키마와 어긋날 위험이 있고 "Flyway가 유일한
  변경 수단"이라는 conventions.md 원칙과 충돌해 제외합니다.
- Flyway + `ddl-auto=validate` — Flyway가 스키마를 만들고 Hibernate는 기동 시 엔티티 매핑이 실제
  테이블과 일치하는지만 검증합니다. 자동 변경이 전혀 없어 conventions.md 원칙과 일치하고, 엔티티와
  스키마가 어긋나면 기동 실패로 즉시 드러나 선택했습니다.
- Flyway + `ddl-auto=none` — 검증도 하지 않아 `validate`보다 더 관대하지만, 엔티티-스키마 불일치를
  기동 시점에 잡아주는 안전망을 스스로 포기하는 것이라 `validate`보다 못합니다.

## 결정

- Flyway를 스키마 변경의 유일한 수단으로 채택합니다. 테이블·컬럼·인덱스·제약조건 변경은 반드시
  `src/main/resources/db/migration`의 버전 마이그레이션 파일로 남깁니다(conventions.md와 동일).
- `spring.jpa.hibernate.ddl-auto`는 `validate`로 고정합니다. Hibernate는 엔티티 매핑이 Flyway가
  만든 실제 테이블과 일치하는지만 검증하고, 어떤 방식으로도 스키마를 자동 생성·변경하지 않습니다.
- 로컬과 운영 모두 같은 값을 사용합니다(ADR-0005의 "같은 빌드로 로컬 Compose와 운영 RDS에 연결"
  원칙과 동일하게, 환경별로 다른 `ddl-auto` 값을 두지 않습니다).
- 실제 도메인 테이블을 만드는 초기 마이그레이션(V1) 파일 작성은 이 ADR의 범위가 아니며, ADR-0007이
  이미 명시한 "후속 구현 작업"에서 진행합니다. 이 ADR이 승인된 시점에는 `@Entity`가 없으므로
  `validate`가 검증할 대상도 없고, 마이그레이션 파일도 아직 없습니다.

## 결과

- 운영 스키마 변경은 항상 리뷰 가능한 Flyway 마이그레이션 파일로 남고, Hibernate가 우회로 스키마를
  바꿀 수 없습니다.
- 엔티티 매핑과 실제 테이블이 어긋나면 애플리케이션이 기동 단계에서 실패해 문제를 늦게 발견하는
  위험을 줄입니다. 반대로 이미 작성된 엔티티가 최신 마이그레이션과 어긋나면(예: 마이그레이션 작성을
  깜빡함) 애플리케이션 기동 자체가 막히므로, 엔티티 추가 시 대응 마이그레이션 작성을 빠뜨리지 않아야
  합니다.
- 되돌리려면 `ddl-auto` 값 하나만 바꾸면 되지만, 이미 `validate`를 전제로 여러 엔티티와 마이그레이션이
  쌓인 뒤에는 전환 비용이 커집니다.
- 이 ADR의 결정 범위는 "Flyway 채택 + `ddl-auto=validate`"입니다. 실제 도메인 테이블을 만드는 초기
  마이그레이션(`V1__create_core_domain_tables.sql`)은 같은 스토리(SCRUM-26)의 하위 작업에서 이미
  작성했고, `src/main`의 실제 `@Entity` 도입만 후속 구현 작업으로 남습니다.
- Spring Boot 4.1은 Flyway 자동설정을 별도 모듈 `org.springframework.boot:spring-boot-flyway`로
  분리했습니다. 이 모듈과 `org.flywaydb:flyway-mysql`을 추가하면 `flyway-core`는 두 의존성의 전이
  의존성으로 함께 들어오므로, `flyway-core`를 직접 선언하지는 않습니다(직접 Flyway API를 쓰지 않음).
- 테스트 전용 엔티티(`QuerydslTestEntity`, `JpaAuditingTestEntity`)는 애플리케이션 스캔 범위
  (`com.example.ilgeobolkka`) 밖의 `com.example.testfixture.*` 패키지에 두어 기본 컨텍스트에 잡히지
  않게 격리합니다. 이 엔티티들의 테이블은 Flyway가 아니라 해당 통합 테스트가 raw SQL로 직접
  만들므로, 그 엔티티를 `@EntityScan`으로 등록하는 두 통합 테스트
  (`QuerydslMySqlIntegrationTest`, `JpaAuditingMySqlIntegrationTest`)에 한해서만
  `@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=none")`로 검증을 끕니다.
- 이렇게 격리한 결과, `IlgeobolkkaApplicationTests`(스모크)와 `CoreDomainSchemaMigrationTest`는 운영
  기준값 `validate` 그대로 기동해, `application.yaml`의 `validate` 설정이 실제로 동작하는지를 회귀
  테스트로 지킵니다. 즉 `validate`를 삭제·변경하면 이 두 테스트가 실패합니다.
- 초기 검토 시점에는 `IlgeobolkkaApplicationTests`를 포함한 여러 테스트가 `none`으로 검증을 껐으나,
  PR 리뷰에서 "그러면 `validate`가 실제로 동작하는지 아무 테스트도 확인하지 못한다"는 지적을 받아 위
  격리 구조로 바꿨습니다(검증을 끄는 테스트는 픽스처 엔티티를 쓰는 두 통합 테스트로 한정).
