#!/bin/sh
set -eu

. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/common.sh"

require_performance_app_stopped

echo "삭제 범위: Compose project $PERFORMANCE_PROJECT"
echo "삭제 가능한 volume: ${PERFORMANCE_PROJECT}_mysql-perf-data"
echo "삭제 가능한 volume: ${PERFORMANCE_PROJECT}_prometheus-data"
echo "보존 대상: ilgeobolkka_mysql-data"

performance_compose down -v --remove-orphans
performance_compose up -d --wait mysql mysql-exporter prometheus grafana
performance_compose ps
