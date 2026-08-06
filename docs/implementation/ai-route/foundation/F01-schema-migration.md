# F01 AI 목표 스키마 migration

[구현 작업 색인](../README.md)으로 돌아갑니다.

- 권장 담당: 파동 1 / 담당 A
- 선행: [해제된 GATE-AIR-01 초기 콘텐츠 버전](../00-implementation-gates.md#gate-air-01-초기-콘텐츠-버전-해제)
- 후속: [F02 JPA 매핑](./F02-jpa-mapping.md), [C04 원자적 적재](../content/C04-atomic-import.md)

## 목표

AI 경로 목표 ERD의 기존 테이블 확장과 일곱 새 테이블을 다음 Flyway migration 하나로 적용하고 MySQL
제약을 자동 검증합니다. 이 작업은 Java Entity와 API를 만들지 않습니다.

## 정본 링크

- [ERD AI 목표 모델](../../../erd.md#ai-잉크-경로-2차-mvp-목표-모델-구현-전)
- [ERD 기존 테이블 확장](../../../erd.md#기존-테이블-확장)
- [ERD 새 테이블](../../../erd.md#새-테이블)
- [ERD 트랜잭션·삭제 경계](../../../erd.md#목표-트랜잭션과-삭제-경계)
- [Repository와 스키마 규칙](../../../conventions.md#repository와-스키마)

## 현재 구현 기준선

- 적용된 migration은 [V1](../../../../src/main/resources/db/migration/V1__create_core_domain_tables.sql)뿐입니다.
- [초기 manifest](../../../../fixtures/content/manifest.json)의 `contentVersion`은 `initial-v1`이며
  GATE-AIR-01에서 기존 V1 `book` 행 전체의 backfill 값으로 확정했습니다.
- 기존 스키마 검증 패턴은
  [InkRentalOwnershipSchemaMigrationTest](../../../../src/test/java/com/example/ilgeobolkka/support/schema/InkRentalOwnershipSchemaMigrationTest.java)를
  따릅니다.

## 입력과 산출물

- 입력: 초기 버전 `initial-v1`, 전체 기존 행 backfill 규칙과 ERD의 물리 타입·NULL·PK·UK·FK·인덱스
- 산출물: 현재 기준선에서는 `V2__create_ai_route_domain.sql`; 착수 시 V2가 존재하면 다음 번호
- 산출물: `AiRouteSchemaMigrationTest`
- F02에 넘길 것: 실제 테이블·컬럼·제약 이름과 migration 적용 증거

## 수정 허용 파일

- 새 Flyway migration 한 개
- 새 `src/test/java/com/example/ilgeobolkka/support/schema/AiRouteSchemaMigrationTest.java`

## 구현 조건

1. V1을 수정하지 않고 모든 기존 `book` 행을 `initial-v1`로 backfill합니다. 최종 컬럼은 기존 writer와
   테스트 fixture 호환을 위해 `NOT NULL DEFAULT 'initial-v1'`로 적용하며 일부 ID만 선별하거나 nullable
   중간 계약을 남기지 않습니다.
2. `book`, `book_page` 확장 컬럼의 타입·ASCII collation·JSON·NULL 계약을 ERD와 일치시킵니다.
3. `ai_route_prerequisite`, `ai_route_generation`, `ai_route_generation_item`, `ai_reading_route`,
   `ai_reading_route_item`, `ai_route_current`, `ai_route_daily_usage`를 빠짐없이 만듭니다.
4. 같은 독자·멱등 키, generation·route의 position·page, generation과 저장 route, 현재 route의 고유성을
   DB 제약으로 보장합니다.
5. 선수 관계의 같은 book 복합 FK와 저장 route의 `(reader_id, book_id, id)` 복합 FK를 검증합니다.
6. 저장 route 삭제 cascade가 잉크·대여·세션·서재 테이블로 전파되지 않게 합니다.

## 테스트

- 빈 schema에 V1부터 새 migration까지 적용
- V1만 적용된 schema에 새 migration 적용, 모든 기존 book의 `initial-v1` backfill과 컬럼 기본값 확인
- 중복 멱등 키·현재 route·position과 잘못된 복합 FK가 SQL 예외로 거부되는지 확인
- route 삭제 뒤 `page_rental`, `ink_ledger`, `library_entry`, `reading_session` 보존 확인
- 명령: `./gradlew test --tests '*AiRouteSchemaMigrationTest'`

## 제외 범위

- JPA Entity·Repository, 상태 전이와 잠금 쿼리
- manifest 파싱·콘텐츠 적재
- H2 호환 schema, V1 수정, 운영 사용자 콘텐츠 교체

## 완료 조건

- migration과 스키마 테스트가 ERD의 모든 목표 필드·제약을 이름까지 검증합니다.
- 기존 migration 테스트와 `./gradlew check`가 통과합니다.
- diff에 migration과 해당 테스트 외 파일이 없습니다.

## 인계

F02 담당자에게 migration 파일과 테스트 결과를 전달합니다. Entity 필드가 migration과 다르면 F02에서
스키마를 고치지 않고 이 작업으로 되돌려 확인합니다.
