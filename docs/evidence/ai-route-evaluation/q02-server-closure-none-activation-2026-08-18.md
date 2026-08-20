# Q02 사람 유용성 판정·최종 report·후보 DB 활성화

2026-08-18 KST에 사용자가 지정 검수자 판정으로 90개 경로 전부를 `useful=true`라고 제공했습니다. 이
판정을 Q01 실제 OpenAI 결과와 결합한 Q02 최종 report는 전체 기준을 통과했고, 새 로컬 후보 DB의 지원
도서 90권을 한 트랜잭션으로 활성화했습니다. 소설 10권은 `false`로 유지했습니다.

이 결과는 Q02와 후보 DB 활성화 완료 근거이며, 실제 서버 기동이나 인증 브라우저 사용자 여정 완료를
뜻하지 않습니다.

> **후속 Q03 상태:** 사용자는 2026-08-18에 일반 서버 배포와 인증 브라우저 전체 여정 완료를 확인했습니다.
> 아래 남은 출시 경계는 Q02 완료 시점의 기록이며 최종 판정은
> [Q03 출시 근거](../release-verification/scrum-478-2026-08-15.md)를 따릅니다.

## 입력과 산출물

| 항목 | 값 |
| --- | --- |
| Q01 artifact | `var/evaluation/ai-route-evaluation-20260818-server-closure-none.json` |
| Q01 SHA-256 | `12e66b112e609ade821289e25f7743f2dc8d6ea0cfa9863b7827a84817924e53` |
| 사람 판정 | 90 cases, `useful=true` 90건, 사용자 제공 |
| 판정 파일 | `var/evaluation/ai-route-human-judgments.json` |
| 판정 SHA-256 | `53ff8206e37a8a51437f66c145e93c071a37212c33aef2d732bf88374853d7cc` |
| Q02 report | `var/evaluation/ai-route-evaluation-report-20260818-server-closure-none.json` |
| report SHA-256 | `afb0d99d255b2fdc88eedca2a1e2585a6aac18f57815fdc20c5b524dd15c4d10` |
| 후보 DB | 로컬 전용 `ilgeobolkka_ai_candidate_20260818` |
| content / policy | `ai-route-v2` / `OPENAI_DEFAULT_RETENTION_V1` |

판정과 report는 purpose·분석 text·provider 원문·API key를 포함하지 않으며 Git 제외 경로에만 둡니다.
사람 유용성 100%는 사용자가 제공한 판정이지 코드나 모델이 대신 생성한 자동 판정이 아닙니다.

## Q02 최종 지표

| 지표 | 결과 | 기준 | 판정 |
| --- | ---: | ---: | --- |
| 완료 경로 | 90 / 90 | 전체 성공 | 통과 |
| 필수 개념 포함률 | 402 / 482 = 83.40% | 80% 이상 | 통과 |
| 무관 또는 중복 추천 | 13 / 769 = 1.69% | 20% 이하 | 통과 |
| 필수 선수 관계 위반 | 0 / 730 = 0% | 5% 이하 | 통과 |
| 사람 유용성 | 90 / 90 = 100% | 80% 이상 | 통과 |
| 전체 처리 p95 | 5.506초 | 10초 이하 | 통과 |
| 20초 초과 | 0 / 90 | 0건 | 통과 |

최종 report의 `metrics.passed`는 `true`입니다. report의 manifest SHA-256, evaluation Git revision,
embedding·route model, candidate·prompt·schema version은 Q01 artifact와 일치합니다.

## 활성화 전후 검증

| DB 상태 | 전체 | support `true` | support `false` | 잘못 활성화 | 활성화 누락 |
| --- | ---: | ---: | ---: | ---: | ---: |
| 활성화 전 | 100 | 0 | 100 | 0 | 90 |
| 활성화 후 | 100 | 90 | 10 | 0 | 0 |

- report의 90개 book ID와 DB의 `ai_route_supported=true` book ID가 정렬 비교에서 정확히 일치했습니다.
- `ai_external_transfer_allowed=false`인데 활성화된 도서는 0권입니다.
- `ai_external_transfer_allowed=true`인데 비활성인 도서는 0권입니다.
- 100권 모두 `contentVersion=ai-route-v2`와 `OPENAI_DEFAULT_RETENTION_V1`을 유지합니다.
- runner 로그는 `books=90`의 활성화 완료를 기록했고 정상 종료했습니다.

## 로컬 검증

- `AiRouteEvaluationMetricsTest`: 17 tests, skipped/failures/errors 0
- `AiRouteSupportActivationMySqlIntegrationTest`: 11 tests, skipped/failures/errors 0
- Q02 report 불변식: `passed=true`, case 90, useful 90, 20초 초과 0건
- report와 DB 지원 book ID 전수 비교: 일치
- 활성화 runner: `BUILD SUCCESSFUL`

최초 finalize 실행 wrapper에서 `.env`를 shell `source`하려다 URL의 `&` 때문에
`.env:6: parse error near '&'`가 출력됐습니다. 이는 shell source 실패이며, runner는 애플리케이션 설정
로더와 명시한 후보 DB URL을 사용해 report를 정상 작성했습니다.
후속 activate 명령에서는 shell `source`를 제거했습니다. 시크릿 값은 명령·로그·문서에 출력하지 않았습니다.

## 남은 출시 경계

- 실제 서버 최초 기동 전 상태: 후보 DB 활성화 완료
- 실제 OpenAI 인증 브라우저 경로 생성·저장·열기·완료·피드백: 미실행
- 월 `$10` OpenAI 프로젝트 hard limit: 적용 완료 — 저장 후 강제 toggle 재확인
  ([근거](../openai-data-policy/2026-08-18.md#전용-프로젝트-설정-관찰))

따라서 실제 서버 없이 가능한 Q02·후보 DB 활성화·강제 지출 상한 적용은 완료됐습니다. 다음 단계는 이 후보
DB로 일반 서버를 처음 기동한 뒤 인증 브라우저 사용자 여정을 검증하는 것입니다.
