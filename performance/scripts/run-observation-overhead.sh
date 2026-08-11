#!/bin/sh
set -eu

. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/common.sh"

run_mode() {
    observation_mode=$1
    "$PERFORMANCE_SCRIPT_DIR/stop-app.sh"
    "$PERFORMANCE_SCRIPT_DIR/recreate-environment.sh"

    if [ "$observation_mode" = "off" ]; then
        performance_compose stop grafana prometheus mysql-exporter >/dev/null 2>&1 || true
    fi

    "$PERFORMANCE_SCRIPT_DIR/reset-mvp.sh"
    "$PERFORMANCE_SCRIPT_DIR/start-app.sh"
    mode_result=0
    if ! OBSERVATION_MODE="$observation_mode" \
            PERFORMANCE_RUN_LABEL="observation-$observation_mode-warm-up" \
            "$PERFORMANCE_SCRIPT_DIR/run-k6.sh" warm-up; then
        mode_result=1
    fi
    if ! OBSERVATION_MODE="$observation_mode" \
            PERFORMANCE_RUN_LABEL="observation-$observation_mode-average" \
            "$PERFORMANCE_SCRIPT_DIR/run-k6.sh" average-load; then
        mode_result=1
    fi
    return "$mode_result"
}

observation_result=0
if ! run_mode off; then
    observation_result=1
fi
if ! run_mode on; then
    observation_result=1
fi

echo "관측 off/on Warm-up·Average 실행을 완료했습니다."
exit "$observation_result"
