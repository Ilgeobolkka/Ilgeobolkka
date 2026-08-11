# MVP1 성능 기준선 환경

## 1단계 시작점

- 준비 실행일: 2026-08-11 KST
- 기준선 예정일과 폴더: 2026-08-12 KST, `mvp1-baseline-2026-08-12`
- 시작 Git SHA: `46c650e8b6c064e979874537e481b2839c344edd`
- 1단계 시작 dirty 상태: clean
- 1단계 구현 결과의 Git SHA와 dirty 파일: 측정 metadata에서 실행마다 기록
- JAR SHA-256: `ad025a61983f583c94cfc8eb8b8a0e006ed43bac8cdf2c6db8c531c696071f0b`

## 도구와 호스트

| 항목 | 값 |
| --- | --- |
| macOS | 26.5.2, arm64 |
| CPU | Apple M5, logical CPU 10 |
| 메모리 | 25,769,803,776 bytes |
| Java | OpenJDK 21.0.12 LTS |
| Gradle | 9.5.1 |
| Spring Boot | 4.1.0 |
| Docker Engine | 29.4.3 |
| Docker Compose | 5.1.3 |
| Docker Desktop 제한 | CPU 4, 메모리 8,322,555,904 bytes |

## 재현 경계

- Compose project: `ilgeobolkka-performance`
- 애플리케이션: Compose의 패키징 JAR, `performance` profile, 내부 `app:8080`, 호스트 `127.0.0.1:8080`
- management: 내부 `app:8081`, 호스트 `127.0.0.1:8081`, `health`와 `prometheus`만 노출
- DB: MySQL 8.4.11, `127.0.0.1:3308`, database `ilgeobolkka_perf`, CPU 2, 메모리 3 GiB
- 부하 생성기: 고정 k6 browser image, 내부 `ilgeobolkka.test:8080`, CPU 1, 메모리 1 GiB
- 관측: Prometheus 3.13.2와 Grafana 13.1.3의 로컬 진단 도구. 운영 관측 표준으로 채택하지 않아 ADR 대상이 아니다.
- 외부 기능: `PORTONE_PAYMENT_ENABLED=false`, `AI_ROUTE_ENABLED=false`
- Redis, Caffeine, Spring Session: 조건부 단계 전이므로 추가하지 않음

## 고정 image

| 용도 | image digest |
| --- | --- |
| MySQL | `mysql:8.4.11@sha256:b3b90af2a6552ae30c266fdb7d5dd55f3afb72404bb78d37fe8a23eb857fd3fb` |
| k6 browser | `grafana/k6:2.2.0-with-browser@sha256:defdc0a3e70c46bce010bfc10dedc03e335cc7febe01f6359552fe72827c2aa2` |
| Prometheus | `prom/prometheus:v3.13.2@sha256:508729e0e2d18e11fd742a5a5ca70e557b940a93948c3c95fd0123a6fd538b69` |
| Grafana | `grafana/grafana:13.1.3@sha256:ab5cb380e3ff3172d6c8bd2e7cfd31cce977d2881b260e1f5bc089bf0b759b43` |
| MySQL exporter | `prom/mysqld-exporter:v0.19.0@sha256:eacb4b18e2ec1e0abdf2d64851b68526c964f6d9cb3e9458fb5d5f5062ea94c1` |
| Java 21 JDK | `eclipse-temurin:21-jdk-jammy@sha256:55fb9bf738f5d9b4a6c01b39337e3070d3e27370dd3c478fd1d5d3cd2233c6d8` |

## 데이터와 시나리오

- 데이터 세트: `mvp`, 도서 100, 페이지 400, 신규 독자 334, 활성 대여 독자 333, 소장 독자 333
- on Average 종료 뒤 DB table+index 할당 크기: 3,014,656 bytes
- 계정 비밀번호는 Git 제외 `.env`에서 주입하며 문서와 artifact에 저장하지 않음
- Warm-up 5 flow iteration/s 3분, Average 20 flow iteration/s 10분, Peak 50 flow iteration/s 10분
- 정상 혼합: 공개 35%, 인증 조회 20%, 소장 15%, 활성 대여 10%, 신규 대여 15%, 로그인 5%
- 각 VU는 독립 cookie jar를 사용하고 로그인·CSRF·session 정책을 그대로 통과
- 실제 HTTP RPS, VU 상한, 시작·종료 UTC와 관측 모드는 실행별 `metadata.json`·`summary.json`에 기록
- JVM: Temurin 21.0.11, heap 최대 512 MiB, Tomcat max threads 200, Hikari max 10

## 1단계에서 채운 근거

- 관측 off/on 결과: [stage1-observation-overhead.md](./stage1-observation-overhead.md)
- Prometheus target: `ilgeobolkka-application`, `ilgeobolkka-mysql` 모두 `up`
- Grafana provision: `ilgeobolkka-performance` dashboard와 `prometheus` datasource 확인
- management: 공개 포트의 actuator 404, management는 host loopback과 Compose 사설망에서만 접근

## 2단계 고정점

- 정식 측정 Git SHA: `8cf53f29f28923ef778583df6fe61d0dc90c7ae5`
- 정식 측정 dirty 상태: 모든 metadata에서 `false`
- 패키징 JAR SHA-256: `ad025a61983f583c94cfc8eb8b8a0e006ed43bac8cdf2c6db8c531c696071f0b`
- 측정 시간: 2026-08-11 10:02~12:22 UTC, 2026-08-11 19:02~21:22 KST
- 정식 Average·Peak 매회 `mvp` 데이터와 새 애플리케이션에서 Smoke → 3분 Warm-up을 거쳤고 Warm-up 표본은 버렸다.
- 승인 범위대로 성능 전용 MySQL·Prometheus volume만 재생성했다. 개발 Compose와
  `ilgeobolkka_mysql-data`는 삭제하지 않았다.
- PortOne·OpenAI는 모든 정식 실행에서 비활성화했다.

## cold 시작 상태

성능 전용 volume을 재생성하고 동일 JAR을 처음 기동한 진단값이며 steady-state 기준선에 합치지 않았다.

| 항목 | 값 |
| --- | ---: |
| 컨테이너 시작부터 ready까지 | 6.31초 |
| Spring startup | 3.35초 |
| 첫 공개 API 요청 | 65.597ms |
| 첫 요청 전후 buffer pool read requests | +27 |
| 첫 요청 전후 buffer pool physical reads | +0 |
| JIT compiled / failed / invalid | 11,143 / 1 / 0 |
| JIT compile time | 4.71초 |
| code cache used / full count | 23,032 KiB / 0 |

## 2단계 근거

- 부하·동시성·브라우저 요약: [summary.md](./summary.md)
- 같은 시간대 자원 지표: [dashboard.md](./dashboard.md)
- history-heavy·JFR·MySQL 진단: [diagnostics.md](./diagnostics.md)
- 원시 artifact 위치·해시: [artifact-manifest.md](./artifact-manifest.md)
- 단계 판정과 병목: [result.md](./result.md)
