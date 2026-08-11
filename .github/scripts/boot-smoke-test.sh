#!/usr/bin/env bash

set -euo pipefail

project_root="$(cd "$(dirname "$0")/../.." && pwd)"
test_root="$(mktemp -d)"
fake_bin="$test_root/bin"
browser_count_file="$test_root/browser-count"
dummy_jar="$test_root/application.jar"

cleanup() {
  rm -rf "$test_root"
}

trap cleanup EXIT

mkdir -p "$fake_bin"
: >"$dummy_jar"

cat >"$fake_bin/java" <<'EOF'
#!/usr/bin/env bash
trap 'exit 0' TERM INT
while true; do
  sleep 1
done
EOF

cat >"$fake_bin/curl" <<'EOF'
#!/usr/bin/env bash
printf '204'
EOF

cat >"$fake_bin/fake-browser" <<'EOF'
#!/usr/bin/env bash
count=0
if [[ -f "$FAKE_BROWSER_COUNT_FILE" ]]; then
  count="$(<"$FAKE_BROWSER_COUNT_FILE")"
fi
count=$((count + 1))
printf '%s' "$count" >"$FAKE_BROWSER_COUNT_FILE"

if [[ "${FAKE_BROWSER_MODE:-retry}" == "failed" ]]; then
  printf '<html data-browser-smoke="failed"></html>\n'
  exit 0
fi

if [[ "$count" == "1" ]]; then
  sleep 5
  exit 0
fi

printf '<html data-browser-smoke="passed"></html>\n'
EOF

chmod +x "$fake_bin/java" "$fake_bin/curl" "$fake_bin/fake-browser"

if ! output="$(
  PATH="$fake_bin:$PATH" \
    BROWSER_BIN="$fake_bin/fake-browser" \
    BROWSER_SMOKE_TIMEOUT_SECONDS=1 \
    FAKE_BROWSER_COUNT_FILE="$browser_count_file" \
    SMOKE_LOG_FILE="$test_root/boot-smoke.log" \
    "$project_root/.github/scripts/boot-smoke.sh" "$dummy_jar" 2>&1
)"; then
  printf '%s\n' "$output" >&2
  printf '브라우저 정체 후 재시도 검증이 실패했습니다.\n' >&2
  exit 1
fi

grep -Fq '새 프로필로 한 번 재시도합니다.' <<<"$output"
grep -Fq '공통 브라우저 smoke 검증 성공' <<<"$output"

if [[ "$(<"$browser_count_file")" != "2" ]]; then
  printf '브라우저 실행 횟수가 2회가 아닙니다: %s\n' "$(<"$browser_count_file")" >&2
  exit 1
fi

rm -f "$browser_count_file"
if output="$(
  PATH="$fake_bin:$PATH" \
    BROWSER_BIN="$fake_bin/fake-browser" \
    BROWSER_SMOKE_TIMEOUT_SECONDS=1 \
    FAKE_BROWSER_COUNT_FILE="$browser_count_file" \
    FAKE_BROWSER_MODE=failed \
    SMOKE_LOG_FILE="$test_root/boot-smoke.log" \
    "$project_root/.github/scripts/boot-smoke.sh" "$dummy_jar" 2>&1
)"; then
  printf '%s\n' "$output" >&2
  printf '브라우저 실패 상태가 부팅 smoke를 실패시키지 않았습니다.\n' >&2
  exit 1
fi

grep -Fq 'data-browser-smoke="failed"' <<<"$output"
if grep -Fq '새 프로필로 한 번 재시도합니다.' <<<"$output"; then
  printf '%s\n' "$output" >&2
  printf '브라우저 실패 상태를 불필요하게 재시도했습니다.\n' >&2
  exit 1
fi

if [[ "$(<"$browser_count_file")" != "1" ]]; then
  printf '브라우저 실패 상태 실행 횟수가 1회가 아닙니다: %s\n' "$(<"$browser_count_file")" >&2
  exit 1
fi

printf '부팅 smoke 브라우저 재시도 검증 성공\n'
