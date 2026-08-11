#!/bin/sh
set -eu

. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/common.sh"

if ! performance_app_is_running; then
    echo "실행 중인 성능 애플리케이션 container가 없습니다."
    exit 0
fi

performance_compose stop app
echo "성능 애플리케이션 container를 종료했습니다."
