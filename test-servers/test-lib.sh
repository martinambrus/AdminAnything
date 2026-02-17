#!/bin/bash
# Shared test functions for AdminAnything server tests.
# Source this file from test scripts: source "$(dirname "$0")/test-lib.sh"

# Globals expected to be set by the caller:
#   SERVER_DIR  - path to the server directory
#   JAVA_BIN    - path to the java binary
#   TIMEOUT     - max seconds to wait for server start (default 120)
#   JVM_HEAP    - max heap size (default 1G)
#   SERVER_PORT - server port (default: random 30000-39999)
# Globals set by this library:
#   FIFO        - path to the stdin FIFO
#   LOG         - path to the server log
#   SERVER_PID  - PID of the server process

TIMEOUT="${TIMEOUT:-120}"
JVM_HEAP="${JVM_HEAP:-1G}"
SERVER_PORT="${SERVER_PORT:-$((RANDOM % 10000 + 30000))}"
FIFO="$SERVER_DIR/stdin.fifo"
LOG="$SERVER_DIR/server.log"
SERVER_PID=""

# Assertion counters
_PASS_COUNT=0
_FAIL_COUNT=0
_ASSERTIONS=()

kill_port() {
    local port="${1:-25565}"
    # Try graceful kill first, then SIGKILL
    fuser -k "$port/tcp" >/dev/null 2>&1 || true
    sleep 2
    # If still bound, force kill
    if fuser "$port/tcp" >/dev/null 2>&1; then
        fuser -k -9 "$port/tcp" >/dev/null 2>&1 || true
        sleep 2
    fi
    # Wait up to 5 more seconds for the port to actually free
    local waited=0
    while [ $waited -lt 5 ] && fuser "$port/tcp" >/dev/null 2>&1; do
        sleep 1
        waited=$((waited + 1))
    done
}

cleanup() {
    if [ -n "${SERVER_PID:-}" ] && kill -0 "$SERVER_PID" 2>/dev/null; then
        echo "stop" > "$FIFO" 2>/dev/null || true
        sleep 3
        kill "$SERVER_PID" 2>/dev/null || true
        sleep 1
        # Force kill if still running
        if kill -0 "$SERVER_PID" 2>/dev/null; then
            kill -9 "$SERVER_PID" 2>/dev/null || true
        fi
        wait "$SERVER_PID" 2>/dev/null || true
    fi
    rm -f "$FIFO"
    # Kill anything still on the server port
    kill_port "$SERVER_PORT"
}

start_server() {
    # Kill anything on our port from a previous run
    kill_port "$SERVER_PORT"

    # Clean previous run state
    rm -f "$FIFO" "$LOG"
    rm -rf "$SERVER_DIR/world" "$SERVER_DIR/world_nether" "$SERVER_DIR/world_the_end"
    rm -rf "$SERVER_DIR/plugins/AdminAnything"
    mkfifo "$FIFO"

    # Set the server port in server.properties
    if [ -f "$SERVER_DIR/server.properties" ]; then
        sed -i "s/^server-port=.*/server-port=$SERVER_PORT/" "$SERVER_DIR/server.properties"
    fi

    echo "=== Starting server in $SERVER_DIR with $JAVA_BIN (port $SERVER_PORT) ==="
    "$JAVA_BIN" -version 2>&1 | head -1

    # Start server with stdin from FIFO, stdout/stderr to log
    (tail -f "$FIFO" 2>/dev/null || true) | \
        (cd "$SERVER_DIR" && "$JAVA_BIN" -Xms256M -Xmx${JVM_HEAP} -jar server.jar nogui) \
        > "$LOG" 2>&1 &
    SERVER_PID=$!
    echo "Server PID: $SERVER_PID"

    # Wait for server to finish loading
    echo "Waiting for server to start (timeout: ${TIMEOUT}s)..."
    local elapsed=0
    local started=false
    while [ $elapsed -lt $TIMEOUT ]; do
        if ! kill -0 "$SERVER_PID" 2>/dev/null; then
            echo "=== SERVER CRASHED DURING STARTUP ==="
            cat "$LOG"
            return 1
        fi
        if grep -q "Done\|DONE\|done.*help" "$LOG" 2>/dev/null; then
            started=true
            break
        fi
        sleep 2
        elapsed=$((elapsed + 2))
    done

    if [ "$started" != "true" ]; then
        echo "=== SERVER FAILED TO START WITHIN ${TIMEOUT}s ==="
        cat "$LOG"
        echo "stop" > "$FIFO" 2>/dev/null || true
        return 1
    fi

    echo "=== Server started successfully ==="
    echo ""
}

wait_for_aa() {
    echo "Waiting for AdminAnything activation..."
    local elapsed=0
    local max_wait=30
    while [ $elapsed -lt $max_wait ]; do
        if grep -q "Now fully activated" "$LOG" 2>/dev/null; then
            echo "AdminAnything activated after ~${elapsed}s"
            return 0
        fi
        sleep 2
        elapsed=$((elapsed + 2))
    done
    # Fallback: even if we don't see the exact message, wait 15s after Done
    echo "Did not see activation message, waiting 15s as fallback..."
    sleep 15
}

send_cmd() {
    local cmd="$1"
    local wait_secs="${2:-5}"
    local before_lines
    before_lines=$(wc -l < "$LOG")

    echo "--- Running: $cmd ---"
    echo "$cmd" > "$FIFO"
    sleep "$wait_secs"

    # Return new output since command was sent
    local new_output
    new_output=$(tail -n +"$((before_lines + 1))" "$LOG")
    if [ -n "$new_output" ]; then
        echo "$new_output"
    else
        echo "(no output)"
    fi
    echo ""
    # Store the last command output for assertions
    _LAST_OUTPUT="$new_output"
}

stop_server() {
    echo "=== Stopping server ==="
    echo "stop" > "$FIFO"
    sleep 5
}

# Assertion helpers
assert_log_contains() {
    local description="$1"
    local pattern="$2"
    if grep -qi "$pattern" "$LOG" 2>/dev/null; then
        echo "  PASS: $description"
        _PASS_COUNT=$((_PASS_COUNT + 1))
        _ASSERTIONS+=("PASS: $description")
    else
        echo "  FAIL: $description (pattern '$pattern' not found in log)"
        _FAIL_COUNT=$((_FAIL_COUNT + 1))
        _ASSERTIONS+=("FAIL: $description")
    fi
}

assert_log_not_contains() {
    local description="$1"
    local pattern="$2"
    if grep -qi "$pattern" "$LOG" 2>/dev/null; then
        echo "  FAIL: $description (pattern '$pattern' WAS found in log)"
        _FAIL_COUNT=$((_FAIL_COUNT + 1))
        _ASSERTIONS+=("FAIL: $description")
    else
        echo "  PASS: $description"
        _PASS_COUNT=$((_PASS_COUNT + 1))
        _ASSERTIONS+=("PASS: $description")
    fi
}

assert_output_contains() {
    local description="$1"
    local pattern="$2"
    if echo "$_LAST_OUTPUT" | grep -qi "$pattern" 2>/dev/null; then
        echo "  PASS: $description"
        _PASS_COUNT=$((_PASS_COUNT + 1))
        _ASSERTIONS+=("PASS: $description")
    else
        echo "  FAIL: $description (pattern '$pattern' not in last command output)"
        _FAIL_COUNT=$((_FAIL_COUNT + 1))
        _ASSERTIONS+=("FAIL: $description")
    fi
}

assert_output_not_contains() {
    local description="$1"
    local pattern="$2"
    if echo "$_LAST_OUTPUT" | grep -qi "$pattern" 2>/dev/null; then
        echo "  FAIL: $description (pattern '$pattern' WAS in last command output)"
        _FAIL_COUNT=$((_FAIL_COUNT + 1))
        _ASSERTIONS+=("FAIL: $description")
    else
        echo "  PASS: $description"
        _PASS_COUNT=$((_PASS_COUNT + 1))
        _ASSERTIONS+=("PASS: $description")
    fi
}

assert_output_match_count() {
    local description="$1"
    local pattern="$2"
    local expected_count="$3"
    local actual_count
    actual_count=$(echo "$_LAST_OUTPUT" | grep -ci "$pattern" 2>/dev/null || echo "0")
    if [ "$actual_count" -ge "$expected_count" ]; then
        echo "  PASS: $description (found $actual_count, expected >= $expected_count)"
        _PASS_COUNT=$((_PASS_COUNT + 1))
        _ASSERTIONS+=("PASS: $description")
    else
        echo "  FAIL: $description (found $actual_count, expected >= $expected_count)"
        _FAIL_COUNT=$((_FAIL_COUNT + 1))
        _ASSERTIONS+=("FAIL: $description")
    fi
}

# Check for error lines in the full log
check_log_errors() {
    echo ""
    echo "=== Checking for errors in server log ==="
    local errors
    errors=$(grep -iE "SEVERE|NullPointerException|Exception.*AdminAnything|Error.*AdminAnything" "$LOG" 2>/dev/null | grep -v "at org.bukkit" | head -20 || true)
    if [ -n "$errors" ]; then
        echo "  FAIL: Found error lines in log:"
        echo "$errors"
        _FAIL_COUNT=$((_FAIL_COUNT + 1))
        _ASSERTIONS+=("FAIL: No errors in server log")
    else
        echo "  PASS: No AA-related errors in server log"
        _PASS_COUNT=$((_PASS_COUNT + 1))
        _ASSERTIONS+=("PASS: No AA-related errors in server log")
    fi
}

print_summary() {
    echo ""
    echo "========================================="
    echo "  TEST SUMMARY"
    echo "========================================="
    for a in "${_ASSERTIONS[@]}"; do
        echo "  $a"
    done
    echo ""
    echo "  Total: $((_PASS_COUNT + _FAIL_COUNT))  Passed: $_PASS_COUNT  Failed: $_FAIL_COUNT"
    echo "========================================="
    if [ $_FAIL_COUNT -gt 0 ]; then
        return 1
    fi
    return 0
}
