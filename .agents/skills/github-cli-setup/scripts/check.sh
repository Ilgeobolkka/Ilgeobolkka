#!/usr/bin/env bash

set -u

blocked=0

ok() {
  printf '[OK] %s\n' "$1"
}

block() {
  printf '[BLOCKED] %s\n' "$1"
  blocked=$((blocked + 1))
}

safe_github_origin() {
  local remote_url=$1
  local repository_path

  case "$remote_url" in
    https://github.com/*)
      repository_path=${remote_url#https://github.com/}
      ;;
    https://*@github.com/*)
      repository_path=${remote_url#*@github.com/}
      ;;
    git@github.com:*)
      repository_path=${remote_url#git@github.com:}
      ;;
    ssh://git@github.com/*)
      repository_path=${remote_url#ssh://git@github.com/}
      ;;
    *)
      return 1
      ;;
  esac

  repository_path=${repository_path%.git}
  if [[ ! "$repository_path" =~ ^[^/@[:space:]]+/[^/@[:space:]]+$ ]]; then
    return 1
  fi

  printf 'github.com/%s\n' "$repository_path"
}

if ! command -v git >/dev/null 2>&1; then
  block "git 명령을 찾을 수 없습니다."
  exit 1
fi

repository_root=$(git rev-parse --show-toplevel 2>/dev/null) || {
  block "Git 저장소 안에서 실행해야 합니다."
  exit 1
}
cd "$repository_root" || exit 1
ok "저장소 루트: $repository_root"

origin_url=$(git remote get-url origin 2>/dev/null || true)
github_origin=0
if [[ -z "$origin_url" ]]; then
  block "origin 원격이 없습니다."
elif masked_origin=$(safe_github_origin "$origin_url"); then
  ok "GitHub origin: $masked_origin"
  github_origin=1
else
  block "origin이 지원하는 GitHub 원격 형식이 아닙니다."
fi

gh_ready=0
if ! command -v gh >/dev/null 2>&1; then
  block "gh CLI를 찾을 수 없습니다."
else
  gh_version=$(gh --version | sed -n '1p')
  ok "$gh_version"

  github_login=$(gh api --hostname github.com user --jq .login 2>/dev/null || true)
  if [[ -z "$github_login" ]]; then
    block "github.com의 gh 인증이 없거나 유효하지 않습니다."
  else
    ok "gh 인증 계정: $github_login"
    gh_ready=1
  fi
fi

if [[ $gh_ready -eq 1 && $github_origin -eq 1 ]]; then
  repository_name=$(gh repo view --json nameWithOwner --jq .nameWithOwner 2>/dev/null || true)
  if [[ -z "$repository_name" ]]; then
    block "현재 계정으로 origin 저장소에 접근할 수 없습니다."
  else
    ok "GitHub 저장소 접근: $repository_name"
  fi
fi

if [[ -r .agents/skills/pr/SKILL.md ]]; then
  ok "공용 PR 스킬 인식 파일"
else
  block ".agents/skills/pr/SKILL.md를 읽을 수 없습니다."
fi

if [[ -d .claude ]]; then
  if [[ -r .claude/skills/pr/SKILL.md ]]; then
    ok "Claude Code PR 스킬 연결"
  else
    block ".claude/skills/pr 연결을 읽을 수 없습니다."
  fi
fi

if [[ $blocked -gt 0 ]]; then
  printf '\n활성화 차단: %d개 항목을 해결한 뒤 다시 실행하세요.\n' "$blocked"
  exit 1
fi

printf '\n준비 완료: gh CLI 기반 PR 작업을 사용할 수 있습니다.\n'
