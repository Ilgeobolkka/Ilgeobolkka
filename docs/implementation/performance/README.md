# MVP1 성능 측정·개선 실행 계획

- 작성일: 2026-08-11 KST
- 실행 기한: 2026-08-15 KST
- 대상: 초기 MVP의 Spring Boot HTTP 애플리케이션과 MySQL 8.4
- 실행 주체: AI 코딩 에이전트
- 상태: 실행 대기

## 1. 목표와 완료 기준

이 계획은 현재 MVP1을 같은 조건에서 먼저 측정하고, 관측한 병목만 수정한 뒤, 환경을 깨끗하게 다시
구성해 같은 부하로 재측정하는 절차다. 도구 도입이나 높은 최대 RPS 자체가 목표가 아니라, 정확성을
유지하면서 사용자가 거치는 핵심 경로의 지연과 처리 용량을 재현 가능하게 개선하는 것이 목표다.

다음을 모두 만족해야 2026-08-15 작업을 완료한 것으로 판정한다.

1. 현재 Git 리비전과 실행 환경을 고정한 MVP1 HTTP 기준선이 3회 이상 보존돼 있다.
2. 도서 탐색, 인증 조회, 신규·활성·소장 페이지 열기, 콘텐츠 전달과 동시 차감 경로가 각각 측정돼 있다.
3. k6 결과와 같은 시간대의 JVM·Tomcat·HikariCP·MySQL 지표, JFR 또는 그에 준하는 진단 근거가 연결돼 있다.
4. 느린 원인을 추측하지 않고 지표·프로파일·실행 계획으로 확인한 뒤 개선한다.
5. 채택한 개선마다 재현 테스트와 변경 전후 동일 시나리오 비교가 있다.
6. 잉크 잔액·원장·대여·소장·세션 불변식이 부하 뒤에도 모두 유지된다.
7. 깨끗하게 다시 만든 동일 환경에서 최종 시나리오를 3회 실행하고 중앙값과 실행 간 편차를 기록한다.
8. `./gradlew test`, `./gradlew check`, `./gradlew build`와 패키징 애플리케이션 부팅 검증이 통과한다.
9. 채택하지 않은 Redis·가상 스레드 등의 실험도 측정값과 폐기 이유를 남긴다.
10. 결과 문서가 기준선, 변경별 효과, 최종 결과, 남은 병목과 다음 용량 한계를 구분해 설명한다.

성능 수치는 실행 환경이 정해지기 전까지 운영 SLO나 운영 최대 용량으로 부르지 않는다. 현재
[배포 가이드](../../deployment.md)는 애플리케이션 실행 환경이 아직 결정되지 않았다고 명시하므로, 로컬
Compose 결과는 코드 회귀 기준선이며 AWS 용량 기준선은 실행 환경을 결정한 뒤 별도로 만든다.

## 2. 전제와 작업 경계

### 2.1 고정 전제

- Java 21, Spring Boot 4.1, MySQL 8.x와 RDS for MySQL 결정은 유지한다.
- 잉크 잔액과 원장은 MySQL이 정본이며 Redis나 Kafka로 옮기지 않는다.
- 신규 대여의 차감·원장·대여·마지막 위치 원자성을 성능 때문에 약화하지 않는다.
- 인가 전에 개인 페이지 콘텐츠를 캐시에서 제공하지 않는다.
- 개인 콘텐츠의 `Cache-Control: private, no-store` 결정을 유지한다.
- BCrypt 비용을 로그인 처리량을 높이기 위한 이유만으로 낮추지 않는다.
- PortOne 테스트 채널과 외부 API에 부하를 전가하지 않는다.
- 실제 사용자 데이터와 운영 자격 증명을 사용하지 않는다.

### 2.2 이번 계획에서 허용하는 변경

- 성능 측정용 k6 스크립트, 결정적 데이터 준비기와 결과 요약기 추가
- Spring Boot Actuator와 Micrometer Prometheus registry 추가
- 성능 전용 Compose 구성에 Prometheus·Grafana·조건부 Redis 추가
- Flyway 인덱스, SQL, 트랜잭션 범위, 요청 로깅, HikariCP·Tomcat·JVM 설정 개선
- 근거가 통과한 경우 Caffeine 또는 Redis 캐시 추가
- 다중 인스턴스를 실제 측정하는 경우 Spring Session Redis 실험
- 가상 스레드 또는 애플리케이션 인스턴스 수 변경 실험
- 성능 변경에 대응하는 JUnit 5·MySQL 통합 테스트 추가

### 2.3 먼저 별도 결정해야 하는 변경

아래 항목은 되돌리기 비싼 기술 결정이므로 실험이 효과를 입증해도 바로 운영 표준으로 고정하지 않는다.
채택 전에 `adr` 스킬에 따라 기존 ADR과 대안을 비교하고 새 ADR 또는 기존 ADR 대체 여부를 판정한다.

- Redis를 운영 캐시 또는 공유 세션 저장소로 채택
- 애플리케이션 다중 인스턴스와 로드 밸런서 구조 채택
- Prometheus·Grafana를 운영 관측 표준으로 채택
- 동기 도메인 처리를 이벤트 기반 구조로 변경

### 2.4 이번 기한에서 제외하는 것

- Kafka 도입: 현재 MVP1의 병목 후보는 동기 HTTP·DB 왕복·행 잠금·로그이며, Kafka는 이벤트 스트리밍
  플랫폼이다. 차감·대여 원자 경로를 비동기로 바꾸면 제품 불변식이 복잡해진다. 요청과 분리 가능한 대규모
  분석·알림 이벤트가 실제 병목으로 확인되기 전에는 추가하지 않는다.
- Swagger와 Postman 도입: API 문서화·수동 호출 도구이며 부하 기준선에 필요하지 않다.
- JMH 도입: 순수 Java 메서드 하나가 JFR에서 유의미한 CPU 병목으로 확인될 때만 별도 마이크로벤치마크를
  만든다.
- 운영 실결제, 외부 OpenAI와 PortOne 서버를 대상으로 한 스트레스 테스트
- 제품 정책, 보안 경계 또는 응답 계약을 성능 수치만을 이유로 변경

## 3. 현재 기준과 우선 가설

기존 [SCRUM-422 측정 근거](../../evidence/query-measurement/scrum-422-2026-08-03.md)는 MySQL 8.4에서
도서 목록 2쿼리, 서재 1쿼리, 신규 페이지 열기 14쿼리를 확인했다. 기록된 3~94ms는 워밍업과 HTTP
경계를 포함하지 않은 단발 파사드 시간이라 지연 판정에는 사용하지 않고 쿼리 수 회귀 기준으로만 유지한다.

현재 코드에서 먼저 검증할 가설은 다음 순서다.

| 우선순위 | 가설 | 현재 근거 | 확인 방법 |
| --- | --- | --- | --- |
| P0 | 신규 대여의 14회 DB 왕복과 잉크 계정 행 잠금이 p95·p99를 제한한다 | `ReadingFacade`가 소장·대여 확인 후 계정을 잠그고 다시 확인한다 | 엔드포인트별 HTTP 지연, Hikari 대기, DB lock wait, JFR |
| P0 | 대여 이력이 늘면 최신 대여 조회가 불필요하게 많은 행을 읽는다 | `page_rental`에 `(reader_id, book_page_id, rented_at, id)` 인덱스가 없다 | 이력 규모별 `EXPLAIN ANALYZE`, rows examined |
| P0 | 원장·서재 이력이 늘면 정렬과 페이지 조회 비용이 커진다 | 원장·서재의 독자별 최신순 전용 인덱스가 없다 | 성장 데이터에서 실행 계획과 p95 비교 |
| P1 | 매 API 요청의 동기 INFO 로그가 높은 RPS에서 I/O와 할당을 늘린다 | `ApiRequestLoggingFilter`가 모든 `/api/**` 완료를 INFO로 기록한다 | 로그 on/off 실험, JFR allocation, CPU·p99 비교 |
| P1 | HikariCP 또는 Tomcat 기본값이 실제 DB·CPU 용량과 맞지 않는다 | 명시적 운영 풀·스레드 기준이 없다 | active/pending/max connection, Tomcat busy thread |
| P1 | 이미지·텍스트 콘텐츠 전송과 정적 자산이 브라우저 체감 시간을 제한한다 | 보호 콘텐츠는 `byte[]`로 응답하고 `no-store`다 | TTFB와 download time 분리, payload, Lighthouse/k6 browser |
| P2 | 반복되는 공개 도서 메타데이터 조회는 캐시로 DB 부하를 줄일 수 있다 | 도서 데이터는 MVP 동안 읽기 위주다 | hit ratio, DB 쿼리율, cache on/off 비교 |
| P2 | 다중 인스턴스에서 인메모리 `HttpSession`이 확장을 막는다 | ADR-0007도 공유 세션 저장소 전환을 예상한다 | 2개 인스턴스와 Redis 세션에서 로그인·로그아웃·부하 검증 |

`LIKE '%keyword%'` 검색은 일반 B-tree 인덱스만 추가해서 해결되지 않는다. MVP 데이터 100권에서 먼저
실제 비용을 측정하고, 규모 때문에 문제가 될 때만 FULLTEXT 등 검색 의미가 달라지는 대안을 별도 결정한다.

## 4. 사용할 도구와 채택 기준

| 도구 | 사용 목적 | 이번 계획의 결정 |
| --- | --- | --- |
| JUnit 5·MySQL 통합 테스트 | 정확성, 동시성, 쿼리 수, 스키마 인덱스 회귀 | 필수. 시간 기반 assertion은 두지 않는다 |
| k6 OSS | HTTP 부하, arrival rate, p50·p95·p99, RPS, 오류율, dropped iterations | 필수. 버전 또는 컨테이너 digest를 고정한다 |
| k6 browser 또는 Lighthouse | 도서 목록·상세·뷰어의 단일 사용자 렌더링과 Web Vitals | 최종 전후 각 3회만 실행한다 |
| Actuator·Micrometer Prometheus | HTTP, JVM, 프로세스, HikariCP 메트릭 | 성능 프로필에 필수. 공개 애플리케이션 포트에 노출하지 않는다 |
| Prometheus | k6 실행 시간대의 시계열 보존 | 성능 전용 Compose에서 필수 |
| Grafana | 동일 시간축의 애플리케이션·DB 지표 확인 | 로컬 진단용. 대시보드 JSON을 저장한다 |
| JFR·`jcmd`·`jfr` | CPU, allocation, GC, monitor·socket·file I/O, 가상 스레드 pinning | Java 21 내장 도구로 필수 진단 |
| MySQL Performance Schema | 상위 SQL, wait, lock, statement latency | 필수 |
| `EXPLAIN ANALYZE` | 실제 rows, loops, iterator별 시간과 인덱스 효과 | 느린 SQL마다 필수 |
| MySQL slow query log | 기준을 넘는 SQL 수집 | 측정 환경에서만 일시 활성화 |
| Docker stats 또는 호스트 지표 | CPU·메모리·네트워크·블록 I/O | 로컬 기준선에서 필수 |
| CloudWatch Database Insights | RDS DB Load·wait·상위 SQL | AWS 기준선을 실행할 때 필수 |
| Caffeine | 단일 인스턴스의 불변·저변경 공개 데이터 캐시 실험 | Redis 전에 먼저 비교한다 |
| Redis | 공유 캐시 또는 다중 인스턴스 세션 실험 | 조건부. 원장·잔액 정본으로 사용 금지 |
| Kafka | 이벤트 스트리밍 | 이번 계획에서는 사용하지 않는다 |

Actuator는 기존 CI 부팅 smoke를 위한 것이 아니다. 성능·운영 관측이라는 새 목적에 한해 추가하며,
`health`와 `prometheus`만 별도 management 포트·사설 네트워크에 노출한다. 민감한 환경변수, 세션 ID,
SQL 파라미터와 요청 본문은 메트릭 label에 넣지 않는다. Tomcat thread 지표가 필요하면 성능 프로필에서만
MBean registry를 활성화한다.

## 5. 측정 환경과 결과 보존

### 5.1 환경 원칙

1. 성능 DB는 `ilgeobolkka_perf`처럼 명시적인 전용 DB를 사용한다.
2. `_test`, 개발 DB, 운영 DB에는 성능 데이터 초기화 명령을 실행하지 않는다.
3. 기준선과 후보는 같은 패키징 JAR 실행 방식, Java 옵션, CPU·메모리 제한, MySQL 버전과 데이터
   스냅샷을 사용한다.
4. 워밍업 전 DB 스냅샷을 복원하고, 변경 요청에는 계정·페이지 풀을 분리해 앞 실행의 대여 상태가 다음
   실행에 섞이지 않게 한다.
5. 배포 환경 측정에서는 부하 생성기를 애플리케이션 호스트와 분리한다.
6. 코드 개선 비교 중에는 인프라 사양을 바꾸지 않는다. 인프라 개선 비교 중에는 애플리케이션 이미지를
   바꾸지 않는다.
7. 각 정식 시나리오는 워밍업 뒤 3회 실행하고 p50·p95·p99·RPS의 중앙값과 최솟값·최댓값을 기록한다.
8. k6 generator CPU가 포화되거나 `dropped_iterations`가 발생하면 서버 한계라고 판정하지 않는다.
9. Prometheus scrape와 JFR의 관측 오버헤드를 Average 1회 on/off로 확인한다. 정식 전후 3회 비교에서는
   관측 설정을 같게 유지하며, 진단용 JFR 실행을 일반 결과와 섞지 않는다.

### 5.2 데이터 세트

두 데이터 세트를 결정적으로 생성한다. 모든 생성기 입력과 예상 행 수를 Git에 보존한다.

| 이름 | 구성 | 목적 |
| --- | --- | --- |
| `mvp` | 도서 100권·400페이지, 부하 계정 1,000개, 신규·활성·소장 상태를 균등 배분 | 실제 MVP 기능 혼합 부하 |
| `history-heavy` | `mvp`에 독자별 대여·원장·서재 이력이 큰 계정군을 추가 | 인덱스와 최신 이력 조회의 성장성 확인 |

`history-heavy`의 정확한 이력 건수는 생성 시간 10분, DB 크기 2GB 이내에서 가능한 최대치로 한 번
결정한 뒤 바꾸지 않는다. 최초 결정값과 생성 시간·행 수·DB 크기를 `environment.md`에 기록한다.

### 5.3 저장 구조

```text
performance/
  README.md
  k6/
    smoke.js
    average-load.js
    peak-load.js
    stress.js
    spike.js
    contention.js
    lib/
  data/
    README.md
  prometheus/
  grafana/
compose.performance.yaml

docs/evidence/performance/
  mvp1-baseline-2026-08-12/
    environment.md
    result.md
    summary-run-1.json
    summary-run-2.json
    summary-run-3.json
    dashboards/
  mvp1-final-2026-08-15/
    environment.md
    comparison.md
    summary-run-1.json
    summary-run-2.json
    summary-run-3.json
    dashboards/
```

작은 k6 summary JSON, 환경 문서와 대시보드 정의는 Git에 저장한다. 원시 시계열, 전체 로그와 `.jfr`처럼
큰 파일은 CI artifact 또는 객체 저장소에 두고 SHA-256, 크기, 보존 위치와 시간 범위만 근거 문서에 남긴다.
비밀번호·쿠키·CSRF 토큰·세션 ID·DB 접속 문자열은 어떤 결과 파일에도 기록하지 않는다.

`environment.md`에는 최소한 다음을 기록한다.

- Git SHA, dirty 여부, 변경 파일 목록
- JAR SHA-256과 컨테이너를 사용하면 image digest
- Java·Spring Boot·Gradle·k6·MySQL·Redis·Prometheus·Grafana 버전
- 호스트 OS, CPU 모델·코어, 메모리, CPU·메모리 제한
- 애플리케이션·DB·부하 생성기의 네트워크 위치
- JVM 옵션, Tomcat과 HikariCP 설정
- 데이터 세트명, 행 수, DB 크기와 스냅샷 식별자
- 시나리오의 arrival rate, VU 상한, 시간, 워밍업, think time와 요청 비율
- 시작·종료 UTC 시각과 연결한 Prometheus·JFR·DB 관측 시간 범위
- 외부 API 대체 여부와 기능 플래그

## 6. 측정 시나리오

### 6.1 정상 혼합 흐름

평균·피크 부하는 아래 비율을 시작점으로 사용한다. 실제 제품 트래픽 자료가 생기면 이 표를 대체하고
근거를 기록한다.

| 그룹 | 비율 | 경로 |
| --- | ---: | --- |
| 공개 탐색 | 35% | 도서 목록, 검색, 상세 |
| 인증 조회 | 20% | 서재, 잉크 잔액, 잉크 내역 |
| 소장 콘텐츠 | 15% | 소장 도서 페이지 열기와 콘텐츠 조회 |
| 활성 대여 재열람 | 10% | 추가 차감 없는 페이지 재열람과 콘텐츠 조회 |
| 신규 대여 | 15% | 새 세션·페이지 이동, 1잉크 차감, 콘텐츠 조회 |
| 로그인 | 5% | 로그인 화면 CSRF 발급과 로그인 |

각 VU는 자신의 cookie jar를 사용한다. 로그인은 각 VU의 최초 흐름에서 한 번 수행하고, 로그인 자체의
5% 시나리오 외에는 준비 시간을 엔드포인트 지연 통계에서 분리한다. CSRF를 끄거나 우회하지 않는다.

### 6.2 부하 단계

예상 운영 트래픽이 아직 없으므로 아래 수치는 제품 SLO가 아니라 8월 15일까지 비교 가능한 공학적
characterization 부하다. 한 단일 애플리케이션이 200 RPS 단계를 안정적으로 통과하면 이번 기한에는 더
높은 최대치를 찾지 않고 실제 사용자 추정치를 먼저 정한다.

| 단계 | 부하 | 시간·반복 | 목적 |
| --- | --- | --- | --- |
| Smoke | 1 VU, 각 흐름 1회 | 변경마다 1회 | 계약·데이터·CSRF·세션 확인 |
| Warm-up | 5 RPS | 5분, 결과 제외 | JIT·풀·캐시 안정화 |
| Average | 20 RPS | 20분 × 3회 | 지속 기준선 |
| Peak | 50 RPS | 15분 × 3회 | 단기 피크 |
| Stress | 10→25→50→100→200 RPS | 단계당 5분, 1회 | 성능 변곡점과 병목 자원 확인 |
| Spike | 20→100→20 RPS | 2분→2분→5분, 1회 | 급증 뒤 회복 확인 |
| Soak | 20 RPS | 최종 60분, 1회 | 메모리·연결·스레드 누수 확인 |

Stress는 오류율 1% 초과 또는 전체 p95 2초 초과가 2분 지속되면 중단하고 해당 단계를 용량 한계로
기록한다. 이 중단선은 정상 품질 목표가 아니라 안전한 테스트 중단 기준이다.

### 6.3 동시성 특화 흐름

정상 혼합 부하와 분리해 다음을 실행한다.

1. 같은 독자·같은 미대여 페이지를 20개 VU가 동시에 연다.
   - 차감 1건, 대여 1건, 나머지는 활성 대여 재사용이어야 한다.
2. 같은 독자·서로 다른 20페이지를 동시에 연다.
   - 페이지별 1번씩만 차감되고 잔액이 음수가 아니어야 한다.
   - 잉크 계정 잠금 대기 시간과 트랜잭션 시간을 기록한다.
3. 서로 다른 100명의 독자가 서로 다른 페이지를 동시에 연다.
   - 공유 핫키 없이 애플리케이션·DB의 순수 처리량을 측정한다.
4. 같은 로그인 계정으로 여러 세션을 만들고 로그아웃한다.
   - 모든 세션·현재 뷰어 정책이 기존 계약대로 동작하는지 확인한다.

각 실행 뒤 SQL 또는 MySQL 통합 검증기로 다음을 대조한다.

- 잔액 = 총 지급 - 총 차감
- 차감 원장 수 = 신규 대여에 연결된 차감 수
- 같은 대여·결제 효과 중복 없음
- 음수 잔액 없음
- 활성 대여 재사용 요청의 추가 차감 없음
- 소장 페이지의 차감 없음
- 마지막 위치와 현재 뷰어의 계약 유지

### 6.4 외부 결제 경계

결제 준비 API는 낮은 부하로 애플리케이션 내부 경로만 측정할 수 있다. 결제 완료·웹훅의 PortOne 서버
재조회는 성능 프로필의 결정적 fake 또는 로컬 stub으로 애플리케이션 로직을 측정하되, 운영과 같은 서명·
멱등·트랜잭션 검증은 기존 통합 테스트로 유지한다. PortOne 테스트 채널 자체에는 Average·Peak·Stress를
실행하지 않는다.

## 7. 수집 지표와 판정선

### 7.1 클라이언트와 사용자 지표

- 엔드포인트·시나리오별 요청 수와 실제 RPS
- `http_req_failed`, checks 실패율, 4xx와 5xx 분리
- p50, p90, p95, p99, max
- DNS·connect·TLS·TTFB·download 시간 분리
- response bytes와 content type
- `dropped_iterations`, VU 사용량과 부하 생성기 CPU
- 브라우저 LCP, CLS, INP 또는 실험실 대체 지표, TTFB

### 7.2 애플리케이션 지표

- `http.server.requests`의 URI template·status별 count와 latency
- JVM process CPU, system CPU, heap·non-heap, allocation, GC pause
- live·daemon·peak thread와 Tomcat current/busy/max thread
- Hikari active, idle, pending, max, acquire·usage time
- 로그 처리량과 stdout/file I/O
- JFR 상위 CPU stack, allocation stack, monitor·socket·file I/O, GC와 pinning

사용자 ID, book ID, page number, session ID처럼 cardinality가 무한히 늘 수 있는 값은 metric tag로 넣지 않는다.

### 7.3 MySQL 지표

- DB CPU, 메모리, connection과 thread
- DB Load, wait event, row lock wait·deadlock
- statements latency, calls, rows examined·returned
- buffer pool hit, disk read/write latency와 IOPS
- 임시 테이블, 정렬, full scan
- 느린 SQL의 `EXPLAIN ANALYZE` 전후 결과

### 7.4 잠정 품질 게이트

아래는 운영 SLO가 아니라 이번 개선의 동일 환경 회귀 게이트다.

| 지표 | Average·Peak 목표 |
| --- | --- |
| 기능 check | 100% 통과 |
| 예상하지 않은 5xx | 0건 |
| 전체 HTTP 실패율 | 0.1% 미만 |
| `dropped_iterations` | 0건 |
| 공개 조회 p95 | 300ms 이하 |
| 인증 조회 p95 | 500ms 이하 |
| 신규 대여 p95 | 800ms 이하 |
| 전체 p99 | 1,500ms 이하 |
| Hikari pending | Average에서 지속 0, Peak에서 장기 누적 없음 |
| JVM·DB CPU | Average에서 70% 이하를 원칙으로 하되 병목 근거와 함께 판정 |
| 부하 뒤 도메인 불변식 | 전부 통과 |

기준선이 이미 이 게이트를 통과하면 지연을 억지로 줄이지 않는다. 그 경우 동일 게이트를 유지하며 지속
처리 가능한 RPS를 높이거나 인프라 복잡도를 늘리지 않는 것을 성공으로 본다. 기준선이 게이트를 넘는다면
8월 15일까지 임계값을 낮춰 통과시키지 말고 미달과 원인을 그대로 보고한다.

## 8. 병목별 트러블슈팅과 개선 후보

### 8.1 판단 순서

| 관측 | 다음 확인 | 우선 개선 |
| --- | --- | --- |
| 애플리케이션 CPU 높음 | JFR CPU·allocation 상위 stack | 직렬화, 로그, 불필요 객체·쿼리 후처리 |
| CPU 낮고 Hikari pending 높음 | DB connection, 상위 SQL, lock wait | SQL·인덱스·트랜잭션 단축, 그 뒤 풀 조정 |
| DB CPU·rows examined 높음 | `EXPLAIN ANALYZE` | 복합 인덱스, 쿼리 형태 변경 |
| DB CPU 낮고 lock wait 높음 | 같은 독자 핫키 여부, 트랜잭션 시간 | 잠금 전 작업 제거, 잠금 구간·쿼리 왕복 단축 |
| p99만 튐 | GC pause, allocation, 로그 I/O, CPU throttle | 할당·로그 개선, 메모리·CPU 제한 조정 |
| TTFB는 빠르고 total time이 느림 | payload와 download | 정적 자산 압축·캐시, 이미지 크기·전송 점검 |
| 서로 다른 독자는 빠르고 같은 독자만 느림 | 잉크 계정 행 잠금 | 정합성 잠금은 유지하고 critical section만 단축 |
| 로그인만 느림 | BCrypt CPU와 비율 | 정상 보안 비용으로 분리 측정, 비용 하향 금지 |
| k6 dropped iteration·generator CPU 높음 | 부하 생성기 지표 | generator 확장 후 재측정 |

### 8.2 1차 개선: 새 상태 인프라 없이 수행

다음은 8월 13일 우선 작업이다. 후보마다 재현 테스트 → 한 가지 변경 → 전체 정확성 검증 → 같은 k6
시나리오 순서로 판정한다.

1. 실행 계획으로 입증된 복합 인덱스
   - 후보: `page_rental(reader_id, book_page_id, rented_at DESC, id DESC)`
   - 후보: `ink_ledger(reader_id, occurred_at DESC, id DESC)`
   - 후보: `library_entry(reader_id, updated_at DESC, id DESC)`
   - 후보: 결제 내역의 독자별 최신순 조회 인덱스
   - Flyway migration과 실제 MySQL 통합 테스트로만 추가한다.
2. 신규 페이지 열기 왕복 단축
   - 14쿼리의 각 SQL과 목적을 기록한다.
   - 잠금 뒤 재확인과 원자성은 유지하면서 중복 balance·ownership·rental 조회 또는 flush를 줄인다.
   - 목표는 쿼리 수 20% 이상 감소이며, 숫자를 맞추기 위해 정책 검증을 삭제하지 않는다.
3. 요청 로그 비용
   - 현재 INFO 완료 로그를 비동기 appender, sampling 또는 운영 목적에 맞는 레벨로 비교한다.
   - 실패·requestId 추적은 유지하고 요청 본문·민감정보는 기록하지 않는다.
4. HikariCP·Tomcat 조정
   - 풀 크기를 먼저 늘리지 않는다. DB CPU·connection limit·acquire time을 확인해 작은 범위로 탐색한다.
   - 각 조합은 별도 결과로 저장하고 가장 큰 값이 아니라 p95와 오류율이 안정적인 최소값을 채택한다.
5. JVM과 컨테이너
   - 명시적 CPU·메모리 제한과 heap 기준을 고정한다.
   - GC 변경은 JFR에서 GC 병목이 확인될 때만 실험한다.

### 8.3 2차 개선: 캐시와 Redis 조건부 실험

캐시는 Average 기준 DB 읽기 부하가 병목이고 같은 키의 반복률이 충분할 때만 실험한다.

1. 단일 인스턴스에서는 Caffeine을 먼저 적용해 네트워크 홉 없는 상한 효과를 측정한다.
2. 다음 데이터만 후보로 삼는다.
   - 공개 도서 목록·상세의 저변경 메타데이터
   - 권한 확인을 통과한 뒤 읽는 불변 페이지 바이트의 내부 캐시
3. 다음 데이터는 캐시하지 않는다.
   - 잉크 잔액과 원장
   - 활성 대여와 소장 여부
   - 현재 `ReadingSession`과 인가 결과
   - 결제 상태와 멱등 처리 결과
4. Caffeine이 효과가 있고 다중 인스턴스 간 공유가 실제 필요할 때만 Redis 캐시를 비교한다.
5. hit ratio 80% 이상, 대상 endpoint p95 20% 이상 개선 또는 DB statement rate 30% 이상 감소 중 하나를
   충족하고 불변식·메모리·무효화 검증을 통과해야 채택한다.
6. 효과가 작으면 Redis 인프라와 의존성을 제거하고 결과만 남긴다.

Redis를 채택하면 장애 시 동작을 명시한다. 공개 메타데이터 캐시는 cache miss로 MySQL에 fallback할 수
있지만, Redis 장애 때문에 인가를 우회하거나 오래된 권한을 사용해서는 안 된다.

### 8.4 3차 개선: 다중 인스턴스와 Spring Session Redis

단일 인스턴스가 목표 부하를 만족하면 8월 15일까지는 다중 인스턴스로 전환하지 않는다. 단일 인스턴스
CPU가 포화되고 DB에 여유가 있거나, 배포 요구가 다중 인스턴스인 경우에만 다음을 실험한다.

1. Spring Session Redis로 `HttpSession`을 공유한다.
2. 기존 쿠키, CSRF, 로그인 성공 세션 ID 교체, 로그아웃 즉시 무효화를 그대로 검증한다.
3. 2개 애플리케이션 인스턴스에 같은 사용자의 요청을 번갈아 보내도 인증 상태가 유지돼야 한다.
4. Redis 재시작·단절 때 세션 실패 방식과 재로그인 경로를 검증한다.
5. 1개 대비 2개 인스턴스의 Peak 처리량이 60% 이상 증가하고 DB·Redis가 새 병목이 되지 않아야 채택한다.

Spring Session Redis는 단일 인스턴스 지연 개선 도구가 아니다. 네트워크 홉 때문에 단일 인스턴스 지연이
악화될 수 있으며, 채택 목적은 다중 인스턴스의 세션 일관성과 수평 확장이다.

### 8.5 가상 스레드 실험

Java 21 가상 스레드는 I/O 대기가 크고 HikariCP에 여유가 있을 때만 별도 후보로 실행한다.

- on/off 외의 코드·설정은 동일하게 유지한다.
- JFR로 pinned virtual thread를 확인한다.
- HikariCP pending이 증가하거나 처리량·p95가 악화되면 폐기한다.
- Peak 처리량 20% 이상 증가와 p95 비악화를 동시에 만족할 때만 채택한다.
- 채택하면 daemon thread와 `spring.main.keep-alive` 영향을 부팅·스케줄 검증에 포함한다.

## 9. 예상 개선 범위

아래 수치는 보장값이 아니라 현재 코드 구조에서 세운 가설 범위다. 해당 병목이 관측되지 않으면 예상 효과는
0%이며, 실제 채택 여부는 같은 환경의 3회 중앙값으로 결정한다.

| 개선 후보 | 대상 | 예상 효과 | 채택 하한 |
| --- | --- | --- | --- |
| 최신 이력 복합 인덱스 | 대여·원장·서재 성장 데이터 | 대상 SQL 50~95%, HTTP p95 10~60% 단축 | 실행 계획 개선과 p95 15% 이상 |
| 신규 대여 쿼리 왕복 단축 | 페이지 첫 열기 | p95 20~45%, 처리량 20~60% 개선 | 쿼리 20% 감소와 p95 15% 이상 |
| 비동기·축약 요청 로그 | 높은 RPS의 모든 API | CPU·p99 5~30% 개선 | p95 또는 CPU 10% 이상, 추적성 유지 |
| HikariCP·Tomcat 적정화 | 풀·스레드 포화 구간 | Peak 처리량 10~40% 개선 | 오류·pending 감소, DB CPU 안전 범위 |
| Caffeine 공개 데이터 캐시 | 반복 도서 조회 | 대상 p95 20~70%, DB 조회 30~90% 감소 | 캐시 채택 기준 충족 |
| Redis 공유 캐시 | 다중 인스턴스 반복 조회 | 대상 p95 10~60%, DB 조회 30~80% 감소 | Caffeine 효과와 공유 필요 모두 확인 |
| Spring Session Redis + 2 인스턴스 | 수평 확장 | 지속 RPS 1.6~1.9배 가능 | 60% 이상 증가와 세션 계약 통과 |
| 가상 스레드 | I/O 대기 높은 경로 | 0~100% 처리량 변화, 회귀 가능 | RPS 20% 이상과 p95 비악화 |
| 정적 자산 압축·캐시 | 공개 화면 | 전송 bytes 20~70%, LCP 10~30% 개선 | Web Vital 또는 bytes 15% 이상 |

전체 목표는 정확성을 유지하며 가장 느린 내부 endpoint 두 개의 p95를 기준선보다 30% 이상 줄이거나,
이미 잠정 지연 게이트를 만족한다면 같은 지연 게이트에서 지속 처리량을 1.5배 이상 높이는 것이다. 새
상태 인프라 없이 이 목표를 만족하면 Redis·다중 인스턴스를 추가하지 않는 결과가 더 낫다.

## 10. 2026-08-11~15 실행 일정

### 8월 11일 — 측정 장치와 재현 환경

- [ ] 실행 시작 SHA와 dirty 상태 기록
- [ ] 관측 스택의 ADR 작성 필요 여부 판정
- [ ] k6 버전·실행 방식을 고정하고 smoke·공통 인증·CSRF 흐름 작성
- [ ] `ilgeobolkka_perf` 전용 DB와 결정적 `mvp` 데이터 준비
- [ ] Actuator·Micrometer Prometheus를 성능 프로필과 사설 management 포트에 추가
- [ ] `compose.performance.yaml`에 Prometheus·Grafana 구성
- [ ] 관측 on/off Average로 관측 오버헤드 기록
- [ ] 민감 label과 endpoint 노출 보안 테스트 추가
- [ ] `docker compose config -q`, 대상 테스트, 전체 test·check·build 통과

산출물: 실행 가능한 smoke, 성능 환경 README, 대시보드 초안, 환경 메타데이터 초안.

### 8월 12일 — MVP1 기준선과 병목 확정

- [ ] 패키징 JAR로 깨끗한 성능 환경 부팅
- [ ] Smoke → Warm-up → Average 3회 → Peak 3회 → Stress 실행
- [ ] 동시성 특화 4종 실행 후 불변식 대조
- [ ] `history-heavy` 데이터 생성 규모 고정
- [ ] JFR과 MySQL Performance Schema·slow query 수집
- [ ] 느린 SQL마다 `EXPLAIN ANALYZE` 저장
- [ ] 기준선 `result.md`에 상위 병목 3개와 근거 작성

산출물: `mvp1-baseline-2026-08-12`, 우선순위가 있는 병목 목록. 근거 없는 개선은 다음 단계에 올리지 않는다.

### 8월 13일 — 1차 개선과 반복 측정

- [ ] 인덱스·SQL·쿼리 왕복·로그·풀 후보를 효과 대비 위험 순서로 하나씩 변경
- [ ] 각 변경 전 재현 테스트 작성
- [ ] 후보마다 대상 테스트와 동일 k6 Average 1회 실행
- [ ] 효과가 채택 하한보다 작거나 정확성이 깨지면 제거
- [ ] 채택 후보를 합친 뒤 전체 test·check·build와 Average·Peak 각 3회 실행

산출물: 변경별 전후 표, 채택·폐기 이유, 1차 후보 이미지/JAR SHA.

### 8월 14일 — 조건부 인프라 실험

- [ ] DB 읽기가 여전히 병목일 때 Caffeine 캐시 실험
- [ ] Caffeine 효과와 공유 필요가 모두 있을 때만 Redis 캐시 실험
- [ ] 수평 확장 요구와 단일 인스턴스 포화가 있을 때만 Spring Session Redis·2인스턴스 실험
- [ ] I/O 대기와 DB 여유가 있을 때만 가상 스레드 on/off 실험
- [ ] Kafka는 채택 게이트가 없으므로 추가하지 않음 확인
- [ ] 최종 후보로 Spike 실행, 불변식 대조
- [ ] 운영 표준으로 채택할 구조의 ADR 작성 또는 대체 절차 수행

산출물: 조건부 실험 비교표, 유지할 인프라만 남은 재현 구성, ADR.

### 8월 15일 — 깨끗한 최종 재측정과 보고

- [ ] 이전 컨테이너·프로세스가 아닌 깨끗한 성능 환경을 동일 사양으로 생성
- [ ] 같은 데이터 생성 입력 또는 스냅샷 복원
- [ ] Smoke → Warm-up → Average 3회 → Peak 3회 → Stress → Spike → Soak 실행
- [ ] 동시성 특화 흐름과 부하 뒤 불변식 전체 대조
- [ ] 브라우저 전후 각 3회 비교
- [ ] 전체 `./gradlew test`, `./gradlew check`, `./gradlew build` 통과
- [ ] 패키징 JAR 부팅과 `/api/smoke` 확인
- [ ] `comparison.md`에 기준선·변경별 효과·최종값·남은 병목·다음 용량 한계 작성
- [ ] 실행 명령, 통과/실패 테스트 수, 생략한 검증과 이유를 그대로 기록

산출물: `mvp1-final-2026-08-15`, 재현 명령, 최종 비교와 후속 작업 목록.

## 11. AI 에이전트 실행 규칙

1. 매 단계 시작 시 완료 기준을 한 문장으로 다시 적고 현재 SHA·dirty 상태를 확인한다.
2. 사용자에게 다시 물어야 하는 경우는 유료 클라우드 자원 생성, 새 secret·외부 계정 필요, 제품 정책 변경,
   운영 데이터 접근뿐이다. 나머지는 이 문서의 우선순위와 채택 게이트로 진행한다.
3. 새 성능 버그는 재현 테스트를 먼저 만들고 수정한다.
4. 한 실험에서 독립 변수는 하나만 바꾼다.
5. 좋은 실행 하나를 고르지 않고 같은 조건 3회 중앙값과 범위를 사용한다.
6. 기준선과 후보의 코드·인프라가 동시에 다르면 비교 결과를 무효로 하고 다시 실행한다.
7. 성능을 위해 assertion, CSRF, 인가, 트랜잭션과 고유 제약을 제거하지 않는다.
8. 성능이 개선돼도 복잡도와 장애 지점이 늘면 채택 하한을 충족하지 않는 한 단순한 구성을 유지한다.
9. 실패한 실험을 숨기지 않고 수치와 폐기 이유를 남긴다.
10. 성능 도구가 생성한 secret·cookie·세션 식별자와 외부 API 원문을 저장소에 넣지 않는다.
11. 코드·설정 변경 뒤에는 [테스트 스킬](../../../.agents/skills/test/SKILL.md)의 완료 체크리스트를 따른다.
12. 구조 변경을 채택할 때는 [ADR 스킬](../../../.agents/skills/adr/SKILL.md)을 따른다.

## 12. 최종 비교 보고서 형식

`comparison.md`는 최소한 다음 표와 판정을 포함한다.

| 시나리오 | 기준 RPS | 최종 RPS | 기준 p95 | 최종 p95 | 기준 p99 | 최종 p99 | 오류율 전→후 | 판정 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 공개 탐색 |  |  |  |  |  |  |  |  |
| 인증 조회 |  |  |  |  |  |  |  |  |
| 신규 대여 |  |  |  |  |  |  |  |  |
| 활성 재열람 |  |  |  |  |  |  |  |  |
| 소장 콘텐츠 |  |  |  |  |  |  |  |  |

| 변경 | 근거 | 기대 | 실제 | 정확성 회귀 | 유지/폐기 |
| --- | --- | --- | --- | --- | --- |
|  |  |  |  |  |  |

보고서 마지막에는 다음을 단정적으로 적는다.

- 어느 자원이 현재 첫 번째 용량 한계인지
- 잠정 품질 게이트를 통과했는지
- 기준선 대비 p95·p99·지속 RPS가 몇 % 변했는지
- Redis·다중 인스턴스·가상 스레드·Kafka를 각각 왜 유지하거나 제외했는지
- 운영 용량으로 해석할 수 있는 환경인지
- 다음 1순위 개선 한 건과 예상 효과

## 13. 공식 참고 자료

- [k6 시나리오와 executor](https://grafana.com/docs/k6/latest/using-k6/scenarios/)
- [k6 threshold](https://grafana.com/docs/k6/latest/using-k6/thresholds/)
- [k6 결과 출력과 summary](https://grafana.com/docs/k6/latest/results-output/)
- [Spring Boot Actuator Metrics](https://docs.spring.io/spring-boot/reference/actuator/metrics.html)
- [Spring Boot Prometheus endpoint](https://docs.spring.io/spring-boot/api/rest/actuator/prometheus.html)
- [Prometheus 개요](https://prometheus.io/docs/introduction/overview/)
- [Java 21 JFR 명령](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jfr.html)
- [Java 21 `jcmd`](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jcmd.html)
- [MySQL 8.4 `EXPLAIN ANALYZE`](https://dev.mysql.com/doc/refman/8.4/en/explain.html)
- [MySQL 8.4 slow query log](https://dev.mysql.com/doc/refman/8.4/en/slow-query-log.html)
- [CloudWatch Database Insights](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/Database-Insights.html)
- [Spring Session Redis 구성](https://docs.spring.io/spring-session/reference/configuration/redis.html)
- [Spring Boot 캐시 공급자](https://docs.spring.io/spring-boot/reference/io/caching.html)
- [Spring Boot 가상 스레드](https://docs.spring.io/spring-boot/reference/features/spring-application.html#features.spring-application.virtual-threads)
- [Apache Kafka 개요](https://kafka.apache.org/documentation/)
