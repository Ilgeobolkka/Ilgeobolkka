#!/bin/sh
set -eu

. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/common.sh"

if [ "$#" -ne 2 ]; then
    echo "사용법: $0 <output-directory> <stop-file>" >&2
    exit 1
fi

output_directory=$1
stop_file=$2
samples_file="$output_directory/generator-cpu.tsv"
summary_file="$output_directory/generator-summary.json"
printf 'timestamp_utc\tcpu_percent\n' >"$samples_file"

while [ ! -f "$stop_file" ]; do
    container_id=$(docker ps -q \
        --filter "label=com.docker.compose.project=$PERFORMANCE_PROJECT" \
        --filter 'label=com.docker.compose.service=k6' | head -n 1)
    if [ -n "$container_id" ]; then
        cpu_percent=$(docker stats --no-stream --format '{{.CPUPerc}}' "$container_id" \
            2>/dev/null | head -n 1 | tr -d '%')
        if [ -n "$cpu_percent" ]; then
            printf '%s\t%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$cpu_percent" \
                >>"$samples_file"
        fi
    fi
    sleep 2
done

sample_count=$(awk 'NR > 1 { count += 1 } END { print count + 0 }' "$samples_file")
if [ "$sample_count" -eq 0 ]; then
    jq -n '{samples: 0, averageCpuPercent: null, maxCpuPercent: null}' >"$summary_file"
    exit 0
fi

average_cpu=$(awk 'NR > 1 { sum += $2; count += 1 } END { printf "%.6f", sum / count }' \
    "$samples_file")
max_cpu=$(awk 'NR > 1 && $2 > maximum { maximum = $2 } END { printf "%.6f", maximum }' \
    "$samples_file")
jq -n \
    --argjson samples "$sample_count" \
    --argjson averageCpuPercent "$average_cpu" \
    --argjson maxCpuPercent "$max_cpu" \
    '{
      samples: $samples,
      averageCpuPercent: $averageCpuPercent,
      maxCpuPercent: $maxCpuPercent
    }' >"$summary_file"
