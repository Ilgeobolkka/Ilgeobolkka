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

- S01이 current upsert(`selectAsCurrent`)와 SAVED generation을, S02가 owner detail DTO를 제공합니다.
- G06 `markConsumed`는 이미 있고 호출자만 없습니다.
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
2. 같은 `(readerId,bookId)` current PK를 S01과 같은 순서로 잠그거나 upsert합니다. 지정은 S01과 같은
   `selectAsCurrent` upsert를 쓰고, 삭제는 현재 포인터를 `FOR UPDATE`로 먼저 읽어 같은 도서의 삭제들을
   직렬화합니다. 현재가 아닌 route를 지울 때도 잠급니다. 잠그지 않으면 현재 route 삭제가 아직 커밋되지
   않은 다른 삭제의 route를 후속 current로 골라 참조 무결성에서 터집니다.
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

- `PUT /api/books/{bookId}/ai-routes/current`는 body `{"routeId": <long>}`을 받고 저장 경로 상세와 같은
  `200` 응답을 돌려줍니다. 지정 직후 화면이 그 경로를 그리므로 상세를 다시 조회하지 않아도 됩니다.
  소유자가 아니거나 `bookId`가 어긋나면 같은 `404 RESOURCE_NOT_FOUND`입니다.
- `DELETE /api/ai-routes/{routeId}`는 body 없는 `204`이고, 이미 지운 route의 재시도는 `404`입니다.
  멱등한 `204`로 만들면 남의 route 식별자를 넣은 요청과 응답이 같아져 존재 여부가 새어 나갑니다.

## 결정 기록

- 잠금 순서는 route → generation → current입니다. S01 저장이 generation → current 순이고 기존 route
  행을 잠그지 않으므로 대기 고리가 생기지 않습니다. 순서를 바꾸는 후속 작업은 이 근거를 다시 확인해야
  합니다.
- `selectAsCurrent` upsert는 호출자가 소유자를 먼저 확인했다는 전제 위에 있습니다. 어긋난 route를 넘기면
  MySQL이 PK가 아니라 `uk_ai_route_current_route`로 매칭해 다른 독자의 current 행을 예외 없이 갱신합니다.
  호출자를 늘리는 후속 작업(W02·W03)은 소유자 확인을 먼저 붙입니다.
- 기능 비활성화 가드 테스트 `IlgeobolkkaApplicationTests`는 S03의 수정 허용 파일 밖입니다. 조건부 등록
  빈이 늘면 같은 가드에 추가하는 것이 S01 선례라 이번에도 따랐고, PR 본문에 이 근거를 남깁니다.
- `AiRouteCurrent.select`·`changeRoute`와 그 소유자 가드는 S03도 upsert를 쓰기로 해 production 호출자가
  없습니다. 실제 불변식은 복합 FK `fk_ai_route_current_route`가 지킵니다. 다만 제거하려면 F02의 매핑·
  상태 테스트를 함께 고쳐야 해서 S03의 수정 허용 파일을 벗어납니다. 별도 정리 과제로 넘깁니다.
