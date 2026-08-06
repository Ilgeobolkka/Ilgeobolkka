# S02 저장 경로 목록·상세 조회

[구현 작업 색인](../README.md)으로 돌아갑니다.

- 권장 담당: 파동 5 / 담당 C
- 선행: [F02 JPA 기반](../foundation/F02-jpa-mapping.md)
- 후속: [S03 현재·삭제](./S03-current-delete.md), [S04 콘텐츠·진행](./S04-content-progress.md),
  [S05 피드백](./S05-feedback.md), [W02 경로 화면](../web/W02-route-detail-page.md)

## 목표

인증 독자의 저장 경로만 10건 고정 페이지로 조회하고, 상세에서는 저장 순서·진행·피드백과 현재 권한으로
재계산한 비용 상태를 반환합니다.

## 정본 링크

- [API 페이지 범위와 정렬](../../../api-spec.md#페이지-범위와-정렬)
- [저장 경로 endpoint](../../../api-spec.md#목표-json-엔드포인트)
- [저장 경로 결과](../../../api-spec.md#저장-경로-결과와-상태-변경)
- [저장과 생명주기 6~8단계](../../../prd/ai-ink-route.md#저장과-생명주기)
- 필수 시나리오: [T-AIR-016](../../../test-strategy.md#5-필수-시나리오)

## 현재 구현 기준선

- 기존 페이지 조회 관례는
  [InkHistoryApiMySqlIntegrationTest](../../../../src/test/java/com/example/ilgeobolkka/ink/InkHistoryApiMySqlIntegrationTest.java)와
  [LibraryApiMySqlIntegrationTest](../../../../src/test/java/com/example/ilgeobolkka/library/LibraryApiMySqlIntegrationTest.java)를
  참고합니다.
- F02 이후 route Entity는 있으나 owner projection·DTO·Controller가 없습니다.

## 입력과 산출물

- endpoint: `GET /api/ai-routes?page={page}`
- endpoint: `GET /api/ai-routes/{routeId}`
- 산출물: `AiRouteQueryFacade`, `AiRouteQueryService`, `AiRouteQueryController`, list/detail projection·DTO
- S03~S05/W02에 넘길 것: owner lookup, 공통 detail DTO와 cost status 계산 API

## 수정 허용 파일

- 새 query Facade·Service·Controller·DTO·projection
- route/current/item Repository의 owner list/detail query
- 기존 ownership/rental Service는 public 조회만 사용하고 수정하지 않음
- 새 `AiRouteQueryApiMySqlIntegrationTest`

## 구현 조건

1. 목록은 readerId 조건과 `createdAt DESC,id DESC`, page 1부터·10건 고정입니다.
2. page 누락·비정수·0 이하는 400, 범위 초과 양수는 200+빈 routes와 정확한 page metadata입니다.
3. 상세는 readerId+routeId로 조회하고 다른 독자는 동일 404입니다.
4. 트랜잭션 안에서 route·items·current·openedAt·completedAt·feedback을 DTO로 완성해 OIV에 의존하지 않습니다.
5. item 순서는 position ASC로 고정하고 저장한 purpose·guide·role·relevance를 변경하지 않습니다.
6. additionalCostStatus만 현재 소장·active rental로 다시 계산하며 조회로 상태를 변경하지 않습니다.
7. 분석 text·embedding·prerequisite graph와 generation fingerprint를 응답하지 않습니다.

## 테스트

- 0·1·10·11 routes의 페이지 metadata, 동시각 id DESC 보조 정렬과 페이지 간 중복 없음
- page invalid/overflow, 빈 계정
- owner 상세 전체 필드·item 순서와 다른 독자 404
- 소장·active rental·미대여 변경에 따른 cost status 재계산과 route 값 불변
- 조회 전후 openedAt·completedAt·feedback·잉크·대여 불변
- 명령: `./gradlew test --tests '*AiRouteQueryApiMySqlIntegrationTest'`

## 제외 범위

- route 저장·현재 변경·삭제
- 콘텐츠 읽기와 progress·feedback 변경
- 내 서재 API 확장·HTML

## 완료 조건

- 두 GET의 페이지·소유권·정렬·DTO 계약이 MySQL 통합 테스트됩니다.
- Entity나 lazy collection이 Controller로 노출되지 않습니다.
- `./gradlew check`가 통과합니다.

## 인계

S03~S05에 owner query와 detail response assembler를, W02에 JSON fixture를 전달합니다. 후속 작업은 목록
정렬·페이지 규칙을 다시 구현하지 않습니다.
