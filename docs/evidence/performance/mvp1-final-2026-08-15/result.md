# 5단계 깨끗한 최종 재측정 결과

## 최종 판정

- 단계 결과: **통과**
- 진행 상태: **5단계 완료·6단계 대기**
- 정확성·잠정 품질 게이트: **통과**
- 성능 목표: **기준선 충족으로 단순 구성 유지**
- 기준 Git SHA: `f3a62809099ba660ce980b006d71c833bbd849fa`
- 최종 JAR SHA-256: `5c4e146b2a98d85cd4465b204a64660911dd72b3965b807791e868d66a2b9eb0`
- 채택 구성: V4 이력 조회 복합 인덱스 2개, 단일 애플리케이션·MySQL
- 제외 구성: Caffeine·Redis cache·Spring Session Redis·다중 인스턴스·가상 스레드·Kafka
- 범위: 로컬 Docker 코드 회귀 검증. 운영 SLO·운영 최대 RPS·운영 용량 근거가 아니다.

5단계는 성능 전용 MySQL·Prometheus volume만 재생성한 깨끗한 환경에서 시작했다. 정식 Average·Peak는
매회 동일 JAR로 `mvp 복원 → 새 앱 → Smoke → 3분 Warm-up → 10분 본 측정`을 수행했고, Stress·Spike·
Soak·동시성·브라우저·전체 빌드·패키징 부팅까지 완료했다. 새 병목이나 포화가 없어 JFR·Performance
Schema·`EXPLAIN ANALYZE` 진단은 정식 실행에 추가하지 않았다. 이력 인덱스의 분리 진단 근거는 3단계
evidence를 그대로 사용한다.

## 시나리오 결과

- Average 3회: checks 31,944~31,945건 모두 통과, 오류·dropped 0, p95 중앙값 17.375ms,
  p99 중앙값 85.170ms
- Peak 3회: checks 79,520~79,525건 모두 통과, 오류·dropped 0, p95 중앙값 10.735ms,
  p99 중앙값 81.960ms
- Stress: 10→25→50→100→200 flow/s 전 단계 완료, checks 142,452/142,452, 오류·dropped 0,
  p95 12.857ms
- Spike: 재실행 checks 73,356/73,356, 오류·dropped 0, p95 15.193ms
- Soak: 30분 재실행 checks 95,689/95,689, 오류·dropped 0, p95 14.734ms, Hikari pending 0
- 동시성 4종: 모든 k6 check와 잔액·원장·대여·소장·마지막 위치·세션 계약 통과
- 브라우저: feature checks 15/15, 공개 자산 전송량 cold/warm 각각 329,581 bytes, 보호 콘텐츠
  `private, no-store` 유지

세부 수치와 브라우저 한계는 [summary.md](./summary.md), 같은 시간대 자원 지표는
[dashboard.md](./dashboard.md), 기준선·후보·최종 비교는 [comparison.md](./comparison.md)에 기록했다.

## 실패·무효 실행

### 첫 Spike와 첫 Soak

첫 Spike와 첫 Soak는 각각 기능 check 1건이 실패해 exit code 99로 무효 처리했다. HTTP 오류·dropped와
DB 공통 불변식은 모두 0이어서 좋은 지연값만 골라 채택하지 않았다. 데이터·앱을 고정 출발점으로 복원하고
전체 시간을 재실행했다. Soak는 최대 VU보다 작은 신규 독자 `vuStride`가 slot 충돌을 허용하는 harness
조건을 바로잡았고 제품 코드·부하 단계는 바꾸지 않았다. 무효·유효 원본을 모두 보존했다.

### 첫 전체 테스트

첫 `./gradlew test --rerun-tasks`는 개발 MySQL을 기동하지 않은 환경에서 실행해 813건 중 386건이
`FlywaySqlUnableToConnectToDbException`·`CommunicationsException`·`java.net.ConnectException`으로
실패했다. 이는 제품 assertion 실패가 아니라 테스트 선행 조건 누락이므로 결과를 유효 통과로 사용하지
않았다. `docker compose up -d --wait`로 개발 MySQL의 healthy 상태를 확인한 뒤 같은 명령을 전체
재실행했다.

## 최종 검증

| 검증 | 결과 |
| --- | --- |
| `docker compose config -q` | 통과 |
| `docker compose -f compose.performance.yaml config -q` | 통과 |
| 변경 대상 테스트 | 5단계 제품 코드 변경 없음으로 별도 대상 없음 |
| `./gradlew test --rerun-tasks` | 재실행 `BUILD SUCCESSFUL`, 813건 중 성공 812·skip 1·실패 0·오류 0 |
| `./gradlew check` | `BUILD SUCCESSFUL` |
| `./gradlew build` | `BUILD SUCCESSFUL` |
| 패키징 JAR 부팅 | performance 설정의 DB·Flyway·JPA 준비와 management health 200 확인 |
| `/api/smoke` | HTTP 204 확인 |
| 애플리케이션 종료 | 정상 종료, 성능·개발 DB volume과 원시 artifact 보존 |

전체 build 뒤 JAR bytes와 SHA-256은 정식 측정 JAR과 같았다. 패키징 smoke는 단일 endpoint의 HTTP 204
사실이며 브라우저·동시성·외부 서비스 수용 검증을 대신하지 않는다. PortOne·OpenAI는 성능 프로필에서
비활성화했다.

## 재현 명령

비밀값은 Git 제외 `.env`에 두고 아래 명령은 프로젝트 루트의 Bash에서 실행한다. 각 정식 실행은 앱을
중지하고 `mvp` 데이터와 새 앱을 복원해 앞 실행의 대여·세션 상태를 제거한다. Smoke·Warm-up·본 부하는
실제 유효 실행과 같은 신규 독자 pool 값을 사용하고, 본 부하 뒤에는 SQL 불변식을 검증한다.

```bash
./gradlew bootJar
./performance/scripts/recreate-environment.sh
./performance/scripts/reset-mvp.sh
./performance/scripts/generate-history-heavy.sh

run_final_series() {
    local series=$1
    local scenario=$2

    for repetition in 1 2 3; do
        ./performance/scripts/stop-app.sh
        ./performance/scripts/reset-mvp.sh
        ./performance/scripts/start-app.sh
        PERF_NEW_READER_OFFSET=0 PERF_NEW_READER_VU_STRIDE=1 PERF_NEW_READER_CYCLES=1 \
            PERFORMANCE_RUN_LABEL="final-$series-r$repetition-smoke" \
            ./performance/scripts/run-k6.sh smoke
        PERF_NEW_READER_OFFSET=1 PERF_NEW_READER_VU_STRIDE=16 PERF_NEW_READER_CYCLES=4 \
            PERFORMANCE_RUN_LABEL="final-$series-r$repetition-warm-up" \
            ./performance/scripts/run-k6.sh warm-up
        PERF_NEW_READER_OFFSET=65 PERF_NEW_READER_VU_STRIDE=50 PERF_NEW_READER_CYCLES=5 \
            PERFORMANCE_RUN_LABEL="final-$series-r$repetition" \
            ./performance/scripts/run-k6.sh "$scenario"
        ./performance/scripts/verify-invariants.sh
    done
}

run_final_load() {
    local scenario=$1
    local label=$2
    local offset=$3
    local stride=$4
    local cycles=$5

    ./performance/scripts/stop-app.sh
    ./performance/scripts/reset-mvp.sh
    ./performance/scripts/start-app.sh
    PERF_NEW_READER_OFFSET=0 PERF_NEW_READER_VU_STRIDE=1 PERF_NEW_READER_CYCLES=1 \
        PERFORMANCE_RUN_LABEL="$label-smoke" ./performance/scripts/run-k6.sh smoke
    PERF_NEW_READER_OFFSET="$offset" PERF_NEW_READER_VU_STRIDE="$stride" \
        PERF_NEW_READER_CYCLES="$cycles" PERFORMANCE_RUN_LABEL="$label" \
        ./performance/scripts/run-k6.sh "$scenario"
    ./performance/scripts/verify-invariants.sh
}

run_final_series average average-load
run_final_series peak peak-load
run_final_load stress final-stress 65 7 37
run_final_load spike final-spike-rerun 0 20 16
run_final_load soak final-soak-rerun 0 24 13

for contention_case in same-page different-pages different-readers session; do
    ./performance/scripts/stop-app.sh
    ./performance/scripts/reset-mvp.sh
    ./performance/scripts/start-app.sh
    CONTENTION_CASE="$contention_case" \
        PERFORMANCE_RUN_LABEL="final-contention-$contention_case" \
        ./performance/scripts/run-k6.sh contention
    ./performance/scripts/verify-invariants.sh "$contention_case"
done

./performance/scripts/stop-app.sh
./performance/scripts/reset-mvp.sh
./performance/scripts/start-app.sh
PERFORMANCE_RUN_LABEL=final-browser-cache ./performance/scripts/run-k6.sh browser-cache
./performance/scripts/verify-invariants.sh

docker compose up -d --wait
./gradlew test --rerun-tasks
./gradlew check
./gradlew build
```

위 명령은 유효한 재실행의 label과 고정 pool 값을 사용한다. 첫 Spike·Soak 무효 실행의 명령과 원인은
[실패·무효 실행](#실패무효-실행)에 별도로 기록했다. 시작·종료 UTC, image, JAR, Git·dirty 상태는 각 원시
`metadata.json`에 있고, 원시 위치와 집계 SHA-256은 [artifact-manifest.md](./artifact-manifest.md)에서
찾을 수 있다.

## 5단계 종료 조건

| 종료 조건 | 근거 |
| --- | --- |
| Average·Peak 각 3회 | 모두 같은 출발점·Warm-up, 유효 checks 100%, 중앙값·범위 기록 |
| Stress·Spike·Soak 각 1회 | 유효 전체 시간 실행과 중단선 판정 기록, 무효 실행 별도 보존 |
| cold·warm 브라우저 분리 | 새 context cold와 같은 context warm 3쌍, 전송량·cache 계약 기록 |
| 동시성 4종·불변식 | k6 check와 SQL 업무 계약·공통 불변식 전부 통과 |
| 전체 test·check·build·JAR smoke | 개발 MySQL 기동 뒤 전체 재실행과 패키징 부팅 통과 |
| 최종 evidence | 환경·summary·dashboard·artifact metadata·comparison 작성 |
| 운영 해석 경계 | 모든 요약과 비교에 로컬 회귀 근거임을 명시 |

5단계 종료 시점에는 6단계 완료 감사·민감정보/diff 전수 감사·container 종료 및 인계, 커밋·push·PR을
실행하지 않았다.

## 후속 `develop` 통합 경계

5단계 종료 보고 뒤 별도 사용자 요청으로 이 evidence를 커밋하기 전에 현재 브랜치를
`origin/develop`의 `b5b4c589b5bf2b4aac94adb480f7f68a0e1c3905`까지 `--ff-only`로 동기화했다. 충돌은 없었고,
동기화한 HEAD에서 다음을 다시 검증했다.

- `./gradlew test --rerun-tasks`: 878건 중 성공 877·skip 1·실패 0·오류 0
- `./gradlew check`: `BUILD SUCCESSFUL`
- `./gradlew build`: `BUILD SUCCESSFUL`

통합된 13개 커밋은 AI 경로·콘텐츠 적재 코드와 테스트이며 성능 원시 결과를 다시 생성하지 않았다. 따라서
이 문서의 부하 수치와 정확성 판정은 계속 정식 측정 SHA `f3a62809099ba660ce980b006d71c833bbd849fa`의
근거이고, `b5b4c58`의 성능 수치라고 확장 해석하지 않는다. 후속 로컬 커밋은 evidence 보존만 포함하며
push·PR·6단계 감사는 별도 승인 경계다.
