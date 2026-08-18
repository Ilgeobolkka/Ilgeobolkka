# AI 잉크 경로 구현 작업 색인

[PRD 색인](../../prd/README.md)으로 돌아갑니다. 이 폴더는 구현 완료된 AI 잉크 경로 2차 MVP를 leaf 작업으로
나눈 실행 이력과 회귀 안내입니다. 정책·API·스키마·테스트 값과 현재 구현 상태의 정본이 아닙니다.

## 먼저 읽을 문서

1. [PRD 색인](../../prd/README.md)에서 제품·API·ERD·테스트 정본을 확인합니다.
2. [결정 게이트](./00-implementation-gates.md)에서 대상 작업에 적용한 중단 조건을 확인합니다.
3. 이 색인의 선행 작업과 공유 파일 소유권을 확인합니다.
4. 아래 leaf 작업 문서 **한 개만** AI에게 전달합니다.
5. 요구사항·필수 시나리오의 전체 연결은 [추적성 표](./TRACEABILITY.md)에서 확인합니다.

Jira에는 담당자·일정·상태와 leaf 문서 링크만 둡니다. 정책 수치·상태 전이·오류 계약은 저장소 정본에만
둡니다. leaf 문서와 정본이 다르면 정본을 우선하고 어느 문서를 갱신할지 사용자에게 확인합니다.

## 작업 크기 규칙

- leaf 문서 하나는 하나의 작업 브랜치·PR과 한 명의 주 담당 AI를 기준으로 합니다.
- 한 작업은 주 생산 코드 경계 하나와 대표 통합 테스트 하나를 넘지 않게 합니다.
- 예상 작업이 5시간을 넘거나 공유 파일 소유권 두 개 이상을 동시에 요구하면 구현 전에 다시 분리합니다.
- 선행 작업의 산출물이 없으면 임시 중복 타입·가짜 영속 모델을 만들지 않고 중단합니다.
- 완료된 기존 도서·잉크·대여·소장·뷰어·서재 유스케이스는 재구현하지 않습니다.
- 커밋·push·PR·정본의 구현 상태 변경은 각각 별도 승인입니다.

## Leaf 작업 목록

### 기반·외부 연동

| ID | 작업 | 선행 | 핵심 산출물 |
| --- | --- | --- | --- |
| F01 | [AI 목표 스키마 migration](./foundation/F01-schema-migration.md) | [해제된 `initial-v1` 정책](../../content-conversion.md#초기-mvp-시연-pdf-기준선) | V2+ migration과 스키마 테스트 |
| F02 | [JPA Entity·Repository 기반](./foundation/F02-jpa-mapping.md) | F01 | AI Entity·Repository와 매핑 테스트 |
| F03 | [기능 플래그·OpenAI 설정 계약](./foundation/F03-openai-configuration.md) | 없음 | 조건부 Bean과 설정 검증 |
| F04 | [Embeddings HTTP adapter](./foundation/F04-embeddings-adapter.md) | F03 | 목적·분석 텍스트 embedding Gateway |
| F05 | [Responses HTTP adapter](./foundation/F05-responses-adapter.md) | F03, [해제된 후보·prompt 정책 v2](../../prd/ai-ink-route.md#후보prompt-정책-v2) | strict 구조화 출력 Gateway |

### 콘텐츠 적재

| ID | 작업 | 선행 | 핵심 산출물 |
| --- | --- | --- | --- |
| C01 | [`ai-route-v2` manifest 파싱](./content/C01-manifest-parsing.md) | 없음 | manifest DTO·형식 검증 |
| C02 | [AI 메타데이터·선수 그래프 검증](./content/C02-metadata-graph-validation.md) | C01 | 전체 사전 검증과 DAG validator |
| C03 | [페이지 분석 텍스트 embedding 생성](./content/C03-content-embeddings.md) | C02, F04 | 검증된 페이지 vector batch |
| C04 | [AI 콘텐츠 원자적 적재](./content/C04-atomic-import.md) | C03, F02 | Book·BookPage·선수 관계 한 트랜잭션 적재 |

### 경로 생성

| ID | 작업 | 선행 | 핵심 산출물 |
| --- | --- | --- | --- |
| G01 | [독서 목적·생성 입력 정규화](./generation/G01-purpose-input.md) | 없음 | 정규화기와 공급자 중립 command |
| G02 | [cosine 후보 검색](./generation/G02-candidate-search.md) | G01, [해제된 후보 정책 v2](../../prd/ai-ink-route.md#후보prompt-정책-v2) | 후보 정렬·허용 후보 집합 |
| G03 | [모델 출력 검증](./generation/G03-output-validation.md) | F05, [해제된 후보·prompt 정책 v2](../../prd/ai-ink-route.md#후보prompt-정책-v2) | 전체 거부형 output validator |
| G04 | [예산·선수·가이드 경로 조립](./generation/G04-route-assembly.md) | G02, G03 | ROUTE·NO_ROUTE 결정 엔진 |
| G05 | [멱등 생성 시작·일일 한도](./generation/G05-idempotency-daily-limit.md) | F02, G01 | generation 시작과 UTC 10회 원자성 |
| G06 | [생성 완료·실패·만료 복구](./generation/G06-generation-lifecycle.md) | G05 | 상태 전이·15분 정리·중단 복구 |
| G07 | [생성 orchestration](./generation/G07-generation-orchestration.md) | F04, F05, G04, G06 | 트랜잭션 밖 외부 호출 Facade |
| G08 | [생성·조회 HTTP API](./generation/G08-generation-api.md) | G07 | POST·GET, ErrorCode와 보안 경계 |

### 저장 경로·열람

| ID | 작업 | 선행 | 핵심 산출물 |
| --- | --- | --- | --- |
| S01 | [`generationId` 단일 저장](./saved-route/S01-save-generation.md) | F02, G06, [해제된 저장 거부 오류 계약](../../api-spec.md#저장-경로-결과와-상태-변경) | 원자적 route 저장과 SAVED 상태 |
| S02 | [저장 경로 목록·상세 조회](./saved-route/S02-route-query.md) | F02 | 소유자 한정 목록·상세 DTO |
| S03 | [현재 경로 지정·삭제](./saved-route/S03-current-delete.md) | S01, S02 | 현재 경로 직렬화와 CONSUMED 전이 |
| S04 | [경로 콘텐츠 제공·진행](./saved-route/S04-content-progress.md) | S02 | 기존 열기 뒤 콘텐츠·openedAt 원자성 |
| S05 | [완료 경로 피드백](./saved-route/S05-feedback.md) | S02 | 선택형 feedback 생성·변경 |

### 웹 화면

| ID | 작업 | 선행 | 핵심 산출물 |
| --- | --- | --- | --- |
| W01 | [AI 경로 생성 화면](./web/W01-generation-page.md) | G08, S01 | 입력·polling·미리보기·저장 화면 |
| W02 | [저장 경로 상세 화면](./web/W02-route-detail-page.md) | [S02](./saved-route/S02-route-query.md), [S03](./saved-route/S03-current-delete.md), [S04](./saved-route/S04-content-progress.md), [S05](./saved-route/S05-feedback.md) | 열람·현재 지정·삭제·피드백 화면 |
| W03 | [도서 상세·내 서재 연결](./web/W03-book-library-integration.md) | W01, S02, S03 | 지원 링크와 현재 경로 요약 |

### 평가·출시

| ID | 작업 | 선행 | 핵심 산출물 |
| --- | --- | --- | --- |
| Q01 | [비웹 평가 runner](./release/Q01-evaluation-runner.md) | C04, G07 | 사용자 상태 없는 동일 엔진 실행 |
| Q02 | [평가 지표·지원 활성화](./release/Q02-metrics-activation.md) | Q01, [해제된 재평가와 지원 활성화 순서](../../prd/ai-ink-route.md#재평가와-지원-활성화-순서) | 지표 artifact와 후보 DB별 원자적 활성화 |
| Q03 | [통합 회귀·출시 증거](./release/Q03-release-regression.md) | G08, [W01](./web/W01-generation-page.md), [W02](./web/W02-route-detail-page.md), [W03](./web/W03-book-library-integration.md), Q02 | 전체 테스트·패키징·smoke 증거 |

## 3인 병렬 작업 파동

같은 행은 동시에 진행할 수 있습니다. 앞 행의 산출물이 모두 병합된 뒤 다음 행을 시작합니다. A·B·C는
사람 이름이 아니라 충돌을 피하기 위한 권장 담당 슬롯입니다.

| 파동 | 담당 A | 담당 B | 담당 C |
| --- | --- | --- | --- |
| 0 | [GATE-AIR-01 해제](./00-implementation-gates.md#gate-air-01-초기-콘텐츠-버전-해제) | [GATE-AIR-02 해제](../../prd/ai-ink-route.md#후보prompt-정책-v2) | [GATE-AIR-03 해제](./00-implementation-gates.md#gate-air-03-저장-전-권한-변동-오류) · [GATE-AIR-04 해제](./00-implementation-gates.md#gate-air-04-재평가-중-공개-지원-상태) |
| 1 | F01 스키마 | F03 설정 | G01 입력 정규화 |
| 2 | F02 JPA | F04 Embeddings | G02 후보 검색 |
| 3 | C01 manifest | F05 Responses | G05 멱등·일일 한도 |
| 4 | C02 그래프 검증 | G03 출력 검증 | G06 생명주기 |
| 5 | C03 콘텐츠 embedding | G04 경로 조립 | S02 경로 조회 |
| 6 | C04 원자적 적재 | G07 orchestration | S01 단일 저장 |
| 7 | Q01 평가 runner | G08 생성 API | S03 현재·삭제 |
| 8 | S04 콘텐츠·진행 | W01 생성 화면 | S05 피드백 |
| 9 | Q02 지표·활성화 | W02 경로 화면 | W03 도서·서재 연결 |
| 10 | Q03 통합·출시 | 담당 영역 회귀 확인 | 담당 영역 회귀 확인 |

파동 안에서도 선행 산출물이 실제 병합되지 않았으면 시작하지 않습니다. 특히 F05와 G03, G06과 S01,
S04·S05와 W02 사이에는 문서 계약뿐 아니라 컴파일 가능한 산출물이 필요합니다.

## 공유 파일 소유권

| 공유 경계 | 단일 소유 작업 | 다른 작업의 규칙 |
| --- | --- | --- |
| Flyway migration | F01 | 후속 작업은 기존 migration을 수정하지 않음 |
| `Book`, `BookPage`, AI Entity·Repository | F02 | 후속 작업은 매핑을 바꾸지 않고, leaf에 명시된 전용 Repository 조회만 추가 |
| `application*.yaml`, `.env.example`, OpenAI 조건부 설정·공통 HTTP Bean | F03 | F04·F05는 설정 필드를 추가하거나 별도 HTTP client를 생성하지 않고 F03 Bean을 주입받아 사용 |
| `contentimport` | C01~C04 담당 A | 다른 담당자는 manifest 타입을 복제하지 않음 |
| generation Facade | G07 | G08은 Facade 호출만 하고 orchestration 추가 금지 |
| `ErrorCode`, `GlobalExceptionHandler`, `SecurityConfig` | G08 | 각 작업은 **자기가 실제로 반환하는 공개 오류 코드와 그 예외 매핑만** 추가하고, **이미 정의된 코드는 재사용하고 다시 추가하지 않습니다**(같은 코드를 여러 endpoint가 반환하면 먼저 진행하는 작업이 정의). 그 밖의 변경(다른 작업 코드·`SecurityConfig`·공통 구조)은 G08에 인계. 단, W01은 2026-08-14 사용자 승인에 따라 `GET /books/{bookId}/ai-route`를 보호하는 matcher만 추가할 수 있습니다. 예: S01은 G08보다 앞 파동이므로 자기 endpoint의 `AI_ROUTE_ENTITLEMENT_CHANGED`·`AI_ROUTE_CONTENT_CHANGED`·`AI_ROUTE_GENERATION_CONSUMED`를 정의하고, 뒤따르는 G08은 그중 자기도 반환하는 코드를 재사용 |
| 저장 경로 Controller | S01~S05의 문서별 별도 Controller | 공용 거대 Controller로 합치지 않음 |
| 새 AI 화면 | W01·W02 각자 | W03은 새 화면 파일을 수정하지 않음 |
| `book-detail.html`, `library.html` | W03 | 다른 웹 작업은 링크 자리만 계약으로 전달 |
| PRD 구현 상태·출시 증거 링크 | Q03 | 개별 작업이 완료 상태를 먼저 바꾸지 않음 |

## 공통 구현 경계

- MySQL·Flyway를 사용하고 `V1__create_core_domain_tables.sql`을 수정하지 않습니다.
- Controller는 `AuthenticatedReader`의 readerId를 사용하고 같은 도메인의 Facade만 호출합니다.
- OpenAI·파일 I/O는 DB 트랜잭션 밖에서 수행합니다.
- 생성·미리보기·저장은 잉크·대여·소장·열람 세션·서재 위치를 변경하지 않습니다.
- 다른 독자의 `generationId`·`routeId`는 존재 여부를 구분하지 않고 `404 RESOURCE_NOT_FOUND`로 처리합니다.
- 독서 목적, 외부 요청·응답 원문, 분석 텍스트, API 키와 사용자 상태를 로그에 남기지 않습니다.
- 새 라이브러리는 추가하지 않습니다. 필요하면 구현 전에 별도 승인을 받습니다.
- 코드 변경은 대응 테스트를 먼저 만들고 변경 테스트, `./gradlew check`, 필요한 `./gradlew build`·실행
  확인까지 완료합니다.

## AI에 전달할 공통 지시문

아래 문장과 leaf 문서 한 개를 함께 전달합니다.

> `docs/prd/README.md`에서 연결된 정본을 먼저 읽고, 지정한 leaf 구현 가이드 한 개만 구현하라. 선행
> 산출물과 결정 게이트를 확인하고 없으면 추측하지 말고 중단해 보고하라. 문서의 수정 허용 파일 밖 작업,
> 완료된 기존 기능 재구현, 무관한 리팩터링을 하지 마라. 대응 테스트와 검증을 완료하되 커밋·push·PR과
> 정본 상태 변경은 하지 마라.
