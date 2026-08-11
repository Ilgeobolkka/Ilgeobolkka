#!/bin/sh
set -eu

. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/common.sh"

if [ "$#" -ne 1 ]; then
    echo "사용법: $0 <smoke|warm-up|average-load|peak-load|stress|spike|soak|contention|browser>" >&2
    exit 1
fi

scenario_name=$1
case "$scenario_name" in
    smoke|warm-up|average-load|peak-load|stress|spike|soak|contention|browser) ;;
    *)
        echo "알 수 없는 k6 시나리오입니다: $scenario_name" >&2
        exit 1
        ;;
esac

require_performance_jar
curl -fsS http://127.0.0.1:8080/api/smoke >/dev/null
mkdir -p "$PERFORMANCE_ROOT/var/performance/results"

run_label=${PERFORMANCE_RUN_LABEL:-$scenario_name}
case "$run_label" in
    *[!a-zA-Z0-9._-]*)
        echo "PERFORMANCE_RUN_LABEL은 영문자·숫자·점·밑줄·하이픈만 허용합니다." >&2
        exit 1
        ;;
esac
run_id="$(date -u +%Y%m%dT%H%M%SZ)-$run_label"
output_directory="$PERFORMANCE_ROOT/var/performance/results/$run_id"
mkdir -p "$output_directory"
started_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)

set -- run --rm -e K6_SUMMARY_PATH="/results/$run_id/summary.json"
if [ "$scenario_name" = "contention" ]; then
    set -- "$@" -e CONTENTION_CASE="${CONTENTION_CASE:-same-page}"
fi

set +e
performance_compose "$@" k6 run --quiet "/scripts/$scenario_name.js"
k6_exit_code=$?
set -e

finished_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)
"$PERFORMANCE_SCRIPT_DIR/collect-metadata.sh" \
    "$output_directory" "$scenario_name" "$started_utc" "$finished_utc"

printf '%s\n' "$k6_exit_code" >"$output_directory/exit-code.txt"
echo "k6 결과: $output_directory"
exit "$k6_exit_code"
