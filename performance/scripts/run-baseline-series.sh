#!/bin/sh
set -eu

. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/common.sh"

if [ "$#" -ne 1 ]; then
    echo "사용법: $0 <average-load|peak-load>" >&2
    exit 1
fi

scenario_name=$1
case "$scenario_name" in
    average-load) series_name=average ;;
    peak-load) series_name=peak ;;
    *)
        echo "기준선 3회 대상이 아닙니다: $scenario_name" >&2
        exit 1
        ;;
esac

for repetition in 1 2 3; do
    echo "기준선 시작: scenario=$scenario_name repetition=$repetition"
    "$PERFORMANCE_SCRIPT_DIR/stop-app.sh"
    "$PERFORMANCE_SCRIPT_DIR/reset-mvp.sh"
    "$PERFORMANCE_SCRIPT_DIR/start-app.sh"

    PERF_NEW_READER_OFFSET=0 PERF_NEW_READER_VU_STRIDE=1 PERF_NEW_READER_CYCLES=1 \
        PERFORMANCE_RUN_LABEL="baseline-$series_name-r$repetition-smoke" \
        "$PERFORMANCE_SCRIPT_DIR/run-k6.sh" smoke
    PERF_NEW_READER_OFFSET=1 PERF_NEW_READER_VU_STRIDE=16 PERF_NEW_READER_CYCLES=4 \
        PERFORMANCE_RUN_LABEL="baseline-$series_name-r$repetition-warm-up" \
        "$PERFORMANCE_SCRIPT_DIR/run-k6.sh" warm-up
    target_label="baseline-$series_name-r$repetition"
    PERF_NEW_READER_OFFSET=65 PERF_NEW_READER_VU_STRIDE=50 PERF_NEW_READER_CYCLES=5 \
        PERFORMANCE_RUN_LABEL="$target_label" \
        "$PERFORMANCE_SCRIPT_DIR/run-k6.sh" "$scenario_name"
    "$PERFORMANCE_SCRIPT_DIR/verify-invariants.sh"

    result_directory=$(find "$PERFORMANCE_ROOT/var/performance/results" \
        -mindepth 1 -maxdepth 1 -type d -name "*-$target_label" | sort | tail -n 1)
    if [ -z "$result_directory" ]; then
        echo "기준선 결과 디렉터리를 찾지 못했습니다: $target_label" >&2
        exit 1
    fi
    jq -e '
      (.metrics.checks.values.fails == 0)
      and (.metrics.http_req_failed.values.rate < 0.001)
      and ((.metrics.dropped_iterations.values.count // 0) == 0)
    ' "$result_directory/summary.json" >/dev/null
    jq -e '.samples > 0 and .maxCpuPercent < 90' \
        "$result_directory/generator-summary.json" >/dev/null
    jq -e '
      .status == "ok"
      and .processCpu.samples > 0
      and .hikariPendingConnections.samples > 0
      and .mysqlConnections.samples > 0
    ' "$result_directory/prometheus-summary.json" >/dev/null
    echo "기준선 통과: $result_directory"
done
