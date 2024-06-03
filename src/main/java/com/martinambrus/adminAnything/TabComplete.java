package com.martinambrus.adminAnything;

import com.martinambrus.adminAnything.events.AAReloadEvent;
import com.martinambrus.adminAnything.events.AASaveCommandHelpDisablesEvent;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.InvalidClassException;
import java.lang.reflect.InvocationTargetException;
import java.rmi.AccessException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates and registers tab completer for commands
 * on servers where this is supported and for commands
 * that do have this feature enabled.
 *
 * @author Martin Ambrus
 */
final class TabComplete implements Listener {

    /**
     * Instance of {@link AdminAnything}.
     */
    private final Plugin plugin;

    /**
     * Name of the super-global group where commands that are common
     * across all groups per single world will be stored.
     */
    private final String superGlobalGroupName = "__superglobal__";

    /**
     * A map of all commands available for a single permission group and all existing worlds.
     * First map's key is group name.
     * Second map's key is world name.
     * Third map's key is a command name and a simple TRUE value
     * (for quicker command retrieval and indexing purposes).
     *
     * There is a "__superglobal__" group key (see superGlobalGroupName above) which holds all commands
     * that are common for all permission groups on the server. Then, each of the permission groups only
     * has additional commands for itself that are there on top of these common commands.
     */
    Map<String, Map<String, Map<String, Boolean>>> groupCommands = new HashMap<String, Map<String, Map<String, Boolean>>>();

    /**
     * A map of all commands available for a single player.
     * First map's key is player's name.
     * Second map's key is world name.
     * Third map's key is the actual available command name and TRUE for its value
     * (for quicker command retrieval and indexing purposes).
     *
     * This is an additive index map, so only commands not present in any of the player's groups exist here.
     * Players commands that do exist in their groups will be in the groupCommands map.
     */
    Map<String, Map<String, Map<String, Boolean>>> playerCommands = new ConcurrentHashMap<String, Map<String, Map<String, Boolean>>>();

    /**
     * Feature name under which this class will be registered as an event listener.
     */
    String featureName = "tabcompletedisable";

    /**
     * This will be set to a delayed task which - when run - will re-initialize
     * the tab-completion caches. It is used when we're managing tab completions
     * via /aa_* commands, so the user can see the changes right away.
     */
    BukkitTask tabReloadWaitingTask = null;

    /**
     * Constructor, creates a new instance of the TabComplete class
     * and registers itself as a TabCompleter for all AdminAnything commands.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * // usage in AdminAnything main class
     * this.tabcomplete = new TabComplete(this);
     * this.tabcomplete.registerTabCompleters();
     * </pre>
     *
     * @param aa Instance of {@link AdminAnything AdminAnything}.
     */
    TabComplete(final Plugin aa) {
        plugin = aa;

        if (!((AdminAnything) plugin).getConf().isDisabled(this.featureName) && !AA_API.isListenerRegistered(this.featureName)) { //NON-NLS
            AA_API.startRequiredListener(this.featureName, this);

            // check if we can use the new PlayerCommandSendEvent
            try {
                Class.forName("org.bukkit.event.player.PlayerCommandSendEvent");

                // if we're here, the new event is present on the server - let's make use of it
                AA_API.startRequiredListener("tabCompleteDisabler");
            } catch (final Throwable e) {
                // the new event is not present on the server,
                // we need to use ProtocolLib and TabComplete packets replacing
                // for this version
                if (null != Bukkit.getPluginManager().getPlugin("ProtocolLib")) {
                    // ProtocolLib plugin found, start the correct listener
                    AA_API.startRequiredListener("tabCompleteDisablerProtocolLib");
                } else {
                    // ProtocolLib not found, warn the user
                    Bukkit.getScheduler().scheduleSyncDelayedTask(this.plugin, new Runnable() {

                        @Override
                        public void run() {
                            AA_API.generateConsoleWarning("\n\n"
                                + "**************************************************\n"
                                + "** " + AA_API.__("config.protocollib-not-enabled-tabcomplete.1") + "\n"
                                + "** " + AA_API.__("config.protocollib-not-enabled-tabcomplete.2") + '\n'
                                + "** " + AA_API
                                .__("config.protocollib-not-enabled-tabcomplete.3", AA_API.getAaDataDir() + File.separator) + '\n'
                                + "**************************************************\n");
                        }

                    }, 0); // 0 = will be run as soon as the server finished loading
                }
            }
        }
    } //end method

    /**
     * Initializes command caches, either for a single player that just joined
     * the server, or completely, including all players on the server as well
     * as all permission groups found there.
     *
     * @param player Can be null or a real online player instance. If this is set to a player instance,
     *                       their commands will be added to the player commands cache. Otherwise all caches will be
     *                       invalidated and reloaded.
     */
    void init(Player player) {
        // no need to init if we don't have this feature enabled
        if ( ((AdminAnything) plugin).getConf().isDisabled(this.featureName) ) {
            return;
        }

        // re-initialize maps, if we're not appending commands for a single player
        if (null == player) {
            groupCommands = new HashMap<String, Map<String, Map<String, Boolean>>>();
            playerCommands = new HashMap<String, Map<String, Map<String, Boolean>>>();
        }

        // load and cache commands for each of the permission groups on the server
        Permissions               permUtils                = ((AdminAnything) plugin).getPermissionUtils();
        List<String>              helpDisablesCommandsList = AA_API.getCommandsList("helpDisables");
        Map<String, List<String>> disabledHelpCommandsMap  = new HashMap<String, List<String>>();
        FileConfiguration         permsFromConfig          = AA_API.getManualPermDescriptionsConfig();
        Player[] players;

        // if we're loading commands for a single player only,
        // do exactly that... otherwise, reload the map for all players on the server
        if (null == player) {
            players = Bukkit.getOnlinePlayers().toArray(new Player[Bukkit.getOnlinePlayers().size()]);
        } else {
            players = new Player[]{ player };
        }

        // iterate over all hidden commands and prepare them
        // for being checked in the next loop
        for (int i = 0; i < helpDisablesCommandsList.size(); i++) {
            // split the group and command and store them
            String group = helpDisablesCommandsList.get(i).substring(0, helpDisablesCommandsList.get(i).indexOf('.'));
            String commandLine = helpDisablesCommandsList.get(i).substring(helpDisablesCommandsList.get(i).indexOf('.') + 1);

            // create new ArrayList for this command group, if not set yet
            if (null == disabledHelpCommandsMap.get(group)) {
                disabledHelpCommandsMap.put(group, new ArrayList<String>());
            }

            // add the command to this group's list
            disabledHelpCommandsMap.get(group).add(commandLine);
        }

        try {
            // iterate over all loaded commands from the commandMap
            // and load their names, plugins and aliases
            for (final Map.Entry<String, Command> pair : AA_API.getAugmentedCommandMap().entrySet()) {
                String key              = pair.getKey();
                String pluginName       = null;

                // strip out the initial colon from commands that start on one (like :ping)
                if (key.startsWith(":")) {
                    key = key.substring(1);
                }

                try {
                    // if the command is not a standard one, it won't be castable to PluginCommand
                    // and the catch statements will take over
                    final PluginCommand pc = ((PluginCommand) pair.getValue());
                    pluginName = pc.getPlugin().getName();
                } catch (final ClassCastException e) {
                    // try the usual route
                    final PluginCommand pc = Bukkit.getPluginCommand(key);

                    // check if prefixed and try getting plugin name from the prefix
                    if ((null == pc) && key.contains(":")) {
                        final Plugin p = AA_API.getPluginIgnoreCase(key.substring(0, key.indexOf(':')));
                        if (null != p) {
                            pluginName = p.getName();
                        }
                    }

                    // non-prefixed, non-standard command
                    // ... we can only guess by its classname location here
                    if (null == pluginName) {
                        try {
                            pluginName = AA_API.guessPluginFromClass(pair.getValue().getClass());
                        } catch (final InvalidClassException e1) {
                            Bukkit.getLogger().severe("[" + AA_API.getAaName() + "] " + AA_API.__("plugin.error-plugin-for-command-not-found") + ": " + pair.getKey());
                            continue;
                        }
                    }
                }

                // name for a plugin not found
                if (null == pluginName) {
                    Bukkit.getLogger().severe('[' + AA_API.getAaName()
                        + "] " + AA_API.__("plugin.error-plugin-for-command-not-found") + ": " + pair.getKey());
                    continue;
                }

                // store clear command name (without colons) for futher processing
                final String clearCommandName = (pair.getKey().contains(":")
                                                 ? pair.getKey().substring(pair.getKey().indexOf(':') + 1)
                                                 : pair.getKey());
                final String lowerClearCommandName = clearCommandName.toLowerCase();
                final List<String> aliases = pair.getValue().getAliases();

                // check if this command is hidden globally
                if (disabledHelpCommandsMap.containsKey("global") && disabledHelpCommandsMap.get("global").contains(lowerClearCommandName)) {
                    // this command is globally hidden from everyone,
                    // let's continue with the next one
                    continue;
                }

                // let's see if we can get permissions for this command
                final Collection<String> tmpPerms = new ArrayList<String>();

                if (null != pair.getValue().getPermission() && !pair.getValue().getPermission().isEmpty()) {
                    // permission is present in the description file
                    tmpPerms.add(pair.getValue().getPermission());
                } else {
                    // permission not present in the description file, try our internal YML descriptions file
                    //noinspection HardCodedStringLiteral
                    final List<String> descriptionedPerms = permsFromConfig
                        .getStringList("manualPermissions." + pluginName.toLowerCase() + '.' + clearCommandName);

                    // strip perms of their descriptions
                    if (!descriptionedPerms.isEmpty()) {
                        for (final String descPerm : descriptionedPerms) {
                            // make sure it's not a custom command description
                            if (!descPerm.startsWith("$")) {
                                tmpPerms.add(descPerm.contains("=") ? descPerm.substring(0, descPerm.indexOf('=')) : descPerm);
                            }
                        }
                    }
                }


                /*
                 * check this command against all permission groups
                 * and if it's enabled for any of the groups in any of the worlds,
                 * add this into our cache
                 */
                if (null == player) {
                    for (String groupName : permUtils.getAllPermGroups()) {
                        // is the command disabled for this group?
                        if (disabledHelpCommandsMap.containsKey(groupName) && disabledHelpCommandsMap.get(groupName)
                                                                                                     .contains(lowerClearCommandName)) {
                            // command disabled for group, continue with the next group
                            continue;
                        }

                        // check command permissions in all worlds
                        for (final World world : Bukkit.getWorlds()) {
                            String worldName = world.getName();

                            // check all permissions for this command
                            for (final String perm : tmpPerms) {

                                // has this group in this world permission to run this command?
                                if (permUtils.checkGroupPermSimple(worldName, groupName, perm)) {
                                    // this group has permissions for this command, let's cache this knowledge...
                                    // group key
                                    if (null == groupCommands.get(groupName)) {
                                        groupCommands.put(groupName, new HashMap<String, Map<String, Boolean>>());
                                    }

                                    // group-world key
                                    if (null == groupCommands.get(groupName).get(worldName)) {
                                        groupCommands.get(groupName).put(worldName, new HashMap<String, Boolean>());
                                    }

                                    // group-world-command key
                                    groupCommands.get(groupName).get(worldName).put(clearCommandName, true);

                                    // we don't need to check any more permissions for this command
                                    break;
                                }
                            }
                        }
                    }
                }

                /*
                 * check this command for all players that are currently online
                 * or for just the single player that has been passed to this method
                 * and if it's enabled for them, add this into our cache
                 */
                for (Player p : players) {
                    String pName = p.getName();

                    // initialize player and their world keys to prevent NPEs
                    // player key
                    if (null == playerCommands.get(pName)) {
                        playerCommands.put(pName, new HashMap<String, Map<String, Boolean>>());
                    }

                    // player-world keys
                    for (final World world : Bukkit.getWorlds()) {
                        String worldName = world.getName();
                        if (null == playerCommands.get(pName).get(worldName)) {
                            playerCommands.get(pName).put(worldName, new HashMap<String, Boolean>());
                        }
                    }

                    String[] playerGroups = permUtils.getPlayerPermGroups(p);

                    // add the superglobal group, so we can do a lookup there as well, if it exists already
                    // ... if it doesn't exist, we are fully-reloading and thus all groups would still have
                    //     all (and duplicated) commands assigned
                    if (null != groupCommands.get(this.superGlobalGroupName)) {
                        ArrayList<String> newGroups = new ArrayList<String>(Arrays.asList(playerGroups));
                        newGroups.add(this.superGlobalGroupName);
                        playerGroups = newGroups.toArray( new String[ newGroups.size() ] );
                    }

                    // check all permissions for this command
                    for (final String perm : tmpPerms) {
                        if (permUtils.checkPermSimple(p, perm)) {
                            // this player has permissions for this command
                            // check if any of this player's group also has this command
                            // and if so, we can safely omit it from this player's list
                            // of commands, as we'd just duplicate it there
                            boolean groupHasCommand = false;
                            // check whether this command isn't disabled in one of this player's
                            // groups, in which case we also need to not add this command to player's
                            // available list of commands
                            boolean isDisabledInGroup = false;
                            for (String groupName : playerGroups) {
                                // if this group has access to this command in all worlds
                                // there is no need to also add it to player perms cache
                                // however, if group only has this permission in some worlds,
                                // then let's use this player's cache, as currently, we check
                                // whether a player has permission to a command, not a command in a world
                                boolean groupHasCommandInAllWorlds = true;
                                for (final World world : Bukkit.getWorlds()) {
                                    String worldName = world.getName();
                                    // check if group has this command in this world
                                    if (
                                        null != groupCommands.get( groupName ) &&
                                            null != groupCommands.get( groupName ).get( worldName ) &&
                                            null != groupCommands.get( groupName ).get( worldName )
                                                                 .get( clearCommandName )
                                    ) {
                                        // this player's group has this same command accessible
                                        groupHasCommand = true;
                                    } else {
                                        // this group doesn't have permission to this command in at least 1 world,
                                        // use player cache
                                        groupHasCommandInAllWorlds = false;
                                        groupHasCommand = false;

                                        // leave here, so we don't set groupHasCommand to true again and we can use
                                        // player's cache to store this command
                                        break;
                                    }
                                }

                                // check if this command is not disabled for this group
                                if (disabledHelpCommandsMap.containsKey(groupName) && disabledHelpCommandsMap.get(groupName)
                                                                                                             .contains(lowerClearCommandName)) {
                                    // this command is disabled for player's group
                                    isDisabledInGroup = true;
                                }

                                if ( (groupHasCommand && groupHasCommandInAllWorlds ) || isDisabledInGroup) {
                                    break;
                                }
                            }

                            // player's group has this permission,
                            // bail out and don't duplicate it in the players map
                            if (groupHasCommand || isDisabledInGroup) {
                                break;
                            }

                            // player-world-command key
                            // ... this extra null check is there for super-slow servers
                            //     which for one reason or another lag out while loading
                            //     and if a player joins the server in such state, playerCommands
                            //     will not yet be populated by their name and this would result in an NPE
                            if ( null != playerCommands.get(pName) ) {
                                for (final World world : Bukkit.getWorlds()) {
                                    playerCommands.get( pName ).get( world.getName() ).put( clearCommandName, true );
                                }
                            }

                            // we don't need to check any more permissions for this command
                            break;
                        }
                    }
                }
            }

            /*
             * Go through all the commands we've currently added to all the groups in all the worlds
             * and create a superglobal group in which we'll store commands that are common to all groups
             * in each of the worlds. This would eliminate a lot of commands duplication across groups
             * and will save memory and lookups later.
             */
            if (null == player) {
                if (null == groupCommands.get(this.superGlobalGroupName)) {
                    groupCommands.put(this.superGlobalGroupName, new HashMap<String, Map<String, Boolean>>());
                }

                // get the first group's name, so we can iterate over all commands in it,
                // which will be enough to see if its commands are present in all the other groups, too
                String firstGroupName = null;
                for (String groupName : groupCommands.keySet()) {
                    if (!groupName.equals(this.superGlobalGroupName)) {
                        firstGroupName = groupName;
                        break;
                    }
                }

                // if we actually have any groups...
                if (null != firstGroupName) {
                    // iterate over all worlds
                    for (Map.Entry<String, Map<String, Boolean>> worldPair : groupCommands.get(firstGroupName)
                                                                                          .entrySet()) {

                        // iterate over all commands for this particular group
                        for (Map.Entry<String, Boolean> commandPair : worldPair.getValue().entrySet()) {

                            // check if this command is present in all the groups
                            boolean commandInAllGroups = true;
                            for (String groupName : groupCommands.keySet()) {
                                if (
                                    // ignore superglobal group
                                    !groupName.equals(this.superGlobalGroupName) &&
                                        // ignore our group
                                        !groupName.equals(firstGroupName) &&
                                        // check if the command is in another group
                                        (
                                            null == groupCommands.get(groupName)
                                            ||
                                            null == groupCommands.get(groupName).get(worldPair.getKey())
                                            ||
                                            null == groupCommands.get(groupName).get(worldPair.getKey())
                                                                 .get(commandPair.getKey())
                                        )
                                ) {
                                    // command not found in one of the other groups
                                    commandInAllGroups = false;
                                    break;
                                }
                            }

                            // we found a common command for this world,
                            // let's add it to the superglobal group
                            if (commandInAllGroups) {
                                if (null == groupCommands.get(this.superGlobalGroupName).get(worldPair.getKey())) {
                                    groupCommands.get(this.superGlobalGroupName)
                                                 .put(worldPair.getKey(), new HashMap<String, Boolean>());
                                }

                                groupCommands.get(this.superGlobalGroupName).get(worldPair.getKey())
                                             .put(commandPair.getKey(), true);
                            }
                        }

                    }

                    // we now have all the superglobal commands set and ready,
                    // remove them from all the other groups
                    for (Map.Entry<String, Map<String, Boolean>> worldPair : groupCommands
                        .get(this.superGlobalGroupName).entrySet()) {
                        // iterate over all commands for our superglobal group
                        for (Map.Entry<String, Boolean> commandPair : worldPair.getValue().entrySet()) {
                            // iterate over all other groups and remove this command from them
                            for (String groupName : groupCommands.keySet()) {
                                // ignore the superglobal group
                                if (groupName.equals(this.superGlobalGroupName)) {
                                    continue;
                                }
                                groupCommands.get(groupName).get(worldPair.getKey()).remove(commandPair.getKey());
                            }
                        }
                    }
                }
            }

            // if we're on 1.13+ server, let player clients know to update their client-side tab-completions
            try {
                Class.forName("org.bukkit.event.player.PlayerCommandSendEvent");
                for (Player p : players) {
                    // if AA is not fully loaded yet, let's wait and fire up these only after its full load,
                    // otherwise our PlayerCommandSendEvent will not react
                    if (AA_API.isWarmingUp()) {
                        Bukkit.getScheduler().scheduleSyncDelayedTask(this.plugin, new Runnable() {
                            @Override
                            public void run() {
                                p.updateCommands();
                            }
                        }, 20 * 12); // full AA load takes 10 seconds, let's wait 12
                    } else {
                        // warmup period has passed, update this player's commands after 2 seconds,
                        // as we may have come from /op or /deop command and consequently from
                        // the actual PlayerCommandSendEvent event already - which would create a stupid loop
                        // with errors until our player's commands are cached
                        Bukkit.getScheduler().scheduleSyncDelayedTask(this.plugin, new Runnable() {
                            @Override
                            public void run() {
                                p.updateCommands();
                            }
                        }, 20 * 2);
                    }
                }
            } catch (Throwable ex) {
                // we're on 1.12.2 or lower server version, and there's no special function to call for all players here
            }
        } catch (final IllegalArgumentException | IllegalAccessException | NoSuchMethodException | SecurityException
            | InvocationTargetException | AccessException e) {
            Bukkit.getLogger().severe(AA_API.__("commands.tabcomplete-failed-to-init"));
            e.printStackTrace();
        }
    } // end method

    /**
     * Gets all commands available to a single player in their current world.
     *
     * @param p The player to retrieve commands for.
     *
     * @return Returns a list of all commands currently available to the given player on this server in player's current world.
     */
    List<String> getPlayerAvailableCommands(Player p) {
        // if this player is not yet in the map, this is being called from the PlayerCommandSendEvent event
        // and before the PlayerJoinEvent event, so just disable all tab-completions until we reload them via
        // the init() method in the PlayerJoinEvent event
        if (null == playerCommands.get(p.getName())) {
            return new ArrayList<String>();
        }

        // all players would have commands that are in the superglobal group,
        // so start with those
        String worldName = p.getWorld().getName();
        List<String> cmds = new ArrayList<String>();
        // there could be no superglobal commands at all, so make sure we have any for this world
        if (null != groupCommands.get(this.superGlobalGroupName) && null != groupCommands.get(this.superGlobalGroupName).get( worldName )) {
            cmds = new ArrayList<String>(groupCommands.get(this.superGlobalGroupName).get(worldName).keySet());
        }

        // get all groups for this player and add any commands not present in the supergroup
        for (String groupName : ((AdminAnything) this.plugin).getPermissionUtils().getPlayerPermGroups( p )) {
            if (null != groupCommands.get(groupName) && null != groupCommands.get(groupName).get(worldName)) {
                cmds.addAll(groupCommands.get(groupName).get(worldName).keySet());
            }
        }

        // add commands that are only available to this player
        if (null != playerCommands.get(p.getName()).get(worldName)) {
            cmds.addAll(playerCommands.get(p.getName()).get(worldName).keySet());
        }

        // return all available commands
        return cmds;
    } // end method

    /**
     * Used on AdminAnything startup (i.e. in onEnable).
     * Parses all commands that are in plugin.yml file for this plugin
     * and registers their tab completers one by one to the correct classes,
     * should these commands have tab completers present.
     * This is possible, as classes follow the same naming convention
     * as commands, i.e. Aa_fixcommand.class = /aa_fixcommand.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * // usage in AdminAnything main class
     * tabcomplete = new TabComplete(this);
     * tabcomplete.registerTabCompleters();
     * </pre>
     */
    void registerTabCompleters() {
        ConfigAbstractAdapter config = ((AdminAnything) plugin).getConf();

        if (null != config.getInternalConf().getCommands()) {
            // iterate all AA commands and register their tab completers, one by one
            for (final String cmd : AA_API.getCommandsKeySet()) {
                // don't register tab completers for disabled commands
                if (!config.isDisabled(cmd.replaceAll("aa_", ""))) { //NON-NLS
                    try {
                        final Class<?> cl = Class.forName("com.martinambrus.adminAnything.tabcomplete." + Utils.capitalize(cmd));

                        TabCompleter tc = (TabCompleter) cl.getConstructor().newInstance();
                        ((JavaPlugin) plugin).getCommand(cmd).setTabCompleter(tc);
                    } catch (final NoClassDefFoundError | ClassNotFoundException e1) {
                        // older versions (1.7-) do not support tab completion
                        // and neither have all commands tab completers
                    } catch (final Throwable e2) {
                        Bukkit.getLogger().severe('[' + config.getPluginName()
                            + "] " + AA_API.__("error.failed-to-register-tab-completer", cmd));
                        e2.printStackTrace();
                    }
                }
            }
        }
    } // end method

    /**
     * Reacts to a PlayerJoinEvent event in order to update players cache map
     * or potentially even reload all the maps if the new player belongs
     * to a non-cached permission group.
     *
     * @param e The actual PlayerJoinEvent even.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void playerJoin(final PlayerJoinEvent e) {
        // if this player is already in the cache, bail out
        // since the procedure below was already called for them
        if (null != playerCommands.get(e.getPlayer().getName())) {
            return;
        }

        // check if player belongs to any of the groups that are currently cached
        for (String groupName : ((AdminAnything)this.plugin).getPermissionUtils().getPlayerPermGroups(e.getPlayer())) {
            if (null == groupCommands.get(groupName)) {
                // player belongs into a non-cached group,
                // we need to reload our caches
                Bukkit.getScheduler().runTaskAsynchronously( this.plugin, new Runnable() {
                    @Override
                    public void run() {
                        TabComplete.this.init(null);
                    }
                } );
                return;
            }
        }

        // if we got here, the player is in one of the cached groups
        // and we can simply add their extra permissions
        Bukkit.getScheduler().runTaskAsynchronously( this.plugin, new Runnable() {
            @Override
            public void run() {
                TabComplete.this.init( e.getPlayer() );
            }
        } );
    } // end method

    /**
     * Reacts to the player quit event, so the player who left
     * can be removed from the cache after a timeout.
     *
     * @param e The actual PlayerQuitEvent even.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void playerLeave(final PlayerQuitEvent e) {
        Bukkit.getScheduler().runTaskLater(this.plugin, new Runnable() {
            @Override
            public void run() {
                String pName = e.getPlayer().getName();
                if (null == Bukkit.getPlayer(pName) && null != playerCommands.get(pName)) {
                    playerCommands.remove(pName);
                }
            }
        }, 20 * 60 * 7); // 7 minutes delay before we clear the cache of this player, in case he returns shortly
    } // end method

    /**
     * Reacts to an event of saving help disables and sets up a delayed task to reload tab-completions
     * for all users on the server. This is, so the administrator managing list of help-disables can see
     * the tab-complete list changes right away.
     *
     * @param e The actual AASaveCommandHelpDisablesEvent event.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void tabDisablesReload(final AASaveCommandHelpDisablesEvent e) {
        // first of all, cancel any old scheduled events
        if (this.tabReloadWaitingTask != null) {
            this.tabReloadWaitingTask.cancel();
            this.tabReloadWaitingTask = null;
        }

        // create a new waiting task to reinitialize the tab-completes cache
        this.tabReloadWaitingTask = Bukkit.getScheduler().runTaskLaterAsynchronously(this.plugin, new Runnable() {
            @Override
            public void run() {
                TabComplete.this.tabReloadWaitingTask = null;
                TabComplete.this.init(null);
            }
        }, 20 * 5);
    }

    /***
     * React to the custom ReloadEvent which is fired when <b><i>/aa_reload</i></b> gets executed
     * or when we enable AA for the first time.
     *
     * @param e The actual reload event with message that says who is this reload for.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void reload(final AAReloadEvent e) {
        // remove previously cached data to release their memory,
        // as we're going to be reloading them from potentially another copy
        // of the TabComplete class
        groupCommands = new HashMap<String, Map<String, Map<String, Boolean>>>();
        playerCommands = new HashMap<String, Map<String, Map<String, Boolean>>>();
    } // end method

} // end class