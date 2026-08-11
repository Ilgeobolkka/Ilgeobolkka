#!/bin/sh
set -eu

. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/common.sh"

require_performance_jar
if performance_app_is_running; then
    echo "성능 애플리케이션 container가 이미 실행 중입니다."
    exit 0
fi

mkdir -p "$PERFORMANCE_ROOT/var/performance"
performance_compose up -d --wait app
curl -fsS http://127.0.0.1:8080/api/smoke >/dev/null
curl -fsS http://127.0.0.1:8081/actuator/health >/dev/null
echo "성능 애플리케이션 container가 준비됐습니다."
