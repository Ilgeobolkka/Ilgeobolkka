#!/bin/sh
set -eu

. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/common.sh"

mkdir -p "$PERFORMANCE_ROOT/var/performance/results"
performance_compose up -d --wait mysql mysql-exporter prometheus grafana
performance_compose ps
