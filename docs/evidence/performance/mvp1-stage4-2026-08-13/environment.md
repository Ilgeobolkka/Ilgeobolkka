# 4단계 측정 환경

## 실행물

| 항목 | 값 |
| --- | --- |
| 브랜치 | `codex/mvp1-performance-stage-4` |
| 기준 Git SHA | `a027643e9702e342a4298979b8bf7c6afae96c05` |
| Git 상태 | `dirty=true` — 4단계 blocker 수정과 evidence가 미커밋 상태 |
| JAR | `build/libs/Ilgeobolkka-0.0.1-SNAPSHOT.jar` |
| JAR bytes | 87,887,367 |
| JAR SHA-256 | `0ce68063a964db0b665c4686c7de533ed76337aed4728e0d2a5ba4d4d891d41e` |
| 실행 날짜 | 2026-08-13 — 계획의 8월 14일보다 먼저 시작하라는 사용자 승인에 따라 실제 날짜 사용 |

## 호스트와 Docker

| 항목 | 값 |
| --- | --- |
| 호스트 | macOS 26.5.2, arm64, Apple M5, 논리 CPU 10, 메모리 24GiB |
| 호스트 Java | 21.0.12 |
| Docker Engine / Compose | 29.4.3 / v5.1.3 |
| Docker Desktop 제한 | CPU 4, 메모리 7.75GiB |
| Compose project | `ilgeobolkka-performance` |
| 성능 DB | `ilgeobolkka_perf` |
| 애플리케이션 | Java 21.0.11, CPU 2, 메모리 2GiB, Hikari 최대 10, Tomcat 최대 thread 200 |
| MySQL | 8.4.11, CPU 2, 메모리 3GiB |
| k6 | 2.2.0-with-browser, CPU 1, 메모리 1GiB |
| 관측 | Prometheus 3.13.2, Grafana 13.1.3, MySQL exporter 0.19.0 |

이미지는 모두 `compose.performance.yaml`의 고정 SHA-256 digest를 사용했다. 새 Redis·proxy·애플리케이션
인스턴스는 만들지 않았다. 실행 전 성능 전용 `mysql-perf-data`, `prometheus-data` volume만 삭제·재생성했고
개발 volume `ilgeobolkka_mysql-data`와 `var/performance/results/`는 보존했다.

## 데이터와 외부 서비스

| 항목 | 값 |
| --- | ---: |
| book / book_page | 100 / 400 |
| reader | 1,000 |
| ink_purchase / ink_ledger | 1,000 / 1,333 |
| page_rental / ownership_payment / book_ownership | 333 / 333 / 333 |
| library_entry / reading_session | 666 / 0 |
| 초기 불변식 위반 | 0 |

PortOne 결제와 OpenAI 경로는 Compose 환경 변수와 metadata에서 모두 비활성화됐다. 비밀값은 변수 존재만
확인했고 출력·evidence·Git에 기록하지 않았다.
