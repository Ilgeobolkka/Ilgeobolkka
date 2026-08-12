#!/bin/sh
set -eu

. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/common.sh"

require_performance_jar
require_performance_app_stopped
performance_compose up -d --wait mysql
"$PERFORMANCE_SCRIPT_DIR/reset-mvp.sh" >/dev/null

run_id="$(date -u +%Y%m%dT%H%M%SZ)-history-heavy"
output_directory="$PERFORMANCE_ROOT/var/performance/results/$run_id"
mkdir -p "$output_directory"
started_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)
started_epoch=$(date +%s)

performance_compose exec -T mysql sh -c \
    'MYSQL_PWD="$MYSQL_PASSWORD" mysql --batch --skip-column-names -u"$MYSQL_USER" "$MYSQL_DATABASE"' \
    <"$PERFORMANCE_ROOT/performance/data/history-heavy.sql"
performance_compose exec -T mysql sh -c \
    'MYSQL_PWD="$MYSQL_PASSWORD" mysql --batch --skip-column-names -u"$MYSQL_USER" "$MYSQL_DATABASE" -e "ANALYZE TABLE ink_purchase, page_rental, ink_ledger"' \
    >/dev/null

finished_epoch=$(date +%s)
finished_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)
duration_seconds=$((finished_epoch - started_epoch))

performance_compose exec -T mysql sh -c \
    'MYSQL_PWD="$MYSQL_PASSWORD" mysql --batch --skip-column-names -u"$MYSQL_USER" "$MYSQL_DATABASE"' \
    <"$PERFORMANCE_ROOT/performance/data/history-heavy-counts.sql" \
    >"$output_directory/counts.tsv"
"$PERFORMANCE_SCRIPT_DIR/verify-invariants.sh" >"$output_directory/invariants.tsv"

expected_counts='ink_purchase	6000
page_rental	500333
ink_ledger	506333
reader	1000
book	100
book_page	400'
actual_counts=$(cat "$output_directory/counts.tsv")
if [ "$actual_counts" != "$expected_counts" ]; then
    echo "history-heavy 행 수가 예상과 다릅니다." >&2
    printf '%s\n' "$actual_counts" >&2
    exit 1
fi

database_size_bytes=$(performance_compose exec -T mysql sh -c \
    'MYSQL_PWD="$MYSQL_PASSWORD" mysql --batch --skip-column-names -u"$MYSQL_USER" "$MYSQL_DATABASE" -e "SELECT COALESCE(SUM(data_length + index_length), 0) FROM information_schema.tables WHERE table_schema = DATABASE()"')
if [ "$duration_seconds" -ge 600 ]; then
    echo "history-heavy 생성 시간이 10분 경계를 넘었습니다: ${duration_seconds}s" >&2
    exit 1
fi
if [ "$database_size_bytes" -ge 2147483648 ]; then
    echo "history-heavy DB 크기가 2GiB 경계를 넘었습니다: $database_size_bytes" >&2
    exit 1
fi

git_sha=$(git -C "$PERFORMANCE_ROOT" rev-parse HEAD)
if [ -n "$(git -C "$PERFORMANCE_ROOT" status --porcelain)" ]; then
    dirty=true
else
    dirty=false
fi
jar_sha=$(shasum -a 256 "$PERFORMANCE_JAR" | awk '{print $1}')
jq -n \
    --arg startedUtc "$started_utc" \
    --arg finishedUtc "$finished_utc" \
    --arg gitSha "$git_sha" \
    --argjson dirty "$dirty" \
    --arg jarSha256 "$jar_sha" \
    --argjson durationSeconds "$duration_seconds" \
    --argjson databaseSizeBytes "$database_size_bytes" \
    '{
      dataset: "history-heavy",
      historyRentalsPerReader: 500,
      startedUtc: $startedUtc,
      finishedUtc: $finishedUtc,
      durationSeconds: $durationSeconds,
      databaseSizeBytes: $databaseSizeBytes,
      gitSha: $gitSha,
      dirty: $dirty,
      jarSha256: $jarSha256
    }' >"$output_directory/metadata.json"

echo "history-heavy 결과: $output_directory"
