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
#   4. Runs run-full-test.sh
#   5. Kills lingering servers
#   6. Prints aggregate summary

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/.."

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
        # 1.17.x - 1.19.x: Java 17
        primary=17; fallback=17
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
echo ""

# --- Run tests for each version ---
TOTAL_PASS=0
TOTAL_FAIL=0
RESULTS=()

for ver in "${VERSIONS[@]}"; do
    echo ""
    echo "####################################################"
    echo "# MC $ver"
    echo "####################################################"
    echo ""

    SERVER_DIR="$SCRIPT_DIR/$ver"

    # Determine Java binary
    JAVA_BIN=$(java_for_mc "$ver") || {
        echo "SKIP: $ver (no suitable Java found)"
        RESULTS+=("$ver: SKIPPED (no Java)")
        continue
    }
    echo "Using Java: $JAVA_BIN"

    # Setup server if needed
    "$SCRIPT_DIR/setup-server.sh" "$ver" "$JAVA_BIN" || {
        echo "SKIP: $ver (setup failed)"
        RESULTS+=("$ver: SKIPPED (setup failed)")
        continue
    }

    # Download plugins
    "$SCRIPT_DIR/download-plugins.sh" "$ver" || {
        echo "SKIP: $ver (plugin download failed)"
        RESULTS+=("$ver: SKIPPED (plugin download failed)")
        continue
    }

    # Deploy AdminAnything JAR
    mkdir -p "$SERVER_DIR/plugins"
    cp "$AA_JAR" "$SERVER_DIR/plugins/adminAnything.jar"

    # Run full test
    set +e
    "$SCRIPT_DIR/run-full-test.sh" "$SERVER_DIR" "$JAVA_BIN"
    TEST_EXIT=$?
    set -e

    # Kill any lingering server
    fuser -k 25565/tcp 2>/dev/null || true
    sleep 1

    if [ $TEST_EXIT -eq 0 ]; then
        RESULTS+=("$ver: PASSED")
    else
        RESULTS+=("$ver: FAILED (exit $TEST_EXIT)")
        TOTAL_FAIL=$((TOTAL_FAIL + 1))
    fi
    TOTAL_PASS=$((TOTAL_PASS + 1))

    echo ""
done

# --- Aggregate summary ---
echo ""
echo "####################################################"
echo "# AGGREGATE TEST RESULTS"
echo "####################################################"
for r in "${RESULTS[@]}"; do
    echo "  $r"
done
echo ""
echo "Versions tested: $TOTAL_PASS  Failed: $TOTAL_FAIL"
echo "####################################################"

if [ $TOTAL_FAIL -gt 0 ]; then
    exit 1
fi
exit 0
