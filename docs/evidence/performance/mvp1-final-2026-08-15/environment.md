# MVP1 최종 재측정 환경

## 실행물

| 항목 | 값 |
| --- | --- |
| 브랜치 | `codex/mvp1-performance-final-audit` |
| 기준 Git SHA | `f3a62809099ba660ce980b006d71c833bbd849fa` |
| 정식 측정 Git 상태 | `dirty=false` |
| JAR | `build/libs/Ilgeobolkka-0.0.1-SNAPSHOT.jar` |
| JAR bytes | 87,887,367 |
| JAR SHA-256 | `5c4e146b2a98d85cd4465b204a64660911dd72b3965b807791e868d66a2b9eb0` |
| 정식 성능 측정 | 2026-08-13 03:36~06:45 UTC, 2026-08-13 12:36~15:45 KST |
| evidence 폴더 | Runbook이 고정한 `mvp1-final-2026-08-15`를 유지하고 실제 실행일을 이 문서에 별도 기록 |

정식 부하는 최신 `origin/develop`과 같은 SHA의 clean 작업 트리에서 시작했다. evidence를 작성한 뒤의
untracked·dirty 상태는 측정물 metadata의 `dirty=false`와 구분한다.

## 호스트와 Docker

| 항목 | 값 |
| --- | --- |
| 호스트 | macOS 26.5.2, arm64, Apple M5, 논리 CPU 10, 메모리 25,769,803,776 bytes |
| 호스트 Java | OpenJDK 21.0.12 LTS |
| Gradle | 9.5.1 |
| Docker Engine / Compose | 29.4.3 / v5.1.3 |
| Docker Desktop 제한 | CPU 4, 메모리 8,322,555,904 bytes |
| Compose project | `ilgeobolkka-performance` |
| 성능 DB | MySQL 8.4.11, `ilgeobolkka_perf`, 호스트 `127.0.0.1:3308`, CPU 2, 메모리 3GiB |
| 애플리케이션 | Java 21.0.11, CPU 2, 메모리 2GiB, Hikari 최대 10, Tomcat 최대 thread 200 |
| 부하 생성기 | k6 2.2.0-with-browser, CPU 1, 메모리 1GiB |
| 관측 | Prometheus 3.13.2, Grafana 13.1.3, MySQL exporter 0.19.0 |

## 고정 image

| 용도 | image digest |
| --- | --- |
| Java 21 JDK | `eclipse-temurin:21-jdk-jammy@sha256:55fb9bf738f5d9b4a6c01b39337e3070d3e27370dd3c478fd1d5d3cd2233c6d8` |
| MySQL | `mysql:8.4.11@sha256:b3b90af2a6552ae30c266fdb7d5dd55f3afb72404bb78d37fe8a23eb857fd3fb` |
| MySQL exporter | `prom/mysqld-exporter:v0.19.0@sha256:eacb4b18e2ec1e0abdf2d64851b68526c964f6d9cb3e9458fb5d5f5062ea94c1` |
| Prometheus | `prom/prometheus:v3.13.2@sha256:508729e0e2d18e11fd742a5a5ca70e557b940a93948c3c95fd0123a6fd538b69` |
| Grafana | `grafana/grafana:13.1.3@sha256:ab5cb380e3ff3172d6c8bd2e7cfd31cce977d2881b260e1f5bc089bf0b759b43` |
| k6 browser | `grafana/k6:2.2.0-with-browser@sha256:defdc0a3e70c46bce010bfc10dedc03e335cc7febe01f6359552fe72827c2aa2` |

## 깨끗한 출발점과 보존 경계

- 실행 중인 process·container와 `8080`, `8081`, `3308`, `9090`, `3000` 포트를 먼저 확인했다.
- 초기화 대상은 `ilgeobolkka-performance_mysql-perf-data`와
  `ilgeobolkka-performance_prometheus-data` 두 volume으로 해석해 출력한 뒤 재생성했다.
- 개발 volume `ilgeobolkka_mysql-data`와 Git 제외 원시 결과 `var/performance/results/`는 삭제하지 않았다.
- 정식 Average·Peak 각 회차는 `mvp 복원 → 새 앱 → Smoke → 3분 Warm-up → 10분 본 측정` 순서였고,
  Warm-up 표본은 정식 통계에서 제외했다.
- Stress·Spike·Soak와 동시성 4종도 각 실행 전에 `mvp`를 복원하고 새 앱으로 시작했다.
- PortOne 결제와 OpenAI 경로는 모든 정식 metadata에서 비활성화했다. 필요한 비밀 변수는 값이 아니라
  존재 여부만 확인했고 출력·artifact·Git에 기록하지 않았다.

## 결정적 데이터

### `mvp`

| 항목 | 행 수 |
| --- | ---: |
| book / book_page | 100 / 400 |
| reader | 1,000 |
| ink_purchase / ink_ledger | 1,000 / 1,333 |
| page_rental | 333 |
| ownership_payment / book_ownership | 333 / 333 |
| library_entry / reading_session | 666 / 0 |

잔액 불일치·음수 잔액·대여 없는 차감·중복 소장·활성 대여 및 소장의 서재 연결 위반은 초기 상태에서
모두 0이었다.

### `history-heavy`

- 입력: 독자당 이력 대여 500건
- snapshot 식별자: `20260813T033644Z-history-heavy`
- 생성 시간: 6초
- DB table+index 크기: 333,807,616 bytes
- 행 수: `page_rental=500,333`, `ink_ledger=506,333`, `ink_purchase=6,000`
- `reader=1,000`, `book=100`, `book_page=400`, `library_entry=666`
- 공통 불변식 6종: 모두 0

기계 판독 원본은 [history-heavy-metadata.json](./history-heavy-metadata.json),
[history-heavy-counts.tsv](./history-heavy-counts.tsv),
[history-heavy-invariants.tsv](./history-heavy-invariants.tsv)에 보존했다. 생성 뒤 정식 혼합 부하는 기준선과
같은 `mvp`로 다시 복원했다.
