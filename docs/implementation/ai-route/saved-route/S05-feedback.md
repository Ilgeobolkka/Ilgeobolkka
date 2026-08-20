# S05 완료 경로 피드백

[구현 작업 색인](../README.md)으로 돌아갑니다.

- 권장 담당: 파동 8 / 담당 C
- 선행: [S02 경로 조회](./S02-route-query.md)
- 후속: [W02 경로 화면](../web/W02-route-detail-page.md), [Q03 출시 회귀](../release/Q03-release-regression.md)

## 목표

완료한 소유자 route에만 세 가지 선택형 feedback을 생성·변경하고 피드백 생략·변경이 완료·잉크·권한에
영향을 주지 않게 합니다.

## 정본 링크

- [진행과 피드백](../../../prd/ai-ink-route.md#진행과-피드백)
- [피드백 endpoint](../../../api-spec.md#목표-json-엔드포인트)
- [ERD ai_reading_route](../../../erd.md#새-테이블)
- 필수 시나리오: [T-AIR-016·017](../../../test-strategy.md#5-필수-시나리오)

## 현재 구현 기준선

- S02가 owner route 조회를 제공하고 F02 route Entity에 feedback 필드가 있습니다.
- feedback Controller·상태 변경 메서드·테스트는 없습니다.

## 입력과 산출물

- endpoint: `PUT /api/ai-routes/{routeId}/feedback`
- request: `rating=HELPFUL|NEUTRAL|NOT_HELPFUL`
- response: routeId, rating, feedbackAt
- 산출물: `AiRouteFeedbackFacade`, `AiRouteFeedbackController`, request/response DTO

## 수정 허용 파일

- 새 feedback Facade·Controller·DTO·exception
- route Repository의 owner lock query와 F02 Entity의 기존 feedback 전이 API 사용
- 새 `AiRouteFeedbackApiMySqlIntegrationTest`

## 구현 조건

1. readerId+routeId 소유자 조건으로 잠금 조회하고 다른 독자는 404입니다.
2. completedAt이 null이면 feedback을 저장하지 않고 확정한 도메인 오류로 응답합니다.
3. 세 Enum 외 null·자유 문구·unknown 값을 400 INVALID_INPUT으로 거부합니다.
4. 최초와 변경 모두 주입 Clock의 feedbackAt을 기록하고 같은 rating 재요청도 API 계약대로 한 결과로
   수렴하게 테스트합니다.
5. feedback은 optional이며 미제출 route의 completedAt·current·items를 바꾸지 않습니다.
6. 잉크·대여·소장·세션·서재를 조회하거나 변경하지 않습니다.

## 테스트

- 완료 owner의 세 rating 생성·변경과 response UTC 시각
- 미완료 route·다른 독자·삭제 route·unknown rating 거부
- 같은 rating 순차·동시 요청 결과 일관성
- feedback 생략·변경 전후 completedAt·current·items·잉크·권한 불변
- CSRF 누락 403
- 명령: `./gradlew test --tests '*AiRouteFeedbackApiMySqlIntegrationTest'`

## 제외 범위

- 자유 입력 피드백·보상·잉크백
- 운영 집계·70% 관찰 지표 화면
- route 완료 판정

## 완료 조건

- T-AIR-016·017 feedback 부분이 MySQL 통합 테스트됩니다.
- owner·completed 조건 외 경로에 feedback이 생기지 않습니다.
- `./gradlew check`가 통과합니다.

## 인계

W02에 rating Enum·미완료 표시 조건·response fixture를 전달합니다. Q03에 feedback optional 회귀 시나리오를
전달합니다.
