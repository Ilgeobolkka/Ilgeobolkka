# MVP1 기준선 artifact manifest

원시 artifact는 Git 제외 `var/performance/` 아래에 보존한다. 비밀번호·cookie·CSRF token·session ID와
DB 접속 문자열은 문서에 기록하지 않았고, 원시 진단 파일의 민감 패턴 검사도 통과했다.

## 정식 부하 결과

각 실행의 `exit-code.txt`는 `0`이고 공통 SHA-256은
`9a271f2a916b0b6ee6cecb2426f0b3206ef074578be55d9bc94f6f3fe3ab86aa`다.

| 결과 디렉터리 | summary.json SHA-256 | metadata.json SHA-256 |
| --- | --- | --- |
| `20260811T100245Z-baseline-average-r1` | `e48e480fefe7e78c4d5d05543a22a6d6d1dd8c37ffe3d74bdc4ef0e32fca4b5e` | `aea2b74dc4cbb95873fda90cc47c68a23e88e2f1b490d16b731a1b08a92c2e0e` |
| `20260811T101632Z-baseline-average-r2` | `b5ed4e5110aa47052153abbe23b314f64232f4146766739cffd5e9a5b5a6fb36` | `95b2bff85e5686872f11785c8fb57facac8b2093949d88ebacbfa58346ea0d23` |
| `20260811T103017Z-baseline-average-r3` | `a635c64524de87d15e7879616060045f3923e5c19502578f8ee061214fedc37a` | `a22dc0145d0c44fe54fc9e5eb7163a7d54c9bb18a04ad91ec36fe05bd631dccd` |
| `20260811T104427Z-baseline-peak-r1` | `4f730ec51fc6272c602f62b8dc58c02b87ca5a811d9a9b0275fa9d0249d028d7` | `5293091d6cc2850e7b4d0f978d7c0f036e0313bae122d9f94c677eeb545d1356` |
| `20260811T105816Z-baseline-peak-r2` | `c30e34b71c0b9abce7f06697301d15aa36b7f425902a8139e704dfc78eccb1b3` | `67502fa9316b7807036ea065d4cde6705beb8a52f31b2b722580ee8d69a44f90` |
| `20260811T111201Z-baseline-peak-r3` | `a9dc487f15d79653112ec405aa4806a47b410f1363611647570872d5ac737864` | `b80ae1a4c779823db85d86e5a8d01b90910b4cd733df7bcb4dca9182fd83ecd6` |
| `20260811T114700Z-baseline-stress-rerun` | `4e157f4b8421324ecc92a7b5bfae171cd980cdcf503752155d0e50a4fe2bbd9e` | `9ce0813f524240f34ad6a1fc682d7eb213e5aa424dcee3608143d5185e6d81d6` |

각 디렉터리에는 같은 구간의 `prometheus-summary.json`, `generator-summary.json`, `k6.log`도 있다.

## 동시성·브라우저·이력

| 파일 | bytes | SHA-256 |
| --- | ---: | --- |
| `results/20260811T122029Z-baseline-contention-same-page-final/summary.json` | 5,700 | `7f956718456bf961cd9ee46df4099e406592f1829f31373244f2e9e54e17e549` |
| `results/20260811T122055Z-baseline-contention-different-pages-final/summary.json` | 5,661 | `ad28de8facac4f43ab3b553f2b715cfb060bfedbea5a247f73f73a15b4cde6a8` |
| `results/20260811T122121Z-baseline-contention-different-readers-final/summary.json` | 5,688 | `d621ae158df2f1d8a072aba06b99d43f583c5e6569365891a0c37d121b951f86` |
| `results/20260811T122150Z-baseline-contention-session-final/summary.json` | 5,677 | `d20e27d1ec477ea802e374aa7862b8979beed036477fe5c0a95328dc4bbcf660` |
| `results/20260811T120616Z-baseline-browser-cache/summary.json` | 8,734 | `14e482e099e638681e2f54c9aabfb1fab39c269ab3f8a6e9598b8098c4fe5b2e` |
| `results/20260811T120616Z-baseline-browser-cache/browser-cache.jsonl` | 16,721 | `9c9996df9d3fe5e767632187e81924b86c753280a496937cfb8f655d3d997b5c` |
| `results/20260811T120703Z-history-heavy/metadata.json` | 363 | `8e5f9cbe6038b2e2a7a78afee849c04d851a4121e3668b58e18c470034732783` |
| `results/20260811T120703Z-history-heavy/counts.tsv` | 90 | `e0f571da0c26bc1c332f387b0d4b151f5e3da62fff86fe40d5194e7b7e1d98f6` |
| `results/20260811T120703Z-history-heavy/invariants.tsv` | 147 | `c62f97ce9c8990e5d8ca10c8f8db1ad11f4be91f61117985d9049afb9a31369c` |

## 큰 진단 artifact

| 보존 위치 | bytes | SHA-256 |
| --- | ---: | --- |
| `var/performance/jfr/stage2-history-heavy-20260811T1208Z.jfr` | 4,722,981 | `5c8bc10696a1f4eee2804be9b989c662737e1abe75511294c909d987646136bd` |
| `var/performance/diagnostics/stage2-mysql-slow.log` | 7,344 | `9e8547de0e7c0019dc35a96574231004e26b00f947308dc8e5a539861f1a178c` |
| `var/performance/diagnostics/stage2-performance-schema-digests.tsv` | 9,949 | `ad0230730e2149cbd255e5196a9cc790f051f801a6ae46efb9940f6ab8255dfe` |
| `var/performance/diagnostics/stage2-history-ledger-explain.txt` | 1,489 | `de88f7f717ee63c73607346bd143890aa69046c4f355a7ab6b01c609a015aa7b` |

## 무효·진단 실행

- 기준선 뒤 management 보안 보완 Smoke는
  `20260811T123545Z-post-baseline-management-fix-smoke`에 분리했다. `summary.json` SHA-256은
  `a2a6ab426235db6880e581e386c9989b8e54edac3ebfd7741aa69626c6606385`, `metadata.json`은
  `598de68623cf901822617efe83260df19ba633b1f57e04da5391bb5f8413b499`다.
- `20260811T112717Z-baseline-stress`는 15분을 완료했지만 신규 대여 계정 pool 소진 오류가 3,940회여서
  무효 처리했다. 계정 offset·stride·cycles를 충돌 없이 고정한 뒤 `baseline-stress-rerun` 전체를 다시
  실행했다.
- 초기 동시성 SQL 대조는 업무 키 대신 잘못 가정한 PK와 잘못된 초기 잔액을 사용했다. k6 자체는
  통과했지만 검증 근거로 채택하지 않았고, 업무 키·정확한 잔액으로 4종을 모두 다시 실행했다.
- `debug-*`, `baseline-diagnostic-*`, history-heavy 준비 실행은 측정 장치 확인 또는 진단용이며 정식
  Average·Peak 중앙값에서 제외했다.
