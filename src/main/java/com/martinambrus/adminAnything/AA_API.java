package com.martinambrus.adminAnything;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import java.io.InvalidClassException;
import java.lang.reflect.InvocationTargetException;
import java.rmi.AccessException;
import java.security.InvalidParameterException;
import java.util.Map.Entry;
import java.util.*;

/**
 * API for AdminAnything used by AA's own procedures
 * but also publicly available to other plugins.
 *
 * @author Martin Ambrus
 */
@SuppressWarnings({"ClassWithTooManyMethods", "UtilityClassWithoutPrivateConstructor"})
public final class AA_API {

    /**
     * Instance of {@link com.martinambrus.adminAnything.AdminAnything}.
     */
    private static AdminAnything aa;

    /**
     * Constructor, stores an instance of {@link com.martinambrus.adminAnything.AdminAnything},
     * so this API can work with it. Not publicly available and only ever called from the main
     * AdminAnything class' constructor.
     *
     * @param aa Instance of {@link com.martinambrus.adminAnything.AdminAnything AdminAnything}.
     */
    public AA_API(final AdminAnything aa) {
        AA_API.aa = aa;
    } // end method

    /**
     * Returns name of the AdminAnything plugin. If it ever changes, we don't have to string-replace
     * all AdminAnything names in all files where console is used to log exceptions.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * Bukkit.getLogger().warning("[" + AA_API.getAaName() + "] Something's gone wrong :-P");
     * </pre>

     *
     * @return Returns the string value from AA's configuration.
     */
    public static String getAaName() {
        return aa.getDescription().getName();
    } // end method

    /**
     * Returns a boolean value from AA's configuration.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * boolean chatNickClicksFeature = AA_API.getConfigBoolean("features.chatnicklinks.enabled");
     * </pre>
     *
     * @param key The config key we want to return a boolean value for.
     *
     * @return Returns the boolean value from AA's configuration.
     */
    public static boolean getConfigBoolean(final String key) {
        return aa.getExternalConf().getBoolean(key);
    } // end method

    /**
     * Returns a string value from AA's configuration.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * String chatNickClicksColor = AA_API.getConfigString("chatNickClickActions." + key + ".color");
     * </pre>
     *
     * @param key The config key we want to return a string value for.
     *
     * @return Returns the string value from AA's configuration.
     */
    public static String getConfigString(final String key) {
        return aa.getExternalConf().getString(key);
    } // end method

    /**
     * Returns a string value from AA's configuration.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * String listCommandsSeparator = AA_API.getConfigString("listCommandsSeparator", " ");
     * </pre>
     *
     * @param key The config key we want to return a string value for.
     * @param def Default value if string is not found in the configuration.
     *
     * @return Returns the string value from AA's configuration.
     */
    public static String getConfigString(final String key, final String def) {
        return aa.getExternalConf().getString(key, def);
    } // end method

    /**
     * Returns keys of a configuration section from AA's configuration.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * {@code
     * for (final String key : AA_API.getConfigSectionKeys("chatNickClickActions")) {
     *     // do something with the configuration key name ("key" variable) here
     * }
     * }
     * </pre>
     *
     * @param key The configuration section the keys for we want to return.
     *
     * @return Returns keys for the requested configuration section.
     */
    public static Iterable<String> getConfigSectionKeys(final String key) {
        if ( null != aa.getExternalConf().getConfigurationSection(key) ) {
            return aa.getExternalConf().getConfigurationSection( key ).getKeys( false );
        } else {
            return null;
        }
    } // end method

    /**
     * Returns the data folder for AdminAnything.<br>
     * This is used in various warning messages in console to inform Admins where to search for
     * files they need to update (for example to turn off statistics etc.)
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * virtualPermissionsList.save(new File(AA_API.getAaDataDir(), "perms.yml"));
     * </pre>
     *
     * @return Returns the full data folder path for AdminAnything.
     */
    public static String getAaDataDir() {
        return aa.getDataFolder().toString();
    } // end method

    /**
     * Gets the plugin version
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * Bukkit.getLogger().log("info", "You are running AdminAnything, version " + AA_API.getAaVersion());
     * </pre>
     *
     * @return Returns version of AdminAnything.
     */
    public static String getAaVersion() {
        return aa.getDescription().getVersion();
    } // end method

    /**
     * Checks whether a feature in AdminAnything is enabled via config.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * {@code
     * if (AA_API.isFeatureEnabled("listcommands")) {
     *     Bukkit.getLogger().log("info", "Listing of commands is enabled in AdminAnything.");
     * }
     * }
     * </pre>
     *
     * @param featureConfigKey The feature name in the config file (for example "addperm" to check
     *                         the "features.addperm.enabled" key).
     *
     * @return Returns true if the requested feature is enabled, false otherwise.
     */
    public static boolean isFeatureEnabled(final String featureConfigKey) {
        return !aa.getConf().isDisabled(featureConfigKey);
    } // end method

    /**
     * Gets the maximum records (lines) per page displayable in various AdminAnything listings,
     * such as list of commands, permissions or conflicts.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * {@code
     * if (recordsNumber > AA_API.getMaxRecordsPerPage()) {
     *     Bukkit.getLogger().log("info", "Pagination should be enabled here, as we've requested more records than we allow per one page.");
     * }
     * }
     * </pre>
     *
     * @return Returns the maximum number of records (lines) per one page to be returned to the client.
     */
    public static double getMaxRecordsPerPage() {
        return aa.getConf().getChatMaxPerPageRecords();
    } // end method

    /**
     * Gets this plugin's debug state.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * {@code
     * if (AA_API.getDebug()) {
     *     Bukkit.getLogger().log("debug", "AdminAnything debug information line.");
     * }
     * }
     * </pre>
     *
     * @return Returns true if debug is turned ON, false otherwise.
     */
    public static boolean getDebug() {
        return aa.getConf().getDebug();
    } // end method

    /**
     * Get list of built-in commands for a Spigot server.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * {@code
     * if (AA_API.getBuiltInCommands().contains("ban")) {
     *     Bukkit.getLogger().log("info", "/ban is a Minecraft built-in server command.");
     * }
     * }
     * </pre>
     *
     * @return Returns a hard-coded list of core commands from the CommandMap of Spigot
     * as of version 1.12. There are commands from all - Minecraft, CraftBukkit and Spigot.
     */
    public static Collection<String> getBuiltInCommands() {
        return aa.getConf().getBuiltInCommands();
    } // end method

    /**
     * Retrieves a set of all commands for AdminAnything.
     * This is used when registering command executors, tab completers,
     * as well as in class transformations due to enabled commands muting.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * {@code
     * for (final String cmd : AA_API.getCommandsKeySet()) {
     *   // register tab listener for the command in question
     *   // note: exception handling omitted for brevity
     *   final Class<?> cl = Class.forName("com.martinambrus.adminAnything.tabcomplete." + cmd.substring(0, 1).toUpperCase() + cmd.substring(1));
     *
     *   TabCompleter tc = (TabCompleter) cl.getConstructor().newInstance();
     *   ((JavaPlugin) plugin).getCommand(cmd).setTabCompleter(tc);
     * }
     * }
     * </pre>
     *
     * @return Returns a set of names for all commands of AdminAnything.
     */
    public static Iterable<String> getCommandsKeySet() {
        // when AA instance is null, this method is being called from a transform agent during instrumentation,
        // i.e. when using the /aa_mutecommand functionality, so we'll need to get AA commands set manually
        if (null == aa) {
            return Bukkit.getServer().getPluginManager().getPlugin("AdminAnything").getDescription().getCommands().keySet();
        }

        return aa.getConf().getInternalConf().getCommands().keySet();
    } // end method

    /**
     * Checks whether the given key is present in the plugin configuration.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * {@code
     * if (AA_API.configContainsKey("listcommandsDefaults.showdescriptions")) {
     *   // config contains a default setting for showing/hiding command descriptions
     *   // when using /aa_listcommands
     * }
     * }
     * </pre>
     *
     * @return Returns true if the key is present in configuration, false otherwise.
     */
    public static boolean configContainsKey(final String key) {
        return aa.getExternalConf().contains(key);
    } // end method

    /**
     * Checks whether the given plugin has full access to AdminAnything's API,
     * including all data adjusting event calls (i.e. to add/remove records to/from lists
     * of fixed, muted, disabled... commands etc.).
     *
     * @param pluginName Name of the plugin to check for. Letter case is ignored.
     *
     * @return Returns true if the given plugin has full access to AA's API, false otherwise.
     */
    public static boolean pluginHasFullApiAccess(String pluginName) {
        return aa.getConf().pluginHasFullApiAccess(pluginName);
    } // end method

    /**
     * Loads list of IP ban commands from the config file, since different MC versions
     * tend to use different ban-ip commands and the server could even have its own flavor
     * of ban-ip from a plugin.
     *
     * @return Returns list of all commands AdminAnything should consider as IP-ban ones.
     */
    public static Collection<String> getBanIpCommandsList() {
        return aa.getConf().getBanIpCommandsList();
    } // end method

    /**
     * Loads join and click event links that will be displayed in chat
     * next to player username's if enabled in config.
     */
    public static void loadJoinLeaveClickLinks() {
        aa.getConf().loadJoinLeaveClickLinks();
    } // end method

    /**
     * Loads nick click links that will be displayed in chat
     * next to player username's if enabled in config.
     */
    public static void loadNickClickLinks() {
        aa.getConf().loadNickClickLinks();
    } // end method

    /**
     * Loads chat nick click event links for an in-game chest GUI
     * if enabled in config.
     */
    public static void loadNickGUIItems() {
        aa.getConf().loadNickGUIItems();
    } // end method

    /**
     * Gets map of actions to be added to the chat next to player's
     * username when they join the server, if enabled.
     *
     * @return Returns the map of actions to be added to player's username
     *         when they join the server.
     */
    public static Map<String, Map<String, String>> getChatJoinActionsMap() {
        return aa.getConf().getChatJoinActionsMap();
    } // end method

    /**
     * Gets map of actions to be added to the chat next to player's
     * username when they leave the server, if enabled.
     *
     * @return Returns the map of actions to be added to player's username
     *         when they leave the server.
     */
    public static Map<String, Map<String, String>> getChatLeaveActionsMap() {
        return aa.getConf().getChatLeaveActionsMap();
    } // end method

    /**
     * Gets map of actions to be added to the chat next to player's
     * username when a chat message is sent, if enabled.
     *
     * @return Returns the map of actions to be added to player's username
     *         when a chat message is sent.
     */
    public static Map<String, Map<String, String>> getNickClickActionsMap() {
        return aa.getConf().getNickClickActionsMap();
    } // end method

    /**
     * Gets map of items to be added to the chest GUI when a player clicks
     * on the [A] link in chat next to other player's nickname, if enabled.
     *
     * @return Returns the map of items to be added a chest GUI.
     */
    public static Map<String, Map<String, String>> getGUIItemsMap() {
        return aa.getConf().getGUIItemsMap();
    } // end method

    /**
     * Checks whether a named listener is already registered with AdminAnything.
     * This method is used to prevent the same event listener being registered more than once.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * {@code
     * if (!AA_API.isListenerRegistered("chatJoinLeaveClicks")) {
     *   // register an event listener to display join and leave click links next to nicknames
     *   // in chat
     * }
     * }
     * </pre>
     *
     * @param listenerName Name of the listener to check for.
     *
     * @return Returns true if the given listener name is already registered, false otherwise.
     */
    public static boolean isListenerRegistered(final String listenerName) {
        return aa.getListenerUtils().isListenerRegistered(listenerName);
    } // end method

    /***
     * Registers a listener if that listener was not registered already.
     * Can be used to lazy-register listeners from various listener classes.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * // register an event listener to display join and leave click links next to nicknames in chat
     * AA_API.startRequiredListener("chatJoinLeaveClicks");
     * </pre>
     *
     * @param listenerName ClassName of the listener to register. This class will then be loaded
     *                     from <i>com.martinambrus.adminAnything.listeners.[[ listenerName ]]</i>
     */
    public static void startRequiredListener(final String listenerName) {
        aa.getListenerUtils().startRequiredListener(listenerName);
    } // end method

    /***
     * Registers a listener if that listener was not registered already.
     * Can be used to lazy-register listeners from various listener classes.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * AA_API.startRequiredListener("yourListenerName", yourListenerClassInstance);
     * </pre>
     *
     * @param listenerName          An arbitrary name of the listener to register. AA uses its feature names
     *                              to register event listeners this way.
     * @param listenerClassInstance The actual instance of the listener class we'd like to register.
     */
    public static void startRequiredListener(final String listenerName, final Listener listenerClassInstance) {
        aa.getListenerUtils().startRequiredListener(listenerName, listenerClassInstance);
    } //end method

    /***
     * Generates a console warning. In AdminAnything, this method is used when AA is starting or reloading
     * to show any and all error messages that a server Admin should be aware of.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * AA_API.generateConsoleWarning("You shouldn't eat yellow snow!");
     * </pre>
     *
     * @param warningText The actual text to display in console.
     */
    public static void generateConsoleWarning(final String warningText) {
        Bukkit.getLogger().warning('[' + getAaName() + ']' + warningText);
    } //end method

    /***
     * Uses a query parsing algorithm to check for one or many permission nodes given
     * via the permsQuery parameter.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * {@code
     * if (aa_plugin_instance.getPluginUtils().checkPerms(sender, "my_permission", true) {
     *  // do your stuff
     *  // ... if the commandSender (sender) OR their permission groups do not have the relevant permission,
     *  //     an error message will be displayed to the sender
     * }
     * }
     * </pre>
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * {@code
     * if (aa_plugin_instance.getPluginUtils().checkPerms(sender, "my_permission", true, true) {
     * // do your stuff
     * // ... if the commandSender (sender) themselves does not have the relevant permission,
     * //     an error message will be displayed to them
     * }
     * }
     * </pre>
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * {@code
     * if (aa_plugin_instance.getPluginUtils().checkPerms(sender, "(perm1 OR perm2) AND perm3", false) {
     *  // do your stuff
     *  // ... in this case, no error message is displayed to the command sender (last parameter is false)
     *  //     and you can handle this case with your own logic
     * }
     * }
     * </pre>
     *
     * @param sender     The org.bukkit.command.CommandSender CommandSender who we're checking the permission(s) for.
     * @param permsQuery An SQL-like query containing all the parameters that we need to check for.<br>
     *                   The syntax is the same as used in SQL queries with AND / OR sections. Parenthesis are important, as is
     *                   remembering the general rule of operator priority, which says that AND has a higher priority over OR.<br>
     *                   That means that any AND statements will be evaluated first.<br><br>
     *                   <u>Example</u>: <b><i>perm1 AND perm2</i></b><br>&nbsp;&nbsp;&raquo; will check for both, perm1 and perm2 permissions<br><br>
     *                   <u>Example</u>: <b><i>perm1 OR perm2</i></b><br>&nbsp;&nbsp;&raquo; will check for either perm1 or perm2 permission<br><br>
     *                   <u>Example</u>: <b><i>perm1 AND perm2 OR perm3</i></b><br>&nbsp;&nbsp;&raquo; will check for either perm1 + perm2, or the perm3 permission<br><br>
     *                   <u>Example</u>: <b><i>perm1 AND (perm2 OR perm3)</i></b><br>&nbsp;&nbsp;&raquo; the use of parenthesis now changes the previous example,
     *                                     so perm1 + EITHER ONE of perm2 or perm3 permissions will be checked
     * @param showResultToSender If TRUE, a message will be sent out to the player / console for who we're checking these permissions.
     *
     * @return Returns true if the sender has the requested permission(s), false otherwise.
     */
    public static boolean checkPerms(final CommandSender sender, final String permsQuery, final boolean showResultToSender) {
        return aa.getPermissionUtils().checkPerms(sender, permsQuery, showResultToSender);
    } //end method

    /***
     * Checks whether the given CommandSender's primary group has the requested permission assigned.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * {@code
     * if (AA_API.checkGroupPerm(sender, "my_permission") {
     *  // do your stuff
     * }}
     * </pre>
     *
     * @param sender The org.bukkit.command.CommandSender CommandSender whose group we're checking the permission(s) for.
     * @param perm   The actual permission we're checking for.
     *
     * @return Returns true if the sender's primary group has the requested permission, false otherwise (or if Vault is not enabled).
     */
    public static boolean checkGroupPerm(final CommandSender sender, final String perm) {
        return aa.getPermissionUtils().checkGroupPermSimple(sender, perm);
    } //end method

    /***
     * Checks whether the given group in the given world has the requested permission assigned.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * {@code
     * if (AA_API.checkGroupPerm("world", "default", "my_permission") {
     *  // do your stuff
     * }}
     * </pre>
     *
     * @param worldName Name of the world for the perm group check.
     * @param groupName Name of the group we're performing the check for.
     * @param perm      The actual permission we're checking.
     *
     * @return Returns true if the sender's primary group has the requested permission, false otherwise (or if Vault is not enabled).
     */
    public static boolean checkGroupPerm(final String worldName, final String groupName, final String perm) {
        return aa.getPermissionUtils().checkGroupPermSimple(worldName, groupName, perm);
    } //end method

    /**
     * Checks whether <a href="https://dev.bukkit.org/projects/vault" target="_blank"><b>Vault</b></a> is enabled on the server.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * {@code
     * if (AA_API.isVaultEnabled()) {
     *   // Vault is enabled, we should hook in to check player permissions
     * }
     * }
     * </pre>
     *
     * @return Returns true if <a href="https://dev.bukkit.org/projects/vault" target="_blank"><b>Vault</b></a> is enabled, false otherwise.
     */
    public static boolean isVaultEnabled() {
        return aa.getPermissionUtils().isVaultEnabled();
    } // end method

    /**
     * Checks whether a player is in the given permission group.
     * This check is done via Vault's API and if <a href="https://dev.bukkit.org/projects/vault" target="_blank"><b>Vault</b></a> is not present
     * or enabled, it will return TRUE in case the group name provided
     * was either "global" or "default", FALSE otherwise.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     *  {@code
     *  if (AA_API.isPlayerInPermGroup( playerInstance, "owners" )) {
     *    // player is in the "Owners" group, let's roll...
     *  }
     *  }
     *  </pre>
     *
     * @param p Player for which we want to check presence in the permision group.
     * @param group The actual permission group name to check if the player is in.
     *
     * @return Returns TRUE if the player is present in the group, or if <a href="https://dev.bukkit.org/projects/vault" target="_blank"><b>Vault</b></a> is disabled,
     *         then returns TRUE if the player is in either group named "global" or "default".
     *         Returns FALSE otherwise.
     */
    public static boolean isPlayerInPermGroup(Player p, String group) { return aa.getPermissionUtils().isPlayerInPermGroup(p, group); } // end method

    /**
     * Returns all permission groups in which a player is present.
     * If <a href="https://dev.bukkit.org/projects/vault" target="_blank"><b>Vault</b></a> is disabled, an empty array is returned.
     *
     * @param p The player to check groups for.
     *
     * @return Returns all permission groups in which a player is present.
     *         If <a href="https://dev.bukkit.org/projects/vault" target="_blank"><b>Vault</b></a> is disabled, an empty array is returned.
     */
    public static String[] getPlayerPermGroups(Player p) { return aa.getPermissionUtils().getPlayerPermGroups(p); } // end method

    /**
     * Retrieves player's primary group name.
     *
     * @param p Player to retrieve primary group info for.
     *
     * @return Returns name of player's primary group or "" (empty string) if <a href="https://dev.bukkit.org/projects/vault" target="_blank"><b>Vault</b></a> is disabled.
     */
    public static String getPlayerPrimaryPermGroup(Player p) { return aa.getPermissionUtils().getPlayerPrimaryPermGroup(p); } // end method

    /**
     * Checks whether the command map contains the given key.
     * The key will be a full command name, such as "essentials:ban".
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * {@code
     * if (AA_API.commandMapContainsKey("essentials:ban")) {
     *   // essentials:ban found to be a real command from the command map
     *   // and we can immediately execute it
     * }
     * }
     * </pre>
     *
     * @param key The actual command name we want to look up in the command map.
     *
     * @return Returns true if the command map contains the given command, false otherwise.
     *
     * @throws IllegalAccessException When access is denied to org.bukkit.command.CommandMap or org.bukkit.command.SimpleCommandMap.knownCommands field.
     * @throws NoSuchMethodException When there is a get() method missing from the org.bukkit.command.SimpleCommandMap.knownCommands field.
     * @throws SecurityException When private fields cannot be accessed due to a security restriction.
     * @throws InvocationTargetException When we try to invoke get() on org.bukkit.command.SimpleCommandMap.knownCommands with invalid org.bukkit.command.CommandMap parameter.
     * @throws AccessException When we don't have the permission to access the org.bukkit.command.SimpleCommandMap.knownCommands field of commandMap.
     */
    @SuppressWarnings("JavadocReference")
    public static boolean commandMapContainsKey(final String key) throws AccessException, IllegalAccessException,
    NoSuchMethodException, SecurityException, InvocationTargetException {
        return aa.getCommandsUtils().getCommandMap().containsKey(key);
    }

    /**
     * Retrieves an actual command from the command map according to the given command name.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * // ban ZathrusWriter using essentials' ban command
     * AA_API.getCommandMapKey("essentials:ban").execute(Bukkit.getConsoleSender(), "ban", "ZathrusWriter");
     * </pre>
     *
     * @param key The command name we want to retrieve the actual command for.
     *
     * @return Returns the actual command retrieved from the command map according to the given command name.
     *
     * @throws IllegalAccessException When access is denied to the org.bukkit.command.CommandMap or org.bukkit.command.SimpleCommandMap.knownCommands field.
     * @throws NoSuchMethodException When there is a get() method missing from the org.bukkit.command.SimpleCommandMap.knownCommands field.
     * @throws SecurityException When private fields cannot be accessed due to a security restriction.
     * @throws InvocationTargetException When we try to invoke get() on org.bukkit.command.SimpleCommandMap.knownCommands with invalid org.bukkit.command.CommandMap parameter.
     * @throws AccessException When we don't have the permission to access the org.bukkit.command.SimpleCommandMap.knownCommands field of org.bukkit.command.CommandMap.
     */
    @SuppressWarnings("JavadocReference")
    public static Command getCommandMapKey(final String key) throws AccessException, IllegalAccessException,
    NoSuchMethodException, SecurityException, InvocationTargetException {
        return aa.getCommandsUtils().getCommandMap().get(key);
    } // end method

    /**
     * Returns a copy of the entry set for the command map.
     * This is used in the runnable part of <b><i>/aa_checkcommandconflicts</i></b> and is coded this way,
     * so we don't give write access to the actual command map.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * {@code
     * for (final Entry<String, Command> pair : AA_API.getCommandMapCopy().entrySet()) {
     *   // pair.getKey() will contain the command name of each command registered via CommandMap on the server
     *   // pair.getValue() will contain the actual command registered via CommandMap
     * }}
     * </pre>
     *
     * @return Returns a clone of the server's command map.
     *
     * @throws IllegalAccessException When access is denied to the org.bukkit.command.CommandMap or org.bukkit.command.SimpleCommandMap.knownCommands field.
     * @throws NoSuchMethodException When there is a get() method missing from the org.bukkit.command.SimpleCommandMap.knownCommands field.
     * @throws SecurityException When private fields cannot be accessed due to a security restriction.
     * @throws InvocationTargetException When we try to invoke get() on org.bukkit.command.SimpleCommandMap.knownCommands with invalid org.bukkit.command.CommandMap parameter.
     * @throws AccessException When we don't have the permission to access the org.bukkit.command.SimpleCommandMap.knownCommands field of org.bukkit.command.CommandMap.
     */
    @SuppressWarnings("JavadocReference")
    public static Map<String, Command> getCommandMapCopy() throws AccessException, IllegalAccessException,
    NoSuchMethodException, SecurityException, InvocationTargetException {
        Map<String, Command> newMap = new HashMap<String, Command>();
        for (final Entry<String, Command> pair : aa.getCommandsUtils().getCommandMap().entrySet()) {
            newMap.put(pair.getKey(), pair.getValue());
        }

        return newMap;
    }

    /**
     * Augments and returns a temporary copy of a commandMap with manually-crafter commands from permdescriptions.yml file,
     * or rather mostly sub-commands of a single command (/plugman help, /plugman info...),
     * so these can be shown in /aa_playercommands and /aa_listcommands.
     *
     * @return Returns a copy of server's commandMap, augmented with extra commands from the from permdescriptions.yml file.
     *
     * @throws IllegalAccessException When access is denied to the {@link org.bukkit.command.CommandMap commandMap} or {@link org.bukkit.command.SimpleCommandMap.knownCommands knownCommands} field.
     * @throws NoSuchMethodException When there is a get() method missing from the {@link org.bukkit.command.SimpleCommandMap.knownCommands knownCommands} field.
     * @throws InvocationTargetException When we try to invoke get() on {@link org.bukkit.command.SimpleCommandMap.knownCommands knownCommands} with invalid {@link org.bukkit.command.CommandMap commandMap} parameter.
     * @throws AccessException When we don't have the permission to access the {@link org.bukkit.command.SimpleCommandMap.knownCommands knownCommands} field of {@link org.bukkit.command.CommandMap commandMap}.
     */
    @SuppressWarnings({"unchecked", "JavadocReference"})
    public static Map<String, Command> getAugmentedCommandMap()
        throws InvocationTargetException, NoSuchMethodException, AccessException, IllegalAccessException {
        return aa.getCommandsUtils().getAugmentedCommandMap();
    }

    /***
     * Returns a clear command name from the command given.
     * Used when a command is prefixed (i.e. minecraft:ban or essentials:ban).
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * // will hold "ban"
     * String clearBan = AA_API.getClearCommand("minecraft:ban")[0];
     * </pre>
     *
     * @param cmd The actual command to return stripped of dots and colons.
     *
     * @return Returns a command that's stripped-down version of the original commands,
     *         i.e. without any dots or colons.
     */
    public static String[] getClearCommand(final String cmd) {
        return aa.getCommandsUtils().getClearCommand(cmd);
    } // end method

    /***
     * Returns name of the plugin to which the given command belongs.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * {@code
     * for (final Entry<String, Command> pair : AA_API.getCommandMapCopy().entrySet()) {
     *     Bukkit.getLogger().log("info", "Command '" + pair.getKey() + "' is from the plugin '" + AA_API.getPluginForCommand(pair.getKey(), pair.getValue()) + "'");
     * }
     * }
     * </pre>
     *
     * @param key The commands name to lookup a plugin name for.
     * @param value The actual command we're trying to lookup a plugin for. Used for a short-circuited
     *              version of plugin checking when org.bukkit.command.Command Command} is actually
     *              an instance of org.bukkit.command.PluginCommand PluginCommand}, from which the plugin
     *              name retrieval is trivial.
     *
     * @return Returns name of the plugin to which the given command belongs.
     * @throws InvalidClassException When this plugin's class file is not found in the current classLoader.
     */
    public static String getPluginForCommand(final String key, final Command value) throws InvalidClassException {
        return aa.getCommandsUtils().getPluginForCommand(key, value);
    } // end method

    /***
     * Returns a list of plugin names that contain the given command.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * {@code
     * for (final String pluginName : AA_API.getCommandContainingPlugins("ban")) {
     *     Bukkit.getLogger().log("info", "Command 'ban' found in plugin '" + pluginName);
     * }}
     * </pre>
     *
     * @param command The actual command we need to check all plugins for.
     * @param extendCorePluginNames If true, an extended name with a "Core -> " prefix is returned in the listing, otherwise
     *                              only the clear command name will be returned.
     *
     * @return Returns a list of plugins that contain the command in question.
     * @throws AccessException AccessException When we don't have the permission to access the org.bukkit.command.SimpleCommandMap.knownCommands field of org.bukkit.command.CommandMap.
     * @throws IllegalAccessException When access is denied to the org.bukkit.command.CommandMap or org.bukkit.command.SimpleCommandMap.knownCommands field.
     * @throws InvalidClassException When a class for any of the server plugins cannot be found within the current classLoader.
     * @throws InvocationTargetException When we try to invoke get() on org.bukkit.command.SimpleCommandMap.knownCommands with invalid org.bukkit.command.CommandMap parameter.
     * @throws SecurityException When private fields cannot be accessed due to a security restriction.
     * @throws NoSuchMethodException When there is a get() method missing from the org.bukkit.command.SimpleCommandMap.knownCommands field.
     */
    @SuppressWarnings("JavadocReference")
    public static List<String> getCommandContainingPlugins(final String command, final boolean... extendCorePluginNames)
            throws AccessException, IllegalAccessException, InvalidClassException, NoSuchMethodException,
            SecurityException, InvocationTargetException {
        return aa.getCommandsUtils().getCommandContainingPlugins(command, extendCorePluginNames);
    } // end method

    /***
     * Checks whether the given command is AdminAnything's internal command.
     * Used to prevent disabling or otherwise messing with our commands, so
     * admins can undo things when they make a mistake (which they wouldn't
     * be able to if they disabled certain combination of commands of AdminAnything
     * without stopping the server, editing config.yml and re-starting).
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * {@code
     * if (AA_API.isAaCoreCommand("aa_reload")) {
     *     Bukkit.getLogger().log("info", "Command /aa_reload() is indeed an AdminAnything core command :)");
     * }
     * }
     * </pre>
     *
     * @param cmd The command to check.
     *
     * @return Returns true if the command in question comes from AdminAnything, false otherwise.
     */
    public static boolean isAaCoreCommand(final String[] cmd) {
        return aa.getCommandsUtils().isAaCoreCommand(cmd);
    } // end method

    /**
     * Returns the plugin name from the given class,
     * which should be one of the command is was
     * just being executed.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * Bukkit.getLogger().log("info", "Requested class originates from plugin '" + AA_API.guessPluginFromClass(somePluginCommandClass) + "'");
     * </pre>
     *
     * @param clazz Class of the command being executed.
     *
     * @return Returns the name of plugin containing the command currently called.
     * @throws InvalidClassException When this plugin's class file is not found in the current classLoader.
     */
    public static String guessPluginFromClass(final Class<?> clazz) throws InvalidClassException {
        return aa.getCommandsUtils().guessPluginFromClass(clazz);
    } // end method

    /**
     * Retrieves a list of all commands on the server.
     *
     * @return Returns a list of all registered command names on the server.
     * @throws IllegalAccessException    When access is denied to the org.bukkit.command.CommandMap or org.bukkit.command.SimpleCommandMap.knownCommands field.
     * @throws NoSuchMethodException     When there is a get() method missing from the org.bukkit.command.SimpleCommandMap.knownCommands field.
     * @throws InvocationTargetException When we try to invoke get() on org.bukkit.command.SimpleCommandMap.knownCommands with invalid org.bukkit.command.CommandMap parameter.
     * @throws AccessException           When we don't have the permission to access the org.bukkit.command.SimpleCommandMap.knownCommands field of org.bukkit.command.CommandMap.
     */
    @SuppressWarnings("JavadocReference")
    public static Iterable<String> getServerCommands()
        throws AccessException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        return aa.getCommandsUtils().getServerCommands();
    } // end method

    /**
     * Retrieves a list of all commands for the given plugin.
     *
     * @param plugin The plugin name for which to retrieve commands.
     *
     * @return Returns a list of commands for the given plugin or null if none are found.
     * @throws IllegalAccessException    When access is denied to the org.bukkit.command.CommandMap or org.bukkit.command.SimpleCommandMap.knownCommands field.
     * @throws NoSuchMethodException     When there is a get() method missing from the org.bukkit.command.SimpleCommandMap.knownCommands field.
     * @throws InvocationTargetException When we try to invoke get() on org.bukkit.command.SimpleCommandMap.knownCommands with invalid org.bukkit.command.CommandMap parameter.
     * @throws AccessException           When we don't have the permission to access the org.bukkit.command.SimpleCommandMap.knownCommands field of org.bukkit.command.CommandMap.
     * @throws InvalidClassException     When this plugin's class file is not found in the current classLoader.
     */
    @SuppressWarnings("JavadocReference")
    public static Iterable<String> getPluginCommands(String plugin) throws AccessException, IllegalAccessException,
        NoSuchMethodException, InvocationTargetException, InvalidClassException {
        return aa.getCommandsUtils().getPluginCommands(plugin);
    } // end method

    /**
     * Loads the requested commands list which will then
     * be used in various parts of this plugin.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * // loads the list of all disabled commands
     * AA_API.loadCommandsListFromConfig("removals");
     * </pre>
     *
     * @param which Says which list (for which feature) we want to load.<br>
     *              Valid values are:
     *              <ul>
     *                  <li>ignores</li>
     *                  <li>overrides</li>
     *                  <li>virtualperms</li>
     *                  <li>removals</li>
     *                  <li>mutes</li>
     *                  <li>redirects</li>
     *                  <li>helpDisables</li>
     *              </ul>
     * @throws InvalidParameterException When the given which parameter does not conform to any known list of commands.
     */
    public static void loadCommandsListFromConfig(final String which) throws InvalidParameterException {
        aa.getCommandListenersUtils().loadCommandsListFromConfig(which);
    } // end method

    /**
     * Gets an unmodifiable list of the requested type of commands
     * (i.e. ignores, fixes etc.)
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * Bukkit.getLogger().log("info", "Commands currently disabled via AdminAnything: " + String.join(", ", AA_API.getCommandsList("removals"), true));
     * </pre>
     *
     * @param which Says which list (for which feature) we want to return.<br>
     *              Valid values are:
     *              <ul>
     *                  <li>ignores</li>
     *                  <li>overrides</li>
     *                  <li>virtualperms</li>
     *                  <li>removals</li>
     *                  <li>mutes</li>
     *                  <li>redirects</li>
     *                  <li>helpDisables</li>
     *              </ul>
     *
     * @return Returns an unmodifiable list of the requested type of commands (i.e. ignores, fixes etc.)
     * @throws InvalidParameterException When the given which parameter does not conform to any known list of commands.
     */
    public static List<String> getCommandsList(final String which) throws InvalidParameterException {
        return aa.getCommandListenersUtils().getCommandsList(which);
    } // end method

    /**
     * Retrieves a list of values from the configuration of the commands
     * to be redirected, muted, overridden etc.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * {@code
     * if (AA_API.getCommandsConfigurationValues("virtualperms").containsKey("customban")) {
     *     Bukkit.getLogger().log("info", "The 'customban' virtual permission was successfully located on this server.");
     * }
     * }
     * </pre>
     *
     * @param which The actual configuration file to return the value set for.<br>
     *              Valid values are:
     *              <ul>
     *                  <li>ignores</li>
     *                  <li>overrides</li>
     *                  <li>virtualperms</li>
     *                  <li>removals</li>
     *                  <li>mutes</li>
     *                  <li>redirects</li>
     *                  <li>helpDisables</li>
     *              </ul>
     *
     * @return Returns a list of values from the requested commands configuration.
     * @throws InvalidParameterException When the given which parameter does not conform to any known list of commands.
     */
    public static Map<String, Object> getCommandsConfigurationValues(final String which) throws InvalidParameterException {
        return aa.getCommandListenersUtils().getCommandsConfigurationValuesMap(which);
    } // end method

    /**
     * Retrieves a list of keys from the configuration of the commands
     * to be redirected, muted, overridden etc.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * {@code
     * // as used in /aa_delredirect
     * final Set<String> redirects = AA_API.getCommandsConfigurationKeys("redirects", false);
     * if (redirects.size() == 0) {
     *     sender.sendMessage(ChatColor.GREEN + "No commands are currently being redirected via " + AA_API.getAaName() + ".");
     * }
     * }
     * </pre>
     *
     * @param which The actual configuration file to return the keys set for.<br>
     *              Valid values are:
     *              <ul>
     *                  <li>ignores</li>
     *                  <li>overrides</li>
     *                  <li>virtualperms</li>
     *                  <li>removals</li>
     *                  <li>mutes</li>
     *                  <li>redirects</li>
     *                  <li>helpDisables</li>
     *              </ul>
     *
     *  @param deep When set to TRUE, configuration keys including heir subkeys will be returned.
     *              When set to FALSE, only first-level keys will be returned.
     *
     * @return Returns a list of keys from the requested commands configuration.
     * @throws InvalidParameterException When the given which parameter does not conform to any known list of commands.
     */
    public static Set<String> getCommandsConfigurationKeys(final String which, final boolean deep) throws InvalidParameterException {
        return aa.getCommandListenersUtils().getCommandsConfigurationKeys(which, deep);
    } // end method

    /**
     * Retrieves a value to be used in place of the original command
     * which was ignored, muted, overridden etc.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * {@code
     * if (!AA_API.getCommandsConfigurationValue("removals", "ban").equals("")) {
     *     sender.sendMessage(ChatColor.GREEN + "The command 'ban' is currently disabled.");
     * }
     * }
     * </pre>
     *
     * @param which The actual configuration file to look in for the value.<br>
     *              Valid values are:
     *              <ul>
     *                  <li>ignores</li>
     *                  <li>overrides</li>
     *                  <li>virtualperms</li>
     *                  <li>removals</li>
     *                  <li>mutes</li>
     *                  <li>redirects</li>
     *                  <li>helpDisables</li>
     *              </ul>
     * @param key The configuration key (i.e. our command) we want to return a replacement for.
     *
     * @return Returns the replacement command value we asked for from the loaded configuration.
     * @throws InvalidParameterException When the given which parameter does not conform to any known list of commands.
     */
    public static String getCommandsConfigurationValue(final String which, final String key) throws InvalidParameterException {
        return aa.getCommandListenersUtils().getCommandsConfigurationValue(which, key);
    } // end method

    /**
     * Check whether virtual permissions should consider letter case
     * when checking them against player's permissions.
     *
     * @return Returns true if we should consider custom permissions letter case,
     *                 false otherwise.
     */
    public static boolean getVirtualPermsCaseSensitive() {
        return aa.getCommandListenersUtils().getVirtualPermsCaseSensitive();
    } // end method

    /**
     * Gets an unmodifiable map of muted commands.
     *
     * @return Returns an unmodifiable map of muted commands.
     */
    public static Map<String, Boolean> getMutesMap() {
        return aa.getCommandListenersUtils().getMutesMap();
    } // end method

    /***
     * Returns plugin name from the cache of lowercased plugins
     * as opposed to the Bukkit.getPluginManager().getPlugin() case-sensitive getting.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * // realPluginName will be set to "AdminAnything" for this one
     * String realPluginName = AA_API.getPluginIgnoreCase("ADminAnyThINg");
     * </pre>
     *
     * @param pluginName Name of the plugin (in any text case, i.e. plugin / Plugin / PLuGIn)
     *                   we wish to retrieve the original name for.
     *
     * @return Returns the original name of the given plugin as it's known on the server.
     */
    public static Plugin getPluginIgnoreCase(final String pluginName) {
        return aa.getPluginUtils().getPluginIgnoreCase(pluginName);
    } // end method

    /**
     * Attempts to retrieve a location of the plugin's class file
     * within the currently loaded JAR archives structure.
     *
     * This method is used if we can't determine which plugin fired up
     * a command directly. Usually this means that the plugin uses a different
     * classLoader to make its magic work on multiple platforms, such as Spigot, Sponge and others.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * // usage in Commands class
     * Command value = [value_from_a_method_call];
     * String commandLocationParsed = this.plugin.getPluginUtils().parsePluginJARLocation(value.getClass());
     *
     * // then we can actually get a plugin name from this command's class location
     * String pluginName = this.plugin.getPluginUtils().getPluginNameViaClassPathsMap(commandLocationParsed);
     * </pre>
     *
     * @param commandClass The actual command class that has executed the command.
     *
     * @return Returns the JAR location of the given command class, and thus the plugin itself.
     * @throws InvalidClassException When this command's class cannot be found within the current class loader.
     */
    public static String parsePluginJARLocation(final Class<?> commandClass) throws InvalidClassException {
        return aa.getPluginUtils().parsePluginJARLocation(commandClass);
    } // end method

    /**
     * Checks for existence of a command location key in the class map.
     * Since we don't want to expose the whole map, as it would be writable
     * by anyone, we just expose this method directly here.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * {@code
     * if (AA_API.classMapContainsKey(someRandomAdminAnythingCommandClassMapLocation)) {
     *     pluginName = AA_API.getClassMapKey(someRandomAdminAnythingCommandClassMapLocation);
     * }}
     * </pre>
     *
     * @param key The key we want to check for in the class map.
     *
     * @return Returns true if the given key exists within the class map, false otherwise.
     */
    public static boolean classMapContainsKey(final String key) {
        return aa.getPluginUtils().classMapContainsKey(key);
    } // end method

    /**
     * Retrieves a key from the class map cache.
     * Since we don't want to expose the whole map, as it would be writable
     * by anyone, we just expose this method directly here.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * {@code
     * if (AA_API.classMapContainsKey(someRandomAdminAnythingCommandClassMapLocation)) {
     *     pluginName = AA_API.getClassMapKey(someRandomAdminAnythingCommandClassMapLocation);
     * }
     * }
     * </pre>
     *
     * @param key The key we want to get from the class map.
     *
     * @return Returns the requested key from the class map.
     */
    public static String getClassMapKey(final String key) {
        return aa.getPluginUtils().getClassMapKey(key);
    } // end method

    /***
     * Returns a clear name of the plugin from a string which may contain the plugin along with
     * a command (essentials:ban) or along with a core alias (Core -&gt; minecraft:ban).
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * // essentials will be set to "essentials"
     * String essentials = AA_API.getCleanPluginName("essentials:ban");
     *
     * // ban will be set to "ban"
     * String ban = aa_plugin_instance.getPluginUtils().getCleanPluginName("Core -&gt; ban");
     * </pre>
     *
     * @param pluginWithCommand The actual command with a colon or the AA_API.__("general.core") + " -&gt;" prefix.
     *
     * @return Returns a clear name of the plugin from a string which may contain colon or a AA_API.__("general.core") + " -&gt;" prefix.
     */
    public static String getCleanPluginName(final String pluginWithCommand, final boolean returnRealPluginName) {
        return aa.getPluginUtils().getCleanPluginName(pluginWithCommand, returnRealPluginName);
    } // end method

    /**
     * Retrieves a set of all plugin names on the server.
     *
     * @return Returns a set with names of all the plugins on the server.
     */
    public static Iterable<String> getServerPlugins() {
        return aa.getPluginUtils().getServerPlugins();
    } // end method

    /**
     * Retrieves a set of all plugin names on the server, prepended by the given string prefix.
     *
     * @param prependBy A single-dimensional array with a string to prepend to each plugin name.
     *                  Used when tab-completing plugin names from the /aa_listcommands command,
     *                  where we need to prefix plugin names with pl:, plug: or plugin:
     *
     * @return Returns a set with names of all the plugins on the server.
     */
    public static Iterable<String> getServerPlugins(String... prependBy) {
        return aa.getPluginUtils().getServerPlugins(prependBy);
    } // end method

    /**
     * Returns the newVersionAvailable value, be it null
     * or a real new version string.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * {@code
     * String newVersion = AA_API.getAvailableNewVersion();
     * if (newVersion != null) {
     *     Bukkit.getLogger().log("info", "New AdminAnything version (" + newVersion + ") was released. Download your copy today!");
     * }
     * }
     * </pre>
     *
     * @return Returns the value of newVersionAvailable. It will be null if no new version
     *         is available, a real version number string otherwise.
     */
    public static String getAvailableNewVersion() {
        return aa.getUpdater().getAvailableNewVersion();
    } // end method

    /**
     * Returns current configuration of manual permissions and commands for plugins that don't provide these
     * in their plugin.yml file.
     *
     * @return Returns current configuration of manual permissions and commands for plugins that don't provide these
     *         in their plugin.yml file.
     */
    public static FileConfiguration getManualPermDescriptionsConfig() {
        return aa.getCommandsUtils().getManualPermDescConfig();
    }

    /**
     * Returns TRUE if the initial warmup of AdminAnything is not yet passed,
     * FALSE otherwise. This is because we need to wait for all plugins before
     * we can adjust their listener priorities and tab-completers.
     *
     * @return Returns TRUE if the initial warmup of AdminAnything is not yet passed, FALSE otherwise.
     */
    public static boolean isWarmingUp() {
        return aa.isWarmingUp();
    }

    /**
     * Gets all commands available to a single player in their current world.
     *
     * @param p The player to retrieve commands for.
     *
     * @return Returns a list of all commands currently available to the given player on this server in player's current world.
     */
    public static List<String> getPlayerAvailableCommands(Player p) {
        return aa.getTabCompletUtils().getPlayerAvailableCommands(p);
    }

    /**
     * Creates a chest GUI with commands runnable for the given player.
     *
     * @param player Player to create the chest GUI for.
     * @param playerNameForCommands Player name to be used in all the commands in the GUI
     *                              instead of a %PLAYER% placeholder.
     */
    public static Inventory createGUIPlayerInventory(Player player, String playerNameForCommands) {
       return aa.getInventoryManager().createGUIPlayerInventory( player, playerNameForCommands );
    }

    /**
     * Returns translation for the given identifier, and optionally
     * a set of parameters. If this identifier is not found, the same
     * identifier is used instead of the translation and returned with
     * any given parameters replaced.
     *
     * @param identifier The actual identifier from the .properties file to work with.
     * @param params     Optional parameters used to format translations with placeholders.
     *
     * @return Returns a the requested translation with all optional placeholders correctly substituted.
     */
    public static String __(String identifier, Object... params) {
        return (null != params && 0 < params.length ? aa.getLang().__(identifier, params) :
                aa.getLang().__(identifier));
    } // end method

} // end class