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
| 변경 완료 게이트 | Compose config와 성능 script 구문, `mvp` 행 수·불변식, `./gradlew test`, `check`, `build`가 통과했다. 테스트 리포트는 608건 중 실패·오류 0, 조건부 1건 skip이다. |

## 우선 병목

1. **이력 성장 조회의 인덱스·정렬 형태**
   - history-heavy에서 최신 대여 조회는 호출당 1,906.86행, 서재 상관 조회는 1,511행, 원장 목록은
     544행을 조사했다.
   - 원장 목록은 독자별 기존 index로 567행을 읽어 최신순 정렬 후 20행을 반환했다.
   - 3단계에서 최신 대여·원장·서재 복합 인덱스를 각각 독립 후보로 측정하되 실행 계획 개선과 HTTP p95
     15% 이상을 모두 만족할 때만 채택한다.
2. **공개 정적 자산의 매 탐색 재전송**
   - 같은 browser context의 cold·warm 3쌍 모두 공개 자산이 `no-store`였고 329,581 bytes를 동일하게
     전송했다.
   - 보호 콘텐츠의 `private, no-store`는 유지하면서 공개 fingerprinted 자산에만 cache 정책을 실험한다.
     bytes 또는 Web Vital 15% 이상 개선이 없으면 폐기한다.
3. **로그인 BCrypt CPU 집중**
   - JFR execution sample 1,228개 중 `BCrypt.key`가 880개(71.66%)였다.
   - 이는 5% 로그인 흐름의 정상 보안 비용이며 strength를 낮추지 않는다. 불필요한 반복 인증이 있는지만
     확인하고, 없거나 전체 CPU·p95 개선이 채택 하한에 못 미치면 변경하지 않는다.

## 해석과 다음 경계

Average·Peak는 잠정 품질 게이트를 이미 통과했고 Stress 계획 상한까지 첫 포화 자원이 없었다. 따라서
3단계의 목적은 수치를 만들기 위한 복잡도 추가가 아니라 위 후보가 실제 채택 하한을 넘는지 확인하는 것이다.
Redis·Spring Session Redis·다중 인스턴스·가상 스레드는 현재 진입 근거가 없으며 4단계 승인 전에는
추가하지 않는다.

성능 Compose는 증거 작성과 검증을 마친 뒤 container·network만 정지한다. 성능 전용 MySQL·Prometheus
volume은 다음 단계의 동일 입력 재현을 위해 보존한다.

3단계 — 새 상태 인프라 없는 1차 개선은 아직 실행하지 않았다.
