# Q03 통합 회귀·출시 증거

[구현 작업 색인](../README.md)으로 돌아갑니다.

- 권장 담당: 파동 10 / 담당 A가 통합, B·C가 담당 영역 회귀 확인
- 선행: [G08 생성 API](../generation/G08-generation-api.md),
  [W01 생성 화면](../web/W01-generation-page.md), [W02 경로 화면](../web/W02-route-detail-page.md),
  [W03 도서·서재 연결](../web/W03-book-library-integration.md), [Q02 지표·활성화](./Q02-metrics-activation.md)
- 후속: 별도 승인 뒤 정본 구현 상태·Jira·PR 갱신

## 목표

AI leaf 작업과 기존 초기 MVP 전체 회귀를 MySQL·패키징 실행물·브라우저에서 검증하고 기술 dry run과 실제
외부 OpenAI/배포 증거를 구분해 출시 판정 자료를 만듭니다.

## 정본 링크

- [AI 2차 MVP 출시 게이트](../../../test-strategy.md#ai-잉크-경로-2차-mvp)
- [배포 전 게이트](../../../deployment.md#5-배포-전-게이트)
- [OpenAI 데이터·비용 제어](../../../deployment.md#openai-데이터비용-제어)
- [PRD 품질과 출시 기준](../../../prd/ai-ink-route.md#품질과-출시-기준)
- [전체 추적성](../TRACEABILITY.md)

## 현재 구현 기준선

- AI 구현 전 기존 전체 사용자 흐름은
  [CoreUserJourneyMySqlIntegrationTest](../../../../src/test/java/com/example/ilgeobolkka/CoreUserJourneyMySqlIntegrationTest.java)가
  회귀 기준입니다.
- 일반 smoke는 `/api/smoke` 204이며 실제 사용자·브라우저·OpenAI 검증과 동일하지 않습니다.
- PRD 구현 상태는 [PRD 색인](../../../prd/README.md)에서만 관리하고 leaf 완료마다 갱신하지 않습니다.

## 입력과 산출물

- 입력: 모든 leaf test, Q02 평가 report, 데이터 정책 근거, 전용 project spend limit 확인 자료
- 산출물: 전체 명령 결과, 패키징 smoke, 브라우저 시나리오, 외부 호출 여부가 구분된 출시 증거
- 산출물: 실패 leaf ID·재현 명령·정본 기대값 목록
- 별도 승인 시에만 PRD 구현 상태와 배포 가이드의 실제 명령·증거 링크 갱신

## 수정 허용 파일

- 원칙적으로 생산 코드 수정 없음; 실패가 나오면 해당 leaf로 되돌림
- 통합 전용 `AiRouteUserJourneyMySqlIntegrationTest`와 증거 artifact 경로만 추가 가능
- PRD·배포 문서 상태 변경은 사용자 별도 승인 필요

## 검증 순서

1. `docker compose config -q`, `docker compose up -d --wait`로 MySQL 8.4를 준비합니다.
2. `./gradlew test`, `./gradlew check`, `./gradlew build`를 실행합니다.
3. `INV-014~021`, `T-AIR-001~020`과 기존 초기 MVP 필수 시나리오 누락을 추적성 표로 대조합니다.
4. `AI_ROUTE_ENABLED=false` 패키징 서버에서 AI route 미등록·기존 smoke/도서/뷰어/결제를 확인합니다.
5. 설정을 갖춘 활성 서버에서 지원/미지원·owner/다른 독자·생성/저장/읽기/삭제 흐름을 확인합니다.
6. 브라우저에서 도서 상세→생성→polling→저장→서재→경로 읽기→완료→feedback→삭제를 확인합니다.
7. 로그·응답·HTML·artifact에서 key·purpose·분석 text·provider 원문 금지 항목을 검색합니다.
8. 실제 OpenAI 호출·project spend limit·data policy 근거는 실행했다면 기술 test와 별도 증거로 기록합니다.

## 필수 실패 주입

- 미지원·profile mismatch·일일 11번째·provider budget/temporary·invalid output·20초 timeout
- 같은 key 동시 생성, 같은 generation 동시 저장, current 지정/삭제 교차
- content open 전 권한 만료·storage 실패·다른 독자 ID
- 비활성 서버·설정 누락·평가 실패·활성화 transaction rollback

## 제외 범위

- 테스트 실패를 통합 작업에서 임시 patch로 우회
- 실제 OpenAI를 호출하지 않은 결과를 외부 연동 검증으로 표현
- CI 성공을 브라우저·배포·실사용 검증으로 표현
- 커밋·push·PR·Jira·출시 상태 자동 변경

## 완료 조건

- 모든 leaf 완료 조건과 초기 MVP 회귀가 통과합니다.
- package 실행물의 비활성·활성 smoke와 브라우저 핵심 흐름이 구분된 증거로 남습니다.
- 평가 report·정책 근거·spend limit 확인과 코드 revision을 연결합니다.
- 실패가 있으면 관련 leaf로 반환하고 Q03을 완료 처리하지 않습니다.

## 인계

사용자에게 통과·실패·미실행 항목을 분리해 보고합니다. 사용자가 승인한 뒤에만 PRD의 “구현 전” 상태,
Jira 상태, commit·push·PR을 각각 갱신합니다.
