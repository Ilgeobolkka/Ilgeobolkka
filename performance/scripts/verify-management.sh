#!/bin/sh
set -eu

. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/common.sh"

temporary_directory=$(mktemp -d)
trap 'rm -rf "$temporary_directory"' EXIT

curl -fsS http://127.0.0.1:8081/actuator/health >"$temporary_directory/health.json"
curl -fsS http://127.0.0.1:8081/actuator >"$temporary_directory/links.json"
curl -fsS http://127.0.0.1:8081/actuator/prometheus >"$temporary_directory/metrics.txt"

if [ "$(jq -r '.status' "$temporary_directory/health.json")" != "UP" ]; then
    echo "management health가 UP이 아닙니다." >&2
    exit 1
fi

actual_links=$(jq -r '._links | keys[]' "$temporary_directory/links.json" | sort)
expected_links='health
health-path
prometheus
self'
if [ "$actual_links" != "$expected_links" ]; then
    echo "허용하지 않은 management endpoint가 노출됐습니다." >&2
    printf '%s\n' "$actual_links" >&2
    exit 1
fi

application_status=$(curl -sS -o /dev/null -w '%{http_code}' \
    http://127.0.0.1:8080/actuator/health)
if [ "$application_status" != "404" ]; then
    echo "애플리케이션 공개 포트에 actuator가 노출됐습니다: HTTP $application_status" >&2
    exit 1
fi

if rg -n '(^|[,\{])(reader_id|book_id|page_number|session_id|email)=' \
    "$temporary_directory/metrics.txt"; then
    echo "금지한 사용자·콘텐츠 식별 metric label을 찾았습니다." >&2
    exit 1
fi

if ! lsof -nP -iTCP:8081 -sTCP:LISTEN | rg -q '127\.0\.0\.1:8081'; then
    echo "management 포트가 loopback에만 바인딩되지 않았습니다." >&2
    exit 1
fi

echo "management health·prometheus·loopback·label 경계를 확인했습니다."
