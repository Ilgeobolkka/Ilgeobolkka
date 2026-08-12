#!/usr/bin/env bash

set -euo pipefail

app_jar="${1:-}"
smoke_url="${SMOKE_URL:-http://127.0.0.1:8080/api/smoke}"
browser_smoke_url="${BROWSER_SMOKE_URL:-http://127.0.0.1:8080/browser-smoke.html}"
browser_smoke_timeout_seconds="${BROWSER_SMOKE_TIMEOUT_SECONDS:-30}"
log_file="${SMOKE_LOG_FILE:-build/boot-smoke.log}"
raw_log="$(mktemp)"
browser_dom="$(mktemp)"
browser_log="$(mktemp)"
browser_profile="$(mktemp -d)"
browser_test_root="$(cd .github/browser-smoke && pwd)"
app_pid=""
browser_pid=""

sensitive_variables=(
  DB_URL
  DB_USERNAME
  DB_PASSWORD
  PORTONE_API_SECRET
  PORTONE_WEBHOOK_SECRET
  CONTENT_STORAGE_PATH
  CONTENT_STORAGE_ROOT
)

cleanup() {
  if [[ -n "$browser_pid" ]] && kill -0 "$browser_pid" 2>/dev/null; then
    kill "$browser_pid"
    wait "$browser_pid" 2>/dev/null || true
  fi
  if [[ -n "$app_pid" ]] && kill -0 "$app_pid" 2>/dev/null; then
    kill "$app_pid"
    wait "$app_pid" 2>/dev/null || true
  fi
  rm -f "$raw_log" "$browser_dom" "$browser_log"
  rm -rf "$browser_profile"
}

write_sanitized_log() {
  local sanitized
  local variable
  local value

  sanitized="$(<"$raw_log")"
  for variable in "${sensitive_variables[@]}"; do
    value="${!variable-}"
    if [[ -n "$value" ]]; then
      sanitized="${sanitized//"$value"/[REDACTED]}"
    fi
  done

  mkdir -p "$(dirname "$log_file")"
  printf '%s\n' "$sanitized" >"$log_file"
}

assert_sensitive_values_absent() {
  local variable
  local value

  for variable in DB_URL DB_PASSWORD PORTONE_API_SECRET PORTONE_WEBHOOK_SECRET CONTENT_STORAGE_PATH CONTENT_STORAGE_ROOT;
  do
    value="${!variable-}"
    if [[ -n "$value" ]] && grep -Fq -- "$value" "$raw_log"; then
      printf '부팅 로그에 민감 환경 변수 %s 값이 노출되었습니다.\n' "$variable" >&2
      return 1
    fi
  done
}

show_failure_log() {
  write_sanitized_log
  printf '민감값을 치환한 부팅 로그:\n' >&2
  cat "$log_file" >&2
}

find_browser() {
  local candidate
  local resolved
  local mac_chrome="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"

  if [[ -n "${BROWSER_BIN:-}" ]]; then
    if [[ ! -x "$BROWSER_BIN" ]]; then
      printf 'BROWSER_BIN 실행 파일을 찾을 수 없습니다: %s\n' "$BROWSER_BIN" >&2
      return 1
    fi
    printf '%s\n' "$BROWSER_BIN"
    return
  fi

  for candidate in google-chrome google-chrome-stable chromium chromium-browser; do
    if resolved="$(command -v "$candidate" 2>/dev/null)"; then
      printf '%s\n' "$resolved"
      return
    fi
  done

  if [[ -x "$mac_chrome" ]]; then
    printf '%s\n' "$mac_chrome"
    return
  fi

  printf '실제 브라우저 smoke 검증에 사용할 Chrome 또는 Chromium을 찾을 수 없습니다.\n' >&2
  return 1
}

run_browser_smoke() {
  local attempt
  local browser_attempt
  local browser_bin

  browser_bin="$(find_browser)" || return 1

  if [[ ! "$browser_smoke_timeout_seconds" =~ ^[1-9][0-9]*$ ]]; then
    printf 'BROWSER_SMOKE_TIMEOUT_SECONDS는 양의 정수여야 합니다: %s\n' "$browser_smoke_timeout_seconds" >&2
    return 1
  fi

  for browser_attempt in 1 2; do
    : >"$browser_dom"
    : >"$browser_log"
    rm -rf "$browser_profile"
    browser_profile="$(mktemp -d)"

    "$browser_bin" \
        --headless=new \
        --disable-background-networking \
        --disable-dev-shm-usage \
        --disable-gpu \
        --disable-sync \
        --metrics-recording-only \
        --no-default-browser-check \
        --no-first-run \
        --user-data-dir="$browser_profile" \
        --virtual-time-budget=15000 \
        --dump-dom \
        "$browser_smoke_url" >"$browser_dom" 2>"$browser_log" &
    browser_pid=$!

    for ((attempt = 1; attempt <= browser_smoke_timeout_seconds; attempt++)); do
      if grep -Eq 'data-browser-smoke="(passed|failed)"' "$browser_dom"; then
        break
      fi
      if ! kill -0 "$browser_pid" 2>/dev/null; then
        break
      fi
      sleep 1
    done

    if kill -0 "$browser_pid" 2>/dev/null; then
      kill "$browser_pid"
    fi
    wait "$browser_pid" 2>/dev/null || true
    browser_pid=""

    if grep -Fq 'data-browser-smoke="passed"' "$browser_dom"; then
      printf '공통 브라우저 smoke 검증 성공: %s\n' "$browser_smoke_url"
      return 0
    fi

    if grep -Fq 'data-browser-smoke="failed"' "$browser_dom" || [[ "$browser_attempt" == "2" ]]; then
      printf '브라우저 smoke 검증이 통과 상태를 반환하지 않았습니다.\n' >&2
      cat "$browser_dom" >&2
      cat "$browser_log" >&2
      return 1
    fi

    printf '브라우저 smoke 검증이 최종 상태를 반환하지 않아 새 프로필로 한 번 재시도합니다.\n' >&2
    cat "$browser_log" >&2
  done
}

trap cleanup EXIT

if [[ -z "$app_jar" ]] || [[ ! -f "$app_jar" ]]; then
  printf '실행 가능한 Spring Boot JAR 경로가 필요합니다: %s\n' "$app_jar" >&2
  exit 1
fi

java -jar "$app_jar" \
  "--spring.web.resources.static-locations=classpath:/META-INF/resources/,classpath:/resources/,classpath:/static/,classpath:/public/,file:${browser_test_root}/" \
  >"$raw_log" 2>&1 &
app_pid=$!

for ((attempt = 1; attempt <= 60; attempt++)); do
  if ! kill -0 "$app_pid" 2>/dev/null; then
    show_failure_log
    printf '애플리케이션이 smoke 요청 전에 종료되었습니다.\n' >&2
    exit 1
  fi

  http_status="$(curl --silent --output /dev/null --write-out '%{http_code}' "$smoke_url" || true)"
  if [[ "$http_status" == "204" ]]; then
    if ! assert_sensitive_values_absent; then
      show_failure_log
      exit 1
    fi
    if ! run_browser_smoke; then
      show_failure_log
      exit 1
    fi
    write_sanitized_log
    printf '부팅 smoke 검증 성공: %s -> HTTP 204\n' "$smoke_url"
    exit 0
  fi

  sleep 1
done

show_failure_log
printf '60초 안에 smoke 요청이 성공하지 않았습니다: %s\n' "$smoke_url" >&2
exit 1
