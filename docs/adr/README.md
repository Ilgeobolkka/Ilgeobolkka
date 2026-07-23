# 아키텍처 결정 기록

ADR은 결정 당시의 맥락과 근거를 보존합니다. 승인된 본문의 과거 표현을 현재 정책처럼 읽지 않고,
`상태`와 `대체·후속 결정`을 따라 현행 계약을 확인합니다.

## 현행 계약 읽기

- 제품 범위와 사용자 정책: [`docs/prd.md`](../prd.md)
- 도메인 용어: [`CONTEXT.md`](../../CONTEXT.md)
- 검증 불변식: [`docs/test-strategy.md`](../test-strategy.md)
- 기본 ERD와 API: ADR-0007, ADR-0008을 먼저 읽고 ADR-0018~0021의 현행 변경을 적용합니다.
- 현재 과금·소장: ADR-0018
- 현재 인증: ADR-0015와 ADR-0019
- 현재 페이지 이미지: ADR-0014와 ADR-0020
- 현재 검색·페이지네이션·오류 응답: ADR-0021
- 현재 QueryDSL 선택 근거: ADR-0022

## 색인

| 번호 | 결정 | 상태 | 현행 참고 |
| --- | --- | --- | --- |
| [0001](./0001-server-validated-reading-confirmation.md) | 서버 검증 6초 기준 열람 확정 | 승인됨 | 유지 |
| [0002](./0002-serve-page-images.md) | 원본 PDF 대신 현재 페이지 이미지 제공 | 승인됨 | ADR-0014·0020과 함께 적용 |
| [0003](./0003-use-point-ledger-and-fixed-page-price.md) | 포인트 원장과 전역 페이지 단가 | 대체됨 → 0017 | 현행 소장은 ADR-0018 |
| [0004](./0004-use-java21-spring-boot41-and-rds-mysql.md) | Java 21·Spring Boot 4.1·RDS MySQL | 승인됨 | 유지 |
| [0005](./0005-separate-local-and-production-database-configuration.md) | 로컬·운영 DB 설정 분리 | 승인됨 | 유지 |
| [0006](./0006-organize-backend-packages-by-domain.md) | 도메인 우선 패키지와 Facade | 승인됨 | 유지 |
| [0007](./0007-define-core-domain-erd.md) | 핵심 도메인 ERD | 승인됨 | 카운트·소장은 ADR-0018 적용 |
| [0008](./0008-define-api-contract.md) | 기본 API 계약 | 승인됨 | ADR-0018~0021 적용 |
| [0009](./0009-use-openfeign-querydsl.md) | 초기 OpenFeign QueryDSL 선택 | 대체됨 → 0022 | ADR-0022 적용 |
| [0010](./0010-manage-schema-with-flyway-not-ddl-auto.md) | Flyway 전용 스키마 변경 | 승인됨 | 유지 |
| [0011](./0011-use-poppler-and-private-s3-for-page-images.md) | Poppler·비공개 S3 | 대체됨 → 0012 | ADR-0014 적용 |
| [0012](./0012-separate-cover-and-validate-page-image-quality.md) | 표지 분리·이미지 품질 | 대체됨 → 0013 | ADR-0014 적용 |
| [0013](./0013-validate-page-images-at-viewer-size.md) | 뷰어 크기 품질 검증 | 대체됨 → 0014 | ADR-0014 적용 |
| [0014](./0014-separate-image-fidelity-and-viewport-readability.md) | 이미지 충실도·뷰포트 가독성 | 승인됨 | HTTP 전달은 ADR-0020 |
| [0015](./0015-use-server-session-authentication.md) | 서버 세션 인증 | 승인됨 | 보안 세부는 ADR-0019 |
| [0016](./0016-use-thymeleaf-bootstrap-vanilla-js.md) | Thymeleaf·Bootstrap·Vanilla JS | 승인됨 | 유지 |
| [0017](./0017-convert-reading-spend-to-ownership.md) | 공개 가격 기반 소장 | 대체됨 → 0018 | ADR-0018 적용 |
| [0018](./0018-convert-ninety-percent-reading-to-ownership.md) | 90% 열람 확정 기반 소장 | 승인됨 | 현행 |
| [0019](./0019-complete-password-and-session-security.md) | 비밀번호·세션·CSRF 보안 | 승인됨 | 현행 |
| [0020](./0020-proxy-current-page-images-through-application.md) | 애플리케이션 페이지 이미지 프록시 | 승인됨 | 현행 |
| [0021](./0021-stabilize-catalog-pagination-and-error-contracts.md) | 검색·페이지네이션·오류 응답 | 승인됨 | 현행 |
| [0022](./0022-retain-openfeign-querydsl-for-type-safe-queries.md) | 타입 안전 조회 기술 검증용 QueryDSL | 승인됨 | 현행 |
