# MVP1 기준선 자원 지표

각 값은 해당 k6 `metadata.json`의 UTC 구간으로 Prometheus를 조회한 결과다. CPU는 단일 애플리케이션
process의 비율이며 `0.10`은 약 10%다.

## Average

| 실행 | 앱 CPU 평균 / 최대 | heap 최대 | Tomcat busy 최대 | Hikari active / pending 최대 | MySQL QPS 평균 / 최대 | slow 증가 | generator CPU 평균 / 최대 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| r1 | 10.10% / 29.11% | 108,291,264 B | 1 | 1 / 0 | 269.27 / 281.05 | 0 | 2.27% / 4.20% |
| r2 | 10.10% / 31.59% | 111,477,296 B | 0 | 0 / 0 | 268.34 / 278.55 | 0 | 2.38% / 5.42% |
| r3 | 10.10% / 39.53% | 109,875,208 B | 0 | 0 / 0 | 268.53 / 278.64 | 0 | 2.46% / 4.82% |

## Peak

| 실행 | 앱 CPU 평균 / 최대 | heap 최대 | Tomcat busy 최대 | Hikari active / pending 최대 | MySQL QPS 평균 / 최대 | slow 증가 | generator CPU 평균 / 최대 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| r1 | 15.12% / 62.23% | 116,367,120 B | 0 | 0 / 0 | 656.52 / 682.68 | 0 | 3.65% / 9.91% |
| r2 | 14.73% / 61.00% | 114,682,560 B | 8 | 5 / 0 | 657.65 / 693.07 | 0 | 3.40% / 5.85% |
| r3 | 14.99% / 63.40% | 105,667,592 B | 5 | 1 / 0 | 661.93 / 692.96 | 0 | 4.00% / 7.32% |

## Stress

| 앱 CPU 평균 / 최대 | heap 최대 | Tomcat busy 최대 | Hikari active / pending 최대 | MySQL QPS 평균 / 최대 | slow 증가 | generator CPU 평균 / 최대 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 16.55% / 36.80% | 113,482,024 B | 4 | 5 / 0 | 767.44 / 2,590.76 | 0 | 2.89% / 15.67% |

계획 상한까지 Hikari pending, 오류, dropped iteration이 없었고 앱 CPU·generator CPU도 포화되지
않았다. 따라서 첫 포화 자원은 확인되지 않았다. MySQL QPS 최대는 부하 단계 전환 표본을 포함한
Prometheus 값이며 그 자체를 지속 처리량으로 해석하지 않는다.

## 결과 경로와 시간 연결

| 구분 | 결과 경로 |
| --- | --- |
| Average | `20260811T100245Z-baseline-average-r1`, `20260811T101632Z-baseline-average-r2`, `20260811T103017Z-baseline-average-r3` |
| Peak | `20260811T104427Z-baseline-peak-r1`, `20260811T105816Z-baseline-peak-r2`, `20260811T111201Z-baseline-peak-r3` |
| Stress | `20260811T114700Z-baseline-stress-rerun` |

모든 경로는 Git 제외 `var/performance/results/` 아래다. 각 디렉터리의 `metadata.json`이 시작·종료 UTC,
Git·JAR·image·자원 제한을, `prometheus-summary.json`이 같은 범위의 집계값을 보존한다.
