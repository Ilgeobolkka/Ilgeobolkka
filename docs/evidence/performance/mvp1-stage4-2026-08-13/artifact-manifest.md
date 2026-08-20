# 4단계 artifact manifest

큰 원시 결과는 Git에서 제외된 `var/performance/results/`에 보존하고, 위치·bytes·SHA-256으로 연결한다.

## 유효 Spike

경로: `var/performance/results/20260813T022438Z-stage4-final-spike-rerun`

| 파일 | bytes | SHA-256 |
| --- | ---: | --- |
| `summary.json` | 8,720 | `f59f056ae37dec9ece28c165dc3098995e68ddb3094178f14170664ca7a63235` |
| `metadata.json` | 2,019 | `9a22347e7b904ad2051967e8e9cd605e39cc975ffd99be057f3e39354984b3a9` |
| `generator-summary.json` | 84 | `df7c8acfa755769c51fa5df03139942211b8e42a5ea680623e073b4e383a46a9` |
| `prometheus-summary.json` | 1,099 | `cff437ba2034af2a667e2a6c1c1878829bed34731e1192e38aef71cf7c962d8f` |
| `k6.log` | 435 | `f3929a17eb2d642837a8df82d0d00da7a838df8d1c06fa2fbaeacc58f989deec` |
| `exit-code.txt` | 2 | `9a271f2a916b0b6ee6cecb2426f0b3206ef074578be55d9bc94f6f3fe3ab86aa` |

## 무효 Spike

경로: `var/performance/results/20260813T021309Z-stage4-final-spike`

| 파일 | bytes | SHA-256 |
| --- | ---: | --- |
| `summary.json` | 8,775 | `2a2630df398d7b98f22e12c8d05c58b2abb20bfb640f9f9b9ec62a2c24ef30b1` |
| `metadata.json` | 2,018 | `99cddddba0cacba07aad9d58b1f77ec1a3b75a385c4f83b4c513519caef49f62` |
| `generator-summary.json` | 83 | `19bf3068eec4d8a85d54a818affa2d1177a247795d3c99d1e0d61226bc0d08ef` |
| `prometheus-summary.json` | 1,111 | `65b2b2bfbc352ac6b82d57a7c2f997adb4922087b62ef536e9fbfa324bb97ec4` |
| `k6.log` | 584,326 | `25649755a7e6d553a8c4a20b8c48512e1df0cd06c5c020ee54c8c9ba3be27823` |
| `exit-code.txt` | 4 | `e00e6031fc5e7ef95c526573343419859e112cebd550f10e2089bf393f5ed2bd` |

이 실행은 신규 대여 계정 pool 소진으로 exit code 110이어서 성능 수치에서 제외했다. 실패 로그와 summary는
삭제하지 않았다.

## Smoke

경로: `var/performance/results/20260813T021229Z-stage4-current-smoke`

| 파일 | bytes | SHA-256 |
| --- | ---: | --- |
| `summary.json` | 6,719 | `684d349b1fc54dcf9b1e4c507f7d0692e475b6e34bd6912c608337828a953867` |
| `metadata.json` | 2,018 | `fe1c9a2d627df9e671ab0b8eec8d0dcab4279bb009e23be5ac99f7ba5ddee68d` |
| `generator-summary.json` | 73 | `043999137580435bf22ea2dc8c1acacbde9fee210d4945ed06e8f1110d25cdeb` |
| `prometheus-summary.json` | 1,075 | `8b50402a03d9f1903d5df86a617e3840dc511187171972d9bf0956638c6a063c` |
| `k6.log` | 435 | `a88e064870e355469fa5636b1e1d21be17d2e2fe4b1a141dd8d84a388cb486fc` |
| `exit-code.txt` | 2 | `9a271f2a916b0b6ee6cecb2426f0b3206ef074578be55d9bc94f6f3fe3ab86aa` |

## 실행 JAR

| 경로 | bytes | SHA-256 |
| --- | ---: | --- |
| `build/libs/Ilgeobolkka-0.0.1-SNAPSHOT.jar` | 87,887,367 | `0ce68063a964db0b665c4686c7de533ed76337aed4728e0d2a5ba4d4d891d41e` |
