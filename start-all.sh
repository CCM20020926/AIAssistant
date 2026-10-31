#!/bin/bash
# =============================================================================
# AI Assistant - Host Mode Startup Script
# =============================================================================
# Starts the 3 Java microservices on the host (non-Docker):
#   aiassistant-gateway (8080), aiassistant-agent (8081),
#   aiassistant-document-parse (8082)
#
# Middleware (MySQL, Redis, Kafka, Nacos, MinIO, PGVector, MinerU) must already
# be running. This script only starts the Java services.
#
# Usage:
#   bash start-all.sh              # run existing jars (default, skips build)
#   bash start-all.sh --build      # rebuild fat jars first, then run
#   bash start-all.sh --skip-build # same as default (explicit)
# =============================================================================

set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="${PROJECT_ROOT}/logs"
PID_DIR="${PROJECT_ROOT}/.pids"
SKIP_BUILD=true

if [ "$1" = "--build" ]; then
    SKIP_BUILD=false
fi

# Modules to start: "<dir>|<port>|<jar-pattern>"
MODULES=(
    "aiassistant-gateway|8080|aiassistant-gateway-*.jar"
    "aiassistant-agent|8081|aiassistant-agent-*.jar"
    "aiassistant-document-parse|8082|aiassistant-document-parse-*.jar"
)

# ===== Load .env =====
ENV_FILE="${PROJECT_ROOT}/.env"
if [ ! -f "${ENV_FILE}" ]; then
    echo "[ERROR] .env not found at ${ENV_FILE}"
    echo "  cp .env.template .env && vim .env"
    exit 1
fi

set -a
# shellcheck disable=SC1090
source "${ENV_FILE}"
set +a

export JAVA_OPTS="${JAVA_OPTS:--Xms256m -Xmx512m}"

mkdir -p "${LOG_DIR}" "${PID_DIR}"

echo "============================================="
echo "  AI Assistant - Host Mode Startup"
echo "============================================="
echo ""
echo "Project root: ${PROJECT_ROOT}"
echo "Log dir:      ${LOG_DIR}"
echo "Nacos:        ${NACOS_ADDR:-127.0.0.1:8848}"
echo "Redis:        ${REDIS_HOST:-127.0.0.1}:${REDIS_PORT:-6379}"
echo "MySQL:        ${MYSQL_HOST:-127.0.0.1}:${MYSQL_PORT:-3306}"
echo "Kafka:        ${KAFKA_ADDR:-127.0.0.1:9092}"
echo ""

# ===== Build (optional) =====
# NOTE: this repo does NOT bind spring-boot:repackage to the package phase
# (custom parent POM; plugin declared without <executions>), so a plain
# `mvn package` produces a THIN jar with no Main-Class ("no main manifest
# attribute"). We install first (so aiassistant-common is resolvable), then run
# `package spring-boot:repackage` per runnable module to make executable jars.
if [ "${SKIP_BUILD}" = false ]; then
    echo "[BUILD] mvn clean install -DskipTests ..."
    cd "${PROJECT_ROOT}"
    mvn clean install -DskipTests -q
    echo "[BUILD] Repackaging executable jars ..."
    for entry in "${MODULES[@]}"; do
        module="${entry%%|*}"
        echo "  - ${module}"
        mvn -pl "${module}" -DskipTests package spring-boot:repackage -q
    done
    echo "[BUILD] Done."
    echo ""
fi

# ===== Helper =====
start_service() {
    local module=$1
    local port=$2
    local jar_pattern=$3
    local jar_file
    jar_file=$(find "${PROJECT_ROOT}/${module}/target" -name "${jar_pattern}" \
        -not -name "*-sources*" -not -name "*-javadoc*" -not -name "*.original" \
        2>/dev/null | head -1)

    if [ -z "${jar_file}" ]; then
        echo "[ERROR] Jar not found for ${module}. Run with --build first."
        return 1
    fi

    local pid_file="${PID_DIR}/${module}.pid"
    local log_file="${LOG_DIR}/${module}.log"

    if [ -f "${pid_file}" ] && kill -0 "$(cat "${pid_file}")" 2>/dev/null; then
        echo "[SKIP] ${module} (port ${port}) already running (PID $(cat "${pid_file}"))"
        return 0
    fi

    echo -n "[START] ${module} (port ${port}) ... "
    nohup java ${JAVA_OPTS} \
        -Dserver.port=${port} \
        -jar "${jar_file}" \
        > "${log_file}" 2>&1 &

    echo $! > "${pid_file}"
    echo "PID $!"
}

# ===== Start all =====
echo "--- Starting microservices ---"
for entry in "${MODULES[@]}"; do
    module="${entry%%|*}"
    rest="${entry#*|}"
    port="${rest%%|*}"
    jar_pattern="${rest#*|}"
    start_service "${module}" "${port}" "${jar_pattern}"
done
echo ""

# Brief pause so immediately-crashing processes show up in the summary.
sleep 3

# ===== Summary =====
echo "============================================="
echo "  All Services Started"
echo "============================================="
echo ""
echo "  Service                     Port    Status"
echo "  --------------------------  ------  ----------------------"
for entry in "${MODULES[@]}"; do
    module="${entry%%|*}"
    rest="${entry#*|}"
    port="${rest%%|*}"
    pid_file="${PID_DIR}/${module}.pid"
    if [ -f "${pid_file}" ] && kill -0 "$(cat "${pid_file}")" 2>/dev/null; then
        status="RUNNING (PID $(cat "${pid_file}"))"
    else
        status="FAILED (see log)"
    fi
    printf "  %-26s  %-6s  %s\n" "${module}" "${port}" "${status}"
done
echo ""
echo "Useful commands:"
echo "  View logs:   tail -f ${LOG_DIR}/<module>.log"
echo "  Stop all:    for f in ${PID_DIR}/*.pid; do kill \"\$(cat \"\$f\")\"; rm -f \"\$f\"; done"
echo ""
