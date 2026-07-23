# 0009. OpenFeign QueryDSL로 타입 안전 JPA 쿼리를 작성한다

- 상태: 승인됨
- 날짜: 2026-07-22

## 맥락

도서 목록과 검색처럼 조건이 조합되는 JPA 조회를 문자열 쿼리 없이 작성하고, 엔티티 변경을 컴파일 시점에 검출할 수단이 필요합니다.
현재 프로젝트는 Spring Boot 4.1이 관리하는 Jakarta Persistence 3.2와 Hibernate 7.4를 사용하므로 이 조합을 지원하면서 Q타입을 생성할 라이브러리를 선택해야 합니다.

## 고려한 대안

- `com.querydsl` QueryDSL 5.1.0 — Spring Boot가 버전을 관리하지만 Hibernate 7을 기준으로 유지보수되지 않아 제외합니다.
- JPA Criteria API·Specification — 별도 라이브러리가 필요 없지만 복합 조회의 표현이 장황하고 Q타입 생성이라는 SCRUM-91의 목적을 충족하지 않아 제외합니다.
- jOOQ — SQL 표현력과 타입 안전성이 강하지만 별도 스키마 기반 코드 생성과 데이터 접근 방식을 도입해야 하므로 현재 JPA 기반 MVP에는 과해 제외합니다.

## 결정

OpenFeign QueryDSL 7.5의 JPA 모듈을 사용합니다.
`main`과 `test` 컴파일에 JPA `annotation processor`를 설정하고, Gradle Java 플러그인의 기본 경로인 `build/generated/sources/annotationProcessor/java/`에 Q타입을 생성합니다.
생성물은 빌드 결과이므로 저장소에 커밋하지 않습니다.

## 결과

엔티티의 지속 필드를 기반으로 Q타입이 생성되어 타입 안전한 동적 JPA 쿼리를 작성할 수 있습니다.
QueryDSL 버전은 Spring Boot의 의존성 관리 대상이 아니므로 Gradle에서 명시적으로 고정하고, Spring Boot·Hibernate를 올릴 때 annotation processing과 실제 쿼리 호환성을 함께 검증해야 합니다.
