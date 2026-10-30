#!/usr/bin/env bash
# ============================================================
# AI Assistant - one-click microservice launcher (Linux)
#
# Builds and starts all runnable Spring Boot modules in the
# background, one log file per module under ./logs.
#
# Usage:
#   ./start-all.sh                 # build (if needed) + start all
#   ./start-all.sh --skip-build    # start all, reuse existing jars
#   ./start-all.sh stop            # stop all
#   ./start-all.sh restart         # stop + start all
#   ./start-all.sh status          # show running state
#
# Env overrides:
#   MVN=/path/to/mvn  JAVA_HOME=/path/to/jdk21  JAVA_OPTS="-Xmx1g"
#   BUILD_OFFLINE=1  (pass -o to Maven)
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

LOG_DIR="$SCRIPT_DIR/logs"
MVN="${MVN:-mvn}"

# name            module dir                    port
MODULE_NAMES=(    agent        document-parse                gateway )
MODULE_DIRS=(     aiassistant-agent aiassistant-document-parse aiassistant-gateway )
MODULE_PORTS=(    8081         8082                            8080 )

# ------------------------------------------------------------
# helpers
# ------------------------------------------------------------
log()  { printf '[%s] %s\n' "$(date '+%H:%M:%S')" "$*"; }
die()  { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

pid_file() { echo "$LOG_DIR/$1.pid"; }
log_file() { echo "$LOG_DIR/$1.log"; }

module_dir()  { echo "${MODULE_DIRS[$1]}"; }
module_name() { echo "${MODULE_NAMES[$1]}"; }
module_port() { echo "${MODULE_PORTS[$1]}"; }

jar_path() {
  local dir="$1"
  find "$dir/target" -maxdepth 1 -name '*.jar' \
       ! -name '*.original' ! -name '*-sources.jar' ! -name '*-javadoc.jar' \
       2>/dev/null | head -n1
}

read_pid() {
  local pf; pf="$(pid_file "$1")"
  [[ -f "$pf" ]] && cat "$pf" || echo ""
}

is_running() {
  local pid; pid="$(read_pid "$1")"
  [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null
}

port_open() {
  (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null && { exec 3<&- 3>&-; return 0; } || return 1
}

load_env() {
  if [[ -f "$SCRIPT_DIR/.env" ]]; then
    log "loading environment from .env"
    set -a; # shellcheck disable=SC1091
    . "$SCRIPT_DIR/.env"
    set +a
  fi
}

check_java() {
  local java_bin="java"
  [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]] && java_bin="$JAVA_HOME/bin/java"
  command -v "$java_bin" >/dev/null 2>&1 || die "java not found (set JAVA_HOME to a JDK 21)"
  local ver; ver="$("$java_bin" -version 2>&1 | head -n1)"
  log "using java: $ver"
  JAVA_BIN="$java_bin"
}

# ------------------------------------------------------------
# build
# ------------------------------------------------------------
build() {
  local mvn_args=(-DskipTests clean install)
  [[ "${BUILD_OFFLINE:-0}" == "1" ]] && mvn_args=(-o "${mvn_args[@]}")

  log "building modules: $MVN ${mvn_args[*]}"
  "$MVN" -q "${mvn_args[@]}" || die "maven build failed"

  # The boot plugin has no bound repackage execution, so make executable
  # fat jars explicitly for each runnable module.
  local i name dir
  for i in "${!MODULE_NAMES[@]}"; do
    name="$(module_name "$i")"; dir="$(module_dir "$i")"
    log "repackaging $name (spring-boot:repackage)"
    local rep_args=(-pl "$dir" spring-boot:repackage)
    [[ "${BUILD_OFFLINE:-0}" == "1" ]] && rep_args=(-o "${rep_args[@]}")
    "$MVN" -q "${rep_args[@]}" || die "repackage failed for $name"
    [[ -n "$(jar_path "$dir")" ]] || die "no jar produced for $name"
  done
}

# ------------------------------------------------------------
# start / stop / status
# ------------------------------------------------------------
start_one() {
  local i="$1" name dir port jar
  name="$(module_name "$i")"; dir="$(module_dir "$i")"; port="$(module_port "$i")"

  if is_running "$name"; then
    log "$name already running (pid $(read_pid "$name")) - skipping"
    return 0
  fi

  jar="$(jar_path "$dir")"
  [[ -n "$jar" ]] || die "$name jar not found in $dir/target (run without --skip-build)"

  local lf pf; lf="$(log_file "$name")"; pf="$(pid_file "$name")"
  log "starting $name (port $port) -> $lf"
  # shellcheck disable=SC2086
  nohup "$JAVA_BIN" $JAVA_OPTS -jar "$jar" >"$lf" 2>&1 &
  echo $! >"$pf"
  disown || true
}

wait_ready() {
  local i="$1" name port timeout=60 waited=0
  name="$(module_name "$i")"; port="$(module_port "$i")"
  while (( waited < timeout )); do
    if ! is_running "$name"; then
      log "$name FAILED to stay up - see $(log_file "$name")"
      return 1
    fi
    if port_open "$port"; then
      log "$name is up on port $port (${waited}s)"
      return 0
    fi
    sleep 2; waited=$((waited + 2))
  done
  log "$name did not open port $port within ${timeout}s (check $(log_file "$name"))"
  return 1
}

start_all() {
  mkdir -p "$LOG_DIR"
  check_java
  local i
  for i in "${!MODULE_NAMES[@]}"; do start_one "$i"; done
  log "waiting for services to become ready..."
  local failures=0
  for i in "${!MODULE_NAMES[@]}"; do wait_ready "$i" || failures=$((failures + 1)); done
  echo
  status_all
  (( failures == 0 )) || die "$failures service(s) did not come up cleanly"
}

stop_one() {
  local i="$1" name pid
  name="$(module_name "$i")"; pid="$(read_pid "$name")"
  if [[ -z "$pid" ]]; then log "$name not running (no pid file)"; return 0; fi
  if ! kill -0 "$pid" 2>/dev/null; then
    log "$name not running (stale pid $pid)"; rm -f "$(pid_file "$name")"; return 0
  fi
  log "stopping $name (pid $pid)"
  kill "$pid" 2>/dev/null || true
  local waited=0
  while kill -0 "$pid" 2>/dev/null && (( waited < 20 )); do sleep 1; waited=$((waited + 1)); done
  if kill -0 "$pid" 2>/dev/null; then
    log "$name did not stop gracefully, sending SIGKILL"
    kill -9 "$pid" 2>/dev/null || true
  fi
  rm -f "$(pid_file "$name")"
}

stop_all() {
  # stop gateway first, then backends
  local order=( 2 0 1 ) i
  for i in "${order[@]}"; do stop_one "$i"; done
}

status_all() {
  printf '%-18s %-8s %-10s %s\n' "MODULE" "PORT" "STATE" "PID / LOG"
  local i name port state pid
  for i in "${!MODULE_NAMES[@]}"; do
    name="$(module_name "$i")"; port="$(module_port "$i")"; pid="$(read_pid "$name")"
    if is_running "$name"; then state="RUNNING"; else state="STOPPED"; pid="-"; fi
    printf '%-18s %-8s %-10s %s  %s\n' "$name" "$port" "$state" "$pid" "$(log_file "$name")"
  done
}

# ------------------------------------------------------------
# main
# ------------------------------------------------------------
ACTION="start"
SKIP_BUILD=0
for arg in "$@"; do
  case "$arg" in
    start|stop|restart|status) ACTION="$arg" ;;
    --skip-build) SKIP_BUILD=1 ;;
    *) die "unknown argument: $arg" ;;
  esac
done

case "$ACTION" in
  status)  status_all ;;
  stop)    stop_all ;;
  restart) stop_all; load_env; [[ "$SKIP_BUILD" == "1" ]] || build; start_all ;;
  start)   load_env; [[ "$SKIP_BUILD" == "1" ]] || build; start_all ;;
esac
