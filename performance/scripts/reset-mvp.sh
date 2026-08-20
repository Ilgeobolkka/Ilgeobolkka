#!/bin/sh
set -eu

. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/common.sh"

require_performance_jar
require_performance_app_stopped
performance_compose up -d --wait mysql

performance_compose run --rm --no-deps \
    -e SPRING_PROFILES_ACTIVE=performance,performance-seed \
    app

"$PERFORMANCE_SCRIPT_DIR/verify-dataset.sh"
