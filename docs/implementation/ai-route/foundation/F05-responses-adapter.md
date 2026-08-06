# F05 Responses HTTP adapter

[구현 작업 색인](../README.md)으로 돌아갑니다.

- 권장 담당: 파동 3 / 담당 B
- 선행: [F03 OpenAI 설정](./F03-openai-configuration.md),
  [GATE-AIR-02 후보·prompt 정책](../00-implementation-gates.md#gate-air-02-후보prompt-정책-v1)
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
- F03이 공통 설정과 HTTP Bean을 제공하고 GATE-AIR-02가 prompt·schema version을 확정해야 합니다.
- 외부 모델 결과의 최종 신뢰 경계는 G03이 소유합니다.

## 입력과 산출물

- 입력: normalizedPurpose, 같은 book·contentVersion의 candidate pageNumber·analysisText·검증된 선수 edge
- 산출물: `OpenAiRouteGateway`, `OpenAiHttpRouteGateway`, 공급자 DTO, `ModelRouteProposal`
- proposal 필드: pageNumber, `HIGH|MEDIUM`, prerequisite boolean,
  `PREREQUISITE|CORE|EXAMPLE|COUNTERPOINT|CONCLUSION`
- 오류: budget, temporary, timeout/incomplete, refusal, malformed response의 공급자 중립 분류

## 수정 허용 파일

- 새 `infra/openai` route 전용 Gateway·adapter·DTO·prompt/schema resource
- F03 공통 설정 파일과 F04 embedding 파일은 수정하지 않음
- 새 `OpenAiHttpRouteGatewayTest`

## 구현 조건

1. 요청은 ADR의 route model, `store=false`, `text.format` strict JSON Schema를 사용합니다.
2. schema는 위 proposal 필드만 허용하고 `additionalProperties=false`로 둡니다.
3. 독자 ID, 예산, 잉크, 대여·소장·결제·세션, 평가 정답을 입력 DTO가 받을 수 없게 합니다.
4. 가이드·비용·예상 시간과 자유 문구를 모델 출력 schema에 넣지 않습니다.
5. Conversations, previous response, Background mode, streaming, hosted tools를 사용하지 않습니다.
6. `incomplete`, refusal, output item 누락·복수 message·schema parse 실패를 성공 proposal로 바꾸지 않습니다.
7. 요청·응답 원문과 분석 텍스트를 로그·예외에 포함하지 않습니다.

## 테스트

- 가짜 HTTP server에서 model, `store=false`, `text.format`, strict schema와 금지 필드 부재 확인
- 정상 proposal Enum·순서 parse
- 자유 필드·알 수 없는 Enum·incomplete·refusal·malformed JSON·5xx·budget limit 실패 주입
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
원문을 다시 parse하지 않습니다.
