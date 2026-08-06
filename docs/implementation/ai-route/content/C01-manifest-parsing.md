# C01 `ai-route-v2` manifest 파싱

[구현 작업 색인](../README.md)으로 돌아갑니다.

- 권장 담당: 파동 3 / 담당 A
- 선행: 없음
- 후속: [C02 메타데이터·그래프 검증](./C02-metadata-graph-validation.md)

## 목표

`contentVersion=initial-v1`인 초기 manifest 계약을 보존하면서 `contentVersion=ai-route-v2`의 구조
메타데이터와 평가 파일을 타입으로
읽고 형식 오류를 외부 호출·파일 변환·DB 접근 전에 거부합니다.

## 정본 링크

- [AI 코퍼스 구조 메타데이터](../../../ai-route-content-corpus.md#구조-메타데이터)
- [AI 코퍼스 품질 평가 데이터](../../../ai-route-content-corpus.md#품질-평가-데이터)
- [2차 fixture 기준선](../../../content-conversion.md#ai-잉크-경로-2차-fixture-기준선)
- 필수 시나리오: [T-AIR-011·012](../../../test-strategy.md#5-필수-시나리오)

## 현재 구현 기준선

- [초기 manifest](../../../../fixtures/content/manifest.json)는 `contentVersion=initial-v1`, `books[]`와
  PDF 정보만 가집니다.
- [ContentBatchConverter](../../../../src/main/java/com/example/ilgeobolkka/contentimport/ContentBatchConverter.java)는
  초기 100권·400페이지 계약만 검증합니다.
- `fixtures/content/ai-route-v2/`와 평가 JSON은 아직 없습니다.

## 입력과 산출물

- 입력: 초기 manifest 또는 `fixtures/content/ai-route-v2/manifest.json`
- 입력: `ai-route-v2/evaluation.json`
- 산출물: `ContentManifest` 공통 식별 타입과 초기/AI version별 명시적 subtype 또는 parser 결과
- 산출물: `AiRouteContentManifest`, `AiRouteEvaluationDataset`과 JSON 형식 검증
- C02에 넘길 것: 파일 경로를 해석하지 않은 불변 manifest·evaluation 객체

## 수정 허용 파일

- 새 `contentimport/manifest` 하위 DTO·parser·형식 validator
- 기존 `ContentBatchConverter`에는 version별 parser 선택 연결만 허용
- 새 `AiRouteContentManifestTest`, 기존 `ContentBatchConverterTest`의 초기 계약 회귀

## 구현 조건

1. `contentVersion`으로 초기 계약과 AI 계약을 명시적으로 분기하고 필드 존재 여부로 추측하지 않습니다.
2. 코퍼스가 정의한 최상위·book·page·prerequisite·evaluation 필드를 빠짐없이 읽습니다.
3. 알 수 없는 필드, 중복 bookId·pageNumber·caseId, null 대신 필요한 빈 배열 위반을 거부합니다.
4. `embeddingDimensions > 0`, 필수 문자열 non-blank, Enum과 숫자 범위 같은 단일 객체 형식만 검사합니다.
5. 파일 존재·SHA, 페이지 간 참조, DAG, 권리·정책 일치는 C02로 넘기고 parser에서 외부 I/O를 섞지 않습니다.
6. 초기 manifest는 `contentVersion=initial-v1`을 필수로 요구하되 OpenAI 필드는 요구하지 않고 기존
   100권·400페이지 테스트를 그대로 통과합니다.

## 테스트

- 정상 최소 AI manifest와 evaluation parse
- 필수 필드 누락, unknown field, duplicate ID, 잘못된 Enum·차원·배타 입력 실패
- 초기 manifest parse 결과와 기존 테스트 회귀
- 명령: `./gradlew test --tests '*AiRouteContentManifestTest' --tests '*ContentBatchConverterTest'`

## 제외 범위

- PDF·분석 입력 파일 존재와 SHA 검증
- 선수 그래프·도서 전체 완전성
- Embeddings 호출과 DB 적재
- 실제 90권 fixture 제작

## 완료 조건

- parser 결과만 보고 초기와 AI version을 구분할 수 있습니다.
- 형식 오류가 파일 변환·Gateway·Repository 호출 전에 실패합니다.
- 초기 manifest 회귀와 `./gradlew check`가 통과합니다.

## 인계

C02 담당자에게 parser 진입점, 불변 DTO와 형식 오류 타입을 전달합니다. C02는 JSON을 다시 읽거나 별도 DTO를
복제하지 않습니다.
