#!/bin/sh
set -eu

PERFORMANCE_SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PERFORMANCE_ROOT=$(CDPATH= cd -- "$PERFORMANCE_SCRIPT_DIR/../.." && pwd)
PERFORMANCE_COMPOSE_FILE="$PERFORMANCE_ROOT/compose.performance.yaml"
PERFORMANCE_PROJECT="ilgeobolkka-performance"
PERFORMANCE_JAR="$PERFORMANCE_ROOT/build/libs/Ilgeobolkka-0.0.1-SNAPSHOT.jar"

performance_compose() {
    docker compose \
        -p "$PERFORMANCE_PROJECT" \
        -f "$PERFORMANCE_COMPOSE_FILE" \
        "$@"
}

require_performance_jar() {
    if [ ! -f "$PERFORMANCE_JAR" ]; then
        echo "패키징 JAR이 없습니다: $PERFORMANCE_JAR" >&2
        echo "먼저 ./gradlew build를 실행해야 합니다." >&2
        exit 1
    fi
}

performance_app_is_running() {
    performance_compose ps --status running --services | rg -qx app
}

require_performance_app_stopped() {
    if performance_app_is_running; then
        echo "성능 애플리케이션이 실행 중이어서 데이터 초기화를 거부합니다." >&2
        exit 1
    fi
}
