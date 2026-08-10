# F02 JPA Entity·Repository 기반

[구현 작업 색인](../README.md)으로 돌아갑니다.

- 권장 담당: 파동 2 / 담당 A
- 선행: [F01 스키마 migration](./F01-schema-migration.md)
- 후속: [G05 멱등·일일 한도](../generation/G05-idempotency-daily-limit.md),
  [S01 단일 저장](../saved-route/S01-save-generation.md), [S02 경로 조회](../saved-route/S02-route-query.md)

## 목표

F01 schema와 정확히 일치하는 AI Entity·Repository 기반을 만들고 이후 작업이 컬럼·연관관계를 다시
설계하지 않게 합니다. 유스케이스 조회·잠금 메서드는 각 후속 작업이 추가합니다.

## 정본 링크

- [ERD 기존 테이블 확장](../../../erd.md#기존-테이블-확장)
- [ERD 새 테이블](../../../erd.md#새-테이블)
- [Entity와 DTO 규칙](../../../conventions.md#entity와-dto)
- [Repository와 스키마 규칙](../../../conventions.md#repository와-스키마)

## 현재 구현 기준선

- 기존 [Book](../../../../src/main/java/com/example/ilgeobolkka/book/entity/Book.java)과
  [BookPage](../../../../src/main/java/com/example/ilgeobolkka/book/entity/BookPage.java)에는
  AI 필드가 없습니다.
- AI 패키지와 Repository는 아직 없습니다.
- 기존 매핑 검증은
  [CoreEntityMappingMySqlIntegrationTest](../../../../src/test/java/com/example/ilgeobolkka/support/entity/CoreEntityMappingMySqlIntegrationTest.java)를
  참고합니다.

## 입력과 산출물

- 입력: F01 migration과 적용된 MySQL schema
- 산출물: `Book`·`BookPage` AI 필드와 유효한 상태 변경 메서드
- 산출물: `AiRoutePrerequisite`, `AiRouteGeneration`, `AiRouteGenerationItem`, `AiReadingRoute`,
  `AiReadingRouteItem`, `AiRouteCurrent`, `AiRouteDailyUsage`
- 산출물: Entity별 Spring Data Repository와 `AiRouteEntityMappingMySqlIntegrationTest`

## 수정 허용 파일

- 기존 `book/entity/Book.java`, `book/entity/BookPage.java`
- 새 `airoute/entity/*.java`, `airoute/repository/*.java`
- 기존 `src/test/java/com/example/ilgeobolkka/support/entity/CoreEntityMappingMySqlIntegrationTest.java`
- 새 매핑 통합 테스트와 필요한 테스트 fixture만

## 구현 조건

1. API DTO를 Entity로 사용하지 않고 ERD의 상태·요청 종류·깊이·역할·피드백을 명시적 Enum으로 둡니다.
2. `BookPage`의 분석 텍스트·embedding·중복 그룹은 응답 직렬화 대상이 되지 않습니다.
3. 전체 setter와 `@Data`를 쓰지 않고 `complete`, `fail`, `saveAsRoute`, `markOpened`처럼 허용 상태 전이만
   메서드로 노출합니다. 실제 전이 사용은 후속 작업에서 테스트합니다.
4. JSON 배열은 읽기·쓰기 변환이 명확한 타입으로 매핑하고 lazy loading을 Controller로 넘기지 않습니다.
5. Repository에는 `JpaRepository` 기본 계약만 우선 두고 task-specific lock/query는 G05·S01~S05가 자기
   테스트와 함께 추가합니다. 컬럼 매핑은 후속 작업이 변경하지 않습니다.
6. 연관관계 cascade는 ERD 삭제 경계를 넘지 않고 Entity 양방향 편의 매핑을 습관적으로 추가하지 않습니다.
7. `Book`·`BookPage`에 매핑한 AI 확장 컬럼은
   `CoreEntityMappingMySqlIntegrationTest.AI_ROUTE_EXTENSION_COLUMNS` 제외 목록에서 즉시 제거하고,
   모든 확장 컬럼을 매핑한 뒤 빈 제외 상수와 관련 필터를 삭제합니다.

## 테스트

- 모든 Entity가 F01 schema로 부팅·저장·조회되는 매핑 통합 테스트
- F01의 `AUTO_INCREMENT` AI Entity가 모두 `GenerationType.IDENTITY`를 사용하는지 확인
- AI Entity의 컬럼명과 NULL 허용이 F01 물리 schema와 양방향으로 일치하는지 확인
- Enum의 DB 문자열과 API 철자 일치 확인
- `BookPage` JSON 필드 round-trip과 비공개 필드 직렬화 비노출 확인
- 명령: `./gradlew test --tests '*AiRouteEntityMappingMySqlIntegrationTest'`

## 제외 범위

- 생성 상태 전이의 시간·멱등 정책, lock query
- Controller·DTO·오류 코드
- 콘텐츠 적재와 OpenAI 호출

## 완료 조건

- F01 schema와 Entity가 필드·NULL·Enum 기준으로 일치합니다.
- 기존 도서·페이지 API 회귀 테스트와 `./gradlew check`가 통과합니다.
- 후속 작업이 schema나 Entity 필드를 새로 추측하지 않고 Repository 메서드만 추가할 수 있습니다.

## 인계

G05·S01·S02 담당자에게 Entity 생성 API와 Repository 이름을 전달합니다. 후속 작업에서 매핑 변경이
필요하면 공유 파일을 동시에 수정하지 말고 F02 담당자와 먼저 조정합니다.
