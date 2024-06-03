package com.martinambrus.adminAnything;

import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginDescriptionFile;

import java.util.*;

/**
 * Base configuration class containing all common functionality
 * and properties for extending config sub-classes.
 *
 * @author Martin Ambrus
 */
@SuppressWarnings("HardCodedStringLiteral")
abstract class ConfigAbstractAdapter implements Listener {

    /**
     * Instance of {@link AdminAnything}.
     */
    AdminAnything plugin = null;

    /**
     * Plugin description file, contains things like commands and permissions
     * from the plugin.yml file of this plugin.
     */
    transient PluginDescriptionFile yml = null;

    /**
     * Holds the text "AdminAnything" for logging purposes.
     */
    String pluginName = "AdminAnything";

    /**
     * List of all features that are currently disabled in AdminAnything's user config,
     * i.e. config.yml file in Plugins folder.
     */
    final Collection<String> disabledFeatures = new ArrayList<String>();

    /**
     * ENUM of some valid configuration values
     * to prevent duplication of string literals in code.
     */
    enum CONFIG_VALUES {
        /**
         * Whether or not chat links next to nicknames are in use.
         */
        CHATNICKLINKSENABLED(
                "features.chatnicklinks.enabled"
                ),
        /**
         * Whether or not chest GUI link next to nicknames is in use.
         */
        CHESTGUIENABLED(
            "features.chatnickgui.enabled"
        ),
        /**
         * Whether or not chat links when a player joins/leaves are in use.
         */
        CHATJOINLEAVELINKSENABLED(
                "features.chatjoinleaveclicks.enabled"
                ),
        /**
         * Whether or not player auto-kick after an IP-ban is in use.
         */
        CHATKICKAFTERIPBANENABLED(
                "chatKickAfterIpBan"
                ),
        /**
         * Whether or not the debug mode is enabled.
         */
        DEBUGENABLED(
                "debugMode"
                ),
        /*
          Language translation string from config.
         */
        /*LANGVISUALIZERDISABLED(
                "lang.visualizerDisabled"
        ),*/
        ;

        /**
         * The string value of an ENUM.
         */
        private final String configValue;

        /**
         * Constructor for String ENUMs.
         * @param value String value for the ENUM.
         */
        CONFIG_VALUES(final String value) {
            configValue = value;
        }

        /* (non-Javadoc)
         * @see java.lang.Enum#toString()
         */
        @Override
        public String toString() {
            return configValue;
        }

        private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
            throw new java.io.NotSerializableException("com.martinambrus.adminAnything.Config.CONFIG_VALUES");
        }
    }

    /**
     * Plugin debug mode flag.
     */
    boolean debug = false;

    /**
     * List of plugins allowed full access to the AdminAnything API,
     * which means these plugins will be able to fire up AA add and remove
     * events (for example to add new fixed, muted, redirected... command etc.).
     */
    List<String> fullApiAccessPlugins = null;

    /**
     * List of commands used to ban player by IP.
     */
    List<String> banIpCommandsList = null;

    /**
     * Map of commands to be added to player's username
     * in chat when join event occurs and this feature is enabled.
     */
    Map<String, Map<String, String>> joinClickCommands = new LinkedHashMap<String, Map<String, String>>();

    /**
     * Map of commands to be added to player's username
     * in chat when leave event occurs and this feature is enabled.
     */
    Map<String, Map<String, String>> leaveClickCommands = new LinkedHashMap<String, Map<String, String>>();

    /**
     * Map of commands to be added to player's username
     * in chat when a chat message is sent.
     */
    Map<String, Map<String, String>> clickCommands = new LinkedHashMap<String, Map<String, String>>();

    /**
     * Map of commands to be shown in a chest GUI when this GUI is opened
     * by clicking on [A] link in chat next to player's username.
     */
    Map<String, Map<String, String>> guiItems = new LinkedHashMap<String, Map<String, String>>();

    /**
     * Configuration identifiers and their respective file names.
     */
    final HashMap<String, String> configs = new HashMap<String, String>() {{
        put("main", "config-file.yml");
        put("ignores", "command_ignores.yml");
        put("overrides", "command_overrides.yml");
        put("perms", "virtual_permissions.yml");
        put("disables", "command_removals.yml");
        put("mutes", "command_mutes.yml");
        put("redirects", "command_redirects.yml");
        put("helpDisables", "command_help_disabled.yml");
        put("permDescriptions", "permdescriptions.yml");
    }};

    /**
     * Gets this plugin's debug state.
     *
     * @return Returns true if debug is turned ON, false otherwise.
     */
    boolean getDebug() {
        return debug;
    } //end method

    /**
     * Sets this plugin's debug state.
     *
     * @param value New value for plugin's debug state - true to turn ON, false to turn OFF.
     */
    abstract void setDebug(final boolean value);

    /**
     * Gets the plugin's display name, as it's being used in many places for logging purposes.
     *
     * @return Returns the text "AdminAnything".
     */
    String getPluginName() {
        return pluginName;
    } //end method

    /**
     * Get list of built-in commands for a Spigot server.
     *
     * @return Returns a hard-coded list of core commands from the CommandMap of Spigot
     * as of version 1.12. There are commands from all - Minecraft, CraftBukkit and Spigot.
     */
    Collection<String> getBuiltInCommands() {
        return Constants.BUILTIN_COMMANDS.getValues();
    } // end method

    /**
     * Checks whether a feature of AdminAnyhing is disabled.
     *
     * @param feature The name of the feature to check for disabled status.
     * @return Returns true if the feature is disabled, false otherwise.
     */
    boolean isDisabled(final String feature) {
        return disabledFeatures.contains(feature);
    } //end method

    /**
     * Gets internal YAML plugin configuration for AdminAnything.
     *
     * @return Returns the internal YAML plugin configuration for AdminAnything
     * from the plugin.yml file located inside the JAR file of the plugin.
     */
    PluginDescriptionFile getInternalConf() {
        return yml;
    } //end method

    /**
     * Gets user plugin configuration for AdminAnything.
     *
     * @return Returns plugin configuration for AdminAnything, loaded either from
     * the config.yml file located in [pluginsFolder]/AdminAnything
     * or from a DB backend.
     */
    abstract ConfigSectionAbstractAdapter getConf();

    /**
     * Loads list of all plugins that are enabled full access to AdminAnything's API,
     * including all data adjusting event calls (i.e. to add/remove records to/from lists
     * of fixed, muted, disabled... commands etc.).
     */
    abstract void loadFullApiAccessPlugins();

    /**
     * Checks whether the given plugin has full access to AdminAnything's API,
     * including all data adjusting event calls (i.e. to add/remove records to/from lists
     * of fixed, muted, disabled... commands etc.).
     *
     * @param pluginName Name of the plugin to check for. Letter case is ignored.
     *
     * @return Returns true if the given plugin has full access to AA's API, false otherwise.
     */
    boolean pluginHasFullApiAccess(String pluginName) {
        loadFullApiAccessPlugins();
        return AA_API.isFeatureEnabled("apifullaccess") && fullApiAccessPlugins.contains(pluginName.toLowerCase());

    } // end method

    /**
     * Loads list of IP ban commands from the config file, since different MC versions
     * tend to use different ban-ip commands and the server could even have its own flavor
     * of ban-ip from a plugin.
     *
     * @return Returns list of all commands AdminAnything should consider as IP-ban ones.
     */
    abstract Collection<String> getBanIpCommandsList();

    /**
     * Loads join and click event links that will be displayed in chat
     * next to player username's if enabled in config.
     */
    abstract void loadJoinLeaveClickLinks();

    /**
     * Gets map of actions to be added to the chat next to player's
     * username when they join the server, if enabled.
     *
     * @return Returns the map of actions to be added to player's username
     *         when they join the server.
     */
    Map<String, Map<String, String>> getChatJoinActionsMap() {
        return joinClickCommands;
    } // end method

    /**
     * Gets map of actions to be added to the chat next to player's
     * username when they leave the server, if enabled.
     *
     * @return Returns the map of actions to be added to player's username
     *         when they leave the server.
     */
    Map<String, Map<String, String>> getChatLeaveActionsMap() {
        return leaveClickCommands;
    } // end method

    /**
     * Loads chat nick click event links that will be displayed in chat
     * next to player username's if enabled in config.
     */
    abstract void loadNickClickLinks();

    /**
     * Loads chat nick click event links for an in-game chest GUI
     * if enabled in config.
     */
    abstract void loadNickGUIItems();

    /**
     * Gets map of actions to be added to the chat next to player's
     * username when a chat message is sent, if enabled.
     *
     * @return Returns the map of actions to be added to player's username
     *         when a chat message is sent.
     */
    Map<String, Map<String, String>> getNickClickActionsMap() {
        return clickCommands;
    } // end method

    /**
     * Gets map of items to be added to the chest GUI when a player clicks
     * on the [A] link in chat next to other player's nickname, if enabled.
     *
     * @return Returns the map of items to be added a chest GUI.
     */
    Map<String, Map<String, String>> getGUIItemsMap() {
        return guiItems;
    } // end method

    /***
     * Closes any DB or file connections that the config class
     * have open upon disabling the plugin.
     */
    abstract void onClose();

    /**
     * Reloads configuration from the source, overwriting any old values we might have cached in memory.
     * Used when AdminAnything is being disabled to prevent storing old values into the DB.
     */
    abstract void reloadConfig(); // end method

    /**
     * Retrieves max number of records to be shown per single page when showing
     * paginated results in player chat.
     *
     * @return Returns max number of records to be shown per single page when showing
     *         paginated results in player chat.
     */
    abstract double getChatMaxPerPageRecords(); // end method

} // end class