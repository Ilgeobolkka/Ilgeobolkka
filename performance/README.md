# 로컬 성능 환경 실행

이 디렉터리는 MVP1 성능 runbook의 로컬 진단 환경이다. 운영 관측 표준이 아니며 Compose project
`ilgeobolkka-performance`, DB `ilgeobolkka_perf`, 전용 volume만 사용한다. PortOne과 OpenAI는 모든
성능 실행에서 강제로 비활성화하며 Redis는 조건부 4단계 전에는 포함하지 않는다.

## 사전 조건

- Docker Desktop이 실행 중이어야 한다.
- Git에서 제외된 루트 `.env`에 기존 `DB_USERNAME`, `DB_PASSWORD`, `DB_ROOT_PASSWORD`,
  `DEMO_VALIDATION_PASSWORD`와 `PERF_MYSQL_EXPORTER_PASSWORD`가 설정돼 있어야 한다.
- 비밀번호·cookie·CSRF token·session ID·JDBC URL은 결과 파일에 기록하지 않는다.

## 빌드와 환경 준비

저장소 루트에서 다음 순서로 실행한다.

```sh
./gradlew build
./performance/scripts/recreate-environment.sh
./performance/scripts/reset-mvp.sh
./performance/scripts/start-app.sh
./performance/scripts/verify-dataset.sh
./performance/scripts/verify-management.sh
```

`recreate-environment.sh`는 애플리케이션이 멈춘 상태에서 성능 project의
`mysql-perf-data`, `prometheus-data`만 삭제한다. 개발 volume `ilgeobolkka_mysql-data`는 건드리지 않는다.
데이터 초기화기는 JDBC catalog가 정확히 `ilgeobolkka_perf`가 아니면 첫 조회·초기화 전에 실패한다.

## 부하 실행

각 실행은 `var/performance/results/<UTC>-<이름>/`에 `summary.json`, `metadata.json`,
`exit-code.txt`를 만든다. Average·Peak 정식 반복은 매회 `stop-app.sh` → `reset-mvp.sh` →
`start-app.sh` → `warm-up` → 대상 시나리오 순서를 지킨다.

```sh
./performance/scripts/run-k6.sh smoke
./performance/scripts/run-k6.sh warm-up
./performance/scripts/run-k6.sh average-load
./performance/scripts/run-k6.sh peak-load
./performance/scripts/run-k6.sh stress
./performance/scripts/run-k6.sh spike
./performance/scripts/run-k6.sh soak
./performance/scripts/run-k6.sh browser
./performance/scripts/run-k6.sh browser-cache
```

Average·Peak은 공개 탐색 35%, 인증 조회 20%, 소장 콘텐츠 15%, 활성 대여 10%, 신규 대여 15%,
로그인 5%를 서로 다른 k6 scenario로 실행한다. 표의 20/50 RPS는 k6 flow iteration 도착률이며 한 flow가
여러 HTTP 요청을 포함할 수 있으므로 실제 HTTP RPS는 summary와 Prometheus 값으로 별도 기록한다.
각 VU는 k6 기본 독립 cookie jar를 사용하고 CSRF를 우회하지 않는다.
신규 대여는 VU별 시나리오 iteration을 사용해 80회마다 결정적으로 다른 신규 계정으로 순환한다.
기준선 runner는 Smoke·Warm-up·정식 부하의 신규 계정 pool을 분리하며, 그 경계에서만 다시 로그인한다.
로그인 준비 요청은 `setup=true` tag로 구분한다.

2단계 Average·Peak 3회는 매회 데이터 복원, 새 애플리케이션, Smoke, 고정 Warm-up을 자동으로 적용한다.

```sh
./performance/scripts/run-baseline-series.sh average-load
./performance/scripts/run-baseline-series.sh peak-load
```

각 정식 부하 결과에는 `generator-cpu.tsv`, `generator-summary.json`,
`prometheus-summary.json`, `prometheus-range/`, `k6.log`가 함께 저장된다. generator CPU가 90% 이상이거나
dropped iteration이 발생하면 해당 실행을 유효 기준선으로 사용하지 않는다.
`PERF_DURATION`은 도구 진단용 실행에만 사용하며 정식 Warm-up·Average·Peak에서는 설정하지 않는다.

동시성 특화 흐름은 매번 데이터를 복원한 뒤 하나씩 실행한다.

```sh
CONTENTION_CASE=same-page ./performance/scripts/run-k6.sh contention
CONTENTION_CASE=different-pages ./performance/scripts/run-k6.sh contention
CONTENTION_CASE=different-readers ./performance/scripts/run-k6.sh contention
CONTENTION_CASE=session ./performance/scripts/run-k6.sh contention
./performance/scripts/verify-invariants.sh
```

동시성 실행 뒤에는 행 수가 변하므로 초기 행 수까지 고정하는 `verify-dataset.sh`가 아니라 잔액·원장·대여·
소장 불변식만 확인하는 `verify-invariants.sh`를 사용한다.

브라우저 cache 진단은 서로 독립된 새 context 3개에서 공개 목록과 보호 뷰어를 각각 cold→warm 순서로
반복한다. 공개 정적 자산의 전송량·Cache-Control과 보호 콘텐츠의 `private, no-store`를 분리해
`browser-cache.jsonl`에 저장한다.

`history-heavy`는 애플리케이션을 정지한 상태에서 고정 `mvp`를 복원한 뒤 독자마다 과거 대여 500건을
추가한다. 생성 시간 10분·DB 2GiB 경계를 넘으면 실패하며, 진단 뒤에는 다시 `reset-mvp.sh`를 실행한다.

```sh
./performance/scripts/stop-app.sh
./performance/scripts/generate-history-heavy.sh
```

## 관측과 진단

Grafana는 `http://127.0.0.1:3000`, Prometheus는 `http://127.0.0.1:9090`, management는
`http://127.0.0.1:8081`에서만 접근한다. 대시보드는 HTTP, JVM, Tomcat, HikariCP, MySQL을 같은 시간축에
표시한다. 관측 scrape 오버헤드는 두 모드 모두 새 성능 volume과 새 JVM에서 3분 Warm-up 뒤 10분 Average로
측정한다.

```sh
./performance/scripts/run-observation-overhead.sh
```

JFR은 정식 부하 결과와 분리해 app container 안에서 다음처럼 저장한다.

```sh
mkdir -p "$(pwd)/var/performance/jfr"
PERFORMANCE_JFR_NAME="diagnostic-$(date -u +%Y%m%dT%H%M%SZ).jfr"
docker compose -p ilgeobolkka-performance -f compose.performance.yaml exec -T app \
  jcmd 1 JFR.start name=performance-diagnostic settings=profile duration=120s \
  filename="/var/performance/jfr/$PERFORMANCE_JFR_NAME"
shasum -a 256 "$(pwd)/var/performance/jfr/$PERFORMANCE_JFR_NAME"
```

## 중지와 보존

```sh
./performance/scripts/stop-app.sh
./performance/scripts/stop-environment.sh
```

중지는 volume을 보존한다. 전용 volume까지 삭제하려면 애플리케이션을 멈춘 뒤
`./performance/scripts/recreate-environment.sh`가 출력하는 정확한 삭제 범위를 다시 확인한다.
