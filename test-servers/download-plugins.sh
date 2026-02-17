#!/bin/bash
# Download test plugin JARs to a local cache directory.
# Usage: ./download-plugins.sh <mc-version>
# Supported versions: 1.8.8, 1.12.2, 1.13.2, 1.16.5, 1.20.4
# Downloads are cached in test-servers/plugin-cache/ and skipped if already present.

set -euo pipefail

MC_VERSION="${1:?Usage: ./download-plugins.sh <mc-version>}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CACHE_DIR="$SCRIPT_DIR/plugin-cache"
mkdir -p "$CACHE_DIR"

download() {
    local name="$1"
    local url="$2"
    local filename="$3"
    local dest="$CACHE_DIR/$filename"

    if [ -f "$dest" ]; then
        echo "  Cached: $name -> $filename"
        return 0
    fi

    echo "  Downloading: $name -> $filename"
    if curl -fSL --max-time 120 -o "$dest.tmp" "$url"; then
        mv "$dest.tmp" "$dest"
        echo "  OK: $filename ($(du -h "$dest" | cut -f1))"
    else
        rm -f "$dest.tmp"
        echo "  FAILED: $name from $url"
        return 1
    fi
}

echo "=== Downloading plugins for MC $MC_VERSION ==="

case "$MC_VERSION" in
    1.7*)
        # Original Essentials (last release, 2.14.1.3) for 1.7.x servers
        download "Essentials 2.14.1.3" \
            "https://github.com/essentials/Essentials/releases/download/ess-release/Essentials.jar" \
            "Essentials-2.14.1.3.jar"

        download "Vault 1.7.3" \
            "https://github.com/MilkBowl/Vault/releases/download/1.7.3/Vault.jar" \
            "Vault-1.7.3.jar"
        ;;

    1.20.4)
        download "EssentialsX 2.21.2" \
            "https://github.com/EssentialsX/Essentials/releases/download/2.21.2/EssentialsX-2.21.2.jar" \
            "EssentialsX-2.21.2.jar"

        download "Vault 1.7.3" \
            "https://github.com/MilkBowl/Vault/releases/download/1.7.3/Vault.jar" \
            "Vault-1.7.3.jar"

        download "WorldEdit 7.3.9" \
            "https://cdn.modrinth.com/data/1u6JkXh5/versions/Bu1zaaoc/worldedit-bukkit-7.3.9.jar" \
            "WorldEdit-7.3.9.jar"

        download "LuckPerms 5.5.17" \
            "https://cdn.modrinth.com/data/Vebnzrzj/versions/OrIs0S6b/LuckPerms-Bukkit-5.5.17.jar" \
            "LuckPerms-5.5.17.jar"
        ;;

    1.16.5)
        download "EssentialsX 2.19.7" \
            "https://github.com/EssentialsX/Essentials/releases/download/2.19.7/EssentialsX-2.19.7.jar" \
            "EssentialsX-2.19.7.jar"

        download "Vault 1.7.3" \
            "https://github.com/MilkBowl/Vault/releases/download/1.7.3/Vault.jar" \
            "Vault-1.7.3.jar"
        ;;

    1.20.*|1.21.*)
        download "EssentialsX 2.21.2" \
            "https://github.com/EssentialsX/Essentials/releases/download/2.21.2/EssentialsX-2.21.2.jar" \
            "EssentialsX-2.21.2.jar"

        download "Vault 1.7.3" \
            "https://github.com/MilkBowl/Vault/releases/download/1.7.3/Vault.jar" \
            "Vault-1.7.3.jar"

        download "WorldEdit 7.3.9" \
            "https://cdn.modrinth.com/data/1u6JkXh5/versions/Bu1zaaoc/worldedit-bukkit-7.3.9.jar" \
            "WorldEdit-7.3.9.jar"

        download "LuckPerms 5.5.17" \
            "https://cdn.modrinth.com/data/Vebnzrzj/versions/OrIs0S6b/LuckPerms-Bukkit-5.5.17.jar" \
            "LuckPerms-5.5.17.jar"
        ;;

    1.8.*|1.9.*|1.10.*|1.11.*|1.12.*|1.13.*|1.14.*|1.15.*|1.16.*|1.17.*|1.18.*|1.19.*)
        # EssentialsX 2.19.7 and Vault 1.7.3 support 1.8+ servers
        download "EssentialsX 2.19.7" \
            "https://github.com/EssentialsX/Essentials/releases/download/2.19.7/EssentialsX-2.19.7.jar" \
            "EssentialsX-2.19.7.jar"

        download "Vault 1.7.3" \
            "https://github.com/MilkBowl/Vault/releases/download/1.7.3/Vault.jar" \
            "Vault-1.7.3.jar"
        ;;

    *)
        echo "ERROR: No plugin definitions for MC version $MC_VERSION"
        echo "Supported: 1.8.x through 1.21.x"
        exit 1
        ;;
esac

echo ""
echo "=== Plugin cache contents ==="
ls -lh "$CACHE_DIR"
echo "=== Done ==="
