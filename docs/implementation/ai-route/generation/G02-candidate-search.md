# G02 cosine 후보 검색

[구현 작업 색인](../README.md)으로 돌아갑니다.

- 권장 담당: 파동 2 / 담당 C
- 선행: [G01 입력 정규화](./G01-purpose-input.md),
  [GATE-AIR-02 후보 정책](../00-implementation-gates.md#gate-air-02-후보prompt-정책-v1)
- 후속: [G04 경로 조립](./G04-route-assembly.md), [Q01 평가 runner](../release/Q01-evaluation-runner.md)

## 목표

한 도서·콘텐츠 버전의 페이지 vector와 목적 vector를 메모리에서 정확 cosine 비교하고, 확정한 후보 정책
v1에 따라 결정적인 후보 집합을 반환합니다.

## 정본 링크

- [경로 생성 정책](../../../prd/ai-ink-route.md#경로-생성-정책)
- [ADR-0014 정확 비교](../../../adr/application/0014-use-openai-and-mysql-for-ai-route-generation.md#결정)
- [BookPage embedding 모델](../../../erd.md#기존-테이블-확장)
- 필수 시나리오: [T-AIR-002·012](../../../test-strategy.md#5-필수-시나리오)

## 현재 구현 기준선

- vector 검색 코드와 vector DB가 없습니다.
- F02의 BookPage mapping과 GATE-AIR-02의 후보 범위·최소 관련성·동점 기준을 입력으로 사용합니다.
- 평가 정답은 [evaluation.json 계약](../../../ai-route-content-corpus.md#품질-평가-데이터)에만 있으며 runtime
  검색 입력이 아닙니다.

## 입력과 산출물

- 입력: bookId, contentVersion, purpose vector, 같은 version page vector·analysis metadata
- 산출물: `AiRouteCandidateSelector`, `AiRouteCandidate`, `AiRouteCandidatePolicy`
- candidate: pageId·pageNumber·similarity·analysisText reference·prerequisite edge reference
- G04/F05에 넘길 것: 확정 순서의 후보와 candidatePolicyVersion

## 수정 허용 파일

- 새 `airoute/service/candidate`의 selector·입출력·정책 파일
- Entity·Repository·Gateway 파일은 수정하지 않음
- 새 `AiRouteCandidateSelectorTest`

## 구현 조건

1. 목적·페이지 model과 dimensions가 모두 같고 vector가 유한 실수인지 계산 전에 확인합니다.
2. zero norm vector를 similarity 0으로 조용히 처리하지 않고 잘못된 콘텐츠/입력으로 실패합니다.
3. 한 권의 모든 지원 페이지를 메모리에서 exact cosine으로 계산하고 근사 검색·vector DB를 도입하지 않습니다.
4. GATE-AIR-02의 threshold/top-K·동점 보조 정렬을 그대로 적용해 실행마다 같은 순서를 만듭니다.
5. 이미지 페이지도 분석 text와 vector가 있으면 동일하게 후보에 포함합니다.
6. 평가 reference·requiredConcepts·allowedAlternativePageNumbers를 selector 타입이 받을 수 없게 합니다.
7. similarity 백분율을 사용자 DTO로 만들지 않습니다.

## 테스트

- 직교·동일·반대 vector cosine, dimensions·model 불일치, zero norm·비유한 수 실패
- threshold 직전·정확 경계·직후, top-K 경계와 동점 보조 정렬
- TEXT·IMAGE 동일 처리와 다른 contentVersion 혼입 거부
- 같은 입력 반복 결과 동일성, 평가 정답 타입 의존성 부재
- 명령: `./gradlew test --tests '*AiRouteCandidateSelectorTest'`

## 제외 범위

- Embeddings 호출·DB 조회
- 선수 전이 폐쇄, 모델 호출·출력 검증
- 후보 정책 tuning과 평가 수치 계산

## 완료 조건

- 확정 후보 정책 v1의 값·버전과 selector 결과가 테스트로 고정됩니다.
- 한 권 정확 비교 외 검색 인프라가 추가되지 않습니다.
- `./gradlew check`가 통과합니다.

## 인계

G04 담당자에게 후보 순서·similarity 의미·policyVersion을 전달합니다. F05/G07은 selector 내부 계산을
복제하지 않습니다.
