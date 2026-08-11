#!/bin/sh
set -eu

. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/common.sh"

database_name=$(performance_compose exec -T mysql sh -c \
    'MYSQL_PWD="$MYSQL_PASSWORD" mysql --batch --skip-column-names -u"$MYSQL_USER" "$MYSQL_DATABASE" -e "SELECT DATABASE()"')
if [ "$database_name" != "ilgeobolkka_perf" ]; then
    echo "성능 데이터 검증은 ilgeobolkka_perf에서만 허용됩니다: $database_name" >&2
    exit 1
fi

actual_counts=$(performance_compose exec -T mysql sh -c \
    'MYSQL_PWD="$MYSQL_PASSWORD" mysql --batch --skip-column-names -u"$MYSQL_USER" "$MYSQL_DATABASE"' \
    <"$PERFORMANCE_ROOT/performance/data/dataset-counts.sql")
expected_counts='book	100
book_page	400
reader	1000
ink_account	1000
ink_purchase	1000
ink_ledger	1333
page_rental	333
ownership_payment	333
book_ownership	333
library_entry	666
reading_session	0'

if [ "$actual_counts" != "$expected_counts" ]; then
    echo "성능 mvp 데이터 행 수가 예상과 다릅니다." >&2
    printf '%s\n' "$actual_counts" >&2
    exit 1
fi

invariant_counts=$(performance_compose exec -T mysql sh -c \
    'MYSQL_PWD="$MYSQL_PASSWORD" mysql --batch --skip-column-names -u"$MYSQL_USER" "$MYSQL_DATABASE"' \
    <"$PERFORMANCE_ROOT/performance/data/verify-invariants.sql")
if printf '%s\n' "$invariant_counts" | awk '$2 != 0 { exit 1 }'; then
    printf '%s\n' "$actual_counts"
    printf '%s\n' "$invariant_counts"
else
    echo "성능 mvp 데이터 불변식이 깨졌습니다." >&2
    printf '%s\n' "$invariant_counts" >&2
    exit 1
fi
