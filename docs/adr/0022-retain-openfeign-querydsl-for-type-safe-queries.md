# 0022. 타입 안전 조회 기술 검증을 위해 OpenFeign QueryDSL을 유지한다

- 상태: 승인됨
- 날짜: 2026-07-23

## 맥락

현재 MVP의 도서 검색은 정규화한 검색어 하나로 제목 또는 저자를 부분 일치 조회하는 정도라 JPQL
`@Query`만으로도 구현할 수 있습니다. QueryDSL을 제품 요구사항에 반드시 필요한 도구로 과장하지 않고,
이미 완료한 Jakarta Persistence·Hibernate 조합의 타입 안전 조회 기술 검증을 프로젝트 목표로 유지할지
근거를 다시 정리합니다.

## 고려한 대안

- Spring Data 파생 쿼리 — 의존성이 없지만 OR 조건, 이스케이프와 고정 정렬을 메서드 이름에 표현하면 길어져 제외합니다.
- JPQL `@Query` — 현재 검색만 보면 가장 단순하지만 필드 변경을 런타임까지 발견하지 못하고 완료한 Q타입·MySQL 조회 검증을 제거해야 해 제외합니다.
- JPA Criteria API·Specification — 별도 의존성은 없지만 현재 조회에 비해 표현이 장황해 제외합니다.
- OpenFeign QueryDSL 유지 — 제품 필수 기술은 아니지만 컴파일 시점 필드 검증과 실제 MySQL 조회를 학습·검증하는 프로젝트 선택으로 채택합니다.

## 결정

- OpenFeign QueryDSL 7.5의 JPA 모듈과 현재 annotation processor 구성을 유지합니다.
- 도서 검색처럼 조건 조합이 필요한 조회에만 사용하고 단순 ID 조회까지 QueryDSL로 바꾸지 않습니다.
- `%`, `_` 이스케이프, `title ASC, id ASC` 정렬과 실제 MySQL 실행을 통합 테스트로 검증합니다.
- QueryDSL 도입 근거는 복잡한 미래 검색을 미리 구현하기 위함이 아니라 타입 안전 조회 기술 검증입니다.
- 새 필터가 필요해질 때까지 범용 Predicate builder, 공통 추상 Repository와 동적 정렬 프레임워크를 만들지 않습니다.

## 결과

- 이미 구축한 Q타입 생성과 MySQL 호환성 검증을 활용할 수 있습니다.
- 단순 JPQL보다 의존성과 annotation processing 비용이 남으므로 Spring Boot·Hibernate 업그레이드 때 함께 검증해야 합니다.
- 기술 검증 목적이 사라지거나 유지 비용이 이점을 넘으면 JPQL 전환을 새 ADR로 결정합니다.
