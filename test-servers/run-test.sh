#!/bin/bash
# Test AdminAnything plugin on a Minecraft server (basic load/activate test)
# Usage: ./run-test.sh <server-dir> <java-binary> [timeout_seconds]

set -euo pipefail

SERVER_DIR="$1"
JAVA_BIN="$2"
TIMEOUT="${3:-120}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/test-lib.sh"

trap cleanup EXIT

start_server

wait_for_aa

# Test 1: Check if plugin loaded
echo "=== TEST: Check plugin loaded ==="
grep -i "adminanything\|AdminAnything" "$LOG" | head -10
echo ""

# Test 2: Run /aa_version
send_cmd "aa_version" 5

# Test 3: Run /ccc (checkcommandconflicts)
send_cmd "ccc" 5

# Test 4: Run /lc (listcommands) - first page
send_cmd "lc" 5

# Test 5: Run /aa_info
send_cmd "aa_info" 5

# Stop server
stop_server

echo ""
echo "=== FULL SERVER LOG ==="
cat "$LOG"

echo ""
echo "=== TEST COMPLETE ==="
