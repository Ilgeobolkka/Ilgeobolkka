#!/bin/sh
set -eu

. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/common.sh"

database_name=$(performance_compose exec -T mysql sh -c \
    'MYSQL_PWD="$MYSQL_PASSWORD" mysql --batch --skip-column-names -u"$MYSQL_USER" "$MYSQL_DATABASE" -e "SELECT DATABASE()"')
if [ "$database_name" != "ilgeobolkka_perf" ]; then
    echo "성능 불변식 검증은 ilgeobolkka_perf에서만 허용됩니다: $database_name" >&2
    exit 1
fi

invariant_counts=$(performance_compose exec -T mysql sh -c \
    'MYSQL_PWD="$MYSQL_PASSWORD" mysql --batch --skip-column-names -u"$MYSQL_USER" "$MYSQL_DATABASE"' \
    <"$PERFORMANCE_ROOT/performance/data/verify-invariants.sql")
if printf '%s\n' "$invariant_counts" | awk '$2 != 0 { exit 1 }'; then
    printf '%s\n' "$invariant_counts"
else
    echo "성능 데이터 불변식이 깨졌습니다." >&2
    printf '%s\n' "$invariant_counts" >&2
    exit 1
fi
