# F03 기능 플래그·OpenAI 설정 계약

[구현 작업 색인](../README.md)으로 돌아갑니다.

- 권장 담당: 파동 1 / 담당 B
- 선행: 없음
- 후속: [F04 Embeddings adapter](./F04-embeddings-adapter.md), [F05 Responses adapter](./F05-responses-adapter.md), [G08 생성 API](../generation/G08-generation-api.md)

## 목표

공개 AI 기능 플래그와 전용 OpenAI 프로젝트 설정을 타입 안전하게 바인딩하고, 일반 서버·콘텐츠 배치·평가 실행별로 필요한 설정을 외부 호출 전에 검증합니다.

## 정본 링크

- [배포 가이드 OpenAI 설정](../../../deployment.md#openai-데이터비용-제어)
- [OpenAI 데이터 정책 프로필](../../../evidence/openai-data-policy/README.md)
- [사용자와 지원 도서](../../../prd/ai-ink-route.md#사용자와-지원-도서)
- 필수 시나리오: [T-AIR-014·018](../../../test-strategy.md#5-필수-시나리오)

## 현재 구현 기준선

- [.env.example](../../../../.env.example)에는 네 환경변수 이름이 있지만 애플리케이션은 아직 바인딩하지 않습니다.
- [application.yaml](../../../../src/main/resources/application.yaml)과 [application-content-import.yaml](../../../../src/main/resources/application-content-import.yaml)에 AI 설정 Bean이 없습니다.
- 조건부 외부 adapter 패턴은 [PortOnePaymentConfiguration](../../../../src/main/java/com/example/ilgeobolkka/infra/portone/PortOnePaymentConfiguration.java)를 참고합니다.

## 입력과 산출물

- 입력: `AI_ROUTE_ENABLED`, `OPENAI_PROJECT_ID`, `OPENAI_API_KEY`, `OPENAI_DATA_POLICY_VERSION`
- 산출물: `AiRouteFeatureProperties`, `OpenAiProperties`, `OpenAiConfiguration`
- 산출물: 서버·content-import·evaluation 실행 모드별 검증 메서드와 조건부 Bean 테스트
- F04·F05에 넘길 것: 공통 `openAiRestClient` Bean과 검증된 base URL·projectId·apiKey·dataPolicyVersion 읽기 계약

## 수정 허용 파일

- `global/config` 또는 `infra/openai`의 새 설정·Properties 파일
- 기존 `application*.yaml`, `.env.example`은 정본의 변수명·빈 기본값 연결만 수정
- 새 `OpenAiPropertiesTest`, `AiRouteFeatureFlagIntegrationTest`

## 구현 조건

1. 일반 서버는 `AI_ROUTE_ENABLED=false`일 때 OpenAI 키 없이 부팅하고 AI Controller/Page Bean이 등록될 조건을 false로 제공합니다.
2. 활성 서버는 projectId·apiKey·dataPolicyVersion 중 하나라도 비면 민감값 없이 부팅을 거부합니다.
3. 초기 `initial-v1` content-import는 OpenAI 값 없이 유지하고 OpenAI Gateway를 호출하지 않습니다.
   `content-import`의 별도 검증 진입점은 `OpenAiProperties.validateForContentImport()`이며,
   F03이 제공하는 `openAiRestClient`가 외부 요청 직전에 이를 호출합니다. `contentVersion`은 콘텐츠 적재 작업이 명시적으로 분기합니다.
   `ai-route-v2` content-import와 evaluation은 공개 플래그와 무관하게 세 OpenAI 값을 요구하며,
   evaluation은 `OpenAiProperties.validateForEvaluation()`로 기동 중 검증합니다.
4. API 키를 record `toString`, validation message, 로그, 오류 응답에 포함하지 않습니다.
5. DB·manifest와 dataPolicyVersion 비교는 각 호출 작업이 수행하고 설정 Bean이 임의로 대체하지 않습니다.
6. endpoint·model·prompt를 무제한 환경 옵션으로 추가하지 않습니다.

## 테스트

- 비활성 일반 서버 + 키 없음 부팅 성공
- 활성 일반 서버의 세 설정 누락별 부팅 실패
- 초기 `initial-v1` content-import의 키 없는 기동 성공
- `ai-route-v2` content-import·evaluation 모드의 설정 누락별 외부 호출 전 실패
- 예외·로그 capture에 API 키 원문이 없는지 확인
- 명령: `./gradlew test --tests '*OpenAiPropertiesTest' --tests '*AiRouteFeatureFlagIntegrationTest'`

## 제외 범위

- 실제 Embeddings·Responses 요청·응답 adapter와 공급자 오류 매핑
- Controller 경로 등록과 DB 프로필 일치
- spend limit 원격 조회·주기적 증명

## 완료 조건

- 세 실행 모드의 설정 행렬이 테스트로 고정됩니다.
- F04·F05가 yaml이나 환경변수를 직접 읽지 않고 F03의 `openAiRestClient`와 Properties를 주입받습니다.
- 기존 PortOne 설정과 `./gradlew check`가 회귀 통과합니다.

## 인계

F04·F05 담당자에게 Bean 이름과 검증된 Properties API를 전달합니다.
후속 adapter는 `openAiRestClient`를 주입받아 사용하고 `RestClient.builder()` 등으로 별도 client를 만들어 공통 설정과 검증을 우회하지 않습니다.
설정 필드를 추가하지 않고 필요한 새 설정이 있으면 이 작업으로 되돌립니다.
