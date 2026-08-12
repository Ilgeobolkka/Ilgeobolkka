# 2단계 MVP1 기준선과 병목 결과

## 판정

- 단계 결과: 통과
- 정확성·잠정 품질 게이트: 통과
- 정식 Git SHA: `8cf53f29f28923ef778583df6fe61d0dc90c7ae5`
- 패키징 JAR SHA-256: `ad025a61983f583c94cfc8eb8b8a0e006ed43bac8cdf2c6db8c531c696071f0b`
- 환경: [environment.md](./environment.md)
- 부하 결과: [summary.md](./summary.md)
- 자원 지표: [dashboard.md](./dashboard.md)
- 진단: [diagnostics.md](./diagnostics.md)
- artifact: [artifact-manifest.md](./artifact-manifest.md)

## 종료 조건 검증

| 종료 조건 | 근거 |
| --- | --- |
| Average·Peak 유효 3회와 중앙값·범위 | 동일 JAR·clean SHA·복원 데이터·새 앱·Warm-up 뒤 각 3회가 있고 오류·dropped iteration은 모두 0이다. |
| Stress·동시성 4종·브라우저 3쌍 | Stress는 계획 상한 200 flow iteration/s까지 완료했다. 동시성 k6 check와 SQL 계약, 공개·보호 cache 3쌍을 대조했다. |
| 같은 시간대 자원 지표 | 실행별 metadata UTC 범위와 Prometheus JVM·Tomcat·HikariCP·MySQL, generator CPU summary가 연결된다. |
| JFR과 느린 SQL 실행 계획 | 분리된 120초 JFR, 1분 Performance Schema, 0.2초 slow log와 대표 최신순 원장 `EXPLAIN ANALYZE`를 보존했다. |
| 부하 뒤 도메인 불변식 | 잔액 불일치·음수 잔액·대여 없는 차감·중복 소장·서재 연결 위반이 모두 0이다. |
| 관측 기반 병목만 다음 단계로 전달 | 아래 3개만 기록하고, Hikari·Tomcat·GC·generator·DB 포화는 근거가 없어 후보로 올리지 않았다. |
| 변경 완료 게이트 | Compose config와 성능 script 구문, `mvp` 행 수·불변식, `./gradlew test`, `check`, `build`가 통과했다. 테스트 리포트는 609건 중 실패·오류 0, 조건부 1건 skip이다. |

## 우선 병목

1. **이력 성장 조회의 인덱스·정렬 형태**
   - history-heavy에서 최신 대여 조회는 호출당 1,906.86행, 서재 상관 조회는 1,511행, 원장 목록은
     544행을 조사했다.
   - 원장 목록은 독자별 기존 index로 567행을 읽어 최신순 정렬 후 20행을 반환했다.
   - 3단계에서 최신 대여·원장·서재 복합 인덱스를 각각 독립 후보로 측정한다. 이 단계에서 세운 실행 계획
     개선과 HTTP p95 15% 이상 조건은 후속 3단계 재검증과 2026-08-12 사용자 결정으로 폐기됐으며, 현재
     채택 기준은 [성능 실행 계획](../../../implementation/performance/README.md#9-예상-개선-범위)을 따른다.
2. **공개 정적 자산의 매 탐색 재전송**
   - 같은 browser context의 cold·warm 3쌍 모두 공개 자산이 `no-store`였고 329,581 bytes를 동일하게
     전송했다.
   - 보호 콘텐츠의 `private, no-store`는 유지하면서 공개 fingerprinted 자산에만 cache 정책을 실험한다.
     이 단계에서 세운 bytes 또는 Web Vital 15% 이상 조건은 2026-08-12 사용자 결정으로 폐기됐으며,
     현재 채택 기준은 [성능 실행 계획](../../../implementation/performance/README.md#9-예상-개선-범위)을 따른다.
3. **로그인 BCrypt CPU 집중**
   - JFR execution sample 1,228개 중 `BCrypt.key`가 880개(71.66%)였다.
   - 이는 5% 로그인 흐름의 정상 보안 비용이며 strength를 낮추지 않는다. 불필요한 반복 인증이 있는지만
     확인하고, 없거나 전체 CPU·p95 개선이 채택 기준에 못 미치면 변경하지 않는다.

## 해석과 다음 경계

Average·Peak는 잠정 품질 게이트를 이미 통과했고 Stress 계획 상한까지 첫 포화 자원이 없었다. 따라서
3단계의 목적은 수치를 만들기 위한 복잡도 추가가 아니라 위 후보가 실제 채택 기준을 충족하는지 확인하는 것이다.
Redis·Spring Session Redis·다중 인스턴스·가상 스레드는 현재 진입 근거가 없으며 4단계 승인 전에는
추가하지 않는다.

성능 Compose는 증거 작성과 검증을 마친 뒤 container·network만 정지한다. 성능 전용 MySQL·Prometheus
volume은 다음 단계의 동일 입력 재현을 위해 보존한다.

## 기준선 뒤 보안 회귀 보완

PR 전 전체 영향 검토에서 Actuator 기본값이 `performance` 외 profile의 애플리케이션 포트에도 health를
노출할 수 있음을 확인했다. 기본 `management.server.port=-1`과 회귀 테스트를 추가했고,
`performance` profile만 기존 8081 override를 유지한다. 이 변경은 정식 부하 수치에 섞지 않았다.

- 보완 뒤 JAR SHA-256: `559eaa3719c0546834d4c720bb36539bb9b356c4f861d4868d1fc6cd45b68423`
- 별도 Smoke: `20260811T123545Z-post-baseline-management-fix-smoke`, checks 32/32, HTTP 오류 0
- management health·prometheus·loopback·label 경계와 복원한 `mvp` 행 수·불변식을 다시 확인했다.

## 다중 세션 로그아웃 검증 보완

초기 session 동시성 실행은 대상 독자의 현재 세션을 먼저 만들지 않아 빈 상태의 로그아웃만 확인했다.
보완된 harness에서 페이지 열기 setup을 추가하고 다음 조건으로 session 케이스만 다시 실행했다.

- 검증 Git SHA: `0203b7e8497ee7fe8e3c710cefcbec7a068c820b`, dirty `false`
- 패키징 JAR SHA-256: `087a1ac3282ade7ca21e2a4d90333401540594fbbfc7d69474496312e0d83d29`
- 결과: `20260811T150246Z-post-fix-contention-session-final`, checks 84/84, HTTP 오류 0,
  p95 665.367ms, exit code 0
- SQL 대조: 잔액 99, 대상 페이지 대여·차감·서재 각 1, 현재 세션 0이며 공통 불변식 6종도 모두 0

3단계 — 새 상태 인프라 없는 1차 개선은 2026-08-12 실행을 완료했으며, 결과는
[3단계 결과](../mvp1-stage3-2026-08-12/result.md)에 기록했다.
