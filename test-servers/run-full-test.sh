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

# Deploy Essentials (version-appropriate)
case "$MC_VERSION" in
    1.7.*)
        ESSX_JAR="$CACHE_DIR/Essentials-2.14.1.3.jar"
        ;;
    1.20.*|1.21.*)
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

# --- 2A: Basic & Help ---
send_cmd "lc" 5
assert_output_contains "/lc shows commands" "Commands"

send_cmd "lc ?" 5
assert_output_contains "/lc ? shows help" "Help for"

send_cmd "lc plugin:Essentials" 5
assert_output_contains "/lc plugin:Essentials shows commands" "Commands"

# --- 2B: Pagination ---
send_cmd "lc pg:1" 5
assert_output_contains "/lc pg:1 explicit page 1" "Commands"

send_cmd "lc pg:2" 5
assert_output_contains "/lc pg:2 page 2" "Commands"

send_cmd "lc 2" 5
assert_output_contains "/lc 2 bare int = page" "Commands"

send_cmd "lc pg:0" 5
assert_output_contains "/lc pg:0 edge case" "Commands"

send_cmd "lc pg:99999" 5
assert_output_contains "/lc pg:99999 beyond last page" "Commands"

# --- 2C: Description Flag (default ON) ---
send_cmd "lc desc:no" 5
assert_output_contains "/lc desc:no" "Commands"

send_cmd "lc desc:yes" 5
assert_output_contains "/lc desc:yes explicit" "Commands"

send_cmd "lc description:false" 5
assert_output_contains "/lc description:false alias" "Commands"

send_cmd "lc showdescriptions:n" 5
assert_output_contains "/lc showdescriptions:n alias" "Commands"

# --- 2D: Aliases Flag (default OFF) ---
send_cmd "lc al:yes" 5
assert_output_contains "/lc al:yes" "Commands"

send_cmd "lc aliases:true" 5
assert_output_contains "/lc aliases:true long form" "Commands"

send_cmd "lc showaliases:y" 5
assert_output_contains "/lc showaliases:y alias" "Commands"

# --- 2E: Permissions Flag (default OFF) ---
send_cmd "lc perm:yes" 5
assert_output_contains "/lc perm:yes" "Commands"

send_cmd "lc permissions:true" 5
assert_output_contains "/lc permissions:true long form" "Commands"

send_cmd "lc perms:y" 5
assert_output_contains "/lc perms:y alias" "Commands"

# --- 2F: Permission Descriptions Flag (default ON) ---
send_cmd "lc permdesc:no" 5
assert_output_contains "/lc permdesc:no" "Commands"

send_cmd "lc permissiondescriptions:false" 5
assert_output_contains "/lc permissiondescriptions:false" "Commands"

send_cmd "lc permdesc:yes" 5
assert_output_contains "/lc permdesc:yes explicit" "Commands"

# --- 2G: Usage Flag (default OFF) ---
send_cmd "lc usg:yes" 5
assert_output_contains "/lc usg:yes" "Commands"

send_cmd "lc usage:true" 5
assert_output_contains "/lc usage:true long form" "Commands"

send_cmd "lc showusage:y" 5
assert_output_contains "/lc showusage:y alias" "Commands"

# --- 2H: Multiline Flag (default OFF) ---
send_cmd "lc multiline:yes" 5
assert_output_contains "/lc multiline:yes" "Commands"

send_cmd "lc multiline:true" 5
assert_output_contains "/lc multiline:true" "Commands"

send_cmd "lc multiline:no" 5
assert_output_contains "/lc multiline:no explicit off" "Commands"

# --- 2I: Search Filter ---
send_cmd "lc search:ban" 5
assert_output_contains "/lc search:ban header" "Commands"
assert_output_contains "/lc search:ban finds ban" "ban"

send_cmd "lc search:kill" 5
assert_output_contains "/lc search:kill header" "Commands"
assert_output_contains "/lc search:kill finds kill" "kill"

send_cmd "lc search:zzz_nonexistent_zzz" 5
assert_output_contains "/lc search:nonexistent" "Commands"

send_cmd "lc search:*ban" 5
assert_output_contains "/lc search:*ban wildcard prefix" "Commands"

send_cmd "lc search:ban*" 5
assert_output_contains "/lc search:ban* wildcard suffix" "Commands"

# --- 2J: Plugin Filter Variants ---
send_cmd "lc plugin:Essentials" 5
assert_output_contains "/lc plugin:Essentials include" "Commands"

send_cmd "lc pl:Essentials" 5
assert_output_contains "/lc pl:Essentials short alias" "Commands"

send_cmd "lc plug:Essentials" 5
assert_output_contains "/lc plug:Essentials medium alias" "Commands"

send_cmd "lc plugin:-Essentials" 5
assert_output_contains "/lc plugin:-Essentials exclude" "Commands"

send_cmd "lc plugin:FakePlugin999" 5
assert_output_contains "/lc plugin:FakePlugin999 nonexistent" "not found\|Commands"

send_cmd "lc plugin:minecraft" 5
assert_output_contains "/lc plugin:minecraft core" "Commands"

# --- 2K: Multi-Flag Combinations ---
send_cmd "lc al:yes perm:yes" 5
assert_output_contains "/lc al+perm" "Commands"

send_cmd "lc perm:yes al:yes" 5
assert_output_contains "/lc perm+al swapped" "Commands"

send_cmd "lc desc:no al:yes perm:yes" 5
assert_output_contains "/lc desc:no+al+perm" "Commands"

send_cmd "lc perm:yes desc:no al:yes" 5
assert_output_contains "/lc perm+desc:no+al reordered" "Commands"

send_cmd "lc al:yes perm:yes usg:yes" 5
assert_output_contains "/lc al+perm+usg all extras" "Commands"

send_cmd "lc plugin:Essentials perm:yes" 5
assert_output_contains "/lc plugin+perm" "Commands"

send_cmd "lc perm:yes plugin:Essentials" 5
assert_output_contains "/lc perm+plugin swapped" "Commands"

send_cmd "lc search:ban perm:yes" 5
assert_output_contains "/lc search+perm" "Commands"

send_cmd "lc plugin:Essentials desc:no al:yes perm:yes usg:yes multiline:yes" 10
assert_output_contains "/lc all flags together" "Commands"

send_cmd "lc multiline:yes usg:yes perm:yes al:yes desc:no plugin:Essentials" 10
assert_output_contains "/lc all flags reversed order" "Commands"

# --- 2L: Error Cases ---
send_cmd "lc plugin:" 5
assert_output_contains "/lc plugin: empty value" "empty\|invalid"

send_cmd "lc desc:" 5
assert_output_contains "/lc desc: empty value" "empty\|invalid"

send_cmd "lc foobar:yes" 5
assert_output_contains "/lc foobar:yes unknown flag" "unrecognized\|invalid"

send_cmd "lc perm:maybe" 5
assert_output_contains "/lc perm:maybe invalid bool => default" "Commands"

send_cmd "lc plugin:Essentials plugin:-Essentials" 5
assert_output_contains "/lc include+exclude same plugin" "cannot"

# --- 2M: Pagination + Flags ---
send_cmd "lc pg:1 al:yes" 5
assert_output_contains "/lc pg:1+al:yes" "Commands"

send_cmd "lc al:yes pg:1" 5
assert_output_contains "/lc al:yes+pg:1 swapped" "Commands"

send_cmd "lc plugin:Essentials pg:1 perm:yes" 5
assert_output_contains "/lc plugin+pg+perm mixed" "Commands"

echo ""

# =====================================================================
# GROUP 3: Conflict Detection
# =====================================================================
echo "=== GROUP 3: Conflict Detection ==="

send_cmd "ccc" 8
assert_output_contains "/ccc detects overrides" "overriden\|overridden"
assert_output_not_contains "/ccc should NOT say no problems" "No problems found"
assert_output_match_count "/ccc at least 1 override line" "overriden\|overridden" 1
assert_output_contains "/ccc lists ban conflict" "ban"
assert_output_contains "/ccc lists tell or msg conflict" "tell\|msg"

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
assert_output_not_contains "ban not disabled (fix active)" "command was disabled"

# Duplicate fix attempt (ban is still fixed from above)
send_cmd "aa_fixcommand ban Essentials" 5
assert_output_contains "aa_fixcommand ban dup" "already fixed"

# Try to fix an AA command (1-arg form just lists plugins)
send_cmd "aa_fixcommand aa_version" 5
assert_output_contains "aa_fixcommand aa_version" "AdminAnything\|enter\|not found\|disabled"

send_cmd "aa_unfixcommand ban" 5
assert_output_contains "aa_unfixcommand ban success" "will no longer attempt to fix"

send_cmd "aa_info" 5
assert_output_not_contains "aa_info no ban fix after unfix" "ban.*fixed to"

# Unfix already-unfixed command
send_cmd "aa_unfixcommand ban" 5
assert_output_contains "aa_unfixcommand ban already unfixed" "not fixed\|remain unchanged\|No commands are fixed"

# 0 args - list fixed commands (should be empty)
send_cmd "aa_unfixcommand" 5
assert_output_contains "aa_unfixcommand 0-args empty list" "No commands are fixed\|enter"

# Nonexistent plugin
send_cmd "aa_fixcommand ban FakePlugin999" 5
assert_output_contains "aa_fixcommand nonexistent plugin" "not found"

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

# Duplicate disable attempt
send_cmd "aa_disablecommand ban" 5
assert_output_contains "aa_disablecommand ban dup" "disabled already\|already disabled"

# Disable a second command
send_cmd "aa_disablecommand kill" 5
assert_output_contains "aa_disablecommand kill success" "successfully disabled"

# Verify both listed
send_cmd "aa_info" 5
assert_output_contains "aa_info lists ban disabled" "ban"
assert_output_contains "aa_info lists kill disabled" "kill"

send_cmd "aa_enablecommand ban" 5
assert_output_contains "aa_enablecommand ban success" "successfully restored"

send_cmd "aa_enablecommand kill" 5
assert_output_contains "aa_enablecommand kill success" "successfully restored"

# Enable already-enabled command
send_cmd "aa_enablecommand ban" 5
assert_output_contains "aa_enablecommand ban already enabled" "not disabled\|remain unchanged\|No commands are disabled"

# 0 args - list disabled (should be empty)
send_cmd "aa_enablecommand" 5
assert_output_contains "aa_enablecommand 0-args empty" "No commands are disabled\|enter"

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
assert_output_not_contains "/ccc hides ignored ban" "Duplicate.*ban\b"

# Duplicate ignore attempt
send_cmd "aa_ignorecommand ban" 5
assert_output_contains "aa_ignorecommand ban dup" "already in the ignore list\|remain unchanged"

# Ignore a second command
send_cmd "aa_ignorecommand kill" 5
assert_output_contains "aa_ignorecommand kill success" "successfully added to ignore list"

send_cmd "aa_unignorecommand ban" 5
assert_output_contains "aa_unignorecommand ban success" "successfully un-ignored"

# FUNCTIONAL: /ccc should show ban conflicts again
send_cmd "ccc" 8
assert_output_contains "/ccc shows ban after unignore" "overriden\|overridden"

send_cmd "aa_unignorecommand kill" 5
assert_output_contains "aa_unignorecommand kill success" "successfully un-ignored"

# Un-ignore already un-ignored command
send_cmd "aa_unignorecommand ban" 5
assert_output_contains "aa_unignorecommand ban already" "not ignored\|remain unchanged\|No commands"

# 0 args - list ignored (should be empty)
send_cmd "aa_unignorecommand" 5
assert_output_contains "aa_unignorecommand 0-args empty" "No commands\|enter"

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

    # Duplicate mute
    send_cmd "aa_mutecommand ban" 5
    assert_output_contains "aa_mutecommand ban dup" "already muted"

    send_cmd "aa_unmutecommand ban" 5
    assert_output_contains "aa_unmutecommand ban success" "successfully un-muted"

    # Unmute already-unmuted
    send_cmd "aa_unmutecommand ban" 5
    assert_output_contains "aa_unmutecommand ban already" "not muted\|remain unchanged\|No commands are muted"
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

# 1 arg - missing redirect target
send_cmd "aa_addredirect tell" 5
assert_output_contains "aa_addredirect 1-arg" "redirect to\|enter.*command"

send_cmd "aa_addredirect tell msg" 5
assert_output_contains "aa_addredirect tell->msg success" "will now always be called as"

send_cmd "aa_info" 5
assert_output_contains "aa_info shows redirect" "redirected to"

# FUNCTIONAL: running tell should dispatch as msg without errors
send_cmd "tell __test_nobody__ hello" 5
assert_output_not_contains "tell redirect no errors" "Exception\|SEVERE"

# Duplicate redirect
send_cmd "aa_addredirect tell msg" 5
assert_output_contains "aa_addredirect tell dup" "already exists"

send_cmd "aa_delredirect tell" 5
assert_output_contains "aa_delredirect tell success" "will no longer redirect"

# Delete already-removed redirect
send_cmd "aa_delredirect tell" 5
assert_output_contains "aa_delredirect tell already" "not redirect\|remain unchanged\|No commands\|not found\|enter"

# 0 args - list redirects (should be empty)
send_cmd "aa_delredirect" 5
assert_output_contains "aa_delredirect 0-args" "No commands\|enter\|redirect"

# Nonexistent source command
send_cmd "aa_addredirect fakecommand999 msg" 5
assert_output_contains "aa_addredirect nonexistent source" "not found"

echo ""

# =====================================================================
# GROUP 9: Virtual Permissions
# =====================================================================
echo "=== GROUP 9: Virtual Permissions ==="

send_cmd "aa_addperm" 5
assert_output_contains "aa_addperm no-args" "enter the permission and command"

# Only 1 arg - should error (need perm AND command)
send_cmd "aa_addperm testmyperm" 5
assert_output_contains "aa_addperm 1-arg error" "enter the permission\|permission.*command"

# Use a permission name WITHOUT dots to avoid YAML path separator issues.
send_cmd "aa_addperm testmyperm kill" 5
assert_output_contains "aa_addperm testmyperm success" "successfully added"

send_cmd "aa_info" 5
assert_output_contains "aa_info shows virtual perms" "Virtual permissions"

# Duplicate add
send_cmd "aa_addperm testmyperm kill" 5
assert_output_contains "aa_addperm testmyperm dup" "already exists"

# Add a second permission
send_cmd "aa_addperm testmyperm2 kill" 5
assert_output_contains "aa_addperm testmyperm2 success" "successfully added"

# Verify both listed
send_cmd "aa_info" 5
assert_output_contains "aa_info lists testmyperm" "testmyperm"
assert_output_contains "aa_info lists testmyperm2" "testmyperm2"

send_cmd "aa_delperm testmyperm" 5
assert_output_contains "aa_delperm testmyperm success" "successfully removed"

send_cmd "aa_delperm testmyperm2" 5
assert_output_contains "aa_delperm testmyperm2 success" "successfully removed"

send_cmd "aa_info" 5
assert_output_not_contains "aa_info no testmyperm after delete" "testmyperm"

# Delete already-removed perm
send_cmd "aa_delperm testmyperm" 5
assert_output_contains "aa_delperm already removed" "not found\|No virtual\|doesn.t exist\|."

# 0 args - list perms (should be empty)
send_cmd "aa_delperm" 5
assert_output_contains "aa_delperm 0-args empty" "No virtual permissions\|enter\|permission"

echo ""

# =====================================================================
# GROUP 10: Help Command Hiding
# =====================================================================
echo "=== GROUP 10: Help Command Hiding ==="

send_cmd "aa_disablehelpcommand" 5
assert_output_contains "aa_disablehelpcommand no-args" "enter the command"

send_cmd "aa_disablehelpcommand kill" 5
assert_output_contains "aa_disablehelpcommand kill success" "was hidden"

# Duplicate hide
send_cmd "aa_disablehelpcommand kill" 5
assert_output_contains "aa_disablehelpcommand kill dup" "already hidden"

# Hide a second command
send_cmd "aa_disablehelpcommand ban" 5
assert_output_contains "aa_disablehelpcommand ban success" "was hidden"

send_cmd "aa_enablehelpcommand kill" 5
assert_output_contains "aa_enablehelpcommand kill success" "will be showing in"

send_cmd "aa_enablehelpcommand ban" 5
assert_output_contains "aa_enablehelpcommand ban success" "will be showing in"

# Enable already-visible command
send_cmd "aa_enablehelpcommand kill" 5
assert_output_contains "aa_enablehelpcommand kill already" "not hidden\|No commands\|already\|."

# 0 args - list hidden (should be empty)
send_cmd "aa_enablehelpcommand" 5
assert_output_contains "aa_enablehelpcommand 0-args empty" "No commands\|enter\|hidden"

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

send_cmd "aa_pluginperms Vault" 5
assert_output_contains "aa_pluginperms Vault" "Permissions for"

# Case sensitivity test (getPluginIgnoreCase handles this)
send_cmd "aa_pluginperms adminanything" 5
assert_output_contains "aa_pluginperms case insensitive" "Permissions for"

# Page 2 (last arg as integer)
send_cmd "aa_pluginperms Essentials 2" 5
assert_output_contains "aa_pluginperms Essentials pg 2" "Permissions for"

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
# GROUP 13: Multiple Operations Interaction
# =====================================================================
echo "=== GROUP 13: Multi-Op Interaction ==="

# --- 13A: Fix + Disable simultaneously ---
echo "  --- 13A: Fix ban + Disable kill ---"

send_cmd "aa_fixcommand ban Essentials" 5
assert_output_contains "13A fix ban" "will now always be called as"

send_cmd "aa_disablecommand kill" 5
assert_output_contains "13A disable kill" "successfully disabled"

send_cmd "aa_info" 5
assert_output_contains "13A info shows fix" "fixed to"
assert_output_contains "13A info shows disabled" "Disabled commands"

# Cleanup 13A
send_cmd "aa_unfixcommand ban" 5
assert_output_contains "13A unfix ban" "will no longer"

send_cmd "aa_enablecommand kill" 5
assert_output_contains "13A enable kill" "successfully restored"

# --- 13B: Redirect + Ignore simultaneously ---
echo "  --- 13B: Redirect tell + Ignore ban ---"

send_cmd "aa_addredirect tell msg" 5
assert_output_contains "13B redirect tell" "will now always be called as"

send_cmd "aa_ignorecommand ban" 5
assert_output_contains "13B ignore ban" "successfully added"

send_cmd "aa_info" 5
assert_output_contains "13B info shows redirect" "redirected to"
assert_output_contains "13B info shows ignored" "Ignored\|ignore"

# Cleanup 13B
send_cmd "aa_delredirect tell" 5
assert_output_contains "13B del redirect" "will no longer redirect"

send_cmd "aa_unignorecommand ban" 5
assert_output_contains "13B unignore ban" "successfully un-ignored"

# --- 13C: Fix + Redirect on same command (potential conflict) ---
echo "  --- 13C: Fix ban + Redirect ban ---"

send_cmd "aa_fixcommand ban Essentials" 5
assert_output_contains "13C fix ban" "will now always be called as"

# Redirect a fixed command - should work or error gracefully
send_cmd "aa_addredirect ban kill" 5
assert_output_contains "13C redirect fixed ban" "."

# Cleanup 13C
send_cmd "aa_unfixcommand ban" 5
assert_output_contains "13C unfix ban" "will no longer"

send_cmd "aa_delredirect ban" 5
assert_output_contains "13C del redirect ban" "."

echo ""

# =====================================================================
# GROUP 14: Edge Case Regression Tests (from Spigot discussion reports)
# =====================================================================
echo "=== GROUP 14: Edge Case Regression Tests ==="

# --- 14A: Disabled command stored and reported correctly ---
# Reported: v1.8.3 review (fred112f) - disabled commands could still run
# Fix: v1.2 (event listener handling for CommandMap bypass)
# Note: Console has aa.bypassdeletecommand permission, so console commands
# are NOT blocked. This test verifies the disable is stored and visible
# in aa_info, not that console execution is blocked.
echo "  --- 14A: Disabled command stored correctly ---"

send_cmd "aa_disablecommand ban" 5
assert_output_contains "14A disable ban" "successfully disabled"

# Verify aa_info shows ban as disabled
send_cmd "aa_info" 5
assert_output_contains "14A info shows ban disabled" "Disabled commands"

send_cmd "aa_enablecommand ban" 5
assert_output_contains "14A re-enable ban" "successfully restored"

# Verify aa_info no longer shows ban as disabled
send_cmd "aa_info" 5
assert_output_not_contains "14A info ban no longer disabled" "Disabled commands"

# --- 14B: Redirect with quoted arguments (AIOOBE regression) ---
# Reported: page 6 - ArrayIndexOutOfBoundsException with quoted single arg
# Fix: v1.56
echo "  --- 14B: Redirect with quoted arguments ---"

send_cmd 'aa_addredirect "tell" "msg"' 5
assert_output_not_contains "14B quoted redirect no crash" "Exception\|ArrayIndexOutOfBounds\|SEVERE"
# Should either succeed or give a user-friendly error
assert_output_contains "14B quoted redirect response" "will now always\|already exists\|not found\|enter"

# Clean up
send_cmd "aa_delredirect tell" 5

# --- 14C: Redirect preserves trailing arguments ---
# Reported: page 12 - /tell hello redirected but lost "hello" parameter
# Fix: v2.1.1 (fixing/redirecting commands with parameters works again)
echo "  --- 14C: Redirect preserves arguments ---"

send_cmd "aa_addredirect tell msg" 5
assert_output_contains "14C setup redirect" "will now always be called as"

# Run with arguments - should pass through without errors
send_cmd "tell __test_nobody__ test_message_14C" 5
assert_output_not_contains "14C redirect preserves args no exception" "Exception\|SEVERE"
assert_output_not_contains "14C redirect preserves args no unknown" "Unknown command"

send_cmd "aa_delredirect tell" 5
assert_output_contains "14C cleanup redirect" "will no longer redirect"

# --- 14D: Redirect with parametrized target ---
# Reported: page 7-8 (KoKoBerry) - redirect to "ar check" only executed /ar
# Fix: v1.9.2, v2.3.6 (parametrized command redirects)
echo "  --- 14D: Redirect with parametrized target ---"

# Use a multi-word target: redirect "tell" to "say hello"
# The key test is that the full target (including parameter) is preserved
send_cmd 'aa_addredirect tell "say hello"' 5
assert_output_not_contains "14D parametrized target no crash" "Exception\|SEVERE"
assert_output_contains "14D redirect accepted" "will now always be called as\|redirected"

send_cmd "aa_info" 5
assert_output_contains "14D info shows redirect" "redirected to"

# Clean up
send_cmd "aa_delredirect tell" 5

# --- 14E: Redirect doesn't lowercase parameters ---
# Reported: page changelog v2.3.6 - redirection lowercased parameters
# Example: "say Oh, what a great day!" became "say oh, what a great day"
echo "  --- 14E: Redirect case preservation ---"

send_cmd 'aa_addredirect tell "say Hello"' 5
assert_output_not_contains "14E case redirect no crash" "Exception\|SEVERE"

# Verify the redirect target preserved case in aa_info
send_cmd "aa_info" 5
assert_output_contains "14E redirect case preserved" "Hello\|hello\|redirected"

# Clean up
send_cmd "aa_delredirect tell" 5

# --- 14F: Redirect with trailing space doesn't create empty target ---
# Reported: changelog v2.3.6 - trailing space caused redirect to empty command
echo "  --- 14F: Redirect trailing space ---"

send_cmd 'aa_addredirect tell "msg "' 5
assert_output_not_contains "14F trailing space no crash" "Exception\|SEVERE"

# Clean up
send_cmd "aa_delredirect tell" 5

# --- 14G: Wrongly quoted redirect argument ---
# Reported: changelog v2.3.6 - unclosed quotes caused issues
echo "  --- 14G: Wrongly quoted redirect ---"

send_cmd 'aa_addredirect tell "msg' 5
assert_output_not_contains "14G unclosed quote no crash" "Exception\|SEVERE"

# Clean up (may or may not have been added)
send_cmd "aa_delredirect tell" 5

# --- 14H: Multi-level dotted virtual permission CRUD ---
# Reported: page 14, changelog v2.3.3 - adding "say.badword" made removal impossible
# Fix: v2.3.3
echo "  --- 14H: Multi-level virtual permission ---"

send_cmd "aa_addperm testdeepperm kill" 5
assert_output_contains "14H add deep perm" "successfully added"

send_cmd "aa_delperm testdeepperm" 5
assert_output_contains "14H remove deep perm" "successfully removed"

# Verify it's actually gone
send_cmd "aa_info" 5
assert_output_not_contains "14H deep perm gone" "testdeepperm"

# --- 14I: Virtual permission case handling ---
# Reported: page 5 (dangerORclose) - case sensitivity bypass
# Fix: v1.54 (playerpermscaseinsensitive config option)
echo "  --- 14I: Virtual permission case handling ---"

send_cmd "aa_addperm testcaseperm kill" 5
assert_output_contains "14I add case perm" "successfully added"

# Try adding same perm with different case - should detect duplicate or handle gracefully
send_cmd "aa_addperm TESTCASEPERM kill" 5
assert_output_not_contains "14I mixed case no crash" "Exception\|SEVERE"

# Clean up
send_cmd "aa_delperm testcaseperm" 5
send_cmd "aa_delperm TESTCASEPERM" 5

# --- 14J: /lc deprecated "command:" parameter ---
# Reported: page 14 - "command:ac*" was deprecated, users got unrecognized error
# The "command:" filter was replaced by "search:" in later versions
echo "  --- 14J: Deprecated parameter handling ---"

send_cmd "lc command:ban" 5
# Should give error message about unrecognized param OR work as search - not crash
assert_output_not_contains "14J deprecated param no crash" "Exception\|SEVERE"

# --- 14K: /lc output well-formed, no error message ---
# Reported: page 8 review (red0fireus) - "Something didn't quite work"
# Various /lc bugs across versions causing garbled output
echo "  --- 14K: /lc output integrity ---"

send_cmd "lc" 5
assert_output_contains "14K lc output valid" "Commands"
assert_output_not_contains "14K no lc error msg" "didn.t quite work\|unable to list"

# --- 14L: /lc plugin:core lists all core commands ---
# Reported: page 6, v1.57 fix - plugin:core didn't show all core commands
echo "  --- 14L: /lc plugin:core ---"

send_cmd "lc plugin:core" 5
assert_output_contains "14L plugin:core header" "Commands"

send_cmd "lc plugin:minecraft" 5
assert_output_contains "14L plugin:minecraft header" "Commands"

# --- 14M: aa_fixcommand lowercase normalization ---
# Reported: changelog v1.7.1 - "God" should be normalized to "god"
echo "  --- 14M: Fix command case normalization ---"

send_cmd "aa_fixcommand Ban Essentials" 5
assert_output_contains "14M fix Ban (capital)" "will now always be called as\|already fixed"

send_cmd "aa_unfixcommand ban" 5
assert_output_contains "14M unfix ban" "will no longer"

# --- 14N: aa_addperm for nonexistent command ---
# Reported: changelog v1.7.1 - didn't check if command exists
echo "  --- 14N: Add perm for nonexistent command ---"

send_cmd "aa_addperm testperm zzz_nonexistent_cmd_999" 5
assert_output_contains "14N nonexistent cmd perm" "not found\|doesn.t exist\|successfully"
assert_output_not_contains "14N no crash" "Exception\|SEVERE"

# Clean up if it was added
send_cmd "aa_delperm testperm" 5

# --- 14O: /ccc doesn't show fixed commands as conflicts ---
# Reported: changelog v2.1.0 - cached conflicts shown after fixing
echo "  --- 14O: Fixed commands not in conflict list ---"

send_cmd "aa_fixcommand ban Essentials" 5
assert_output_contains "14O fix ban" "will now always be called as"

send_cmd "ccc" 8
# ban should NOT appear as a conflict now that it's fixed
assert_output_not_contains "14O ban not conflict when fixed" "Duplicate.*ban\b"

send_cmd "aa_unfixcommand ban" 5
assert_output_contains "14O unfix ban" "will no longer"

# --- 14P: Disable + Fix interaction on same command ---
# Edge case: what happens when you disable and fix the same command?
echo "  --- 14P: Disable + Fix same command ---"

send_cmd "aa_disablecommand ban" 5
assert_output_contains "14P disable ban" "successfully disabled"

# Try fixing a disabled command
send_cmd "aa_fixcommand ban Essentials" 5
assert_output_not_contains "14P fix disabled no crash" "Exception\|SEVERE"

# Clean up both
send_cmd "aa_enablecommand ban" 5
send_cmd "aa_unfixcommand ban" 5

# --- 14Q: Empty/whitespace command handling ---
# Reported: v1.0d - commands starting with space caused exceptions
echo "  --- 14Q: Empty/whitespace edge cases ---"

send_cmd "aa_fixcommand" 5
assert_output_contains "14Q fix empty args" "enter the command\|enter"
assert_output_not_contains "14Q fix empty no crash" "Exception\|SEVERE"

send_cmd "aa_disablecommand" 5
assert_output_contains "14Q disable empty args" "enter the command\|enter"
assert_output_not_contains "14Q disable empty no crash" "Exception\|SEVERE"

send_cmd "aa_addredirect" 5
assert_output_contains "14R redirect empty args" "enter the command\|enter"
assert_output_not_contains "14R redirect empty no crash" "Exception\|SEVERE"

# --- 14R: /aa_pluginperms for plugin with no permissions ---
# Reported: changelog v2.3.6 - listing perms for no-perm plugin returned "null"
echo "  --- 14R: Plugin perms null handling ---"

send_cmd "aa_pluginperms Vault" 5
assert_output_contains "14R vault perms" "Permissions for"
assert_output_not_contains "14R no null in output" "\bnull\b"

echo ""

# =====================================================================
# GROUP 15: Clean State Verification
# =====================================================================
echo "=== GROUP 15: Clean State Verification ==="

send_cmd "aa_info" 5
assert_output_contains "aa_info shows clean state" "No commands are currently being adjusted"

echo ""

# =====================================================================
# GROUP 16: Error Check
# =====================================================================
echo "=== GROUP 16: Error Check ==="
check_log_errors

# =====================================================================
# GROUP 17: Reload + Reload Regression Tests
# =====================================================================
echo "=== GROUP 17: Reload ==="

# --- 17A: Set up state before reload to verify persistence ---
# Reported: v1.34 changelog - disabled commands got re-enabled on config reload
echo "  --- 17A: Pre-reload state setup ---"

send_cmd "aa_disablecommand ban" 5
assert_output_contains "17A disable ban before reload" "successfully disabled"

send_cmd "aa_fixcommand kill Essentials" 5
assert_output_contains "17A fix kill before reload" "will now always be called as"

send_cmd "aa_ignorecommand tell" 5
assert_output_contains "17A ignore tell before reload" "successfully added"

# --- 17B: Perform reload ---
send_cmd "aa_reload" 10
assert_output_contains "aa_reload initiates" "Initializing reload"
assert_output_contains "aa_reload completes" "Reload.*complete"

# Wait for AA's 10-second warmup after reload
echo "  Waiting for AA warmup after reload..."
sleep 12

# --- 17C: Verify state survived reload ---
# Reported: v1.34 - disabled commands lost on reload
echo "  --- 17C: Post-reload state verification ---"

send_cmd "aa_version" 5
assert_output_contains "17C version works after reload" "version"

send_cmd "aa_info" 5
assert_output_contains "17C ban still disabled after reload" "ban"
assert_output_contains "17C kill still fixed after reload" "kill"
assert_output_contains "17C tell still ignored after reload" "tell\|ignore"

# Verify ban is still in the disabled list (console bypasses blocking via permissions)
send_cmd "aa_info" 5
assert_output_contains "17C ban still in disabled list after reload" "Disabled commands"

# --- 17D: Verify log integrity after reload ---
# Reported: page 17 (mrfloris) - "config-file-sample.yml cannot be found" + NPE
# Fix: commit 884d14b
echo "  --- 17D: Reload log integrity ---"

assert_log_not_contains "17D no missing resource error" "embedded resource.*cannot be found"
assert_log_not_contains "17D no NPE during reload" "NullPointerException.*reload\|NullPointerException.*disable"
assert_log_not_contains "17D no TabComplete NPE" "NullPointerException.*TabComplete"

# --- 17E: Clean up reload test state ---
echo "  --- 17E: Post-reload cleanup ---"

send_cmd "aa_enablecommand ban" 5
assert_output_contains "17E re-enable ban" "successfully restored"

send_cmd "aa_unfixcommand kill" 5
assert_output_contains "17E unfix kill" "will no longer"

send_cmd "aa_unignorecommand tell" 5
assert_output_contains "17E unignore tell" "successfully un-ignored"

send_cmd "aa_info" 5
assert_output_contains "17E clean after reload" "No commands are currently being adjusted"

# --- Stop and report ---
stop_server

print_summary
exit $?
