# S03 현재 경로 지정·삭제

[구현 작업 색인](../README.md)으로 돌아갑니다.

- 권장 담당: 파동 7 / 담당 C
- 선행: [S01 단일 저장](./S01-save-generation.md), [S02 경로 조회](./S02-route-query.md)
- 후속: [W02 경로 화면](../web/W02-route-detail-page.md), [W03 서재 연결](../web/W03-book-library-integration.md)

## 목표

같은 독자·도서의 현재 route를 하나로 직렬화하고, route 삭제 시 후속 current 선택과 generation CONSUMED
전이를 한 transaction으로 처리합니다.

## 정본 링크

- [저장과 생명주기 6·10단계](../../../prd/ai-ink-route.md#저장과-생명주기)
- [저장 경로 상태 변경](../../../api-spec.md#저장-경로-결과와-상태-변경)
- [ERD ai_route_current](../../../erd.md#새-테이블)
- [ERD 삭제 경계](../../../erd.md#목표-트랜잭션과-삭제-경계)
- 필수 시나리오: [T-AIR-005·016·019](../../../test-strategy.md#5-필수-시나리오)

## 현재 구현 기준선

- S01이 current upsert/lock과 SAVED generation을, S02가 owner detail DTO를 제공합니다.
- current 변경·삭제 Controller와 CONSUMED 연결은 없습니다.

## 입력과 산출물

- endpoint: `PUT /api/books/{bookId}/ai-routes/current`, body routeId
- endpoint: `DELETE /api/ai-routes/{routeId}`
- 산출물: `AiRouteCurrentFacade`, `AiRouteDeleteFacade`, 별도 Controller·DTO
- W02/W03에 넘길 것: current 변경 response와 204 삭제·후속 선택 규칙

## 수정 허용 파일

- 새 current/delete Facade·Service·Controller·request DTO
- current/route/generation Repository의 이 작업 전용 lock·delete query
- G06 CONSUMED API와 S02 response assembler는 호출만 함
- 새 `AiRouteCurrentDeleteMySqlIntegrationTest`

## 구현 조건

1. current 지정은 readerId+bookId+routeId가 같은 저장 route인지 확인하고 다른 독자는 404입니다.
2. 같은 `(readerId,bookId)` current PK를 S01과 같은 순서로 잠그거나 upsert합니다.
3. current 지정은 route content·items·progress·feedback을 변경하지 않습니다.
4. 삭제는 current pointer, items, route 순서로 명시 처리하고 연결 generation이 남아 있으면 G06 CONSUMED로
   바꿉니다.
5. current route 삭제 후 남은 route 중 `createdAt DESC,id DESC` 첫 항목을 같은 transaction에서 current로
   지정하고 없으면 current 없음입니다.
6. 삭제 재시도는 다른 route·generation을 건드리지 않고 404 계약을 따릅니다.
7. 대여·잉크 원장·서재·열람 세션은 삭제하지 않습니다.

## 테스트

- current 지정 정상·다른 book·다른 독자 404
- 서로 다른 route current 변경·저장·삭제 교차 동시성에서 current 최대 한 건
- current/non-current 삭제, 후속 최신 route 선택과 남은 route 없음
- SAVED generation의 CONSUMED 전이와 저장 재시도 부활 금지
- 잉크·대여·서재·세션 row 보존과 rollback 주입
- 명령: `./gradlew test --tests '*AiRouteCurrentDeleteMySqlIntegrationTest'`

## 제외 범위

- route 생성·내용 수정, 자동 재생성
- 콘텐츠 제공·feedback
- HTML confirmation UI

## 완료 조건

- T-AIR-005·016·019의 current/delete 부분이 실제 MySQL 동시성으로 통과합니다.
- 어떤 commit 시점에도 reader·book current가 최대 하나입니다.
- `./gradlew check`가 통과합니다.

## 인계

W02에 PUT/DELETE 계약을, W03에 book별 current projection을 전달합니다. Q03에 삭제 후 잉크·대여 보존
회귀 시나리오를 전달합니다.
