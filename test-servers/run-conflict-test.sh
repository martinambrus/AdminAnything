#!/bin/bash
# Test AdminAnything command conflict detection with real plugins.
# Usage: ./run-conflict-test.sh <server-dir> <java-binary> <plugin-jar>...
#
# Example:
#   ./run-conflict-test.sh 1.20.4 /usr/lib/jvm/temurin-21-jdk-amd64/bin/java \
#       plugin-cache/EssentialsX-2.21.2.jar plugin-cache/Vault-1.7.3.jar
#
# The script copies specified plugin JARs into the server's plugins/ dir,
# starts the server, waits for AA activation, then runs the conflict
# detection -> fix -> unfix test sequence with assertions.

set -euo pipefail

if [ $# -lt 3 ]; then
    echo "Usage: $0 <server-dir> <java-binary> <plugin-jar>..."
    echo ""
    echo "Example:"
    echo "  $0 1.20.4 /usr/lib/jvm/temurin-21-jdk-amd64/bin/java plugin-cache/EssentialsX-2.21.2.jar plugin-cache/Vault-1.7.3.jar"
    exit 1
fi

SERVER_DIR="$1"
JAVA_BIN="$2"
shift 2
PLUGIN_JARS=("$@")
TIMEOUT="${TIMEOUT:-180}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/test-lib.sh"

trap cleanup EXIT

# --- Deploy plugins ---
echo "=== Deploying plugins to $SERVER_DIR/plugins/ ==="
PLUGINS_DIR="$SERVER_DIR/plugins"
mkdir -p "$PLUGINS_DIR"

# Remove previously deployed test plugins (not AdminAnything JAR)
for jar in "$PLUGINS_DIR"/*.jar; do
    [ -f "$jar" ] || continue
    jarname="$(basename "$jar")"
    if [ "$jarname" != "adminAnything.jar" ]; then
        echo "  Removing old: $jarname"
        rm -f "$jar"
    fi
done

# Copy in the requested plugin JARs
for jar in "${PLUGIN_JARS[@]}"; do
    if [ ! -f "$jar" ]; then
        # Try relative to SCRIPT_DIR
        if [ -f "$SCRIPT_DIR/$jar" ]; then
            jar="$SCRIPT_DIR/$jar"
        else
            echo "ERROR: Plugin JAR not found: $jar"
            exit 1
        fi
    fi
    echo "  Deploying: $(basename "$jar")"
    cp "$jar" "$PLUGINS_DIR/"
done

# Ensure AdminAnything JAR is present
if [ ! -f "$PLUGINS_DIR/adminAnything.jar" ]; then
    AA_JAR="$SCRIPT_DIR/../build/libs/adminAnything.jar"
    if [ -f "$AA_JAR" ]; then
        echo "  Deploying: adminAnything.jar (from build/libs/)"
        cp "$AA_JAR" "$PLUGINS_DIR/"
    else
        echo "ERROR: adminAnything.jar not found in $PLUGINS_DIR or build/libs/"
        exit 1
    fi
fi

echo ""
echo "Plugins in $PLUGINS_DIR:"
ls -1 "$PLUGINS_DIR"/*.jar 2>/dev/null | while read -r f; do echo "  $(basename "$f")"; done
echo ""

# --- Start server ---
start_server
wait_for_aa

# --- Verify plugins loaded ---
echo "=== TEST: Verify plugins loaded ==="

assert_log_contains "AdminAnything loaded" "AdminAnything.*enabl"

# Check for each deployed plugin in the log.
# JAR filenames may differ from internal plugin names (e.g., EssentialsX-2.21.2.jar
# registers as "Essentials"), so we try multiple patterns.
for jar in "${PLUGIN_JARS[@]}"; do
    jarname="$(basename "$jar" .jar)"
    # Strip version suffix (with or without hyphen) and trailing X (EssentialsX -> Essentials)
    plugin_name=$(echo "$jarname" | sed 's/-\?[0-9][0-9._]*$//; s/X$//')
    assert_log_contains "$plugin_name loaded" "Enabling $plugin_name\|$plugin_name.*enabl\|\[$plugin_name\]"
done

# --- Test 1: Check Command Conflicts (ccc) ---
echo ""
echo "=== TEST: Command conflict detection (/ccc) ==="
send_cmd "ccc" 8

# /ccc produces output (page header)
assert_output_contains "/ccc produces output" "Command.*alias conflicts\|conflicts"

# /ccc should detect server command overrides (e.g., Essentials overriding /ban)
assert_output_contains "/ccc detects overrides" "commands are being overriden\|overridden via"
assert_output_not_contains "/ccc should NOT say no problems" "No problems found"

# --- Test 2: Fix a command ---
echo ""
echo "=== TEST: Fix command - list plugins for /ban ==="
send_cmd "aa_fixcommand ban" 5

# The output should list plugins that have a /ban command.
# With EssentialsX, there should be at least "essentials" and "minecraft" versions.
assert_output_contains "/ban shows multiple plugins" "essentials\|minecraft\|bukkit"

echo ""
echo "=== TEST: Fix /ban to Essentials ==="
send_cmd "aa_fixcommand ban Essentials" 5

assert_output_contains "/ban fixed successfully" "will now always be called as"

# --- Test 3: Verify fix shows in /aa_info ---
echo ""
echo "=== TEST: Verify fix in /aa_info ==="
send_cmd "aa_info" 5

assert_output_contains "/aa_info shows fix" "fixed to\|hard-wired\|Command fixes"

# --- Test 4: Unfix the command ---
echo ""
echo "=== TEST: Unfix /ban ==="
send_cmd "aa_unfixcommand ban" 5

assert_output_contains "/ban unfixed" "will no longer\|no longer attempt to fix\|restored"

# --- Test 5: Verify unfix in /aa_info ---
echo ""
echo "=== TEST: Verify unfix in /aa_info ==="
send_cmd "aa_info" 5

# After unfixing, the info should not show "ban" as fixed
# (unless other fixes still exist, but since we only fixed ban, it should be gone)
assert_output_not_contains "/aa_info no longer shows ban fix" "ban.*fixed to"

# --- Test 6: List commands ---
echo ""
echo "=== TEST: List commands (/lc) ==="
send_cmd "lc" 5

assert_output_contains "/lc produces output" "commands\|page\|---"

# --- Error check ---
check_log_errors

# --- Stop and report ---
stop_server

echo ""
echo "=== FULL SERVER LOG ==="
cat "$LOG"

print_summary
exit $?
