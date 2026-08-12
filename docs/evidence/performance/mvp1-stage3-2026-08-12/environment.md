# 3단계 측정 환경

## 범위와 실행물

- 범위: 로컬 Compose의 코드 회귀 비교. 운영 SLO·운영 용량 근거가 아니다.
- 측정 기준 Git SHA: `4736e95c3ed2772e222f27221976f888d959793f`
- 최초 control JAR SHA-256:
  `911626816238389c8b7c7c8d30a97fb53fddb7f056a4d740ad4d7d33a7fb6ed6`
- 이력 endpoint control/candidate 공통 JAR SHA-256:
  `36dde2e716d4097d70842dfd7e61d31b72f356e19721d23bed56b5aba556647c`
- 최종 결합 JAR SHA-256:
  `98b375a4bc14a290f445d5ef4c8ae81be7a2a19f9d13b8f943dc9c25b20ec843`
- endpoint와 결합 실행 metadata의 Git 상태는 `dirty=true`였다. 애플리케이션 실행물은 위 JAR 해시로
  고정했고, 이후 커밋에는 측정 문서·테스트·k6 시나리오가 함께 포함됐다.

## 호스트와 컨테이너

| 항목 | 값 |
| --- | --- |
| 호스트 | macOS 26.5.2, arm64, Apple M5, 논리 CPU 10, 메모리 24GiB |
| Docker Desktop 제한 | CPU 4, 메모리 7.75GiB |
| 애플리케이션 | Java 21.0.11, Spring Boot 4.1.0, 메모리 2GiB, Hikari 최대 10, Tomcat 최대 thread 200 |
| 애플리케이션 image | `eclipse-temurin:21-jdk-jammy@sha256:55fb9bf738f5d9b4a6c01b39337e3070d3e27370dd3c478fd1d5d3cd2233c6d8` |
| MySQL | 8.4.11, CPU 2, 메모리 3GiB |
| MySQL image | `mysql:8.4.11@sha256:b3b90af2a6552ae30c266fdb7d5dd55f3afb72404bb78d37fe8a23eb857fd3fb` |
| k6 image | `grafana/k6:2.2.0-with-browser@sha256:defdc0a3e70c46bce010bfc10dedc03e335cc7febe01f6359552fe72827c2aa2` |
| 외부 서비스 | PortOne·OpenAI 비활성화 |

## 데이터셋

| 데이터셋 | 주요 행 수 | 용도 |
| --- | --- | --- |
| `mvp` | 도서 100, 페이지 400, 독자 1,000, 대여 333, 원장 1,333, 서재 666 | 전체 혼합 회귀 |
| `history-heavy` | 구매 6,000, 대여 500,333, 원장 506,333, 독자 1,000, 도서 100, 페이지 400, 서재 666 | 이력 endpoint와 실행 계획 비교 |

`history-heavy`는 2026-08-12 03:19:23~03:19:29 UTC에 생성했으며 DB 크기는 333,807,616 bytes였다.
생성 직후 행 수와 도메인 불변식 6종을 확인했다.

기존 `history-index` 원시 `metadata.json`은 공통 수집기의 고정값 때문에 데이터셋 이름을 `mvp`로
잘못 기록했다. 해당 여섯 실행의 데이터셋 근거는 직전 `history-heavy` 생성 artifact의 행 수와 동일 DB
실행 기록이며, 결과 수치를 사후 수정하지 않았다. 이번 수정은 `history-index` 시작 전에 위 고정 행 수를
검증하고 실제 행 수를 `dataset.json`과 `metadata.json`에 기록한다.

## 비교 조건과 Git 보존 결과

- 이력 endpoint: control/candidate마다 새 애플리케이션, 30초 Warm-up, 2분 본 측정. 서재와 원장을 각각
  10 iteration/s로 실행했다.
- history-heavy mixed: 각 상태를 새로 생성하고 새 애플리케이션, 1분 Warm-up, 3분 Average를 실행했다.
- 결합 검증: 매회 `mvp` 복원, 새 애플리케이션, Smoke, 3분 Warm-up, 10분 본 측정 순서로 Average·Peak
  각 3회를 실행했다.
- 작은 k6 summary는 이 폴더의 `summary-*.json`에 보존한다. 큰 Prometheus 시계열·전체 로그는
  [artifact-manifest.md](./artifact-manifest.md)의 위치·크기·SHA-256으로 연결한다.
