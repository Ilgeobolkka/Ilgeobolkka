# 아키텍처 결정 기록

이 디렉터리에는 되돌리기 비싼 기술 결정의 맥락과 대체 이력을 보존합니다. 가격·기간·사용자 상태 전이는
[제품 정책](../prd/product-policy.md), 현재 HTTP 계약은 [API 계약](../api-spec.md), 현재 데이터 모델은
[ERD](../erd.md), 검증 절차는 [테스트 전략](../test-strategy.md)에서 관리합니다.

## 최초 기준선

2026-07-26에 핵심 도메인 구현과 공유·시연 데이터 생성 전에 제품 방향과 문서 구조를 다시 확정했습니다.
그 전에 작성한 ADR 0001~0022는 합의된 기술 이력이 아니라 제품 방향을 탐색하던 초안이었으므로 폐기하고,
현재 색인의 ADR 0001~0012를 최초 승인 기준선으로 사용합니다. 폐기한 초안은 Git 이력에서만 확인합니다.
아래 보존·대체 규칙은 이 최초 기준선부터 적용합니다.

ADR은 주제를 나타내는 하위 디렉터리 한 곳에 두고, 번호는 디렉터리와 관계없이 저장소 전체에서 유일한
순번으로 부여합니다. 새 ADR은 모든 주제 폴더에 있는 ADR 중 가장 큰 번호에 1을 더한 4자리 번호를
사용합니다.

## 상태와 대체

- `승인됨`: 현재 적용하는 기술 결정입니다.
- `대체됨`: 이후 승인된 ADR이 대신하는 과거 기술 결정입니다.

최초 기준선의 기존 결정을 바꿀 때는 기존 ADR의 제목·날짜·맥락·대안·결정·결과를 그대로 보존하고 상태 줄만
`대체됨`으로 바꾸며, 자신을 대체한 새 ADR을 상대 Markdown 링크로 직접 가리킵니다. 새 ADR은 `승인됨`으로
작성하고 `맥락`에서 기존 ADR을 상대 Markdown 링크로 가리키며 변경 이유를 명시합니다. 반영하기 전에 양쪽
링크가 실제 ADR 파일을 가리키는지, 기존 ADR은 `대체됨`이고 새 ADR은 `승인됨`인지 확인합니다. 색인에는 두
ADR을 모두 남기고, 현행 정본과 저장소의 참조는 새 ADR을 가리키게 갱신합니다.

## 주제 분류

- `platform`: 기반 스택과 스키마 관리
- `application`: 패키지와 애플리케이션 경계
- `domain`: 데이터와 대여 모델
- `content`: 콘텐츠 변환과 전달
- `security`: 인증과 보안
- `frontend`: 프런트엔드 구성

## 색인

### 플랫폼

| 번호 | 기술 결정 | 상태 |
| --- | --- | --- |
| [0001](./platform/0001-use-java21-spring-boot41-and-rds-mysql.md) | Java 21·Spring Boot 4.1·RDS MySQL | 승인됨 |
| [0005](./platform/0005-manage-schema-with-flyway-not-ddl-auto.md) | Flyway 전용 스키마 변경 | 승인됨 |
| [0012](./platform/0012-use-portone-v2-test-payments.md) | PortOne V2 테스트 결제 경계 | 승인됨 |

### 애플리케이션

| 번호 | 기술 결정 | 상태 |
| --- | --- | --- |
| [0002](./application/0002-organize-backend-packages-by-domain.md) | 도메인 우선 패키지와 Facade | 승인됨 |
| [0004](./application/0004-define-api-contract.md) | same-origin JSON API 경계 | 승인됨 |
| [0014](./application/0014-use-openai-and-mysql-for-ai-route-generation.md) | OpenAI와 MySQL 기반 AI 잉크 경로 | 승인됨 |

### 도메인

| 번호 | 기술 결정 | 상태 |
| --- | --- | --- |
| [0003](./domain/0003-define-core-domain-erd.md) | 핵심 권한·결제 관계형 데이터 모델 | 승인됨 |
| [0010](./domain/0010-model-page-rentals-with-ink-ledger.md) | 기간 대여와 잉크 원장 | 승인됨 |

### 콘텐츠

| 번호 | 기술 결정 | 상태 |
| --- | --- | --- |
| [0006](./content/0006-use-poppler-and-private-s3-for-image-pages.md) | Poppler 사전 변환과 비공개 S3 | 승인됨 |
| [0011](./content/0011-align-page-content-with-source-pdf.md) | 원본 PDF 연결 텍스트·이미지 콘텐츠 | 대체됨 |
| [0013](./content/0013-define-page-content-and-public-ai-fixture-boundary.md) | 원본 PDF 연결 콘텐츠와 공개 AI 시연 fixture 경계 | 승인됨 |
| [0015](./content/0015-allow-verified-poppler-version-set.md) | 산출물 동일성을 확인한 Poppler 버전 집합 허용 | 승인됨 |

### 보안

| 번호 | 기술 결정 | 상태 |
| --- | --- | --- |
| [0007](./security/0007-use-server-session-authentication.md) | 서버 세션 인증 | 승인됨 |
| [0009](./security/0009-complete-password-and-session-security.md) | 비밀번호 해시·세션·CSRF 보안 | 승인됨 |

### 프런트엔드

| 번호 | 기술 결정 | 상태 |
| --- | --- | --- |
| [0008](./frontend/0008-use-thymeleaf-bootstrap-vanilla-js.md) | Thymeleaf·Bootstrap·Vanilla JavaScript | 승인됨 |
