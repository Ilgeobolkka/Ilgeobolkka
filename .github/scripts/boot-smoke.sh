#!/usr/bin/env bash

set -euo pipefail

app_jar="${1:-}"
smoke_url="${SMOKE_URL:-http://127.0.0.1:8080/api/smoke}"
log_file="${SMOKE_LOG_FILE:-build/boot-smoke.log}"
raw_log="$(mktemp)"
app_pid=""

sensitive_variables=(
  DB_URL
  DB_USERNAME
  DB_PASSWORD
  DB_ROOT_PASSWORD
  PORTONE_API_SECRET
  PORTONE_WEBHOOK_SECRET
  CONTENT_STORAGE_PATH
  CONTENT_STORAGE_ROOT
)

cleanup() {
  if [[ -n "$app_pid" ]] && kill -0 "$app_pid" 2>/dev/null; then
    kill "$app_pid"
    wait "$app_pid" 2>/dev/null || true
  fi
  rm -f "$raw_log"
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

  for variable in DB_URL DB_PASSWORD DB_ROOT_PASSWORD PORTONE_API_SECRET PORTONE_WEBHOOK_SECRET CONTENT_STORAGE_PATH CONTENT_STORAGE_ROOT;
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

trap cleanup EXIT

if [[ -z "$app_jar" ]] || [[ ! -f "$app_jar" ]]; then
  printf '실행 가능한 Spring Boot JAR 경로가 필요합니다: %s\n' "$app_jar" >&2
  exit 1
fi

java -jar "$app_jar" >"$raw_log" 2>&1 &
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
    write_sanitized_log
    printf '부팅 smoke 검증 성공: %s -> HTTP 204\n' "$smoke_url"
    exit 0
  fi

  sleep 1
done

show_failure_log
printf '60초 안에 smoke 요청이 성공하지 않았습니다: %s\n' "$smoke_url" >&2
exit 1
