#!/bin/sh
set -eu

if [ -z "${PERF_MYSQL_EXPORTER_PASSWORD:-}" ]; then
    echo "PERF_MYSQL_EXPORTER_PASSWORD가 필요합니다." >&2
    exit 1
fi

escaped_password=$(printf '%s' "$PERF_MYSQL_EXPORTER_PASSWORD" \
    | sed -e 's/\\/\\\\/g' -e "s/'/\\\\'/g")

MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=socket -uroot <<SQL
CREATE USER IF NOT EXISTS 'exporter'@'%' IDENTIFIED BY '${escaped_password}';
ALTER USER 'exporter'@'%' IDENTIFIED BY '${escaped_password}';
GRANT PROCESS, REPLICATION CLIENT, SELECT ON *.* TO 'exporter'@'%';
FLUSH PRIVILEGES;
SQL
