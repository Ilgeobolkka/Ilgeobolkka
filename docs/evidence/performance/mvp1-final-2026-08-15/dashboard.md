# MVP1 최종 자원 지표

각 값은 해당 k6 `metadata.json`의 UTC 구간으로 Prometheus를 조회한 결과다. 앱 CPU는 단일 애플리케이션
process 비율이며 `10.15%`는 원본 `0.1015`를 백분율로 표시한 값이다.

## Average

| 실행 | 앱 CPU 평균 / 최대 | heap 최대 | Tomcat busy 최대 | Hikari active / pending 최대 | MySQL QPS 평균 / 최대 | slow 증가 | generator CPU 평균 / 최대 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| r1 | 10.15% / 30.00% | 110,315,112 B | 1 | 1 / 0 | 269.94 / 278.44 | 0 | 2.38% / 4.25% |
| r2 | 9.94% / 28.07% | 114,197,592 B | 0 | 0 / 0 | 270.41 / 278.62 | 0 | 2.35% / 4.46% |
| r3 | 10.25% / 29.06% | 118,392,280 B | 0 | 0 / 0 | 269.81 / 279.14 | 0 | 2.39% / 4.20% |

## Peak

| 실행 | 앱 CPU 평균 / 최대 | heap 최대 | Tomcat busy 최대 | Hikari active / pending 최대 | MySQL QPS 평균 / 최대 | slow 증가 | generator CPU 평균 / 최대 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| r1 | 14.48% / 70.16% | 142,051,600 B | 1 | 0 / 0 | 660.12 / 681.52 | 0 | 2.82% / 6.21% |
| r2 | 14.78% / 64.86% | 107,222,296 B | 1 | 0 / 0 | 660.45 / 683.15 | 0 | 3.07% / 6.79% |
| r3 | 14.45% / 58.37% | 107,011,976 B | 5 | 5 / 0 | 657.84 / 694.00 | 0 | 3.04% / 6.66% |

## Stress·Spike·Soak

| 실행 | 앱 CPU 평균 / 최대 | heap 최대 | Tomcat busy 최대 | Hikari active / pending 최대 | MySQL QPS 평균 / 최대 | slow 증가 | generator CPU 평균 / 최대 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Stress | 17.16% / 36.90% | 107,216,648 B | 3 | 3 / 0 | 771.96 / 2,593.63 | 0 | 2.48% / 4.18% |
| Spike | 17.28% / 40.96% | 113,020,712 B | 2 | 2 / 0 | 689.63 / 1,327.27 | 0 | 2.71% / 5.34% |
| Soak | 8.86% / 38.13% | 121,210,960 B | 0 | 0 / 0 | 275.82 / 316.29 | 0 | 2.46% / 4.55% |

Soak에서 Tomcat busy와 Hikari active가 모두 0인 것은 5초 scrape가 낮은 부하의 짧은 활성 구간을 놓친
표본 해상도 한계다. HTTP 52,480건과 MySQL QPS는 실제 부하가 수행됐음을 보이며, pending 0과 연결 수
11~12는 별도 시계열로 확인했다. 이 값을 “연결을 사용하지 않았다”로 해석하지 않는다.

## 용량 판정

- Stress 전체 15분(10→25→50→100→200 flow/s) 표본에서 앱 CPU 평균은 17.16%, 최대는 36.90%였고,
  Hikari pending 0, Tomcat busy 최대 3, generator CPU 최대 4.18%였다. 17.16%는 200 flow/s 구간만의
  평균이 아니다.
- MySQL QPS 최대 2,593.63은 단계 전환을 포함한 5초 표본이며 지속 HTTP 처리량이 아니다. slow query
  counter는 3에서 증가하지 않았다.
- Spike의 100 flow/s 구간도 Hikari pending 0이고 급증 뒤 20 flow/s로 회복했다.
- Soak 30분 동안 heap 최대는 121,210,960 bytes로 2GiB container 한도보다 충분히 낮았고 종료 표본도
  최대보다 낮았다.

따라서 계획한 로컬 부하 안에서 첫 번째 포화 자원은 확인되지 않았다. 이는 “용량 한계가 없다”는 뜻이
아니라 이번 계획이 200 flow/s보다 높은 구간을 탐색하지 않았다는 뜻이다. 실제 제품 트래픽 추정이 생기면
그 분포와 안전 여유를 고정한 다음 상한 탐색을 다시 하는 것이 다음 용량 확인 순서다.

## 결과 경로

| 구분 | 결과 디렉터리 |
| --- | --- |
| Average | `20260813T034130Z-final-average-r1`, `20260813T035529Z-final-average-r2`, `20260813T040925Z-final-average-r3` |
| Peak | `20260813T042325Z-final-peak-r1`, `20260813T043725Z-final-peak-r2`, `20260813T045122Z-final-peak-r3` |
| Stress | `20260813T050215Z-final-stress` |
| Spike | `20260813T052817Z-final-spike-rerun` |
| Soak | `20260813T061134Z-final-soak-rerun` |

모든 경로는 Git 제외 `var/performance/results/` 아래다. 같은 디렉터리의 `metadata.json`,
`generator-summary.json`, `prometheus-summary.json`이 실행 시간과 집계값을 연결하며 위치·집계 해시는
[artifact-manifest.md](./artifact-manifest.md)에 기록했다.
