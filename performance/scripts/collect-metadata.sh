#!/bin/sh
set -eu

. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/common.sh"

if [ "$#" -ne 4 ]; then
    echo "사용법: $0 <output-directory> <scenario> <started-utc> <finished-utc>" >&2
    exit 1
fi

output_directory=$1
scenario_name=$2
started_utc=$3
finished_utc=$4
mkdir -p "$output_directory"

git_sha=$(git -C "$PERFORMANCE_ROOT" rev-parse HEAD)
if [ -n "$(git -C "$PERFORMANCE_ROOT" status --porcelain)" ]; then
    dirty=true
else
    dirty=false
fi
jar_sha=$(shasum -a 256 "$PERFORMANCE_JAR" | awk '{print $1}')
host_os=$(sw_vers -productVersion)
host_arch=$(uname -m)
host_cpu=$(sysctl -n machdep.cpu.brand_string)
host_logical_cpu=$(sysctl -n hw.logicalcpu)
host_memory_bytes=$(sysctl -n hw.memsize)
docker_cpu=$(docker info --format '{{.NCPU}}')
docker_memory_bytes=$(docker info --format '{{.MemTotal}}')
observation_mode=${OBSERVATION_MODE:-normal}

jq -n \
    --arg scenario "$scenario_name" \
    --arg startedUtc "$started_utc" \
    --arg finishedUtc "$finished_utc" \
    --arg gitSha "$git_sha" \
    --argjson dirty "$dirty" \
    --arg jarSha256 "$jar_sha" \
    --arg hostOs "$host_os" \
    --arg hostArch "$host_arch" \
    --arg hostCpu "$host_cpu" \
    --argjson hostLogicalCpu "$host_logical_cpu" \
    --argjson hostMemoryBytes "$host_memory_bytes" \
    --argjson dockerCpu "$docker_cpu" \
    --argjson dockerMemoryBytes "$docker_memory_bytes" \
    --arg observationMode "$observation_mode" \
    '{
      scenario: $scenario,
      observationMode: $observationMode,
      startedUtc: $startedUtc,
      finishedUtc: $finishedUtc,
      gitSha: $gitSha,
      dirty: $dirty,
      jarSha256: $jarSha256,
      host: {
        os: $hostOs,
        arch: $hostArch,
        cpu: $hostCpu,
        logicalCpu: $hostLogicalCpu,
        memoryBytes: $hostMemoryBytes
      },
      dockerDesktop: {
        cpu: $dockerCpu,
        memoryBytes: $dockerMemoryBytes
      },
      application: {
        profile: "performance",
        runtime: "Compose app service",
        server: "app:8080; host 127.0.0.1:8080",
        management: "app:8081; host 127.0.0.1:8081",
        image: "eclipse-temurin:21-jdk-jammy@sha256:55fb9bf738f5d9b4a6c01b39337e3070d3e27370dd3c478fd1d5d3cd2233c6d8",
        jvmOptions: [],
        tomcat: {maxThreads: "Spring Boot default"},
        hikari: {maximumPoolSize: "Spring Boot default"}
      },
      database: {
        name: "ilgeobolkka_perf",
        host: "127.0.0.1:3308"
      },
      externalServices: {
        portOne: false,
        openAi: false
      }
    }' >"$output_directory/metadata.json"
