# 환경별 데이터베이스·OpenAI·PortOne 연결과 AWS RDS 배포

## 1. 목적

하나의 Spring Boot 애플리케이션을 로컬에서는 Docker Compose MySQL에, 운영에서는 Amazon RDS for MySQL에 연결하는 설정 계약을 정의합니다. 운영 배포에 `.env`를 복사하거나 MySQL 컨테이너를 함께 실행하지 않습니다.

## 2. 환경별 구성

| 항목 | 로컬 개발·테스트 | AWS 운영 |
| --- | --- | --- |
| 데이터베이스 | Docker Compose MySQL 8.4 | Amazon RDS for MySQL 8.x |
| 호스트 포트 | `localhost:3307` | RDS 엔드포인트의 `3306` |
| 설정 공급자 | Git에서 제외된 `.env` | AWS 배포 환경과 Secrets Manager |
| JDBC TLS | `sslMode=DISABLED` | `sslMode=VERIFY_IDENTITY` |
| DB 공개 접근 | 로컬 호스트에만 노출 | 비공개 RDS, 애플리케이션 보안 그룹만 허용 |

Spring datasource는 아래 `DB_*`를 프로젝트의 권장 계약으로 사용합니다. Spring Boot 표준
`SPRING_DATASOURCE_*`도 대체 체계로 사용할 수 있지만 두 체계를 동시에 주입하지 않습니다.

| 권장 변수 | Spring Boot 표준 대체 변수 | 설명 | 민감정보 |
| --- | --- | --- | --- |
| `DB_URL` | `SPRING_DATASOURCE_URL` | 호스트, 포트, DB 이름, TLS 정책을 포함한 JDBC URL | 아니요 |
| `DB_USERNAME` | `SPRING_DATASOURCE_USERNAME` | 애플리케이션 전용 DB 계정 | 예 |
| `DB_PASSWORD` | `SPRING_DATASOURCE_PASSWORD` | 애플리케이션 전용 DB 비밀번호 | 예 |

로컬 `.env`와 아래 운영 예시는 `DB_*`를 사용합니다. 배포 플랫폼이 Spring Boot 표준 변수를 직접
제공한다면 `SPRING_DATASOURCE_*` 세 개만 대신 주입합니다. 두 체계가 함께 있으면 표준 변수가
`spring.datasource.*`에 직접 바인딩되어 `DB_*`와 다른 연결이 선택될 수 있으므로 혼용하지 않습니다.

`TEST_DB_URL`은 로컬·CI 테스트 전용 `_test` 데이터베이스 JDBC URL입니다. 테스트 프로필은 이 값을
우선 사용하고, 실제 연결 DB 이름이 `_test`로 끝나지 않으면 시작을 거부합니다.

`TEST_DB_URL`, `DB_PORT`, `DB_NAME`, `DB_ROOT_PASSWORD`는 로컬에서만 사용합니다. 운영 애플리케이션에는
`TEST_DB_URL`, RDS master 계정이나 `DB_ROOT_PASSWORD`를 주입하지 않습니다.

로컬·시연 환경에서만 사용하는 시연 계정 비밀번호는 다음 변수로 주입합니다.

| 변수 | 설명 | 운영 주입 |
| --- | --- | --- |
| `DEMO_VALIDATION_PASSWORD` | 세 역할별 시연 계정의 공통 비밀번호 | 금지 |

`.env.example`에는 변수명만 두고 실제 비밀번호는 Git에서 제외된 `.env`에만 기록합니다. 운영 환경에는
검증 계정을 생성하지 않으며 이 변수도 주입하지 않습니다. 세 계정의 잉크와 내역은
[MVP 초기 데이터](./prd/mvp-goals.md#초기-데이터)를 따릅니다.

### 실행 프로필과 시연 데이터

애플리케이션의 기본 프로필은 로컬 개발용 `local`입니다. 사용자 시연 환경은 `demo`, 테스트는 `test`,
AWS 운영은 `prod` 프로필을 명시합니다.

시연 데이터 자동 생성기는 `local`·`demo` 프로필에서만 등록되고, `DEMO_VALIDATION_PASSWORD`가 비어
있으면 아무 데이터도 만들지 않습니다. 조건을 만족한 첫 시작에는 고정 도서 ID 1~100의 메타데이터와
아래 계정 3개를 하나의 트랜잭션으로 생성합니다. 페이지는 일반 서버 시작 전에
[`content-import` 배치](#콘텐츠-변환적재)로 별도 적재해야 합니다.

| 이메일 | 용도 |
| --- | --- |
| `reader-a@demo.ilgeobolkka.test` | 100잉크·지급 원장 검증 |
| `reader-b@demo.ilgeobolkka.test` | 0잉크·잉크 부족 검증 |
| `reader-c@demo.ilgeobolkka.test` | 0잉크·Book 1 온라인 소장 검증 |

재시작할 때 같은 도서 메타데이터는 중복 생성하지 않고 세 계정의 현재 잉크·서재 진행 상태도 초기화하지
않습니다. 주입한 비밀번호가 바뀌면 세 계정의 비밀번호 해시만 갱신합니다. 고정 도서 ID가 충돌하거나,
변환 페이지 수가 다르거나 IMAGE 파일이 없거나, 시연 계정이 일부만 존재하면 기존 데이터를 덮어쓰지 않고
시작을 거부합니다.

`test` 프로필은 자동 시드를 등록하지 않으며 각 테스트가 필요한 fixture만 직접 생성합니다. `prod`
프로필에서는 자동·수동 여부와 관계없이 이 시연 데이터 생성기를 사용할 수 없습니다. 실제 운영 도서
등록은 관리자 기능이 필요한 별도 범위이며 현재 MVP에는 포함하지 않습니다.

PortOne V2는 로컬·시연 환경의 테스트 채널에서만 사용합니다. 애플리케이션은 다음 환경변수를 결제 설정에
바인딩하며, `PORTONE_PAYMENT_ENABLED`의 기본값은 `false`입니다.

| 변수 | 설명 | 민감정보 |
| --- | --- | --- |
| `PORTONE_PAYMENT_ENABLED` | `true`일 때만 결제 API와 화면 활성화 | 아니요 |
| `PORTONE_STORE_ID` | 브라우저 결제 요청에 전달할 PortOne 상점 ID | 아니요 |
| `PORTONE_CHANNEL_KEY` | PortOne 테스트 채널 키 | 아니요 |
| `PORTONE_API_SECRET` | 서버의 결제 재조회 인증 정보 | 예 |
| `PORTONE_WEBHOOK_SECRET` | 서버의 웹훅 서명 검증 정보 | 예 |

`.env.example`은 결제를 기본 비활성화하고 식별자·secret 값을 비워 둡니다. 테스트 결제를 사용할 때만
Git에서 제외된 `.env`에 테스트 채널 값을 주입합니다. `PORTONE_STORE_ID`와 `PORTONE_CHANNEL_KEY`는 결제
준비 API를 통해 브라우저에 전달할 수 있지만 API secret과 웹훅 secret은 서버 밖으로 내보내지 않습니다.
AWS 운영 환경에는 위 PortOne 변수를 주입하지 않고 결제 기능을 비활성화합니다.

### 브라우저 콘텐츠 보안 정책

모든 환경에서 `Content-Security-Policy`를 보고 전용이 아닌 실제 차단 헤더로 적용합니다. 공통 기준은
다음과 같습니다.

```text
default-src 'self';
script-src 'self';
style-src 'self';
img-src 'self' data: blob:;
font-src 'self';
connect-src 'self';
object-src 'none';
base-uri 'self';
form-action 'self';
frame-ancestors 'self';
```

- 결제가 활성화된 비운영 `/ink`와 `/books/{bookId}` 결제 화면만 다음 PortOne 전용 정책을 대신
  사용합니다. 두 정책을 동시에 응답하지 않습니다.

```text
default-src 'self';
script-src 'self' https://cdn.portone.io;
style-src 'self';
img-src 'self' data: blob:;
font-src 'self';
connect-src 'self' https://checkout-service.prod.iamport.co https://tx-gateway-service.prod.iamport.co https://service.iamport.kr https://coretelemetry.prod.iamport.co;
frame-src 'self' https://payment-bridge.prod.iamport.co https://checkout-service.prod.iamport.co https://payment-gateway-sandbox.tosspayments.com;
object-src 'none';
base-uri 'self';
form-action 'self';
frame-ancestors 'self';
```

- `unsafe-inline`, `unsafe-eval`, `*`, 포괄적인 `https:` 출처는 허용하지 않습니다.
- Thymeleaf 화면, Bootstrap WebJar, 애플리케이션 CSS와 JavaScript는 모두 same-origin으로 제공합니다.
- 공통 셸은 PortOne SDK를 로드하지 않습니다. 잉크·소장 결제 화면에서만
  `https://cdn.portone.io/v2/browser-sdk.esm.js`를 동적으로 import하고 로딩 실패를 결제 영역에만
  표시합니다. 탐색·뷰어·서재의 공통 모듈은 이 import에 의존하지 않습니다.
- 현재 정책은 PortOne V2 브라우저 SDK의 고정 출처와 테스트 채널에서 확인한 토스페이먼츠 sandbox
  `frame-src` origin만 허용합니다. 추가 외부 출처가 필요하면 런타임에서 확인한 정확한 origin만
  추가합니다. 추측한 PG사 도메인이나 wildcard는 미리 허용하지 않습니다.

## 3. 로컬 실행

### 최초 기준선 전환

2026-07-26에 승인한 현재 `V1`은 구현·공유 전 초안을 대체한 최초 스키마 기준선입니다. 이전 초안
`V1`이 적용된 로컬 Compose 볼륨은 Flyway checksum이 달라 그대로 사용할 수 없습니다. 보존할 로컬
데이터가 없음을 확인한 경우에만 다음 명령으로 한 번 재생성합니다.

```bash
docker compose down -v
docker compose up -d --wait
```

`docker compose down -v`는 이 프로젝트의 로컬 MySQL 볼륨과 데이터를 삭제하며 되돌릴 수 없습니다.
공유·시연·운영 데이터베이스에는 실행하지 않습니다. 새 볼륨에서는 초기화 스크립트가 개발·테스트
데이터베이스를 함께 만들고 현재 `V1`을 처음부터 적용합니다. 최초 기준선이 공유된 뒤에는 `V1`을
수정하지 않고 새 버전 migration으로 변경합니다.

### 일반 실행

```bash
cp .env.example .env
docker compose up -d --wait
docker compose exec -T mysql sh /docker-entrypoint-initdb.d/01-create-test-database.sh
```

시연 계정의 소장·서재 외래 키는 고정 PDF에서 적재한 `book_page`를 참조합니다. 새 DB에서
`DEMO_VALIDATION_PASSWORD`를 주입하기 전 아래 콘텐츠 배치를 한 번 완료해야 합니다.

### 콘텐츠 변환·적재

`pdftotext`와 `pdftoppm`은 모두 Poppler `26.05.0`이어야 합니다. 먼저 실제 실행 경로와 버전을
확인합니다.

```bash
PDFTOTEXT_COMMAND="$(command -v pdftotext)"
PDFTOPPM_COMMAND="$(command -v pdftoppm)"
"$PDFTOTEXT_COMMAND" -v
"$PDFTOPPM_COMMAND" -v
```

`.env`의 로컬 DB 연결을 사용해 HTTP 서버와 분리된 배치를 실행합니다.

```bash
SPRING_PROFILES_ACTIVE=content-import \
PDFTOTEXT_COMMAND="$PDFTOTEXT_COMMAND" \
PDFTOPPM_COMMAND="$PDFTOPPM_COMMAND" \
./gradlew bootRun
```

기본 입력은 `fixtures/content/manifest.json`, 출력은 Git 제외
`var/content/pages/<manifestSha256>/`입니다. 경로를 바꿔야 할 때만
`CONTENT_IMPORT_MANIFEST`를 배치에 주입하고, `CONTENT_IMPORT_OUTPUT_ROOT`는 `.env`에 설정해 배치와
일반 서버가 같은 콘텐츠 루트를 사용하게 합니다. 초기 manifest는 PDF 100권·400페이지를 검증합니다.
`ai-route-v2` manifest는 [AI 경로 콘텐츠 코퍼스](./ai-route-content-corpus.md)의 도서 구성·페이지 범위·
기존 콘텐츠 보존 조건을 검증하고 전체 페이지 수는 manifest 합계로 계산하며 `dataPolicyVersion`, 임베딩
모델·차원과 페이지별 분석 입력 SHA-256도 결정적 입력으로 고정합니다. 두 버전 모두 SHA-256, 연속 페이지 번호와 TEXT/IMAGE
산출물을 검증한 뒤 도서 메타데이터와 `BookPage`를 한 DB 트랜잭션으로 적재하고 종료합니다. `ai-route-v2`는 기존
대여·소장·내역이 없는 새 시연 DB에 적재하며 분석 메타데이터와 선언한 모델·차원의 임베딩도 `BookPage`와
같은 트랜잭션으로 저장합니다. Embeddings API 호출 전에는
[OpenAI 데이터 정책 프로필](./evidence/openai-data-policy/README.md)에 따라 manifest와 환경 설정의 프로필이
같고 전용 OpenAI 프로젝트의 데이터 제어가 해당 프로필과 같거나 더 엄격한지 확인합니다.

적재 뒤에는 [AI 경로 콘텐츠 코퍼스](./ai-route-content-corpus.md)의 대표 목적 평가 데이터를 비웹으로 실행합니다.
평가는 별도 DB나 평가용 계정·잉크·원장·대여·소장·결제를 만들지 않고 예산과 권한 시나리오를 입력으로
전달합니다. [AI 잉크 경로 PRD의 품질·출시 기준](./prd/ai-ink-route.md#품질과-출시-기준)을 통과하면
콘텐츠 manifest·평가 데이터 Git 리비전, 임베딩 모델·경로 모델·후보 정책·prompt·schema 버전과 지표를 평가 결과에 기록합니다.

초기 manifest 또는 평가를 통과한 `ai-route-v2` 콘텐츠 배치가 끝나면 시연 계정 비밀번호를 `.env`에
주입하고 일반 서버를 시작합니다. 일반 서버가 한 번이라도 HTTP 트래픽을 받은 뒤에는 초기 manifest로 만든
새 DB로 교체하는 롤백을 실행하지 않습니다. 해당 시점 이후의 콘텐츠 버전 마이그레이션과 사용자 기록 보존
롤백은 2차 MVP 범위 밖입니다.

```bash
./gradlew bootRun
```

### OpenAI 데이터·비용 제어

[ADR-0014](./adr/application/0014-use-openai-and-mysql-for-ai-route-generation.md)의 AI 경로를 활성화하기
위한 2차 MVP 구현 전 목표 계약입니다. 현재 애플리케이션은 아직 아래 변수를 바인딩하거나 OpenAI를 호출하지
않습니다. 구현 뒤 다음 환경변수를 사용합니다.

| 변수 | 기본값 | 설명 | 민감정보 |
| --- | --- | --- | --- |
| `AI_ROUTE_ENABLED` | `false` | `true`일 때만 공개 AI 경로 화면·API 등록 | 아니요 |
| `OPENAI_PROJECT_ID` | 없음 | 콘텐츠 적재·평가·일반 생성이 함께 사용하는 전용 프로젝트 ID | 아니요 |
| `OPENAI_API_KEY` | 없음 | 전용 프로젝트 서비스 계정의 서버 API 키 | 예 |
| `OPENAI_DATA_POLICY_VERSION` | 없음 | 실제 프로젝트 조건이 충족함을 확인한 지원 데이터 정책 프로필 ID | 아니요 |

일반 서버는 `AI_ROUTE_ENABLED=false`이면 OpenAI 키 없이 시작하고 AI 경로 화면·API를 등록하지 않습니다.
`true`인데 프로젝트 ID·API 키·데이터 정책 프로필 중 하나가 비어 있으면 외부 호출 전에 시작을 거부합니다.
활성화 시 DB의 지원 도서 프로필과 환경 설정도 같아야 합니다. `ai-route-v2` 콘텐츠 적재와 비웹 평가는 공개
기능 플래그와 관계없이 세 OpenAI 변수를 요구합니다. 적재는 manifest·환경 설정, 평가는 manifest·DB·환경
설정의 프로필이 다르면 실패합니다. 초기 manifest 변환은 세 변수를 요구하지 않습니다.
`.env.example`에는 위 기본값과 빈 변수명만 두고 실제 키는 Git에서 제외된 `.env` 또는 배포 환경의 관리형
secret으로 주입합니다. 키 원문은 브라우저·로그·오류 응답·평가 결과에 기록하지 않습니다.

설정을 주입한 뒤 다음을 확인합니다.

- 콘텐츠 적재·평가·일반 생성은 같은 전용 OpenAI 프로젝트의 서비스 계정 API 키와 지원 데이터 정책
  프로필을 사용합니다. 비민감 프로젝트 ID·프로필을 배포 대상과 함께 관리하고 API 키 원문은 환경 변수로만
  주입합니다.
- OpenAI Platform의 전용 프로젝트 `Limits`에서 월간 spend limit을 설정합니다. 공급자 한도는 적용 전파 중
  소액을 초과할 수 있으므로 절대적인 예산 보증으로 안내하지 않습니다. 구체적인 동작은
  [OpenAI spend limits](https://developers.openai.com/api/docs/guides/spend-limits#understand-hard-limit-behavior)를
  따릅니다. 한도 금액은 변동 가능한 공급자 가격과 배포 예산에 속하므로 PRD에 복사하지 않고, 출시 증거에
  프로젝트 ID·금액·통화·확인 시각과 적용한 가격표 확인일을 기록합니다.
- 출시 직전과 모델·엔드포인트·프로젝트 데이터 제어 변경 시
  [OpenAI 데이터 정책 프로필](./evidence/openai-data-policy/README.md)의 공식 출처를 다시 확인합니다. 내용이
  달라졌으면 날짜별 근거, 사용자 안내와 콘텐츠 지원 조건을 함께 갱신합니다.
- 콘텐츠 적재의 Embeddings API 요청에는 권리·보관 조건을 확인한 페이지 분석 텍스트만 있고, 런타임
  요청에는 정규화한 독서 목적만 있으며 두 입력을 섞지 않는지 확인합니다. 런타임 모델·차원은 콘텐츠
  버전에 묶인 페이지 벡터와 같아야 합니다.
- Responses API 요청에는 독서 목적과 후보 분석 텍스트만 있고 항상 `store=false`이며
  Conversations·Background mode·호스팅 도구를 사용하지 않는지 통합 테스트로 확인합니다. 추가 잉크 예산,
  잔액과 페이지별 대여·소장 상태는 전송하지 않습니다. `store=false`는 응답 상태 저장만 끄며 악용 모니터링과
  프롬프트 캐시 조건은 별도로 확인합니다.
- 공개 AI 시연 콘텐츠와 실제 출판 콘텐츠의 허용 보관 조건·사용자 안내는
  [OpenAI 데이터 정책 프로필](./evidence/openai-data-policy/README.md)과
  [AI 잉크 경로 PRD](./prd/ai-ink-route.md#개인정보와-콘텐츠-보호)를 따릅니다. 실제 적용할 프로젝트의 데이터
  제어와 파생 캐시 조건을 콘텐츠가 허용하지 않으면 AI 경로 지원 대상으로 적재하지 않습니다.
- 콘텐츠 적재 사전 검증에서 외부 전송 권리·지원 프로필이 누락되거나 manifest·환경 설정의 프로필이 다른
  표본을 넣었을 때 Embeddings API 호출과 DB 변경 없이 실패하는지 확인합니다.
- 계정별 일일 생성 한도는 PRD의 `429` 코드와 다음 UTC 날짜까지의 `Retry-After`를 반환합니다. OpenAI의
  지출·사용량 한도 또는 크레딧 소진 오류는 원문 조직·프로젝트·비용 정보를 숨기고
  `503 AI_ROUTE_PROVIDER_BUDGET_UNAVAILABLE`로 변환하며 비-AI 기능은 계속 제공합니다.

PortOne V2 테스트 결제를 확인하려면 `.env`의 `PORTONE_PAYMENT_ENABLED=true`와 테스트 상점·채널 값을
설정하고 위 [브라우저 콘텐츠 보안 정책](#브라우저-콘텐츠-보안-정책)의 화면별 SDK 로딩과 실패 격리를
함께 확인합니다.

검증은 개발 서버와 별도로 다음 명령을 실행합니다.

```bash
./gradlew test
./gradlew check
./gradlew build
```

로컬 개발·테스트 JDBC URL 예시는 다음과 같습니다.

```properties
DB_URL=jdbc:mysql://localhost:3307/ilgeobolkka?allowPublicKeyRetrieval=true&sslMode=DISABLED
TEST_DB_URL=jdbc:mysql://localhost:3307/ilgeobolkka_test?allowPublicKeyRetrieval=true&sslMode=DISABLED
```

Compose 초기화 스크립트는 `${DB_NAME}_test` 데이터베이스를 만들고 로컬 애플리케이션 계정에만 권한을
부여합니다. 기존 볼륨에는 초기화 스크립트가 자동 재실행되지 않으므로 위 `docker compose exec` 명령을
한 번 실행합니다. 모든 Spring 통합 테스트는 `_test`로 끝나는 데이터베이스만 허용합니다.

MySQL을 종료하되 데이터를 유지하려면 `docker compose down`을 실행합니다. 볼륨까지 삭제하는
`docker compose down -v`는 위 최초 기준선 전환처럼 로컬 데이터를 명시적으로 초기화할 때만 사용합니다.

## 4. AWS 운영 연결

운영 배포 환경에는 다음 형식으로 값을 주입합니다. 실제 엔드포인트와 계정·비밀번호를 파일이나 저장소에 기록하지 않습니다.

```text
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:mysql://<RDS-ENDPOINT>:3306/ilgeobolkka?sslMode=VERIFY_IDENTITY
DB_USERNAME=<Secrets Manager에서 주입>
DB_PASSWORD=<Secrets Manager에서 주입>
```

배포 플랫폼에서 Spring Boot 표준 변수를 사용한다면 위 세 값을 각각 `SPRING_DATASOURCE_URL`,
`SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`로 바꾸고 `DB_*`는 주입하지 않습니다.

운영 구성은 다음 조건을 지켜야 합니다.

1. RDS는 public access를 끄고 애플리케이션과 통신 가능한 VPC의 private subnet에 배치합니다.
2. RDS 보안 그룹은 TCP 3306을 인터넷 전체가 아니라 애플리케이션 보안 그룹에서만 허용합니다.
3. 애플리케이션은 RDS master가 아닌 최소 권한 전용 DB 계정을 사용합니다.
4. 계정과 비밀번호는 Secrets Manager에 저장하고 애플리케이션 실행 역할에 해당 secret 조회 권한만 부여합니다.
5. Amazon RDS CA 인증서를 Java runtime truststore에 등록한 뒤 `sslMode=VERIFY_IDENTITY`로 서버 인증서와 호스트 이름을 검증합니다.
6. 로컬과 운영에 같은 Flyway 마이그레이션을 적용하고 운영에서 Hibernate 자동 스키마 변경을 사용하지 않습니다.
7. MVP 운영 환경에서는 PortOne 설정을 주입하지 않고 운영 실결제 API와 화면을 비활성화합니다.

관련 공식 문서:

- [PortOne V2 결제 연동](https://developers.portone.io/opi/ko/integration/start/v2/checkout)
- [PortOne V2 웹훅](https://developers.portone.io/opi/ko/integration/webhook/readme-v2?v=v2)
- [PortOne V2 브라우저 SDK](https://developers.portone.io/sdk/ko/v2-sdk/readme?v=v2)
- [PortOne JVM 서버 SDK](https://central.sonatype.com/artifact/io.portone/server-sdk)
- [Amazon RDS MySQL SSL/TLS 연결](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/mysql-ssl-connections.html)
- [Amazon RDS 인증서와 truststore](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/UsingWithRDS.SSL.html)
- [Amazon RDS 보안 그룹](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Overview.RDSSecurityGroups.html)
- [RDS와 AWS Secrets Manager 자격 증명 관리](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/rds-secrets-manager.html)
- [MySQL Connector/J SSL 모드](https://dev.mysql.com/doc/connector-j/en/connector-j-reference-using-ssl.html)

## 5. 배포 전 게이트

- [ ] 애플리케이션 실행 환경(ECS/Fargate, EC2, Elastic Beanstalk 등)을 결정합니다.
- [ ] RDS를 생성하고 애플리케이션 전용 DB 계정을 준비합니다.
- [ ] Secrets Manager secret과 애플리케이션 실행 역할의 조회 권한을 연결합니다.
- [ ] RDS CA truststore를 배포 이미지 또는 런타임에 설치합니다.
- [x] Flyway 의존성과 현재 [목표 ERD](./erd.md)의 최초 스키마 마이그레이션을 추가합니다. (ADR-0005)
- [x] 로컬 개발 DB와 테스트 전용 DB를 분리하고 테스트 DB 이름을 시작 전에 검증합니다.
- [ ] 로컬·시연 전용 계정 생성 기능이 운영 프로필에서 비활성화되는지 확인합니다.
- [ ] 운영과 동일한 MySQL 버전에서 전체 테스트를 통과시킵니다.
- [ ] 배포 후 TLS 연결과 DB health를 확인합니다.
- [ ] 로컬·시연 환경에서만 PortOne V2 테스트 채널, 서버 재조회와 웹훅 서명 검증을 확인합니다.
- [ ] 운영 환경에서 결제 기능과 PortOne 설정이 비활성화됐는지 확인합니다.

현재 애플리케이션 실행 환경은 아직 결정되지 않았습니다. 실행 환경을 선택한 뒤 해당 서비스의 환경변수·Secrets Manager 연결 절차를 이 문서에 구체화합니다.
