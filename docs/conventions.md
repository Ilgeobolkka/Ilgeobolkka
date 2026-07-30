# 읽어볼까 백엔드 구현 컨벤션

이 문서는 Java/Spring 구현에서 반복되는 패키지 배치, 이름, 계층 책임을 정합니다. 제품 정책이나
도메인 용어를 새로 결정하는 문서가 아니며, 아래 정본과 충돌하면 임의로 맞추지 말고 사용자에게
어느 문서를 갱신할지 확인합니다.

## 문서 라우팅

문서별 정본과 읽는 순서는 [`PRD 색인`](./prd/README.md)에서 관리합니다. 에이전트의 공통 행동과 작업
경계는 [`AGENTS.md`](../AGENTS.md), Java/Spring 구현 방식은 이 문서를 따릅니다.

## 패키지 구조

- 기본 패키지는 `com.example.ilgeobolkka`입니다.
- 기능은 기술 계층 전체를 한곳에 모으지 않고 `reading`, `rental`, `ink`, `ownership`, `book`처럼
  도메인별로 묶습니다.
- 한 도메인 안에서는 필요한 계층만 `controller`, `facade`, `service`, `repository`, `dto`, `entity`로
  나눕니다. 도메인의 API를 구현할 때 Facade를 함께 만들되, 아직 구현하지 않는 도메인의 빈 패키지는
  미리 만들지 않습니다.
- 설정·예외 처리·보안처럼 실제로 여러 도메인이 함께 쓰는 코드만 `global`에 둡니다. 한 도메인에서만
  쓰는 코드를 재사용 가능성만으로 `global`에 올리지 않습니다.
- 파일 저장소나 외부 API 같은 외부 시스템 연동은 `infra`에 둡니다. 연동 대상이 정해지기 전에는
  인터페이스나 어댑터를 미리 만들지 않습니다.

예를 들어 페이지 열기 API를 구현한다면 다음 범위에서 시작합니다.

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
[`ADR-0002`](./adr/application/0002-organize-backend-packages-by-domain.md)에 기록합니다.

## 이름

| 대상 | 규칙 | 읽어볼까 예시 |
| --- | --- | --- |
| Controller | `{Domain}Controller` | `ReadingController` |
| Facade | `{Domain}Facade` | `ReadingFacade` |
| Service | `{Domain}Service` | `InkService`, `OwnershipService` |
| Repository | `{Entity}Repository` | `PageRentalRepository`, `BookOwnershipRepository` |
| Entity | 도메인 명사 단수형 | `ReadingSession`, `PageRental`, `InkLedger`, `BookOwnership` |
| Enum | `{Domain}Status`, `{Domain}Type` | `ReadingSessionStatus` |
| Request DTO | `{Action}{Domain}Request` | `OpenPageRequest`, `CreateOwnershipPaymentRequest` |
| Response DTO | `{Action}{Domain}Response` | `OpenPageResponse`, `CompleteOwnershipPaymentResponse` |
| Exception | 실패한 규칙이 드러나는 이름 | `InsufficientInkException`, `InvalidOwnershipPaymentException` |

- 메서드는 `openPage()`, `deductInk()`, `createOwnershipPayment()`, `findBooks()`처럼 행위와 대상을 드러냅니다.
  `process()`, `handle()`, `updateData()`, `check()`처럼 문맥 없이는 의미를 알 수 없는 이름은 피합니다.
- 변수는 `readerId`, `currentInkBalance`처럼 도메인 용어를 그대로 쓰고, `uid`, `amt` 같은 임의
  축약어를 만들지 않습니다.
- 도서 구매·영구 소유처럼 [`CONTEXT.md`](../CONTEXT.md)의 `_Avoid_`에 있는 표현을 코드 이름에도 쓰지 않습니다.

## Controller

Controller는 HTTP 요청과 응답의 경계만 담당합니다.

- Request DTO 검증, 인증된 독자 식별, 같은 도메인의 Facade 호출, Response DTO 변환까지만 수행합니다.
- 비즈니스 규칙, 트랜잭션, Entity 직접 조립, Repository 접근, 외부 시스템 호출을 두지 않습니다.
- Entity를 응답으로 직접 반환하지 않습니다.
- 의존성은 `private final` 필드와 생성자로 주입합니다. Lombok을 사용한다면
  `@RequiredArgsConstructor`까지만 사용하고 `@Autowired` 필드 주입은 사용하지 않습니다.

## Facade와 트랜잭션

- 도메인마다 `{Domain}Facade`를 두고 Controller가 호출하는 유스케이스 진입점으로 사용합니다.
- public 메서드는 `openPage(readerId, request)`처럼 하나의 유스케이스를 표현합니다.
- 상태 변경 트랜잭션은 Facade에서 시작하고 `@Transactional`을 사용합니다. 조회 전용 유스케이스는
  `@Transactional(readOnly = true)`로 의도를 드러냅니다.
- 페이지 열기의 차감, 내역, 대여와 마지막 위치 갱신은
  [`INV-003`](./test-strategy.md#inv-003-차감과-대여의-원자성)에 따라 하나의 트랜잭션에서 처리합니다.
- 도서 원가 결제 결과 반영과 `BookOwnership` 생성은 잉크 차감 트랜잭션과 분리합니다. 검증된 결제
  식별자와 독자·도서 소장 기록은 각각 한 번만 반영하고 부분 성공을 허용하지 않습니다.
- Facade는 필요한 도메인 Service의 public 메서드를 조합하지만 Repository를 직접 참조하거나 도메인
  규칙을 구현하지 않습니다.
- Facade끼리는 호출하지 않습니다. 다른 도메인의 행위가 필요하면 해당 도메인 Service의 public
  메서드를 호출해 흐름과 트랜잭션의 주인이 하나만 남게 합니다.
- Facade는 Spring `@Service`와 생성자 주입을 사용합니다.
- `spring.jpa.open-in-view=false`를 유지합니다. 조회 트랜잭션 안에서 필요한 값을 Response DTO로 변환하고,
  Controller나 Thymeleaf 렌더링 중 지연 로딩에 의존하지 않습니다.

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
- 같은 페이지 열기 요청의 중복 차감 방지는 [`제품 정책`](./prd/product-policy.md#페이지-열기-처리-순서와-원자성)의
  잠금 뒤 확인을 구현하고, 결제·요청 식별자의 중복이 허용되지 않는 곳에는
  [`ERD`](./erd.md)의 MySQL 고유 제약도 적용합니다.
- 테이블, 컬럼, 인덱스, 제약조건을 바꾸는 작업에는 Flyway migration을 함께 추가합니다. 공유·시연·운영
  데이터베이스에 적용된 migration은 수정하지 않고 다음 번호의 migration으로 보정합니다. 공유 전 초기
  기준선을 정리하는 예외는 [ADR-0005](./adr/platform/0005-manage-schema-with-flyway-not-ddl-auto.md)를 따릅니다.
- JPA Entity 변경만으로 운영 스키마 변경을 대신하지 않습니다.

## Entity와 DTO

- Entity와 API Request/Response DTO를 분리합니다.
- Entity에는 넓은 범위의 setter 대신 `rent()`, `deduct()`처럼 유효한 상태 변경만 허용하는 메서드를
  둡니다.
- Lombok의 `@Data`, 클래스 전체 `@Setter`, Entity의 public 전체 필드 생성자는 사용하지 않습니다.
- Controller용 DTO를 Service 내부 `record`로 숨기지 않고 용도별 파일로 분리합니다. 한 계층에서만 쓰는
  타입은 필요해질 때 그 계층 패키지에 둡니다.

## 검증과 오류 응답

- 문자열 형식, 빈 값, 수량 범위 같은 입력 형식은 Request DTO의 Bean Validation과 `@Valid`로 검증합니다.
- 잔액 부족, 대여 만료, 잉크 수명과 온라인 소장 같은 [`제품 정책`](./prd/product-policy.md)은 Service와
  Entity에서 검증합니다.
- 동시 요청에서 반드시 지켜야 하는 불변식은 DB 제약과 트랜잭션으로 다시 보장합니다.
- `@Valid`나 도메인 메서드가 이미 검증한 조건을 Controller의 `try-catch`로 반복하지 않습니다.
- 도메인 예외는 실패한 규칙이 이름에 드러나게 하고 `@RestControllerAdvice`에서 HTTP 오류로 변환합니다.
- 공개 오류 `code`는 예외 클래스 이름에서 자동으로 만들지 않고 명시적인 `ErrorCode` 값으로 고정합니다.
  예외 클래스 이름을 바꿔도 API 계약이 바뀌면 안 됩니다.
- 공통 성공 응답 래퍼를 구현 편의만으로 추가하지 않습니다. HTTP 상태 코드와 오류 형식은 현재
  [`API 계약`](./api-spec.md)을 따릅니다.

## 설정, 시간, 로그

- 접속 주소, 자격 증명처럼 환경에 따라 달라지는 값은 `application.yaml`과 환경변수로 주입하고
  [`AGENTS.md`](../AGENTS.md)의 민감정보 정책을 따릅니다.
- 잉크와 대여·소장의 가격, 기간과 상태 전이는 단순 운영 설정이 아니라
  [`제품 정책`](./prd/product-policy.md)입니다. 의미 있는 이름으로 표현하고 값을 바꿀 때 제품 정책과
  테스트 전략을 함께 검토합니다.
- 애플리케이션과 DB의 저장 시각은 UTC로 통일합니다. 만료와 경과 시간은 `Instant`와 `Duration`을
  우선 사용합니다.
- 페이지 대여는 `serverNow < expiresAt`일 때만 활성이고 `expiresAt`부터 만료입니다. 테스트 가능한
  `Clock`을 주입합니다. `InkAccount`와 잉크 내역에는 시간 경과로 잔액을 만료시키는 필드나 작업을
  만들지 않습니다.
- `/api`와 `/api/**` 요청마다 서버가 UUID `requestId`를 발급하고 응답의 `X-Request-Id`와 로그에 같은 값을
  남깁니다. 요청이 끝나면 MDC의 `requestId`를 제거해 다음 요청으로 전파하지 않습니다.
- 요청 로그는 HTTP method, 쿼리 문자열을 제외한 경로, 상태 코드와 처리 시간만 기록합니다. 실패 로그는
  공개 `ErrorCode`와 예외 타입까지만 기록하고 예외 원문은 기록하지 않습니다.
- 요청·응답 바디, 쿼리 문자열, 헤더와 쿠키는 로그 입력으로 사용하지 않습니다. 비밀번호, 로그인 세션 ID,
  `viewerSessionId`, PortOne secret, DB 접속 정보와 내부 저장소 경로는 로그나 오류 응답에 남기지 않습니다.

## 인증과 세션

- 이메일은 앞뒤 공백을 제거하고 `Locale.ROOT` 기준 소문자로 정규화한 뒤, 빈 값·일반 이메일 형식·최대
  255자를 검증한 값만 저장·조회합니다.
- 비밀번호 원문은 정규화하거나 자르지 않고 검증 후 `DelegatingPasswordEncoder`의 BCrypt 형식으로
  저장합니다. 원문은 필드, 이벤트, 로그와 오류 응답에 남기지 않습니다.
- 비밀번호는 최소 8자·UTF-8 최대 64바이트이며 영문, 숫자, 공백이 아닌 ASCII 특수문자를 각각
  한 개 이상 포함해야 합니다.
- 로그인 성공 시 세션 ID를 교체하고 [`계정과 인증 정책`](./prd/product-policy.md#계정과-인증)의 비활성
  시간을 적용합니다. 로그아웃 시 로그인 세션과 도메인의 `ReadingSession`을 함께 무효화합니다.
- 회원가입은 계정과 0잉크 계좌만 만들고 인증 세션을 만들지 않습니다.
- 새 뷰어에는 서버가 UUID `viewerSessionId`를 발급합니다. 브라우저는 탭별 `sessionStorage`에 보관하고
  페이지 이동·콘텐츠 요청의 `X-Viewer-Session-Id` 헤더로 전달합니다. 이 값만으로 인증하지 않습니다.
- Spring Security의 CSRF 보호를 끄지 않습니다. Thymeleaf 폼은 hidden field, JavaScript `fetch`는
  페이지의 `_csrf`, `_csrf_header` meta 태그에서 읽은 토큰을 요청 헤더로 전달합니다. 로그인·로그아웃
  뒤에는 새 페이지를 렌더링하며 별도 CSRF 토큰 API를 만들지 않습니다.
- 로그인 실패는 이메일 존재 여부와 관계없이 `INVALID_CREDENTIALS`로 통일합니다.

## Thymeleaf 공통 셸과 브라우저 호출

프런트엔드 기준선에는 다음 의존성만 추가합니다.

- `org.springframework.boot:spring-boot-starter-thymeleaf` — 버전은 Spring Boot BOM을 따릅니다.
- `org.webjars:bootstrap:5.3.8` — CSS와 `bootstrap.bundle.min.js`를 애플리케이션에 포함합니다.

Bootstrap 자산은 `/webjars/bootstrap/5.3.8/` 아래의 버전 명시 경로에서 불러옵니다. 버전 없는 WebJar
경로를 위한 `webjars-locator`, Thymeleaf Layout Dialect, React·Vue·HTMX, Node.js·npm·Vite 빌드 단계는
추가하지 않습니다.

공통 셸은 Thymeleaf 기본 fragment로만 구성하고 다음 책임을 가집니다.

- `src/main/resources/templates`의 공통 head·헤더·내비게이션·오류 영역·본문 fragment를 기능별 화면이
  재사용합니다. 정적 CSS와 ES Module은 `src/main/resources/static`에 둡니다.
- head에는 서버가 렌더링한 `_csrf`, `_csrf_header` meta 태그를 두고 상태 변경 Thymeleaf form에는 hidden
  CSRF field를 둡니다.
- 오류 영역은 `role="alert"`와 `aria-live`를 사용합니다. 오류 문자열은 `textContent`로만 출력하고 서버가
  반환한 HTML이나 예외 원문을 삽입하지 않습니다.
- 모든 조작 요소는 접근 가능한 이름과 `:focus-visible` 상태를 제공합니다. 대체 포커스 표시 없이 기본
  outline을 제거하지 않습니다.
- PortOne SDK는 공통 셸이나 공통 JavaScript 모듈에서 import하지 않습니다. 결제 화면의 로딩 책임은
  [PortOne V2 테스트 결제](#portone-v2-테스트-결제)를 따릅니다.

[HTML 화면 경로](./api-spec.md#html-화면-경로)의 전역 내비게이션은 다음처럼 인증 상태에 따라 렌더링합니다.

- 도서 탐색은 항상 표시합니다.
- 비로그인 상태에는 회원가입과 로그인을 표시합니다.
- 로그인 상태에는 잉크, 소장 결제 내역, 내 서재와 로그아웃을 표시합니다.
- 뷰어는 도서 상세·내 서재에서, 소장 결제 시작은 도서 상세에서만 진입합니다.
- 공통 셸은 인증 여부만 사용하고 도메인 Facade나 Repository를 호출하지 않습니다. 사용자 이메일이나
  잉크 잔액처럼 별도 조회가 필요한 값을 공통 셸에서 조회하지 않습니다.
- 로그아웃은 `POST /api/auth/logout` 성공 뒤 `/books`로 이동합니다. 로그인 성공 뒤에도 `/books`를 새로
  렌더링해 교체된 세션의 CSRF 토큰을 사용합니다.

브라우저용 JSON API 호출은 공통 ES Module의 `requestJson(url, options)`로 통일합니다.

- same-origin URL만 허용하고 `credentials: "same-origin"`을 사용합니다.
- `POST`, `PUT`, `PATCH`, `DELETE` 요청에는 페이지 meta의 CSRF header 이름과 토큰을 자동으로
  추가합니다. `GET`, `HEAD`, `OPTIONS`, `TRACE`에는 추가하지 않습니다.
- JSON 요청은 `Content-Type: application/json`을 사용합니다.
- `2xx` JSON 성공은 공통 래퍼 없이 엔드포인트별 DTO를 반환하고 `204 No Content`는 `null`을 반환합니다.
- 실패 JSON의 `code`, `message`와 HTTP 상태, `X-Request-Id`를 `ApiRequestError`에 보존합니다. JSON으로
  해석할 수 없는 실패와 네트워크 오류에는 민감정보가 없는 고정 한국어 안내를 사용합니다.
- `401`, `403`에서 자동 이동하지 않습니다. 로그인 이동이나 재시도 여부는 요청한 기능 화면이 결정합니다.
- 텍스트·이미지 페이지 콘텐츠는 `requestJson`으로 파싱하지 않고 콘텐츠 전용 `fetch` 흐름으로 처리합니다.

## 목록과 콘텐츠 전달

- 목록·검색·내역의 정렬과 페이지 크기는 [`API 계약`](./api-spec.md)을 그대로 구현합니다.
- 검색어는 앞뒤 공백만 제거하고 내부 공백은 보존합니다. `%`, `_`를 SQL 와일드카드가 아닌 일반 문자로
  이스케이프하고 MySQL `utf8mb4_0900_ai_ci` collation으로 영문 대소문자 구분 없는 검색·정렬을
  유지합니다.
- 브라우저에 원본 PDF와 내부 저장소 주소를 노출하지 않습니다. same-origin 콘텐츠 Controller가 인증된
  독자의 현재 `ReadingSession`, 요청 페이지와 온라인 소장 또는 활성·신규 대여를 검증한 뒤 텍스트 또는
  이미지 콘텐츠를 전달합니다.
- 페이지 콘텐츠 응답은 개인 열람 자산이므로 공유 캐시에 저장하지 않게
  `Cache-Control: private, no-store`를 사용합니다.

## PortOne V2 테스트 결제

- 브라우저는 별도 Node.js 빌드 없이 다음 공식 ESM CDN 모듈을 사용합니다.

```javascript
import * as PortOne from "https://cdn.portone.io/v2/browser-sdk.esm.js";
```

- PortOne JVM SDK 의존성은 `io.portone:server-sdk:0.24.0`으로 고정하며, 서버 결제 재조회 어댑터에서만
  사용합니다.
- PortOne 서버 Client와 웹훅 서명 검증 어댑터는 `infra`에 두고, Controller나 Entity가 SDK 타입에
  의존하지 않게 합니다.
- 잉크 이용권과 소장 결제는 별도 Facade·Entity·API로 유지하고 PortOne 조회·웹훅 검증 Client만
  공통으로 사용합니다.
- 준비 요청은 UUID `paymentId`, 예상 금액과 `PENDING` 시도를 먼저 저장합니다. PortOne 외부 조회를 긴
  DB 트랜잭션 안에서 수행하지 않고 조회 결과를 얻은 뒤 짧은 멱등 완료 트랜잭션을 시작합니다.
- 소장 결제 준비는 `InkAccount`를 잠그고 이미 소장했으면 거부하며, 같은 독자·도서의 `PENDING`이 있으면
  기존 준비 정보를 반환합니다. 기존 시도가 `FAILED`일 때만 새 `paymentId`를 만듭니다.
- 소장 결제 완료도 `InkAccount`를 잠가 신규 페이지 대여와 순서를 정합니다. 먼저 완료된 대여 차감은
  이후 소장되더라도 환불하지 않습니다.
- 브라우저 완료 Controller와 웹훅 Controller는 같은 완료 유스케이스를 호출합니다. 웹훅은
  `/api/webhooks/portone` 경로만 CSRF 예외로 두고 PortOne V2 서명 검증을 필수로 합니다.
- `Transaction.Paid`와 `Transaction.Failed`만 서버 재조회 뒤 상태를 바꿉니다. 정상 서명의 다른 유형은
  `200`, 서명 실패는 `400`, PortOne 조회나 내부 일시 오류는 재전송을 위해 `5xx`로 응답합니다.
- 최종 실패와 금액·통화·식별자 불일치만 `FAILED`로 기록하고 조회 장애·미완료 상태는 `PENDING`을 유지합니다.
- `PORTONE_API_SECRET`과 `PORTONE_WEBHOOK_SECRET`은 서버 환경에만 두며 응답·HTML·JavaScript·로그에
  포함하지 않습니다.
- MVP에서는 테스트 채널의 공통 결제 필드만 사용하고 PG사 전용 기능, 운영 실결제, 취소·환불을
  구현하지 않습니다.

## 테스트

- 테스트 범위와 실행 명령은 `test` 스킬, 필수 시나리오는
  [`docs/test-strategy.md`](./test-strategy.md)를 따릅니다.
- 테스트 메서드는 `활성_대여_페이지를_재열람하면_잉크를_차감하지_않는다()`처럼 조건과 결과가 드러나는
  한글 이름을 사용합니다. 프레임워크가 생성한 기본 smoke test 이름은 예외로 둡니다.
- Facade 테스트는 조합된 유스케이스의 정상·실패 흐름과 트랜잭션 원자성을 검증합니다.
- 도메인 규칙은 빠른 단위 테스트로, JPA·트랜잭션·동시성·고유 제약, 활성 대여 재사용과 만료 후 재대여
  경계, 결제 상태·식별자 중복과 소장·잉크 분리는 MySQL 통합 테스트로 검증합니다.
- 시간 경계 테스트에는 고정된 `Instant`나 주입한 `Clock`을 사용하고 `now()`에 결과가 흔들리지 않게
  합니다.
- 잉크 무기한 보존 테스트는 `Clock`을 수년 뒤로 이동해도 잔액과 내역이 유지되는지 확인합니다.

## 주석과 과한 설계 제한

- Javadoc은 페이지 열기의 잉크 차감·대여 부수효과, 원자성, 던지는 예외처럼 코드 이름만으로 알 수 없는
  "왜"를 설명할 때만 작성합니다. getter, 단순 위임, 자명한 Controller 매핑에는 달지 않습니다.
- 중복 차감 문제는 우선 `InkAccount` 행 잠금, 잠금 뒤 활성 대여 확인과 필요한 MySQL 고유 제약으로
  해결합니다. 증거 없이 Redis 분산 락이나 메시지 브로커를 더하지 않으며, 새 인프라가 필요해지면 실패
  시나리오와 트레이드오프를 ADR로 먼저 남깁니다.
