# AdminAnything - Agent Reference

## Project Summary

AdminAnything is a Spigot/CraftBukkit plugin that resolves plugin command conflicts on Minecraft servers. When multiple plugins register the same command (e.g., Essentials and WorldEdit both registering `/tree`), Spigot randomly picks one. AdminAnything lets administrators control which plugin handles each conflicting command and provides extensive command management capabilities.

Core features:
- Command conflict detection and resolution (`/aa_checkcommandconflicts`, `/aa_fixcommand`)
- Command disabling with per-player permission bypass (`/aa_disablecommand`)
- Command muting - silences console/chat output (Java 8 + MC 1.16.x only)
- Command redirection (e.g., `/shop` to `/warp shop`)
- Virtual permissions for specific command+parameter combinations
- Automated help system showing only commands the player has access to
- Tab-completion management (auto-disable for inaccessible commands)
- Chat GUI with clickable action links (`[A]` prefix next to player names)
- Chest/inventory GUI for admin actions
- Full localization support
- File-based or MySQL configuration backends

Spigot page: https://www.spigotmc.org/resources/adminanything-control-your-server.19436
bStats plugin ID: 29579
Current version: 2.4.12

## Supported Minecraft Versions

Target compatibility: CraftBukkit/Spigot 1.7.10 onwards.
Tested versions: 1.7 through 1.20.6.
plugin.yml api-version: 1.13.
Command muting: only works on Java 8 with MC 1.16.x and below (Java 11+ patched the security holes used for bytecode manipulation).

## Tech Stack

### Build System
- Gradle (installed version: 9.3.1, build.gradle currently references Gradle 7-era plugins)
- Shadow JAR plugin for fat JAR creation (bundles bStats, relocates to avoid conflicts)
- No Gradle wrapper committed to repo (gitignored)

### Current Dependencies (build.gradle, needs updating)
| Dependency | Current Version | Scope | Notes |
|---|---|---|---|
| Spigot API | 1.16.5-R0.1-SNAPSHOT | compileOnly | Server-provided |
| ProtocolLib | 4.5.1 (com.comphenix.protocol) | compileOnly | Optional soft-dep, used for chat packet manipulation |
| Vault API | 1.7 | compileOnly | Optional, for permissions integration |
| Javassist | 3.27.0-GA | compileOnly | For bytecode manipulation (command muting) |
| bStats Bukkit | 1.4 | implementation | Bundled in shadow JAR, relocated to com.martinambrus.adminAnything.bstats |
| EasyChat | 1.0.0 (local JAR in libs/) | compileOnly | For nick click features |

### Target Dependency Updates
| Dependency | Target Version | Change Notes |
|---|---|---|
| Shadow Plugin | 9.3.1 (com.gradleup.shadow) | Was com.github.johnrengelman.shadow:7.0.0. Plugin ID changed. |
| bStats Bukkit | 3.1.0 | Constructor now requires int serviceId (plugin ID: 29579). Was: `new Metrics(plugin)`, now: `new Metrics(plugin, 29579)` |
| ProtocolLib | 5.4.0 | GroupId possibly changed to net.dmulloy2. Verify Maven coordinates. |
| Javassist | Latest compatible with Java 8 target | Check for updates |

### Java
- Source compatibility: Java 8 (VERSION_1_8) - MUST remain Java 8
- Target compatibility: Java 8 (VERSION_1_8) - MUST remain Java 8
- Build JDK on system: OpenJDK 25.0.2 (can cross-compile to Java 8 target)

### Project Structure
```
src/main/java/com/martinambrus/adminAnything/
  AdminAnything.java       - Main plugin class (JavaPlugin, entry point)
  AA_API.java              - Public API
  Commands.java            - Command registration and management
  CommandListeners.java    - Command event listeners
  Config.java              - Config bootstrap
  ConfigAbstractAdapter.java / ConfigFileAdapter.java / ConfigMySQLAdapter.java - Config backends
  CustomMetrics.java       - bStats initialization (NEEDS UPDATE for plugin ID)
  Language.java            - i18n
  Listeners.java           - Event listener management
  Permissions.java         - Vault/native permission handling
  Plugins.java             - Plugin utilities
  TabComplete.java         - Tab completion system
  Utils.java               - General utilities
  commands/                - 57 command handler classes
  events/                  - 18 custom event classes
  listeners/               - 13 listener classes
  tabcomplete/             - 13 tab completer classes
  instrumentation/         - 5 bytecode manipulation classes (command muting)

src/main/resources/
  plugin.yml               - Bukkit plugin descriptor (version injected at build time)
  config.yml               - Default config (just configBackend: file)
  config-file.yml          - File adapter config template
  config-db.yml            - MySQL adapter config template
  permdescriptions.yml     - Manual permission descriptions for popular plugins
  languages/en-gb.properties - English translations
  libraries/natives/64/    - Native attach libraries (Windows, Linux, Mac, Solaris)

libs/
  easyChat-1.0.0.jar       - Local dependency
```

### Repositories Used
- Maven Central
- hub.spigotmc.org/nexus (Spigot API snapshots)
- oss.sonatype.org (snapshots)
- repo.dmulloy2.net (ProtocolLib)
- repo.codemc.org (bStats)
- repo.md-5.net (BungeeCord/misc)

## Build and Update Instructions

### Priority 1: Update Build Toolchain
1. Update shadow plugin from `com.github.johnrengelman.shadow:7.0.0` to `com.gradleup.shadow:9.3.1`
2. Generate Gradle wrapper for 9.x (`gradle wrapper --gradle-version 9.3.1`)
3. Update any deprecated Gradle API usage for Gradle 9.x compatibility
4. Verify the build still produces a working adminAnything.jar targeting Java 8

### Priority 2: Update bStats
1. Update bStats dependency from 1.4 to 3.1.0
2. Update CustomMetrics.java: change `new Metrics(plugin)` to `new Metrics(plugin, 29579)`
3. Verify the relocation in shadowJar still works (`org.bstats` to `com.martinambrus.adminAnything.bstats`)
4. Verify custom charts API compatibility (SimplePie, Callable pattern)

### Priority 3: Update Other Dependencies
1. Update ProtocolLib compile dependency (check if groupId changed to net.dmulloy2)
2. Check Javassist for updates compatible with Java 8 target
3. Keep Vault API at 1.7 (stable, unlikely to need update)
4. Keep Spigot API version as-is for now (1.16.5 for compilation; runtime compatible with 1.7.10+)

### Constraints
- Java source/target MUST remain 8 (VERSION_1_8)
- Plugin must load and function on CraftBukkit/Spigot 1.7.10 through latest
- plugin.yml api-version stays at 1.13

## Testing Strategy

### Archived Server Versions
A 7z archive containing CraftBukkit and Spigot server JARs for many MC versions is available at:
https://www.dropbox.com/scl/fi/4b5dnq3r8okmqc0wdga9t/mc-alpha-beta-craftbukkit-spigot.7z?rlkey=23dr5pbf6ch9z8s9bqkf8ssl6&st=ya37nin0&dl=0

These contain versions up to approximately 2024. Newer versions will be added later.

### Test Requirements

#### Important: AdminAnything Warm-Up Period
AdminAnything has a 10-second activation delay after server start. All tests MUST wait for the log message "[AdminAnything] Now fully activated." before executing any AA commands. Commands sent during warm-up will return a "please wait" message and produce no useful output.

#### Server-Side Automated Tests
- Start each server version with AdminAnything + test plugins installed
- Wait for AA activation (see above) before sending any commands
- Execute admin commands programmatically and verify expected outcomes
- Test command conflict detection, fixing, disabling, redirection
- Test configuration persistence (file and MySQL backends)
- Test permission checks (with and without Vault)
- Test tab-completion filtering

#### Client-Side Automated Tests
- Control a running Minecraft client instance via script
- Emulate user actions: typing chat commands, clicking chat links (on-click events), inventory interactions
- Verify chat UI renders correctly (clickable links, `[A]` prefix, command output formatting)
- Verify inventory/chest GUI shows correct items with correct tooltips
- Navigate through multi-page command listings
- Test across multiple client versions matching the server versions

#### Plugin Compatibility Testing
For each server version, appropriate versions of the following plugins must be installed and tested alongside AdminAnything.

##### Required Test Plugins (User-Specified)
These plugins MUST be included in every test run. Sources and version notes for each:

| Plugin | Source URL | Notes |
|---|---|---|
| EssentialsX | https://www.spigotmc.org/resources/essentialsx.9089/ | Primary command conflict source. For pre-1.8 servers, use original Essentials from dev.bukkit.org. |
| Vault | https://dev.bukkit.org/projects/vault | Economy/Permission/Chat API bridge. Required by many plugins including AA for permission checks. |
| WorldGuard | https://dev.bukkit.org/projects/worldguard | Region protection. Command overlap with WorldEdit. Legacy versions on dev.bukkit.org, modern on EngineHub. |
| WorldEdit | https://dev.bukkit.org/projects/worldedit | World editing. Special // commands need testing. Legacy on dev.bukkit.org, modern on EngineHub. |
| CMILib | https://www.spigotmc.org/resources/cmilib.87610/ | CMI library dependency. Test alongside CMI for command conflicts (e.g., /cmi ic vs /ic). |
| LuckPerms | https://www.spigotmc.org/resources/luckperms.28140/ | Modern permission plugin. Supports world-specific permission contexts. Replacement for PermissionsEx. |
| GriefPrevention | https://dev.bukkit.org/projects/grief-prevention | Land claim plugin. Test command registration and conflict detection. |
| AdvancedHelp | https://www.spigotmc.org/resources/advancedhelp-custom-help-pages-with-gui.44478/ | Custom help pages with GUI. May conflict with AA's auto-generated help system. |
| VentureChat | https://www.spigotmc.org/resources/venturechat.771/ | Chat formatting plugin. Test interaction with AA's chat features ([A] prefix, clickable nick actions, chat packet handling). |

##### Additional High Priority (Known Conflicts/Issues from Changelog/Discussions)
- ProtocolLib - chat packet handling, required for some AA features
- PlaceholderAPI - expansion loading interfered with AA command detection
- ServerControlReloaded - overrides AA command fixes
- UltraPermissions - performance issues when paired with Vault
- ItemsInChat - NullPointerException with AA clickable chat features
- AsyncWorldEdit - extends WorldEdit, affected command conflict detection

##### Medium Priority (Listed in permdescriptions.yml or Discussions)
- mcMMO
- Multiverse-Core
- PermissionsEx (legacy servers only)
- PlugMan / PluginManager
- CoreProtect
- HolographicDisplays
- MassiveCore / Factions
- ChestShop
- ChestCommands
- LWC
- Citizens
- DiscordSRV
- PlotSquared
- Spartan (anti-cheat)

##### Lower Priority (Mentioned in Discussions)
- EasyChat
- Per World Plugins (Heretere)
- SkinsRestorer
- HeadDatabase
- Hyperverse
- Sentinel
- ClearLag
- Plan
- BlueMap
- OpenInv

##### Server Software Variants to Test On
- CraftBukkit
- Spigot
- Paper
- Purpur

### Version-Specific Plugin Sources
- Essentials (pre-1.8): dev.bukkit.org archive
- EssentialsX (1.8+): https://www.spigotmc.org/resources/essentialsx.9089/ and GitHub releases
- WorldEdit/WorldGuard legacy: dev.bukkit.org; modern: EngineHub downloads / GitHub releases
- GriefPrevention: dev.bukkit.org for legacy, SpigotMC/GitHub for modern
- VentureChat: SpigotMC only (no dev.bukkit.org page)
- LuckPerms: SpigotMC + https://luckperms.net/download
- Other plugins: SpigotMC resource pages, GitHub releases, dev.bukkit.org for legacy versions
- For each server version, find the latest plugin release that supports that MC version

### Future Test Automation Notes
The project owner is building a separate Minecraft server test suite for another project (rebuilding MC servers from scratch). A similar but distinct approach may be needed here, focusing on:
1. Server-side: command execution verification, plugin interaction testing
2. Client-side: chat UI interaction, inventory GUI interaction, click event handling
3. Multi-version matrix: each server version x each compatible plugin set
