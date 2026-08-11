#!/bin/sh
set -eu

. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/common.sh"

if [ "$#" -gt 1 ]; then
    echo "사용법: $0 [same-page|different-pages|different-readers|session]" >&2
    exit 1
fi

contention_case=${1:-}
case "$contention_case" in
    ""|same-page|different-pages|different-readers|session) ;;
    *)
        echo "지원하지 않는 동시성 검증 케이스입니다: $contention_case" >&2
        exit 1
        ;;
esac

database_name=$(performance_compose exec -T mysql sh -c \
    'MYSQL_PWD="$MYSQL_PASSWORD" mysql --batch --skip-column-names -u"$MYSQL_USER" "$MYSQL_DATABASE" -e "SELECT DATABASE()"')
if [ "$database_name" != "ilgeobolkka_perf" ]; then
    echo "성능 불변식 검증은 ilgeobolkka_perf에서만 허용됩니다: $database_name" >&2
    exit 1
fi

invariant_counts=$(performance_compose exec -T mysql sh -c \
    'MYSQL_PWD="$MYSQL_PASSWORD" mysql --batch --skip-column-names -u"$MYSQL_USER" "$MYSQL_DATABASE"' \
    <"$PERFORMANCE_ROOT/performance/data/verify-invariants.sql")

if [ -n "$contention_case" ]; then
    contention_counts=$(
        {
            printf "SET @contention_case = '%s';\n" "$contention_case"
            sed -n '1,$p' "$PERFORMANCE_ROOT/performance/data/verify-contention-case.sql"
        } | performance_compose exec -T mysql sh -c \
            'MYSQL_PWD="$MYSQL_PASSWORD" mysql --batch --skip-column-names -u"$MYSQL_USER" "$MYSQL_DATABASE"'
    )
    invariant_counts="$invariant_counts
$contention_counts"
fi

if printf '%s\n' "$invariant_counts" | awk '$2 != 0 { exit 1 }'; then
    printf '%s\n' "$invariant_counts"
else
    echo "성능 데이터 불변식이 깨졌습니다." >&2
    printf '%s\n' "$invariant_counts" >&2
    exit 1
fi
