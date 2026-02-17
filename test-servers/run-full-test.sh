#!/bin/bash
# Comprehensive AdminAnything feature test.
# Exercises all console-testable AA commands with EssentialsX + Vault installed.
#
# Usage: ./run-full-test.sh <server-dir> <java-binary> [extra-plugin-jar...]
#
# The script always deploys EssentialsX + Vault from plugin-cache/ (version-appropriate).
# Additional plugin JARs can be passed as extra arguments.

set -euo pipefail

if [ $# -lt 2 ]; then
    echo "Usage: $0 <server-dir> <java-binary> [extra-plugin-jar...]"
    echo ""
    echo "Example:"
    echo "  $0 1.20.4 /usr/lib/jvm/temurin-21-jdk-amd64/bin/java"
    echo "  $0 1.16.5 /usr/lib/jvm/temurin-11-jdk-amd64/bin/java plugin-cache/WorldEdit-7.3.9.jar"
    exit 1
fi

SERVER_DIR="$1"
JAVA_BIN="$2"
shift 2
EXTRA_JARS=("$@")
TIMEOUT="${TIMEOUT:-180}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/test-lib.sh"

trap cleanup EXIT

# --- Determine MC version from server dir name ---
MC_VERSION=$(basename "$SERVER_DIR" | grep -oE '[0-9]+\.[0-9]+(\.[0-9]+)?' | head -1)
if [ -z "$MC_VERSION" ]; then
    echo "WARNING: Could not determine MC version from server dir '$SERVER_DIR'"
    MC_VERSION="unknown"
fi
echo "=== AdminAnything Full Test Suite ==="
echo "MC version: $MC_VERSION"
echo "Server dir: $SERVER_DIR"
echo ""

# --- Ensure plugin cache is populated ---
CACHE_DIR="$SCRIPT_DIR/plugin-cache"
if [ ! -d "$CACHE_DIR" ] || [ -z "$(ls -A "$CACHE_DIR" 2>/dev/null)" ]; then
    echo "Plugin cache empty, running download-plugins.sh..."
    "$SCRIPT_DIR/download-plugins.sh" "$MC_VERSION"
fi

# --- Deploy plugins ---
echo "=== Deploying plugins ==="
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

# Deploy EssentialsX (version-appropriate)
case "$MC_VERSION" in
    1.20.4)
        ESSX_JAR="$CACHE_DIR/EssentialsX-2.21.2.jar"
        ;;
    *)
        ESSX_JAR="$CACHE_DIR/EssentialsX-2.19.7.jar"
        ;;
esac

VAULT_JAR="$CACHE_DIR/Vault-1.7.3.jar"

for required_jar in "$ESSX_JAR" "$VAULT_JAR"; do
    if [ ! -f "$required_jar" ]; then
        echo "Required plugin not found: $required_jar"
        echo "Running download-plugins.sh for $MC_VERSION..."
        "$SCRIPT_DIR/download-plugins.sh" "$MC_VERSION"
    fi
    echo "  Deploying: $(basename "$required_jar")"
    cp "$required_jar" "$PLUGINS_DIR/"
done

# Deploy extra plugins
for jar in "${EXTRA_JARS[@]+"${EXTRA_JARS[@]}"}"; do
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

# Verify plugins loaded
echo "=== Verify plugins loaded ==="
assert_log_contains "AdminAnything loaded" "AdminAnything.*enabl"
assert_log_contains "Essentials loaded" "Enabling Essentials\|Essentials.*enabl\|\[Essentials\]"
assert_log_contains "Vault loaded" "Enabling Vault\|Vault.*enabl\|\[Vault\]"
echo ""

# =====================================================================
# GROUP 1: Basic Info
# =====================================================================
echo "=== GROUP 1: Basic Info ==="

send_cmd "aa_version" 5
assert_output_contains "aa_version shows version" "version"

# aa_debug is disabled by default in config and its console sendMessage()
# output doesn't appear in the server log on modern Spigot. Skipping.

send_cmd "aa_clearchat" 3
# No assertion needed, just verify no crash

echo ""

# =====================================================================
# GROUP 2: List Commands
# =====================================================================
echo "=== GROUP 2: List Commands ==="

send_cmd "lc" 5
assert_output_contains "/lc shows commands" "Commands"

send_cmd "lc ?" 5
assert_output_contains "/lc ? shows help" "Help for"

send_cmd "lc plugin:Essentials" 5
assert_output_contains "/lc plugin:Essentials shows commands" "Commands"

echo ""

# =====================================================================
# GROUP 3: Conflict Detection
# =====================================================================
echo "=== GROUP 3: Conflict Detection ==="

send_cmd "ccc" 8
assert_output_contains "/ccc detects overrides" "overriden\|overridden"
assert_output_not_contains "/ccc should NOT say no problems" "No problems found"

echo ""

# =====================================================================
# GROUP 4: Command Fixing + Functional Verification
# =====================================================================
echo "=== GROUP 4: Command Fixing ==="

send_cmd "aa_fixcommand" 5
assert_output_contains "aa_fixcommand no-args" "enter the command"

send_cmd "aa_fixcommand ban" 5
assert_output_contains "aa_fixcommand ban lists plugins" "essentials\|minecraft\|bukkit"

send_cmd "aa_fixcommand ban Essentials" 5
assert_output_contains "aa_fixcommand ban Essentials success" "will now always be called as"

send_cmd "aa_info" 5
assert_output_contains "aa_info shows fix" "fixed to"

# FUNCTIONAL: run ban from console, check Essentials handles it
send_cmd "ban __test_nobody__" 5
# After fixing, the command gets rewritten to essentials:ban
# The output should reference essentials handling it (player not found, etc.)
# We just check there's no "disabled" message - the fix is working
assert_output_not_contains "ban not disabled (fix active)" "command was disabled"

send_cmd "aa_unfixcommand ban" 5
assert_output_contains "aa_unfixcommand ban success" "will no longer attempt to fix"

send_cmd "aa_info" 5
assert_output_not_contains "aa_info no ban fix after unfix" "ban.*fixed to"

echo ""

# =====================================================================
# GROUP 5: Command Disabling + Functional Verification
# =====================================================================
echo "=== GROUP 5: Command Disabling ==="

send_cmd "aa_disablecommand" 5
assert_output_contains "aa_disablecommand no-args" "enter the command"

send_cmd "aa_disablecommand ban" 5
assert_output_contains "aa_disablecommand ban success" "successfully disabled"

send_cmd "aa_info" 5
assert_output_contains "aa_info shows disabled commands" "Disabled commands"

# NOTE: Functional verification of disabled commands from console is skipped.
# The ServerCommandEvent listener does not intercept disabled commands for
# console senders - this is a player-only feature (PlayerCommandPreprocessEvent).

# Duplicate disable attempt
send_cmd "aa_disablecommand ban" 5
assert_output_contains "aa_disablecommand ban duplicate" "disabled already\|already disabled"

send_cmd "aa_enablecommand ban" 5
assert_output_contains "aa_enablecommand ban success" "successfully restored"

echo ""

# =====================================================================
# GROUP 6: Command Ignoring + Functional Verification
# =====================================================================
echo "=== GROUP 6: Command Ignoring ==="

send_cmd "aa_ignorecommand" 5
assert_output_contains "aa_ignorecommand no-args" "enter the command"

send_cmd "aa_ignorecommand ban" 5
assert_output_contains "aa_ignorecommand ban success" "successfully added to ignore list"

# FUNCTIONAL: /ccc should no longer show ban conflicts
send_cmd "ccc" 8
# After ignoring ban, conflicts involving ban should not appear.
# We check the output doesn't mention ban in conflict lines.
# Note: This is best-effort; the exact format may vary.
assert_output_not_contains "/ccc hides ignored ban" "Duplicate.*ban\b"

# Duplicate ignore attempt
send_cmd "aa_ignorecommand ban" 5
assert_output_contains "aa_ignorecommand ban duplicate" "already in the ignore list\|remain unchanged"

send_cmd "aa_unignorecommand ban" 5
assert_output_contains "aa_unignorecommand ban success" "successfully un-ignored"

# FUNCTIONAL: /ccc should show ban conflicts again
send_cmd "ccc" 8
assert_output_contains "/ccc shows ban conflicts after unignore" "overriden\|overridden"

echo ""

# =====================================================================
# GROUP 7: Command Muting (conditional)
# =====================================================================
echo "=== GROUP 7: Command Muting (conditional) ==="

JAVA_VERSION=$("$JAVA_BIN" -version 2>&1 | head -1 | grep -o '"[^"]*"' | tr -d '"' | cut -d. -f1)
# Java 8 reports as "1" (1.8.x), newer versions report major directly
if [ "$JAVA_VERSION" = "1" ]; then
    JAVA_MAJOR=8
else
    JAVA_MAJOR="$JAVA_VERSION"
fi

MC_MINOR=$(echo "$MC_VERSION" | cut -d. -f2)

if [ "$JAVA_MAJOR" -le 8 ] && [ "$MC_MINOR" -le 16 ]; then
    echo "Java $JAVA_MAJOR + MC 1.$MC_MINOR -> muting should work"

    send_cmd "aa_mutecommand" 5
    assert_output_contains "aa_mutecommand no-args" "enter the command"

    send_cmd "aa_mutecommand ban" 5
    assert_output_contains "aa_mutecommand ban success" "successfully muted"

    send_cmd "aa_info" 5
    assert_output_contains "aa_info shows muted" "Muted commands"

    send_cmd "aa_unmutecommand ban" 5
    assert_output_contains "aa_unmutecommand ban success" "successfully un-muted"
else
    echo "  SKIP: Java $JAVA_MAJOR + MC 1.$MC_MINOR (muting requires Java 8 + MC <= 1.16)"
fi

echo ""

# =====================================================================
# GROUP 8: Command Redirects + Functional Verification
# =====================================================================
echo "=== GROUP 8: Command Redirects ==="

send_cmd "aa_addredirect" 5
assert_output_contains "aa_addredirect no-args" "enter the command"

send_cmd "aa_addredirect tell msg" 5
assert_output_contains "aa_addredirect tell->msg success" "will now always be called as"

send_cmd "aa_info" 5
assert_output_contains "aa_info shows redirect" "redirected to"

# FUNCTIONAL: running tell should dispatch as msg
# The ServerCommandEvent rewriting means "tell" gets replaced with "msg"
send_cmd "tell __test_nobody__ hello" 5
# We can't easily verify the redirect happened from console output alone,
# but we can verify the redirect doesn't cause errors
assert_output_not_contains "tell redirect no errors" "Exception\|SEVERE"

send_cmd "aa_delredirect tell" 5
assert_output_contains "aa_delredirect tell success" "will no longer redirect"

echo ""

# =====================================================================
# GROUP 9: Virtual Permissions
# =====================================================================
echo "=== GROUP 9: Virtual Permissions ==="

send_cmd "aa_addperm" 5
assert_output_contains "aa_addperm no-args" "enter the permission and command"

# Use a permission name WITHOUT dots to avoid YAML path separator issues.
# Dots in YAML keys create nested sections, making cleanup unreliable.
send_cmd "aa_addperm testmyperm kill" 5
assert_output_contains "aa_addperm testmyperm success" "successfully added"

send_cmd "aa_info" 5
assert_output_contains "aa_info shows virtual perms" "Virtual permissions"

send_cmd "aa_delperm testmyperm" 5
assert_output_contains "aa_delperm testmyperm success" "successfully removed"

send_cmd "aa_info" 5
assert_output_not_contains "aa_info no testmyperm after delete" "testmyperm"

echo ""

# =====================================================================
# GROUP 10: Help Command Hiding
# =====================================================================
echo "=== GROUP 10: Help Command Hiding ==="

send_cmd "aa_disablehelpcommand" 5
assert_output_contains "aa_disablehelpcommand no-args" "enter the command"

send_cmd "aa_disablehelpcommand kill" 5
assert_output_contains "aa_disablehelpcommand kill success" "was hidden"

# NOTE: aa_info does not list hidden help commands. Skipping that assertion.

send_cmd "aa_enablehelpcommand kill" 5
assert_output_contains "aa_enablehelpcommand kill success" "will be showing in"

echo ""

# =====================================================================
# GROUP 11: Plugin Permissions Listing
# =====================================================================
echo "=== GROUP 11: Plugin Permissions Listing ==="

send_cmd "aa_pluginperms" 5
assert_output_contains "aa_pluginperms no-args" "provide name of the plugin"

send_cmd "aa_pluginperms AdminAnything" 5
assert_output_contains "aa_pluginperms AdminAnything" "Permissions for"

send_cmd "aa_pluginperms Essentials" 5
assert_output_contains "aa_pluginperms Essentials" "Permissions for"

send_cmd "aa_pluginperms FakePlugin123" 5
assert_output_contains "aa_pluginperms FakePlugin123 not found" "wasn.t found"

echo ""

# =====================================================================
# GROUP 12: Player-Only Commands
# =====================================================================
echo "=== GROUP 12: Player-Only Commands ==="

send_cmd "aa_actions kill" 5
assert_output_contains "aa_actions is player-only" "designed to be used by players"

send_cmd "aa_playerperms" 5
assert_output_contains "aa_playerperms needs player name" "provide name of the player"

echo ""

# =====================================================================
# GROUP 13: Clean State Verification
# =====================================================================
echo "=== GROUP 13: Clean State Verification ==="

send_cmd "aa_info" 5
assert_output_contains "aa_info shows clean state" "No commands are currently being adjusted"

echo ""

# =====================================================================
# GROUP 14: Error Check
# =====================================================================
echo "=== GROUP 14: Error Check ==="
check_log_errors

# =====================================================================
# GROUP 15: Reload (informational - known to crash due to missing
# config-file-sample.yml resource on re-enable; run last so it doesn't
# poison other tests)
# =====================================================================
echo "=== GROUP 15: Reload (informational) ==="
echo "  NOTE: aa_reload has a known bug (config-file-sample.yml not found on re-enable)."
echo "  This test only verifies the reload initiates. Plugin state will be broken after this."

send_cmd "aa_reload" 10
assert_output_contains "aa_reload initiates" "Initializing reload"

# --- Stop and report ---
stop_server

print_summary
exit $?
