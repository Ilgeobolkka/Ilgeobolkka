# 0026. 백엔드 기준 Java 버전으로 21을 사용한다

- 상태: 승인됨
- 날짜: 2026-07-25

## 맥락

읽어볼까는 Java 21 툴체인으로 이미 빌드 골격과 테스트를 구성했지만
기존 ADR은 Java, Spring Boot와 데이터베이스 결정을 한 문서에 묶어 Java 버전의 대안을 충분히 설명하지 못했습니다.
MVP 일정 안에 검증된 환경을 유지하면서 지원 기간, 팀 학습 비용과 실제로 쓸 언어 기능을 기준으로 버전을 고정해야 합니다.

## 고려한 대안

- Java 17 — Spring Boot 4.1의 최소 버전이고 팀에 익숙하지만 Oracle Premier Support가 2026년 9월 종료 예정이며 Java 21에서 정식화된 기능을 사용할 수 없습니다.
- Java 21 — 최신 LTS는 아니지만 2028년 9월까지 Oracle Premier Support 예정이고 현재 프로젝트 툴체인과 검증 환경을 그대로 사용할 수 있습니다.
- Java 25 — 최신 LTS이고 지원 기간이 더 길지만 현재 프로젝트를 올려 얻는 필수 기능이 없고 팀·CI·배포 런타임을 다시 검증해야 합니다.
- Java 26 — Spring Boot 4.1 호환 범위지만 비LTS라 다음 기능 릴리스로 빠르게 교체해야 하며 MVP 운영 기준 버전으로 적합하지 않습니다.

## 결정

### 선택한 기술

컴파일과 실행의 기준 버전으로 Java 21을 사용하고 Gradle toolchain을 21로 고정합니다.
불변 DTO에는 `record`, 닫힌 결과 유형의 분기에 이점이 있을 때는 정식 기능인 record pattern과 pattern matching `switch`를 사용합니다.
preview 기능과 가상 스레드는 MVP에서 활성화하지 않습니다.

### 핵심 근거

- **지원 기간 — Java 21 vs Java 17**:
  [Oracle 지원 로드맵](https://www.oracle.com/java/technologies/java-se-support-roadmap.html)에서 Java 17 Premier Support는 2026년 9월, Java 21은 2028년 9월까지로 예정되어 있습니다.
  새 MVP의 기준을 곧 지원 단계가 바뀌는 17로 낮출 이유가 없습니다.
- **변경 비용 — Java 21 vs Java 25**:
  현재 `build.gradle`이 21 toolchain을 선언하고 전체 `./gradlew check`가 통과했습니다.
  25의 더 긴 지원은 장점이지만 현재 요구사항에 필요한 기능 차이가 없어 일정 중 런타임 검증을 다시 할 실익이 작습니다.
- **실제 기능 — 21 vs 단순 LTS 명분**:
  [Java 21 언어 변경](https://docs.oracle.com/en/java/javase/21/language/java-language-changes-release.html)에 record pattern과 pattern matching `switch`가 정식 기능으로 포함됩니다.
  다만 초보 팀의 학습 비용을 줄이기 위해 단순 조건문보다 명확한 곳에서만 사용합니다.
- **프레임워크 호환 — 21 vs 비지원 버전**:
  [Spring Boot 4.1 시스템 요구사항](https://docs.spring.io/spring-boot/system-requirements.html)은 Java 17부터 26까지를 지원하므로 21이 공식 범위 안에 있습니다.
- **배포 계약 — 명시적 21 vs 환경 기본값**:
  개발 장비, CI와 운영 이미지가 각자 기본 JDK를 고르게 두지 않고 모두 21을 제공해야 같은 바이트코드와 런타임을 재현할 수 있습니다.

## 결과

- 로컬, CI와 운영 배포 환경은 JDK 또는 JRE 21을 제공해야 합니다.
- 현재 로컬 Gradle Launcher JVM이 17이어도 컴파일 toolchain은 21을 사용하지만, 애플리케이션 실행과 운영 런타임은 21로 맞춰야 합니다.
- Java 25로 올릴 때는 Spring Boot와 주요 라이브러리, CI·배포 이미지와 전체 테스트를 새 ADR로 함께 검증합니다.
- 이 결정은 Java·Spring Boot·RDS를 묶어 기록한 ADR-0004의 Java 결정을 대체합니다.
