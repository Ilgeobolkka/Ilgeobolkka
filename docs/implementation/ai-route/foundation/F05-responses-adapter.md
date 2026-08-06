# F05 Responses HTTP adapter

[구현 작업 색인](../README.md)으로 돌아갑니다.

- 권장 담당: 파동 3 / 담당 B
- 선행: [F03 OpenAI 설정](./F03-openai-configuration.md),
  [해제된 후보·prompt 정책 v1](../../../prd/ai-ink-route.md#후보prompt-정책-v1)
- 후속: [G03 출력 검증](../generation/G03-output-validation.md),
  [G07 생성 orchestration](../generation/G07-generation-orchestration.md)

## 목표

정규화 목적과 서버 후보만 전송하고 strict 구조화 값만 반환하는 Responses adapter를 구현합니다. 서버
후보 검증·예산·가이드 조립은 이 작업에 넣지 않습니다.

## 정본 링크

- [ADR-0014 OpenAI 결정](../../../adr/application/0014-use-openai-and-mysql-for-ai-route-generation.md#결정)
- [경로 생성 정책](../../../prd/ai-ink-route.md#경로-생성-정책)
- [개인정보와 콘텐츠 보호](../../../prd/ai-ink-route.md#개인정보와-콘텐츠-보호)
- [Responses 차이](https://developers.openai.com/api/docs/guides/migrate-to-responses#additional-differences)
- [Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs)

## 현재 구현 기준선

- Responses adapter·prompt resource·strict schema가 없습니다.
- F03이 공통 설정과 HTTP Bean을 제공하고 정본이 prompt·schema resource와 SHA-256 version 계산을 확정했습니다.
- 외부 모델 결과의 최종 신뢰 경계는 G03이 소유합니다.

## 입력과 산출물

- 입력: normalizedPurpose, `air-candidate-v1`의 확정 순서 candidate pageNumber·analysisText·검증된 선수 edge
- 산출물: `OpenAiRouteGateway`, `OpenAiHttpRouteGateway`, 공급자 DTO, `ModelRouteProposal`, `promptVersion`, `schemaVersion`
- proposal 필드: pageNumber, `HIGH|MEDIUM`, prerequisite boolean,
  `PREREQUISITE|CORE|EXAMPLE|COUNTERPOINT|CONCLUSION`
- 오류: budget, temporary, timeout/incomplete, refusal, malformed response의 공급자 중립 분류

## 수정 허용 파일

- 새 `infra/openai` route 전용 Gateway·adapter·DTO·prompt/schema resource
- F03 공통 설정 파일과 F04 embedding 파일은 수정하지 않음
- 새 `OpenAiHttpRouteGatewayTest`

## 구현 조건

1. 요청은 ADR의 route model, `store=false`, `text.format.type=json_schema`,
   `text.format.name=ai_route_proposal_v1`, `strict=true`를 사용합니다.
2. prompt와 schema는 정본의 두 classpath resource만 읽습니다.
   각 UTF-8 원본 byte 전체의 SHA-256을 별도 정규화 없이 계산해 논리 버전 뒤에 64자리 소문자 hex로 붙입니다.
3. schema 최상위는 필수 `items` 배열 하나와 `additionalProperties=false`이고 `minItems=1`, `maxItems=72`입니다.
   각 item의 pageNumber는 `type=integer`, `minimum=1`인 양의 정수이고
   relevance·prerequisite·role을 모두 필수로 가지며 `additionalProperties=false`입니다.
4. 독자 ID, 예산, 잉크, 대여·소장·결제·세션, 평가 정답을 입력 DTO가 받을 수 없게 합니다.
5. 가이드·비용·예상 시간과 자유 문구를 모델 출력 schema에 넣지 않습니다.
6. Conversations, previous response, Background mode, streaming, hosted tools를 사용하지 않습니다.
7. `incomplete`, refusal, output item 누락·복수 message·schema parse 실패를 성공 proposal로 바꾸지 않습니다.
   완료 응답의 message 누락·복수와 schema parse 실패는 G07이 판정할 재시도 가능한 malformed output으로,
   `incomplete`와 refusal은 재시도 불가 실패로 분류합니다.
8. 요청·응답 원문과 분석 텍스트를 로그·예외에 포함하지 않습니다.

## 테스트

- 가짜 HTTP server에서 model, `store=false`, `text.format.type=json_schema`,
  `text.format.name=ai_route_proposal_v1`, `strict=true`, `items` 1~72개, pageNumber의
  `type=integer`·`minimum=1`, 모든 필수 필드와 양쪽 `additionalProperties=false` 확인
- 두 resource의 UTF-8 byte SHA-256과 `air-route-prompt-v1:sha256:...`,
  `air-route-schema-v1:sha256:...` 형식, 공백 변경 시 version 변경 확인
- 정상 proposal Enum·순서 parse
- pageNumber 0·음수, 자유 필드·알 수 없는 Enum·incomplete·refusal·malformed JSON·5xx·budget limit 실패 주입
- 완료 응답의 message 0개·2개가 재시도 가능한 malformed output으로 분류되는지 확인
- 로그 capture의 목적·분석 텍스트·응답 원문 비노출 확인
- 명령: `./gradlew test --tests '*OpenAiHttpRouteGatewayTest'`

## 제외 범위

- 후보 선정, 허용 집합·선수 순서·중복·예산 검증
- 한 번의 검증 재시도와 20초 orchestration
- 공개 API ErrorCode 변환

## 완료 조건

- F05의 출력은 공급자 DTO가 아닌 고정된 `ModelRouteProposal`입니다.
- T-AIR-003·010의 외부 schema·데이터 경계 부분이 자동 검증됩니다.
- `./gradlew check`가 통과하고 F03·F04 파일과 새 의존성을 건드리지 않습니다.

## 인계

G03 담당자에게 proposal 타입과 실패 종류, prompt·schema version 계산 결과를 전달합니다. G03은 HTTP 응답
원문을 다시 parse하지 않습니다. G07은 같은 version과 입력 snapshot으로만 malformed output을 재시도합니다.
