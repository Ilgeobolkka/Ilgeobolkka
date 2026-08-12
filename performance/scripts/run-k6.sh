#!/bin/sh
set -eu

. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/common.sh"

if [ "$#" -ne 1 ]; then
    echo "사용법: $0 <smoke|warm-up|average-load|peak-load|stress|spike|soak|contention|browser|browser-cache|history-index>" >&2
    exit 1
fi

scenario_name=$1
case "$scenario_name" in
    smoke|warm-up|average-load|peak-load|stress|spike|soak|contention|browser|browser-cache|history-index) ;;
    *)
        echo "알 수 없는 k6 시나리오입니다: $scenario_name" >&2
        exit 1
        ;;
esac

case "$scenario_name" in
    stress)
        : "${PERF_NEW_READER_OFFSET:=65}"
        : "${PERF_NEW_READER_VU_STRIDE:=7}"
        : "${PERF_NEW_READER_CYCLES:=37}"
        export PERF_NEW_READER_OFFSET PERF_NEW_READER_VU_STRIDE PERF_NEW_READER_CYCLES
        ;;
    smoke|warm-up|average-load|peak-load|spike|soak)
        : "${PERF_NEW_READER_OFFSET:=0}"
        : "${PERF_NEW_READER_VU_STRIDE:=64}"
        : "${PERF_NEW_READER_CYCLES:=4}"
        export PERF_NEW_READER_OFFSET PERF_NEW_READER_VU_STRIDE PERF_NEW_READER_CYCLES
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
dataset_metadata_file="$output_directory/dataset.json"
if [ "$scenario_name" = "history-index" ]; then
    performance_compose exec -T mysql sh -c \
        'MYSQL_PWD="$MYSQL_PASSWORD" mysql --batch --skip-column-names -u"$MYSQL_USER" "$MYSQL_DATABASE"' \
        <"$PERFORMANCE_ROOT/performance/data/history-heavy-counts.sql" \
        | "$PERFORMANCE_SCRIPT_DIR/render-dataset-metadata.sh" history-heavy \
            >"$dataset_metadata_file"
else
    "$PERFORMANCE_SCRIPT_DIR/render-dataset-metadata.sh" mvp >"$dataset_metadata_file"
fi
started_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)
sampler_stop_file="$output_directory/.generator-sampler-stop"
rm -f "$sampler_stop_file"
"$PERFORMANCE_SCRIPT_DIR/sample-k6-generator.sh" \
    "$output_directory" "$sampler_stop_file" &
sampler_pid=$!

stop_sampler() {
    : >"$sampler_stop_file"
    wait "$sampler_pid" || true
    rm -f "$sampler_stop_file"
}
trap stop_sampler 0 1 2 15

set -- run --rm -e K6_SUMMARY_PATH="/results/$run_id/summary.json"
if [ -n "${PERF_DURATION:-}" ]; then
    set -- "$@" -e "PERF_DURATION=$PERF_DURATION"
fi
if [ -n "${PERF_NEW_READER_OFFSET:-}" ]; then
    set -- "$@" -e "PERF_NEW_READER_OFFSET=$PERF_NEW_READER_OFFSET"
fi
if [ -n "${PERF_NEW_READER_VU_STRIDE:-}" ]; then
    set -- "$@" -e "PERF_NEW_READER_VU_STRIDE=$PERF_NEW_READER_VU_STRIDE"
fi
if [ -n "${PERF_NEW_READER_CYCLES:-}" ]; then
    set -- "$@" -e "PERF_NEW_READER_CYCLES=$PERF_NEW_READER_CYCLES"
fi
if [ "$scenario_name" = "contention" ]; then
    set -- "$@" -e CONTENTION_CASE="${CONTENTION_CASE:-same-page}"
fi

set +e
performance_compose "$@" k6 run --log-format raw --quiet "/scripts/$scenario_name.js" \
    >"$output_directory/k6.log" 2>&1
k6_exit_code=$?
set -e
stop_sampler
trap - 0 1 2 15
if [ "$scenario_name" = "browser-cache" ]; then
    rg -v '^BROWSER_CACHE_EVIDENCE ' "$output_directory/k6.log" || true
else
    cat "$output_directory/k6.log"
fi

finished_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)
"$PERFORMANCE_SCRIPT_DIR/collect-metadata.sh" \
    "$output_directory" "$scenario_name" "$started_utc" "$finished_utc" \
    "$dataset_metadata_file"
sleep 6
"$PERFORMANCE_SCRIPT_DIR/collect-prometheus-summary.sh" "$output_directory"

if [ "$scenario_name" = "browser-cache" ]; then
    rg -o 'BROWSER_CACHE_EVIDENCE \{.*\}' "$output_directory/k6.log" \
        | sed 's/^BROWSER_CACHE_EVIDENCE //' \
        >"$output_directory/browser-cache.jsonl"
    if [ "$(wc -l <"$output_directory/browser-cache.jsonl" | tr -d ' ')" -ne 3 ]; then
        echo "브라우저 cache 근거가 3쌍이 아닙니다." >&2
        exit 1
    fi
fi

printf '%s\n' "$k6_exit_code" >"$output_directory/exit-code.txt"
echo "k6 결과: $output_directory"
exit "$k6_exit_code"
