# 읽어볼까 ADR 목록

ADR은 상태와 주된 결정의 성격에 따라 폴더를 나눕니다. `active`에는 현재 검토 중이거나 유효한 결정,
`superseded`에는 후속 ADR로 대체된 결정의 역사를 둡니다. 번호는 모든 상태·주제 폴더에서 이어지는 전역 순번입니다.

## 현재 유효한 ADR (`active`)

### 과금과 잉크 (`billing`)

| ADR | 상태 | 결정 |
| --- | --- | --- |
| [ADR-0019](./active/billing/0019-charge-ink-when-page-opens.md) | 승인됨 | 페이지를 열 때 잉크를 즉시 차감한다 |
| [ADR-0020](./active/billing/0020-use-universal-ink-page-pass.md) | 승인됨 | 모든 도서에 공통 잉크 이용권을 사용한다 |
| [ADR-0021](./active/billing/0021-rent-each-page-for-thirty-days.md) | 승인됨 | 각 페이지를 차감 시점부터 30일 동안 대여한다 |
| [ADR-0022](./active/billing/0022-serialize-page-rental-with-pessimistic-locks.md) | 승인됨 | 페이지 대여를 비관적 잠금으로 직렬화한다 |

### 콘텐츠 전달과 저장 (`content`)

| ADR | 상태 | 결정 |
| --- | --- | --- |
| [ADR-0023](./active/content/0023-use-pdf-page-mapped-hybrid-content.md) | 승인됨 | 원본 PDF 페이지에 대응하는 혼합 콘텐츠를 제공한다 |

### 콘텐츠 검증 (`validation`)

| ADR | 상태 | 결정 |
| --- | --- | --- |
| [ADR-0024](./active/validation/0024-validate-hybrid-page-content.md) | 승인됨 | 혼합 페이지 콘텐츠를 유형별로 검증한다 |

### 애플리케이션 구조와 기술 (`architecture`)

| ADR | 상태 | 결정 |
| --- | --- | --- |
| [ADR-0006](./active/architecture/0006-organize-backend-packages-by-domain.md) | 승인됨 | 백엔드 패키지를 도메인별로 구성한다 |
| [ADR-0026](./active/architecture/0026-use-java-21.md) | 승인됨 | 백엔드 기준 Java 버전으로 21을 사용한다 |
| [ADR-0027](./active/architecture/0027-use-spring-boot-4-1.md) | 승인됨 | Spring Boot 4.1 계열을 사용한다 |
| [ADR-0028](./active/architecture/0028-use-rds-for-mysql.md) | 승인됨 | 운영 관계형 데이터베이스로 Amazon RDS for MySQL을 사용한다 |
| [ADR-0029](./active/architecture/0029-defer-querydsl-until-dynamic-query-is-needed.md) | 승인됨 | 동적 조회 요구가 생길 때까지 QueryDSL 도입을 미룬다 |
| [ADR-0030](./active/architecture/0030-defer-zero-downtime-deployment.md) | 승인됨 | MVP에서는 서비스 중단을 허용하는 단일 환경 배포를 사용한다 |

### 데이터와 스키마 (`data`)

| ADR | 상태 | 결정 |
| --- | --- | --- |
| [ADR-0005](./active/data/0005-separate-local-and-production-database-configuration.md) | 승인됨 | 로컬과 운영 데이터베이스 연결 설정을 분리한다 |
| [ADR-0010](./active/data/0010-manage-schema-with-flyway-not-ddl-auto.md) | 승인됨 | 스키마 변경은 Flyway로만 관리하고 Hibernate는 검증만 한다 |
| [ADR-0025](./active/data/0025-model-ink-rental-and-page-content.md) | 승인됨 | 잉크·페이지 대여·변환 콘텐츠를 별도 엔티티로 모델링한다 |

### API와 인증·인가 (`api-auth`)

| ADR | 상태 | 결정 |
| --- | --- | --- |
| [ADR-0015](./active/api-auth/0015-separate-reading-session-open-and-page-move.md) | 승인됨 | 열람 세션 생성과 페이지 이동 API를 분리한다 |
| [ADR-0016](./active/api-auth/0016-distinguish-api-error-status-by-failure-semantics.md) | 승인됨 | API 오류를 실패 성격에 따라 HTTP 상태로 구분한다 |
| [ADR-0018](./active/api-auth/0018-use-minimal-api-error-body.md) | 승인됨 | API 오류 바디에 code와 message를 사용한다 |

## 대체된 ADR (`superseded`)

### 과금과 포인트

| ADR | 대체 관계 | 이전 결정 |
| --- | --- | --- |
| [ADR-0001](./superseded/billing/0001-server-validated-reading-confirmation.md) | [ADR-0019](./active/billing/0019-charge-ink-when-page-opens.md)로 대체 | 서버 검증 6초 기준으로 열람을 확정한다 |
| [ADR-0003](./superseded/billing/0003-use-point-ledger-and-fixed-page-price.md) | [ADR-0020](./active/billing/0020-use-universal-ink-page-pass.md)으로 대체 | 포인트 원장과 50P 전역 페이지 단가를 사용한다 |
| [ADR-0017](./superseded/billing/0017-serialize-reading-confirmation-with-pessimistic-locks.md) | [ADR-0022](./active/billing/0022-serialize-page-rental-with-pessimistic-locks.md)로 대체 | 6초 열람 확정을 비관적 잠금으로 직렬화한다 |

### 콘텐츠와 검증

| ADR | 대체 관계 | 이전 결정 |
| --- | --- | --- |
| [ADR-0002](./superseded/content/0002-serve-page-images.md) | [ADR-0023](./active/content/0023-use-pdf-page-mapped-hybrid-content.md)으로 대체 | 원본 PDF 대신 현재 페이지 이미지를 제공한다 |
| [ADR-0011](./superseded/content/0011-use-poppler-and-private-s3-for-page-images.md) | [ADR-0012](./superseded/validation/0012-separate-cover-and-validate-page-image-quality.md)로 대체 | Poppler로 페이지 이미지를 변환하고 비공개 S3에 저장한다 |
| [ADR-0012](./superseded/validation/0012-separate-cover-and-validate-page-image-quality.md) | [ADR-0013](./superseded/validation/0013-validate-page-images-at-viewer-size.md)으로 대체 | 표지를 분리하고 페이지 이미지 품질을 검증한다 |
| [ADR-0013](./superseded/validation/0013-validate-page-images-at-viewer-size.md) | [ADR-0014](./superseded/validation/0014-separate-image-fidelity-and-viewport-readability.md)로 대체 | 실제 뷰어 크기로 페이지 이미지 품질을 검증한다 |
| [ADR-0014](./superseded/validation/0014-separate-image-fidelity-and-viewport-readability.md) | [ADR-0024](./active/validation/0024-validate-hybrid-page-content.md)로 대체 | 페이지 이미지 충실도와 뷰포트 가독성을 분리 검증한다 |

### 구조와 데이터

| ADR | 대체 관계 | 이전 결정 |
| --- | --- | --- |
| [ADR-0004](./superseded/architecture/0004-use-java21-spring-boot41-and-rds-mysql.md) | [ADR-0026](./active/architecture/0026-use-java-21.md), [ADR-0027](./active/architecture/0027-use-spring-boot-4-1.md), [ADR-0028](./active/architecture/0028-use-rds-for-mysql.md)로 분리·대체 | Java 21, Spring Boot 4.1.0과 RDS MySQL을 한 문서에서 결정한다 |
| [ADR-0007](./superseded/data/0007-define-core-domain-erd.md) | [ADR-0025](./active/data/0025-model-ink-rental-and-page-content.md)로 대체 | 영구 확정 페이지와 포인트 기반 핵심 ERD를 정의한다 |
| [ADR-0009](./superseded/architecture/0009-use-openfeign-querydsl.md) | [ADR-0029](./active/architecture/0029-defer-querydsl-until-dynamic-query-is-needed.md)로 대체 | OpenFeign QueryDSL로 타입 안전 JPA 쿼리를 작성한다 |

### API

| ADR | 대체 관계 | 이전 결정 |
| --- | --- | --- |
| [ADR-0008](./superseded/api-auth/0008-define-api-contract.md) | [ADR-0015](./active/api-auth/0015-separate-reading-session-open-and-page-move.md), [ADR-0016](./active/api-auth/0016-distinguish-api-error-status-by-failure-semantics.md), [ADR-0017](./superseded/billing/0017-serialize-reading-confirmation-with-pessimistic-locks.md), [ADR-0018](./active/api-auth/0018-use-minimal-api-error-body.md)로 대체 | 상세 계약은 API 명세, 선택 근거는 후속 ADR로 분리했다 |

## 공용 검증 자료

- [`evidence/page-image-quality`](./evidence/page-image-quality/): 과거 페이지 이미지 변환과 품질 판정의 재현 자료. 혼합 콘텐츠의 새 검증 자료는 ADR-0024 기준으로 후속 추가합니다.
