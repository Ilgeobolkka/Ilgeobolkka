# 읽어볼까 ADR 목록

ADR은 상태와 주된 결정의 성격에 따라 폴더를 나눕니다. `active`에는 현재 검토 중이거나 유효한
결정, `superseded`에는 후속 ADR로 대체된 결정의 역사를 둡니다. 번호는 모든 상태·주제 폴더에서
이어지는 전역 순번입니다.

## 현재 유효한 ADR (`active`)

### 과금과 포인트 (`billing`)

| ADR | 상태 | 결정 |
| --- | --- | --- |
| [ADR-0001](./active/billing/0001-server-validated-reading-confirmation.md) | 승인됨 | 서버 검증 6초 기준으로 열람을 확정한다 |
| [ADR-0003](./active/billing/0003-use-point-ledger-and-fixed-page-price.md) | 승인됨 | 포인트 원장과 전역 페이지 단가를 사용한다 |
| [ADR-0017](./active/billing/0017-serialize-reading-confirmation-with-pessimistic-locks.md) | 승인됨 | 열람 확정은 비관적 잠금으로 직렬화한다 |

### 콘텐츠 전달과 저장 (`content`)

| ADR | 상태 | 결정 |
| --- | --- | --- |
| [ADR-0002](./active/content/0002-serve-page-images.md) | 승인됨 | 원본 PDF 대신 현재 페이지 이미지를 제공한다 |

### 콘텐츠 검증 (`validation`)

| ADR | 상태 | 결정 |
| --- | --- | --- |
| [ADR-0014](./active/validation/0014-separate-image-fidelity-and-viewport-readability.md) | 승인됨 | 페이지 이미지 충실도와 뷰포트 가독성을 분리 검증한다 |

### 애플리케이션 구조와 기술 (`architecture`)

| ADR | 상태 | 결정 |
| --- | --- | --- |
| [ADR-0004](./active/architecture/0004-use-java21-spring-boot41-and-rds-mysql.md) | 승인됨 | Java 21과 Spring Boot 4.1.0, Amazon RDS for MySQL을 사용한다 |
| [ADR-0006](./active/architecture/0006-organize-backend-packages-by-domain.md) | 승인됨 | 백엔드 패키지를 도메인별로 구성한다 |
| [ADR-0009](./active/architecture/0009-use-openfeign-querydsl.md) | 승인됨 | OpenFeign QueryDSL로 타입 안전 JPA 쿼리를 작성한다 |

### 데이터와 스키마 (`data`)

| ADR | 상태 | 결정 |
| --- | --- | --- |
| [ADR-0005](./active/data/0005-separate-local-and-production-database-configuration.md) | 승인됨 | 로컬과 운영 데이터베이스 연결 설정을 분리한다 |
| [ADR-0007](./active/data/0007-define-core-domain-erd.md) | 승인됨 | 핵심 도메인 엔티티와 ERD를 정의한다 |
| [ADR-0010](./active/data/0010-manage-schema-with-flyway-not-ddl-auto.md) | 승인됨 | 스키마 변경은 Flyway로만 관리하고 Hibernate는 검증만 한다 |

### API와 인증·인가 (`api-auth`)

| ADR | 상태 | 결정 |
| --- | --- | --- |
| [ADR-0015](./active/api-auth/0015-separate-reading-session-open-and-page-move.md) | 승인됨 | 열람 세션 생성과 페이지 이동 API를 분리한다 |
| [ADR-0016](./active/api-auth/0016-distinguish-api-error-status-by-failure-semantics.md) | 승인됨 | API 오류를 실패 성격에 따라 HTTP 상태로 구분한다 |
| [ADR-0018](./active/api-auth/0018-use-minimal-api-error-body.md) | 승인됨 | API 오류 바디에 code와 message를 사용한다 |

## 대체된 ADR (`superseded`)

| ADR | 대체 관계 | 이전 결정 |
| --- | --- | --- |
| [ADR-0008](./superseded/api-auth/0008-define-api-contract.md) | [ADR-0015](./active/api-auth/0015-separate-reading-session-open-and-page-move.md), [ADR-0016](./active/api-auth/0016-distinguish-api-error-status-by-failure-semantics.md), [ADR-0017](./active/billing/0017-serialize-reading-confirmation-with-pessimistic-locks.md), [ADR-0018](./active/api-auth/0018-use-minimal-api-error-body.md)로 대체 | 상세 계약은 [API 명세](../api/api-contract.md), 선택 근거는 후속 ADR로 분리했다 |
| [ADR-0011](./superseded/content/0011-use-poppler-and-private-s3-for-page-images.md) | [ADR-0012](./superseded/validation/0012-separate-cover-and-validate-page-image-quality.md)로 대체 | Poppler로 페이지 이미지를 변환하고 비공개 S3에 저장한다 |
| [ADR-0012](./superseded/validation/0012-separate-cover-and-validate-page-image-quality.md) | [ADR-0013](./superseded/validation/0013-validate-page-images-at-viewer-size.md)으로 대체 | 표지를 분리하고 페이지 이미지 품질을 검증한다 |
| [ADR-0013](./superseded/validation/0013-validate-page-images-at-viewer-size.md) | [ADR-0014](./active/validation/0014-separate-image-fidelity-and-viewport-readability.md)로 대체 | 실제 뷰어 크기로 페이지 이미지 품질을 검증한다 |

## 공용 검증 자료

- [`evidence/page-image-quality`](./evidence/page-image-quality/): 페이지 이미지 변환과 품질 판정의 재현 자료
