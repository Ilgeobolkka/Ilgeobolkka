---
name: github-cli-setup
description: 팀원 로컬에서 GitHub CLI 기반 PR 에이전트 환경을 활성화하거나 문제를 점검하게 되면 사용한다. git·gh 설치, GitHub 인증, origin 접근, 저장소 PR 스킬 인식을 진단하고 PR 작업의 Computer Use·브라우저 우회 없이 복구 절차를 안내한다.
---

# GitHub CLI 에이전트 활성화

## 원칙

- 저장소 루트에서 실행한다.
- PR 생성·수정·조회는 `gh` CLI로 수행한다.
- `gh`가 없거나 인증·권한 확인이 실패하면 PR 작업을 중단한다. Computer Use, 브라우저 UI,
  GitHub 앱·커넥터로 우회하지 않는다.
- 이 스킬은 진단만 수행한다. 도구 설치, 로그인, 원격 변경은 사용자 확인 후 진행한다.
- 토큰 값을 출력하거나 파일·프롬프트에 붙여 넣도록 요구하지 않는다.

## 활성화 절차

1. 아래 진단을 실행한다.

   ```bash
   bash .agents/skills/github-cli-setup/scripts/check.sh
   ```

2. `[BLOCKED]`가 있으면 원인별로 처리한다.

   - `git` 또는 `gh` 없음: 새 도구 설치 승인을 요청한다. 설치 경로는
     <https://cli.github.com/>의 현재 공식 안내를 사용한다.
   - GitHub 인증 실패: 사용자가 직접 아래 명령으로 로그인하게 한다.

     ```bash
     gh auth login --hostname github.com --git-protocol https
     ```

   - `origin` 없음·GitHub가 아님: 임의로 바꾸지 말고 필요한 원격 URL을 요청한다.
   - 저장소 접근 실패: 현재 로그인 계정의 조직·저장소 권한을 확인하도록 보고한다.
   - PR 스킬 인식 실패: 누락된 `.agents/skills/pr` 또는 `.claude/skills/pr`를 보고하고,
     버전 관리 중인 하네스 파일을 임의 덮어쓰지 않는다.

3. 진단을 다시 실행해 모든 필수 항목이 `[OK]`이고 종료 코드가 0인지 확인한다.
4. 결과를 `준비 완료` 또는 `활성화 차단`으로 요약한다. 차단 시 실패한 항목과 다음 명령만
   제시한다.

## PR 작업 연결

활성화 완료 후 PR 요청을 받으면 `.agents/skills/pr/SKILL.md`를 읽고 따른다. 이후 `gh` 명령이
실패하더라도 UI 도구로 전환하지 말고, 원문 오류와 해결에 필요한 최소 조치를 보고한다.
