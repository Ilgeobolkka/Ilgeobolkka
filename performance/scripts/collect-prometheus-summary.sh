#!/bin/sh
set -eu

. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/common.sh"

if [ "$#" -ne 1 ]; then
    echo "사용법: $0 <output-directory>" >&2
    exit 1
fi

output_directory=$1
summary_file="$output_directory/prometheus-summary.json"
if [ "${OBSERVATION_MODE:-normal}" = "off" ]; then
    jq -n '{status: "skipped", reason: "observation-off"}' >"$summary_file"
    exit 0
fi
if ! curl -fsS http://127.0.0.1:9090/-/ready >/dev/null; then
    jq -n '{status: "unavailable", reason: "prometheus-not-ready"}' >"$summary_file"
    exit 0
fi

started_utc=$(jq -r '.startedUtc' "$output_directory/metadata.json")
finished_utc=$(jq -r '.finishedUtc' "$output_directory/metadata.json")
range_directory="$output_directory/prometheus-range"
mkdir -p "$range_directory"

query_range() {
    metric_name=$1
    expression=$2
    curl -fsS -G http://127.0.0.1:9090/api/v1/query_range \
        --data-urlencode "query=$expression" \
        --data-urlencode "start=$started_utc" \
        --data-urlencode "end=$finished_utc" \
        --data-urlencode 'step=5s' \
        >"$range_directory/$metric_name.json"
}

query_range process_cpu 'avg(process_cpu_usage{job="ilgeobolkka-application"})'
query_range heap_used 'sum(jvm_memory_used_bytes{job="ilgeobolkka-application",area="heap"})'
query_range tomcat_busy 'sum(tomcat_threads_busy_threads{job="ilgeobolkka-application"})'
query_range hikari_active 'sum(hikaricp_connections_active{job="ilgeobolkka-application"})'
query_range hikari_pending 'sum(hikaricp_connections_pending{job="ilgeobolkka-application"})'
query_range mysql_connections 'mysql_global_status_threads_connected{job="ilgeobolkka-mysql"}'
query_range mysql_qps 'rate(mysql_global_status_queries{job="ilgeobolkka-mysql"}[30s])'
query_range mysql_slow_queries 'mysql_global_status_slow_queries{job="ilgeobolkka-mysql"}'

jq -n \
    --slurpfile processCpu "$range_directory/process_cpu.json" \
    --slurpfile heapUsed "$range_directory/heap_used.json" \
    --slurpfile tomcatBusy "$range_directory/tomcat_busy.json" \
    --slurpfile hikariActive "$range_directory/hikari_active.json" \
    --slurpfile hikariPending "$range_directory/hikari_pending.json" \
    --slurpfile mysqlConnections "$range_directory/mysql_connections.json" \
    --slurpfile mysqlQps "$range_directory/mysql_qps.json" \
    --slurpfile mysqlSlowQueries "$range_directory/mysql_slow_queries.json" \
    '
      def numbers($document):
        [$document[0].data.result[]?.values[]?[1] | tonumber];
      def statistics($document):
        numbers($document) as $values
        | if ($values | length) == 0 then
            {samples: 0, average: null, min: null, max: null, latest: null}
          else
            {
              samples: ($values | length),
              average: (($values | add) / ($values | length)),
              min: ($values | min),
              max: ($values | max),
              latest: $values[-1]
            }
          end;
      {
        status: "ok",
        processCpu: statistics($processCpu),
        heapUsedBytes: statistics($heapUsed),
        tomcatBusyThreads: statistics($tomcatBusy),
        hikariActiveConnections: statistics($hikariActive),
        hikariPendingConnections: statistics($hikariPending),
        mysqlConnections: statistics($mysqlConnections),
        mysqlQueriesPerSecond: statistics($mysqlQps),
        mysqlSlowQueries: statistics($mysqlSlowQueries)
      }
    ' >"$summary_file"
