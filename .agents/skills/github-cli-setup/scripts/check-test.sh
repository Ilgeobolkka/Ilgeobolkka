#!/usr/bin/env bash

set -eu

script_directory=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
check_script="$script_directory/check.sh"
test_root=$(mktemp -d)
fake_bin="$test_root/bin"

cleanup() {
  rm -rf -- "$test_root"
}
trap cleanup EXIT

mkdir -p "$fake_bin" "$test_root/.agents/skills/pr" "$test_root/.claude/skills/pr"
: > "$test_root/.agents/skills/pr/SKILL.md"
: > "$test_root/.claude/skills/pr/SKILL.md"

cat > "$fake_bin/git" <<'EOF'
#!/usr/bin/env bash

case "$*" in
  "rev-parse --show-toplevel")
    printf '%s\n' "$FAKE_REPOSITORY_ROOT"
    ;;
  "remote get-url origin")
    printf '%s\n' "$FAKE_ORIGIN_URL"
    ;;
  *)
    exit 1
    ;;
esac
EOF

cat > "$fake_bin/gh" <<'EOF'
#!/usr/bin/env bash

case "$1" in
  --version)
    printf '%s\n' 'gh version test'
    ;;
  api)
    printf '%s\n' 'test-user'
    ;;
  repo)
    printf '%s\n' 'team/repository'
    ;;
  *)
    exit 1
    ;;
esac
EOF

chmod +x "$fake_bin/git" "$fake_bin/gh"

origin_with_credential='https://x-access-token:super-secret@github.com/team/repository.git'
output=$(
  PATH="$fake_bin:$PATH" \
    FAKE_REPOSITORY_ROOT="$test_root" \
    FAKE_ORIGIN_URL="$origin_with_credential" \
    bash "$check_script"
)

if [[ "$output" == *"super-secret"* ]]; then
  printf '%s\n' 'credential이 origin 출력에 노출됐습니다.' >&2
  exit 1
fi

if [[ "$output" != *"[OK] GitHub origin: github.com/team/repository"* ]]; then
  printf '%s\n' '마스킹한 GitHub 저장소 주소를 확인할 수 없습니다.' >&2
  exit 1
fi

printf '%s\n' 'origin URL credential 마스킹 테스트 통과'
