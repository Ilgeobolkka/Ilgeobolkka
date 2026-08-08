# G01 독서 목적·생성 입력 정규화

[구현 작업 색인](../README.md)으로 돌아갑니다.

- 권장 담당: 파동 1 / 담당 C
- 선행: 없음
- 후속: [G02 후보 검색](./G02-candidate-search.md), [G05 멱등 생성 시작](./G05-idempotency-daily-limit.md),
  [G08 HTTP API](./G08-generation-api.md)

## 목표

독서 목적과 비소장 예산·소장 깊이 입력을 공급자·HTTP와 독립적으로 정규화·검증하고 이후 단계가 같은
canonical command를 사용하게 합니다.

## 정본 링크

- [독서 목적 입력](../../../prd/ai-ink-route.md#독서-목적)
- [비소장 도서](../../../prd/ai-ink-route.md#비소장-도서)
- [소장 도서](../../../prd/ai-ink-route.md#소장-도서)
- [API 생성 입력](../../../api-spec.md#생성-입력과-결과)
- 필수 시나리오: [T-AIR-001](../../../test-strategy.md#5-필수-시나리오)

## 현재 구현 기준선

- AI request DTO·정규화기·depth Enum이 없습니다.
- 인증 독자 타입은
  [AuthenticatedReader](../../../../src/main/java/com/example/ilgeobolkka/global/security/AuthenticatedReader.java)로
  구현되어 있습니다.
- 기존 문자열 검증을 AI 목적 정규화에 재사용할 공통 util은 없습니다.

## 입력과 산출물

- 입력: raw purpose, maxAdditionalInk 또는 depth, bookId·contentVersion, 현재 소장 여부·잉크 잔액
- 산출물: `AiRoutePurposeNormalizer`, `AiRouteGenerationCommand`, `AiRouteRequestType`, `AiRouteDepth`
- command: bookId, contentVersion, normalizedPurpose, `INK_BUDGET|maxAdditionalInk` 또는 `OWNED_DEPTH|depth`
  ([G05 지문 입력 순서](./G05-idempotency-daily-limit.md#구현-조건)와 같게 적습니다)
- G05에 넘길 것: fingerprint 입력 순서가 고정된 canonical command

## 수정 허용 파일

- 새 `airoute/service/AiRoutePurposeNormalizer.java`
- 새 `airoute/dto`가 아닌 공급자 중립 command·Enum 파일
- 새 `AiRoutePurposeNormalizerTest`, `AiRouteGenerationCommandTest`

## 구현 조건

1. raw purpose를 NFC로 정규화합니다.
2. Unicode `White_Space` code point 연속 구간을 ASCII 공백 하나로 바꾸고 앞뒤를 제거합니다. Java regex
   `\s`나 `Character.isWhitespace`를 검증 없이 동일하다고 가정하지 않습니다.
3. 정규화 결과를 UTF-16 length가 아니라 code point 1~200개로 검사합니다.
4. 대소문자와 공백 아닌 문자를 바꾸지 않고 raw purpose는 산출물·로그에 남기지 않습니다.
5. 비소장은 depth=null, 예산 `0..balance`; 소장은 budget=null, depth 세 값만 허용합니다.
6. 비소장 기본값은 호출자가 잔액과 함께 요청했을 때만 계산하고 command 생성 뒤 입력을 재해석하지 않습니다.
7. purpose를 HTML·prompt instruction으로 가공하는 메서드를 만들지 않습니다.

## 테스트

- NFC 조합, 여러 Unicode White_Space, emoji·보조 평면을 포함한 code point 200·201 경계
- 빈 문자열·공백 전용, 대소문자 보존, HTML 모양 purpose 보존
- 소장/비소장 배타 입력, 예산 0·잔액·잔액+1, 세 depth와 unknown 값
- 명령: `./gradlew test --tests '*AiRoutePurposeNormalizerTest' --tests '*AiRouteGenerationCommandTest'`

## 제외 범위

- Bean Validation·HTTP status와 Principal 추출
- 소장·잔액 Repository 조회
- fingerprint SHA-256, OpenAI 요청, 화면 client validation

## 완료 조건

- 같은 의미의 whitespace/NFC 입력이 같은 normalizedPurpose와 command를 만듭니다.
- T-AIR-001 입력 경계가 Spring 없이 단위 테스트됩니다.
- `./gradlew check`가 통과하고 기존 공통 문자열 util을 만들지 않습니다.

## 인계

G05·G08 담당자에게 command 생성 API와 validation exception을 전달합니다. 두 작업은 purpose를 다시
정규화하거나 별도 depth Enum을 만들지 않습니다.

`maxAdditionalInk`와 `depth` **동시 입력을 거부하는 책임은 G08 DTO 단독**입니다
([api-spec 생성 입력](../../../api-spec.md#생성-입력과-결과): 함께 보내면 `400 INVALID_INPUT`).
command는 `forInkBudget`·`forOwnedDepth` 두 팩토리가 각각 반대쪽을 `null`로 고정하므로 둘을 함께 가진
값이 애초에 만들어지지 않고, 따라서 command 층에서는 이 오류를 검출할 수 없습니다. DTO가 둘 다 받은
요청에서 한쪽을 버리고 팩토리를 호출하면 계약이 조용히 깨지므로, G08은 팩토리를 부르기 **전에** 거부해야
합니다.

**validation 예외는 두 종류이고 둘 다 `400 INVALID_INPUT`입니다**
([api-spec 목표 오류](../../../api-spec.md#목표-오류)). 목적 문제는 `InvalidAiRoutePurposeException`, 예산·깊이·
콘텐츠 버전 문제는 `InvalidAiRouteGenerationInputException`입니다. G08 핸들러는 두 타입을 모두 잡아야
합니다. 지금은 소비자가 G08 하나뿐이라 공통 부모를 만들지 않았습니다. G05·W01 등에서 같은 매핑이 또
필요해지면 그때 부모 타입을 도입하는 편이 낫습니다.

정규화한 목적은 **보이지 않는 문자를 지우지 않고 보존합니다.** 지문 무결성을 위해 서로 다른 입력을 합치지
않는 것이 우선이기 때문입니다. 그래서 `U+202E`(RLO) 같은 bidi override도 그대로 남을 수 있습니다. 목적은
작성자 본인에게만 보이고 W01·W02가 [PRD 표시 규칙](../../../prd/ai-ink-route.md#독서-목적)대로 Thymeleaf
이스케이프 출력이나 DOM `textContent`로만 렌더링한다는 전제에서 허용한 값입니다. 목적을 다른 독자에게
보이는 화면이 생기면 이 전제를 다시 따져야 합니다.
