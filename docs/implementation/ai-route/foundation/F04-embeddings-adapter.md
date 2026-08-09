# F04 Embeddings HTTP adapter

[구현 작업 색인](../README.md)으로 돌아갑니다.

- 권장 담당: 파동 2 / 담당 B
- 선행: [F03 OpenAI 설정](./F03-openai-configuration.md)
- 후속: [C03 콘텐츠 embedding](../content/C03-content-embeddings.md), [G07 생성 orchestration](../generation/G07-generation-orchestration.md)

## 목표

페이지 분석 텍스트와 정규화한 독서 목적을 지정 모델·차원의 vector로 변환하는 공급자 adapter를 `infra.openai`에 격리합니다.
후보 검색과 DB 저장은 구현하지 않습니다.

## 정본 링크

- [ADR-0014 모델·저장 결정](../../../adr/application/0014-use-openai-and-mysql-for-ai-route-generation.md#결정)
- [개인정보와 콘텐츠 보호](../../../prd/ai-ink-route.md#개인정보와-콘텐츠-보호)
- [배포 가이드 데이터 경계](../../../deployment.md#openai-데이터비용-제어)
- [OpenAI Embeddings 가이드](https://developers.openai.com/api/docs/guides/embeddings#obtaining-the-embeddings)

## 현재 구현 기준선

- F03이 공통 `openAiRestClient`와 OpenAI 설정 Properties를 제공하고 Embeddings adapter는 아직 없습니다.
- 기존 외부 Gateway 경계는 [PortOnePaymentGateway](../../../../src/main/java/com/example/ilgeobolkka/infra/portone/PortOnePaymentGateway.java)와 [PortOneSdkPaymentGateway](../../../../src/main/java/com/example/ilgeobolkka/infra/portone/PortOneSdkPaymentGateway.java)를 참고합니다.
- `build.gradle`에 OpenAI SDK는 없습니다.

## 입력과 산출물

- 입력: 텍스트 한 종류, model, dimensions
- 산출물: `OpenAiEmbeddingGateway`, `OpenAiHttpEmbeddingGateway`, 공급자 요청·응답 DTO
- 반환: 순서를 보존한 유한 실수 vector와 사용 model·dimensions
- 오류: 예산 한도, 일시 오류, 잘못된 응답의 공급자 중립 분류

## 수정 허용 파일

- 새 `infra/openai` embedding 전용 파일
- F03의 `openAiRestClient`를 주입받아 사용하고 공통 HTTP Bean 설정은 수정하지 않음
- `RestClient.builder()` 등으로 별도 client를 생성해 F03 검증·인증 설정을 우회하지 않음
- 새 `OpenAiHttpEmbeddingGatewayTest`

## 구현 조건

1. 기존 Spring HTTP client와 Jackson을 사용하고 새 SDK 의존성을 추가하지 않습니다.
2. 런타임 호출은 정규화 purpose만, 적재 호출은 한 페이지 `aiAnalysisText`만 받는 명시적 메서드로 분리합니다.
3. OpenAI `dimensions` 파라미터를 요청하고 응답 길이가 요청 차원과 다르면 전체 실패합니다.
4. NaN·Infinity·null·빈 vector와 응답 index 중복·누락을 거부합니다.
5. Authorization·project header는 F03 설정을 사용하고 값이나 요청 text를 로그에 남기지 않습니다.
6. 공급자 429 중 spend·usage·credit 한도와 일반 rate limit을 공개 원문 없이 내부 실패 종류로 구분합니다.

## 테스트

- 가짜 HTTP server에서 endpoint, headers, model, dimensions, 입력 배열과 결과 순서 확인
- purpose 호출에 분석 텍스트 필드가 없고 content 호출에 사용자 상태 필드가 없는지 직렬화 검사
- 차원 불일치·비유한 수·빈 data·5xx·rate limit·budget limit 실패 주입
- 요청·응답·예외 로그에 text·키·project 원문이 없는지 확인
- 명령: `./gradlew test --tests '*OpenAiHttpEmbeddingGatewayTest'`

## 제외 범위

- cosine 계산, candidate top-K, vector DB
- 재시도·20초 전체 제한과 공개 ErrorCode 매핑
- embedding DB 저장과 contentVersion 검증

## 완료 조건

- Gateway가 두 허용 입력 경계를 타입으로 분리하고 같은 응답 검증을 재사용합니다.
- T-AIR-010의 Embeddings 전송·로그 부분이 자동 검증됩니다.
- `./gradlew check`가 통과하고 새 의존성이 없습니다.

## 인계

C03과 G07 담당자에게 Gateway 메서드·내부 오류 종류를 전달합니다. 두 작업은 공급자 DTO나 HTTP client를 직접 참조하지 않습니다.
