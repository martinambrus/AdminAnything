#!/bin/bash
# Auto-build a Minecraft server for a given version using BuildTools.
# Usage: ./setup-server.sh <mc-version> [java-binary]
#
# For versions 1.8+, downloads and runs SpigotMC BuildTools.
# For 1.7.10, looks for a pre-built CraftBukkit JAR in archives/.
# Skips if <mc-version>/server.jar already exists.

set -euo pipefail

MC_VERSION="${1:?Usage: ./setup-server.sh <mc-version> [java-binary]}"
JAVA_BIN="${2:-java}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SERVER_DIR="$SCRIPT_DIR/$MC_VERSION"
BUILDTOOLS_DIR="$SCRIPT_DIR/buildtools-cache"
ARCHIVES_DIR="$SCRIPT_DIR/archives"

# Skip if server already exists
if [ -f "$SERVER_DIR/server.jar" ]; then
    echo "Server already exists: $SERVER_DIR/server.jar (skipping)"
    exit 0
fi

echo "=== Setting up MC $MC_VERSION server ==="
echo "Java: $JAVA_BIN"
"$JAVA_BIN" -version 2>&1 | head -1

# --- 1.7.10: use pre-built JAR from archives ---
if [ "$MC_VERSION" = "1.7.10" ]; then
    echo "1.7.10 requires a pre-built CraftBukkit JAR (BuildTools doesn't support it)."
    ARCHIVE_JAR=""
    if [ -d "$ARCHIVES_DIR" ]; then
        # Look for craftbukkit or spigot JARs for 1.7.10
        for pattern in "craftbukkit-1.7.10"*.jar "spigot-1.7.10"*.jar; do
            found=$(find "$ARCHIVES_DIR" -name "$pattern" -type f 2>/dev/null | head -1)
            if [ -n "$found" ]; then
                ARCHIVE_JAR="$found"
                break
            fi
        done
    fi

    if [ -z "$ARCHIVE_JAR" ]; then
        echo "ERROR: No 1.7.10 server JAR found in $ARCHIVES_DIR/"
        echo "Please place a craftbukkit-1.7.10*.jar or spigot-1.7.10*.jar in the archives/ directory."
        echo "See AGENTS.md for the Dropbox archive link."
        exit 1
    fi

    echo "Found archive JAR: $ARCHIVE_JAR"
    mkdir -p "$SERVER_DIR"
    cp "$ARCHIVE_JAR" "$SERVER_DIR/server.jar"
    echo "Copied to $SERVER_DIR/server.jar"

# --- 1.8+: use BuildTools ---
else
    mkdir -p "$BUILDTOOLS_DIR"
    BUILDTOOLS_JAR="$BUILDTOOLS_DIR/BuildTools.jar"

    # Download BuildTools if not cached
    if [ ! -f "$BUILDTOOLS_JAR" ]; then
        echo "Downloading BuildTools.jar..."
        curl -fSL --max-time 120 -o "$BUILDTOOLS_JAR.tmp" \
            "https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar"
        mv "$BUILDTOOLS_JAR.tmp" "$BUILDTOOLS_JAR"
        echo "BuildTools downloaded."
    else
        echo "Using cached BuildTools.jar"
    fi

    # Run BuildTools in a temp directory to avoid polluting the workspace
    BUILD_WORK="$BUILDTOOLS_DIR/work-$MC_VERSION"
    mkdir -p "$BUILD_WORK"

    echo "Running BuildTools for MC $MC_VERSION (this may take several minutes)..."
    (cd "$BUILD_WORK" && "$JAVA_BIN" -jar "$BUILDTOOLS_JAR" --rev "$MC_VERSION") || {
        echo "ERROR: BuildTools failed for MC $MC_VERSION"
        echo "Check that your Java version is compatible:"
        echo "  1.8 - 1.16.5: Java 8 or 11"
        echo "  1.17+: Java 16+"
        echo "  1.20.4+: Java 21"
        exit 1
    }

    # Find the resulting spigot JAR
    SPIGOT_JAR=$(find "$BUILD_WORK" -maxdepth 1 -name "spigot-${MC_VERSION}*.jar" -type f | head -1)
    if [ -z "$SPIGOT_JAR" ]; then
        # Fallback: look for craftbukkit
        SPIGOT_JAR=$(find "$BUILD_WORK" -maxdepth 1 -name "craftbukkit-${MC_VERSION}*.jar" -type f | head -1)
    fi

    if [ -z "$SPIGOT_JAR" ]; then
        echo "ERROR: BuildTools completed but no server JAR found in $BUILD_WORK/"
        ls -la "$BUILD_WORK"/*.jar 2>/dev/null || echo "(no JARs found)"
        exit 1
    fi

    mkdir -p "$SERVER_DIR"
    cp "$SPIGOT_JAR" "$SERVER_DIR/server.jar"
    echo "Copied $SPIGOT_JAR -> $SERVER_DIR/server.jar"
fi

# --- Common setup ---

# Accept EULA
echo "eula=true" > "$SERVER_DIR/eula.txt"

# Create minimal server.properties
cat > "$SERVER_DIR/server.properties" <<'PROPS'
online-mode=false
level-type=flat
spawn-protection=0
max-world-size=1
generate-structures=false
spawn-npcs=false
spawn-animals=false
spawn-monsters=false
PROPS

echo ""
echo "=== Server setup complete: $SERVER_DIR ==="
ls -lh "$SERVER_DIR/server.jar"
