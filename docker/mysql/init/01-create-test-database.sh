#!/bin/sh

set -eu

case "${MYSQL_DATABASE}" in
    *[!A-Za-z0-9_]*)
        echo "MYSQL_DATABASE는 영문, 숫자, 밑줄만 사용할 수 있습니다." >&2
        exit 1
        ;;
esac

case "${MYSQL_USER}" in
    *[!A-Za-z0-9_]*)
        echo "MYSQL_USER는 영문, 숫자, 밑줄만 사용할 수 있습니다." >&2
        exit 1
        ;;
esac

test_database="${MYSQL_DATABASE}_test"

mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" <<-SQL
    CREATE DATABASE IF NOT EXISTS \`${test_database}\`
        CHARACTER SET utf8mb4;
    GRANT ALL PRIVILEGES ON \`${test_database}\`.* TO '${MYSQL_USER}'@'%';
SQL
