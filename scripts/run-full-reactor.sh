#!/usr/bin/env bash
set -u

PROJECT_DIR="/c/javaProjects/leaderboard-project"
LOG_FILE="$PROJECT_DIR/target-phase7-full-reactor.log"
STATUS_FILE="$PROJECT_DIR/target-phase7-full-reactor.status"
PID_FILE="$PROJECT_DIR/target-phase7-full-reactor.maven.pid"
WRAPPER_PID_FILE="$PROJECT_DIR/target-phase7-full-reactor.wrapper.pid"

printf 'RUNNING\n' > "$STATUS_FILE"
printf '%s\n' "$$" > "$WRAPPER_PID_FILE"
printf 'wrapper_pid=%s start_time=%s\n' "$$" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" >> "$LOG_FILE"

cd "$PROJECT_DIR" || exit 1
mvn clean verify >> "$LOG_FILE" 2>&1 &
MAVEN_PID=$!
printf '%s\n' "$MAVEN_PID" > "$PID_FILE"

wait "$MAVEN_PID"
EXIT_CODE=$?

if [ "$EXIT_CODE" -eq 0 ]; then
  printf 'SUCCESS\n' > "$STATUS_FILE"
else
  printf 'FAILURE:%s\n' "$EXIT_CODE" > "$STATUS_FILE"
fi

printf 'maven_pid=%s exit_code=%s end_time=%s\n' "$MAVEN_PID" "$EXIT_CODE" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" >> "$LOG_FILE"
exit "$EXIT_CODE"

