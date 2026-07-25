# 0027. Spring Boot 4.1 계열을 사용한다

- 상태: 승인됨
- 날짜: 2026-07-25

## 맥락

프로젝트는 기존 서비스의 업그레이드가 아니라 Spring Boot 4.1.0으로 생성한 신규 골격입니다.
4.x 전환에서 모듈과 의존성이 바뀐 부분을 감당할 수 있는지,
Java 21과 Gradle 및 현재 필요한 라이브러리가 실제로 동작하는지 확인한 뒤 버전을 확정해야 합니다.

## 고려한 대안

- Spring Boot 3.5.x — 더 오래 검증된 3.x 생태계를 사용할 수 있지만 새 프로젝트에는 기존 3.x 코드가 없고 현재 4.1 전용 모듈 구성을 다시 낮춰야 합니다.
- Spring Boot 4.0.x — 4.x 전환 범위는 같고 안정 릴리스지만 4.1.0에 포함된 수정과 의존성 갱신을 포기할 이유가 없습니다.
- Spring Boot 4.1.x — 출시 직후 계열이라 알려지지 않은 문제가 생길 가능성은 더 있지만 현재 공식 안정 버전이며 신규 골격과 전체 검사가 이미 이 버전에서 동작합니다.

## 결정

### 선택한 기술

Spring Boot 4.1 계열을 사용하고 현재 기준 버전을 4.1.0으로 고정합니다.
Spring MVC, Spring Data JPA, Spring Security, Validation, Flyway와 MySQL Connector/J는 가능한 한 Spring Boot의 의존성 관리를 따릅니다.
패치 버전 갱신은 릴리스 노트와 전체 검사를 확인한 뒤 적용합니다.

### 핵심 근거

- **신규 프로젝트 비용 — 4.1 vs 3.5**: 옮겨야 할 기존 애플리케이션 코드가 없고 현재 빌드가 `spring-boot-starter-webmvc`,
  분야별 test starter와 분리된 Flyway 모듈 등 4.1 구조를 이미 사용합니다. 3.5로 낮추는 편이 오히려 빌드 변경을 만듭니다.
- **공식 호환 범위 — 4.1 vs 임의 조합**:
  [공식 시스템 요구사항](https://docs.spring.io/spring-boot/system-requirements.html)은 Spring Boot 4.1.0이 Java 17~26, Gradle 8.14+와 9.x를 지원한다고 명시합니다.
  프로젝트의 Java 21과 Gradle 9.5.1은 이 범위 안입니다.
- **출시 상태 — 4.1 vs snapshot**: [Spring의 4.1.0 출시 공지](https://spring.io/blog/2026/06/10/spring-boot-4/)와 [공식 문서](https://docs.spring.io/spring-boot/)에서 4.1.0을 안정 버전으로 제공합니다.
- **실제 검증 — 문서상 호환 vs 프로젝트 빌드**: 2026-07-25에 Java 21 toolchain, Spring Boot 4.1.0과 현재 의존성으로 `./gradlew check`가 성공했습니다.
- **팀 부담 — 핵심 starter 한정 vs 4.x 기능 확장**: MVP는 MVC·JPA·Security의 기본 경로만 사용하고
  gRPC, Native Image와 새로운 관측성 기능을 동시에 도입하지 않아 학습 범위를 제한합니다.

## 결과

- Spring Boot 3.x 예제는 패키지, starter 이름과 테스트 구성이 현재 버전에서도 같은지 확인한 뒤 적용해야 합니다.
- Boot가 관리하지 않는 외부 라이브러리는 별도 필요성·호환성 검증 없이 추가하지 않습니다.
- 4.1 출시 직후 발견되는 회귀가 핵심 기능을 막으면 4.0.x 또는 3.5.x로 낮추는 비용을 새 ADR에서 비교합니다.
- 이 결정은 Java·Spring Boot·RDS를 묶어 기록한 ADR-0004의 Spring Boot 결정을 대체합니다.
