#!/bin/bash
# Orchestrator: build AdminAnything, then run the full test suite for each MC version.
#
# Usage: ./run-all-tests.sh [version...]
#
# If no versions specified, defaults to all versions with server.jar present.
# Builds the AdminAnything JAR once, then for each version:
#   1. Runs setup-server.sh (BuildTools if needed)
#   2. Runs download-plugins.sh
#   3. Deploys adminAnything.jar
#   4. Runs run-full-test.sh (in parallel, up to MAX_PARALLEL at a time)
#   5. Collects results and prints aggregate summary
#
# Environment variables:
#   MAX_PARALLEL - max concurrent test servers (default: 4)
#   BASE_PORT    - starting port for test servers (default: 30001, avoids 25565)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/.."
MAX_PARALLEL="${MAX_PARALLEL:-4}"
BASE_PORT="${BASE_PORT:-30001}"
LOGS_DIR="$SCRIPT_DIR/test-logs"

mkdir -p "$LOGS_DIR"

# --- Java auto-detection ---
find_java() {
    local version="$1"
    local paths=()

    case "$version" in
        8)  paths=(
                /usr/lib/jvm/temurin-8-jdk-amd64/bin/java
                /usr/lib/jvm/java-8-openjdk-amd64/bin/java
                /usr/lib/jvm/adoptopenjdk-8-hotspot-amd64/bin/java
            ) ;;
        11) paths=(
                /usr/lib/jvm/temurin-11-jdk-amd64/bin/java
                /usr/lib/jvm/java-11-openjdk-amd64/bin/java
                /usr/lib/jvm/adoptopenjdk-11-hotspot-amd64/bin/java
            ) ;;
        17) paths=(
                /usr/lib/jvm/temurin-17-jdk-amd64/bin/java
                /usr/lib/jvm/java-17-openjdk-amd64/bin/java
                "$SCRIPT_DIR/buildtools/jdk17/bin/java"
            ) ;;
        21) paths=(
                /usr/lib/jvm/temurin-21-jdk-amd64/bin/java
                /usr/lib/jvm/java-21-openjdk-amd64/bin/java
            ) ;;
    esac

    for p in "${paths[@]}"; do
        if [ -x "$p" ]; then
            echo "$p"
            return 0
        fi
    done
    return 1
}

# Map MC version to required Java version, with fallback
java_for_mc() {
    local mc_version="$1"
    local mc_minor
    mc_minor=$(echo "$mc_version" | cut -d. -f2)

    local primary fallback

    if [ "$mc_minor" -le 11 ]; then
        # 1.7.10 - 1.11.x: Java 8, fallback 11
        primary=8; fallback=11
    elif [ "$mc_minor" -le 16 ]; then
        # 1.12.x - 1.16.x: Java 11, fallback 8
        primary=11; fallback=8
    elif [ "$mc_minor" -le 19 ]; then
        # 1.17.x - 1.19.x: Java 17, fallback 21
        primary=17; fallback=21
    else
        # 1.20+: Java 21
        primary=21; fallback=21
    fi

    local java_bin
    java_bin=$(find_java "$primary" 2>/dev/null) || java_bin=$(find_java "$fallback" 2>/dev/null) || {
        echo "ERROR: No Java $primary (or $fallback) found for MC $mc_version" >&2
        return 1
    }
    echo "$java_bin"
}

# --- Build AdminAnything JAR ---
echo "=== Building AdminAnything JAR ==="
(cd "$REPO_ROOT" && gradle shadowJar --quiet)
AA_JAR="$REPO_ROOT/build/libs/adminAnything.jar"
if [ ! -f "$AA_JAR" ]; then
    echo "ERROR: Build did not produce $AA_JAR"
    exit 1
fi
echo "Built: $AA_JAR ($(du -h "$AA_JAR" | cut -f1))"
echo ""

# --- Determine versions to test ---
VERSIONS=()
if [ $# -gt 0 ]; then
    VERSIONS=("$@")
else
    # Auto-detect: find directories with server.jar
    for dir in "$SCRIPT_DIR"/*/; do
        [ -d "$dir" ] || continue
        if [ -f "$dir/server.jar" ]; then
            ver=$(basename "$dir")
            VERSIONS+=("$ver")
        fi
    done

    if [ ${#VERSIONS[@]} -eq 0 ]; then
        echo "No server versions found. Specify versions on the command line or run setup-server.sh first."
        echo "Usage: $0 [version...]"
        echo "Example: $0 1.20.4 1.16.5"
        exit 1
    fi
fi

echo "=== Test matrix: ${VERSIONS[*]} ==="
echo "=== Parallel: up to $MAX_PARALLEL concurrent servers, ports starting at $BASE_PORT ==="
echo ""

# --- Prepare all versions (sequential, fast) ---
# Build arrays of versions that are ready to test, with their Java binary
READY_VERSIONS=()
READY_JAVA=()
SKIPPED=()

for ver in "${VERSIONS[@]}"; do
    echo "--- Preparing $ver ---"

    SERVER_DIR="$SCRIPT_DIR/$ver"

    # Determine Java binary
    JAVA_BIN=""
    if [[ "$ver" =~ -java([0-9]+)$ ]]; then
        explicit_java="${BASH_REMATCH[1]}"
        JAVA_BIN=$(find_java "$explicit_java" 2>/dev/null) || {
            echo "  SKIP: $ver (no Java $explicit_java found)"
            SKIPPED+=("$ver: SKIPPED (no Java $explicit_java)")
            continue
        }
    else
        JAVA_BIN=$(java_for_mc "$ver" 2>/dev/null) || {
            echo "  SKIP: $ver (no suitable Java found)"
            SKIPPED+=("$ver: SKIPPED (no Java)")
            continue
        }
    fi

    # Setup server if needed
    "$SCRIPT_DIR/setup-server.sh" "$ver" "$JAVA_BIN" || {
        echo "  SKIP: $ver (setup failed)"
        SKIPPED+=("$ver: SKIPPED (setup failed)")
        continue
    }

    # Download plugins
    "$SCRIPT_DIR/download-plugins.sh" "$ver" || {
        echo "  SKIP: $ver (plugin download failed)"
        SKIPPED+=("$ver: SKIPPED (plugin download failed)")
        continue
    }

    # Deploy AdminAnything JAR
    mkdir -p "$SERVER_DIR/plugins"
    cp "$AA_JAR" "$SERVER_DIR/plugins/adminAnything.jar"

    echo "  Ready: $ver (Java: $JAVA_BIN)"
    READY_VERSIONS+=("$ver")
    READY_JAVA+=("$JAVA_BIN")
done

echo ""
echo "=== Preparation complete: ${#READY_VERSIONS[@]} ready, ${#SKIPPED[@]} skipped ==="
echo ""

if [ ${#READY_VERSIONS[@]} -eq 0 ]; then
    echo "No versions ready to test."
    for s in "${SKIPPED[@]}"; do echo "  $s"; done
    exit 1
fi

# --- Run tests in parallel batches ---
RESULTS=()
TOTAL_RUN=0
TOTAL_FAIL=0

# Arrays to track running jobs
declare -a JOB_PIDS=()
declare -a JOB_VERS=()
declare -a JOB_LOGS=()

# Launch a test for a version at a given port
launch_test() {
    local idx="$1"
    local port="$2"
    local ver="${READY_VERSIONS[$idx]}"
    local java="${READY_JAVA[$idx]}"
    local server_dir="$SCRIPT_DIR/$ver"
    local logfile="$LOGS_DIR/${ver}.log"

    echo "  START: $ver (port $port, log: test-logs/${ver}.log)"

    SERVER_PORT="$port" "$SCRIPT_DIR/run-full-test.sh" "$server_dir" "$java" \
        > "$logfile" 2>&1 &
    local pid=$!

    JOB_PIDS+=("$pid")
    JOB_VERS+=("$ver")
    JOB_LOGS+=("$logfile")
}

# Wait for one job to finish, collect result, return its slot index
wait_for_any_job() {
    while true; do
        for i in "${!JOB_PIDS[@]}"; do
            local pid="${JOB_PIDS[$i]}"
            if ! kill -0 "$pid" 2>/dev/null; then
                # Job finished, get exit code
                wait "$pid" 2>/dev/null
                local exit_code=$?
                local ver="${JOB_VERS[$i]}"
                local logfile="${JOB_LOGS[$i]}"

                TOTAL_RUN=$((TOTAL_RUN + 1))

                # Extract pass/fail counts from log
                local summary
                summary=$(grep -E "Total:.*Passed:.*Failed:" "$logfile" 2>/dev/null | tail -1 || echo "")

                if [ $exit_code -eq 0 ]; then
                    echo "  DONE: $ver -> PASSED ($summary)"
                    RESULTS+=("$ver: PASSED ($summary)")
                else
                    echo "  DONE: $ver -> FAILED (exit $exit_code) ($summary)"
                    RESULTS+=("$ver: FAILED (exit $exit_code) ($summary)")
                    TOTAL_FAIL=$((TOTAL_FAIL + 1))
                fi

                # Remove from arrays
                unset 'JOB_PIDS[i]'
                unset 'JOB_VERS[i]'
                unset 'JOB_LOGS[i]'
                # Re-index
                JOB_PIDS=("${JOB_PIDS[@]}")
                JOB_VERS=("${JOB_VERS[@]}")
                JOB_LOGS=("${JOB_LOGS[@]}")
                return 0
            fi
        done
        sleep 5
    done
}

echo "=== Running tests (max $MAX_PARALLEL parallel) ==="

port_counter=0
next_idx=0
total="${#READY_VERSIONS[@]}"

while [ $next_idx -lt $total ] || [ ${#JOB_PIDS[@]} -gt 0 ]; do
    # Launch jobs up to MAX_PARALLEL
    while [ $next_idx -lt $total ] && [ ${#JOB_PIDS[@]} -lt $MAX_PARALLEL ]; do
        local_port=$((BASE_PORT + port_counter))
        launch_test "$next_idx" "$local_port"
        port_counter=$((port_counter + 1))
        next_idx=$((next_idx + 1))
    done

    # If we have running jobs, wait for one to finish
    if [ ${#JOB_PIDS[@]} -gt 0 ]; then
        wait_for_any_job
    fi
done

echo ""

# --- Aggregate summary ---
echo "####################################################"
echo "# AGGREGATE TEST RESULTS"
echo "####################################################"
for s in "${SKIPPED[@]+"${SKIPPED[@]}"}"; do
    echo "  $s"
done
for r in "${RESULTS[@]}"; do
    echo "  $r"
done
echo ""
echo "Tested: $TOTAL_RUN  Passed: $((TOTAL_RUN - TOTAL_FAIL))  Failed: $TOTAL_FAIL  Skipped: ${#SKIPPED[@]}"
echo "Per-version logs: $LOGS_DIR/"
echo "####################################################"

if [ $TOTAL_FAIL -gt 0 ]; then
    exit 1
fi
exit 0
