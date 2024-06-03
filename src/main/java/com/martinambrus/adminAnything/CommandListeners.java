package com.martinambrus.adminAnything;

import com.martinambrus.adminAnything.events.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.io.File;
import java.io.IOException;
import java.io.InvalidClassException;
import java.security.InvalidParameterException;
import java.util.*;

/**
 * Commands-related utilities and methods. Used to seek and find commands from all plugins,
 * search for their duplicates, overwrites and other inconsistencies.
 *
 * @author Martin Ambrus
 */
@SuppressWarnings({"ClassWithTooManyMethods", "OverlyComplexClass", "ClassWithTooManyDependencies"})
final class CommandListeners implements Listener {
    /**
     * Config file for all ignored commands - i.e. those that should
     * not be checked for conflicts when running /aa_checkcommandconflicts,
     * since they already are fixed.
     */
    private final String commandIgnoresConfigFileName = "command_ignores.yml"; //NON-NLS

    /**
     * The actual list of all commands that should be ignored
     * when doing conflicgt checks via /aa_checkcommandconflicts.
     */
    private List<String> commandIgnoresList = null;

    /**
     * The actual configuration file handle for ignored commands.
     */
    private FileConfiguration commandIgnores;

    /**
     * Config file for all fixed commands - i.e. those duplicated onces
     * which the Admin chose which plugin should actually handle.
     */
    private final String commandOverridesConfigFileName = "command_overrides.yml"; //NON-NLS

    /**
     * The actual list of all commands fixed via /aa_fixcommand.
     */
    private List<String> commandOverridesList = null;

    /**
     * The actual configuration file handle for fixed commands.
     */
    private  FileConfiguration commandOverrides;

    /**
     * Config file for all virtual permissions.
     */
    private final String virtualPermsConfigFileName = "virtual_permissions.yml"; //NON-NLS

    /**
     * The actual list of all new permissions added via /aa_addperm.
     */
    private List<String> virtualPermsList = null;

    /**
     * The actual configuration file handle for virtual permissions.
     */
    private FileConfiguration virtualPerms;

    /**
     * Configuration option to say whether virtual permissions
     * should be case sensitive or not.
     */
    private boolean virtualPermsCaseInsensitive = true;

    /**
     * Config file for disabled commands.
     */
    private final String commandRemovalsConfigFileName = "command_removals.yml"; //NON-NLS

    /**
     * The actual list of all commands that were disabled via /aa_disablecommand.
     */
    private List<String> commandRemovalsList = null;

    /**
     * The actual configuration file handle for disabled commands.
     */
    private FileConfiguration commandRemovals;

    /**
     * Config file for all muted commands, i.e. those that should not
     * output anything to the console or the player as they run.
     */
    private final String commandMutesConfigFileName = "command_mutes.yml"; //NON-NLS

    /**
     * The actual list of all commands muted via /aa_mutecommand.
     */
    private List<String> commandMutesList = null;

    /**
     * The actual configuration file handle for muted commands.
     */
    private FileConfiguration    commandMutes;

    /**
     * Map of muted commands used for quick lookups of muted commands,
     * i.e. getting a map item (which can say it doesn't exist) as opposed
     * to checking an ArrayList by using contains() - which would be much slower.
     */
    private Map<String, Boolean> commandMutesMap = null;

    /**
     * Config file for all command redirects - i.e. those commands
     * that should actually run a different command when requested.
     */
    private final String commandRedirectsConfigFileName = "command_redirects.yml"; //NON-NLS

    /**
     * The actual list of all commands redirected to other commands
     * via /aa_addredirect.
     */
    private List<String> commandRedirectsList = null;

    /**
     * The actual configuration file handle for redirected commands.
     */
    private FileConfiguration commandRedirects;

    /**
     * Config file for commands disabled in /aa_playercommands.
     */
    private final String commandHelpDisablesConfigFileName = "command_help_disabled.yml"; //NON-NLS

    /**
     * The actual list of all commands that are disabled in /aa_playercommands.
     */
    private List<String> commandHelpDisablesList = null;

    /**
     * The actual configuration file handle for commands disabled in /aa_playercommands.
     */
    private FileConfiguration commandHelpDisables;

    /**
     * Gets a list reference, so we can use it in other methods.
     *
     * @param which Says which list (for which feature) we want to return.
     *
     * @return Returns the list reference we requested.
     * @throws InvalidParameterException When the given which parameter does not conform to any known list of commands.
     */
    List<String> getListReference(final String which) throws InvalidParameterException {
        switch (which) {
            case "ignores": //NON-NLS
                return commandIgnoresList;
            case "overrides": //NON-NLS
                return commandOverridesList;
            case "virtualperms": //NON-NLS
                return virtualPermsList;
            case "removals": //NON-NLS
                return commandRemovalsList;
            case "mutes": //NON-NLS
                return commandMutesList;
            case "redirects": //NON-NLS
                return commandRedirectsList;
            case "helpDisables": //NON-NLS
                return commandHelpDisablesList;
            default:
                // unknown list type requested
                throw new InvalidParameterException("Commands list of the type \"" + which + "\" does not exist within " + AA_API
                    .getAaName() + '.');
        }
    } // end method

    /**
     * Gets a file configuration handle reference, so we can use it in other methods.
     *
     * @param which Says which handle reference (for which feature) we want to return.
     *
     * @return Returns the file configuration handle reference we requested.
     * @throws InvalidParameterException When the given which parameter does not conform to any known configuration handle.
     */
    ConfigurationSection getFileConfigHandleReference(final String which) throws InvalidParameterException {
        switch (which) {
            case "ignores": //NON-NLS
                return commandIgnores;
            case "overrides": //NON-NLS
                return commandOverrides;
            case "virtualperms": //NON-NLS
                return virtualPerms;
            case "removals": //NON-NLS
                return commandRemovals;
            case "mutes": //NON-NLS
                return commandMutes;
            case "redirects": //NON-NLS
                return commandRedirects;
            case "helpDisables": //NON-NLS
                return commandHelpDisables;
            default:
                // unknown list type requested
                throw new InvalidParameterException("Configuration handle of the type \"" + which + "\" does not exist within " + AA_API
                    .getAaName() + '.');
        }
    } // end method

    /**
     * Loads the requested commands list which will then
     * be used in various parts of this plugin.
     *
     * @param which Says which list (for which feature) we want to load.
     * @throws InvalidParameterException When the given which parameter does not conform to any known list of commands.
     */
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    void loadCommandsListFromConfig(final String which) throws InvalidParameterException {
        String            configFileName;
        FileConfiguration configHandle;
        final String      configSectionName = "commands"; //NON-NLS

        switch (which) {
            case "ignores": //NON-NLS
                configFileName = commandIgnoresConfigFileName;
            configHandle = YamlConfiguration.loadConfiguration(
                    new File(AA_API.getAaDataDir(), configFileName));
                commandIgnoresList = configHandle.getStringList(configSectionName);
                commandIgnores = configHandle;
            break;

            case "overrides": //NON-NLS
                configFileName = commandOverridesConfigFileName;
            configHandle = YamlConfiguration.loadConfiguration(
                    new File(AA_API.getAaDataDir(), configFileName));
                commandOverridesList = new ArrayList<String>(configHandle.getKeys(false));
                commandOverrides = configHandle;
            break;

            case "virtualperms": //NON-NLS
                configFileName = virtualPermsConfigFileName;
            configHandle = YamlConfiguration.loadConfiguration(
                    new File(AA_API.getAaDataDir(), configFileName));
                virtualPermsList = new ArrayList<String>(configHandle.getKeys(false));
                virtualPerms = configHandle;
            break;

            case "removals": //NON-NLS
                configFileName = commandRemovalsConfigFileName;
            configHandle = YamlConfiguration.loadConfiguration(
                    new File(AA_API.getAaDataDir(), configFileName));
                commandRemovalsList = configHandle.getStringList(configSectionName);
                commandRemovals = configHandle;
            break;

            case "mutes": //NON-NLS
                configFileName = commandMutesConfigFileName;
            configHandle = YamlConfiguration.loadConfiguration(
                    new File(AA_API.getAaDataDir(), configFileName));
                commandMutesList = configHandle.getStringList(configSectionName);
                commandMutes = configHandle;
            break;

            case "redirects": //NON-NLS
                configFileName = commandRedirectsConfigFileName;
            configHandle = YamlConfiguration.loadConfiguration(
                    new File(AA_API.getAaDataDir(), configFileName));
                commandRedirectsList = new ArrayList<String>(configHandle.getKeys(false));
                commandRedirects = configHandle;
            break;

            case "helpDisables": //NON-NLS
                configFileName = commandHelpDisablesConfigFileName;
                configHandle = YamlConfiguration.loadConfiguration(
                    new File(AA_API.getAaDataDir(), configFileName));
                commandHelpDisablesList = configHandle.getStringList(configSectionName);
                commandHelpDisables = configHandle;
                break;

            default:             // unknown list type requested
                throw new InvalidParameterException("Could not initialise commands list of the type \"" + which + "\", as it does not exist within " + AA_API
                    .getAaName() + '.');
        }

        // if we've requested to load virtual permissions,
        // we'll also need to load a config setting saying
        // whether they should be case sensitive or not
        if (configFileName.equals(virtualPermsConfigFileName)) {
            virtualPermsCaseInsensitive = AA_API.isFeatureEnabled("playerpermscaseinsensitive"); //NON-NLS
        }

        Collection<Object> l = new ArrayList<Object>();
        l.add(getListReference(which));
        l.add(configHandle);

    } // end method

    /**
     * Gets an unmodifiable list of the requested type of commands
     * (i.e. ignores, fixes etc.)
     *
     * @param which Says which list (for which feature) we want to return.
     *
     * @return Returns an unmodifiable list of the requested type of commands (i.e. ignores, fixes etc.)
     * @throws InvalidParameterException When the given which parameter does not conform to any known list of commands.
     */
    List<String> getCommandsList(final String which) throws InvalidParameterException {
        // load first, if the list is not initialized
        if (null == getListReference(which)) {
            loadCommandsListFromConfig(which);
        }

        return Collections.unmodifiableList(getListReference(which));
    } // end method

    /**
     * Retrieves a list of values from the configuration of the commands
     * to be redirected, muted, overridden etc.
     *
     * @param which The actual configuration file to return the value set for.
     *
     * @return Returns a list of values from the requested commands configuration.
     * @throws InvalidParameterException When the given which parameter does not conform to any known list of commands.
     */
    Map<String, Object> getCommandsConfigurationValuesMap(final String which) throws InvalidParameterException {
        // load first, if the configuration is not initialized
        if (null == getFileConfigHandleReference(which)) {
            loadCommandsListFromConfig(which);
        }

        return Collections.unmodifiableMap(getFileConfigHandleReference(which).getValues(true));
    } // end method

    /**
     * Retrieves a list of keys from the configuration of the commands
     * to be redirected, muted, overridden etc.
     *
     * @param which The actual configuration file to return the keys set for.
     * @param deep When set to TRUE, configuration keys including heir subkeys will be returned.
     *             When set to FALSE, only first-level keys will be returned.
     *
     * @return Returns a list of keys from the requested commands configuration.
     * @throws InvalidParameterException When the given which parameter does not conform to any known list of commands.
     */
    Set<String> getCommandsConfigurationKeys(final String which, final boolean deep) throws InvalidParameterException {
        // load first, if the configuration is not initialized
        if (null == getFileConfigHandleReference(which)) {
            loadCommandsListFromConfig(which);
        }

        return Collections.unmodifiableSet(getFileConfigHandleReference(which).getKeys(deep));
    } // end method

    /**
     * Retrieves a value to be used in place of the original command
     * which was ignored, muted, overridden etc.
     *
     * @param which The actual configuration file to look in for the value.
     * @param key The configuration key (i.e. our command) we want to return a replacement for.
     *
     * @return Returns the replacement command value we asked for from the loaded configuration.
     * @throws InvalidParameterException When the given which parameter does not conform to any known list of commands.
     */
    String getCommandsConfigurationValue(final String which, final String key) throws InvalidParameterException {
        // load first, if the configuration is not initialized
        if (null == getFileConfigHandleReference(which)) {
            loadCommandsListFromConfig(which);
        }

        return getFileConfigHandleReference(which).getString(key);
    } // end method

    /**
     * Check whether virtual permissions should consider letter case
     * when checking them against player's permissions.
     *
     * @return Returns true if we should consider custom permissions letter case,
     *                 false otherwise.
     */
    boolean getVirtualPermsCaseSensitive() {
        return virtualPermsCaseInsensitive;
    } // end method

    /**
     * Gets an unmodifiable map of muted commands.
     *
     * @return Returns an unmodifiable map of muted commands.
     */
    Map<String, Boolean> getMutesMap() {
        // load first, if the map is not initialized
        if (null == commandMutesMap) {
            getCommandsList("mutes"); //NON-NLS
            reloadCommandMutesMap();
        }

        return Collections.unmodifiableMap(commandMutesMap);
    } // end method

    /***
     * React to the custom AddIgnoredCommandEvent which is used when we need to add
     * a new command to the list of ignored commands via /aa_ignorecommand.
     *
     * @param e The actual event.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void addIgnore(final AAAddIgnoredCommandEvent e) {
        try {
            if (
                    "com.martinambrus.adminAnything.commands.Aa_ignorecommand".equals(e.getCallerName()) ||
                AA_API.pluginHasFullApiAccess(AA_API.guessPluginFromClass(Class.forName(e.getCallerName())))
            ) {
                // no AdjustListenerPriorities call, we need to update the list here manually
                commandIgnoresList.add(e.getCommandName());
                commandIgnores.set("commands", commandIgnoresList); //NON-NLS
            }
        } catch (ClassNotFoundException | InvalidClassException e1) {
            e1.printStackTrace();
        }
    } // end method

    /***
     * React to the custom RemoveIgnoredCommandEvent which is used when we need to remove
     * a command from the list of ignored commands via /aa_unignorecommand.
     *
     * @param e The actual event.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void removeIgnore(final AARemoveIgnoredCommandEvent e) {
        try {
            if (
                    "com.martinambrus.adminAnything.commands.Aa_unignorecommand".equals(e.getCallerName()) ||
                AA_API.pluginHasFullApiAccess(AA_API.guessPluginFromClass(Class.forName(e.getCallerName())))
            ) {
                if (commandIgnoresList.contains(e.getCommandName())) {
                    commandIgnoresList.remove(e.getCommandName());
                    // reload commands in the actual config
                    commandIgnores.set("commands", commandIgnoresList); //NON-NLS
                }
            }
        } catch (ClassNotFoundException | InvalidClassException e1) {
            e1.printStackTrace();
        }
    } // end method

    /***
     * React to the custom SaveIgnoredCommandsEvent which is used when we need to save
     * the list of ignored commands to the config file. This event is fired in /aa_ignorecommand.
     *
     * @param e The actual event.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void saveIgnoreCommands(final AASaveCommandIgnoresEvent e) {
        try {
            commandIgnores.save(new File(AA_API.getAaDataDir(), commandIgnoresConfigFileName));

            // clear command caches
            Bukkit.getPluginManager().callEvent(new AAReloadEvent("checkcommandconflicts")); //NON-NLS
            // reload commandPreprocessor internal variables
            Bukkit.getPluginManager().callEvent(new AAReloadEvent("commandPreprocessor")); //NON-NLS
        } catch (final IOException ex) {
            e.getCommandSender().sendMessage(
                ChatColor.RED + AA_API.__("error.general-for-chat"));
            Bukkit.getLogger().severe('[' + AA_API.getAaName()
                + "] " + AA_API.__("config.error-cannot-save-config", "ignored commands")); //NON-NLS
            ex.printStackTrace();
        }
    } // end method

    /***
     * React to the custom AddVirtualPerm event which is used when we need to add
     * a new custom permission to a command via /aa_addperm.
     *
     * @param e The actual event.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void addVirtualPerm(final AAAddVirtualPermEvent e) {
        try {
            if (
                    "com.martinambrus.adminAnything.commands.Aa_addperm_runnable".equals(e.getCallerName()) ||
                AA_API.pluginHasFullApiAccess(AA_API.guessPluginFromClass(Class.forName(e.getCallerName())))
            ) {
                virtualPerms.set(e.getPermName(), e.getCommandLine());
                // NOTE: the actual permissions list gets updated via AdjustListenerPriorities
            }
        } catch (ClassNotFoundException | InvalidClassException e1) {
            e1.printStackTrace();
        }
    } // end method

    /***
     * React to the custom RemoveVirtualPermisisonsEvent which is used when we need to remove
     * a virtual permission from the config via /aa_delperm.
     *
     * @param e The actual event.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void removeVirtualPerm(final AARemoveVirtualPermissionEvent e) {
        try {
            if (
                    "com.martinambrus.adminAnything.commands.Aa_delperm".equals(e.getCallerName()) ||
                AA_API.pluginHasFullApiAccess(AA_API.guessPluginFromClass(Class.forName(e.getCallerName())))
            ) {
                if (virtualPerms.contains(e.getPermName())) {
                    try {
                        virtualPerms.set(e.getPermName(), null);
                        // reload custom permissions list
                        virtualPermsList = new ArrayList<String>(virtualPerms.getKeys(false));
                        // save list of virtual permissions
                        virtualPerms.save(new File(AA_API.getAaDataDir(), virtualPermsConfigFileName));
                    } catch (final IOException ex) {
                        Bukkit.getLogger().severe('[' + AA_API.getAaName()
                            + "] " + AA_API.__("config.error-cannot-save-config", "virtual permissions")); //NON-NLS
                        ex.printStackTrace();
                    }
                }
            }
        } catch (ClassNotFoundException | InvalidClassException e1) {
            e1.printStackTrace();
        }
    } // end method

    /***
     * React to the custom SaveVirtualPermsEvent which is used when we need to save
     * the list of virtual permissions to the config file. This event is fired in /aa_addperm.
     *
     * @param e The actual event.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void saveVirtualPerms(final AASaveVirtualPermsEvent e) {
        try {
            virtualPerms.save(new File(AA_API.getAaDataDir(), virtualPermsConfigFileName));
            // clear command caches
            Bukkit.getPluginManager().callEvent(new AAReloadEvent("checkcommandconflicts")); //NON-NLS
            // reload commandPreprocessor internal variables
            Bukkit.getPluginManager().callEvent(new AAReloadEvent("commandPreprocessor")); //NON-NLS
        } catch (final IOException ex) {
            e.getCommandSender().sendMessage(
                ChatColor.RED + AA_API.__("error.general-for-chat"));
            Bukkit.getLogger().severe('[' + AA_API.getAaName()
                + "] " + AA_API.__("config.error-cannot-save-config", "virtual permissions")); //NON-NLS
            ex.printStackTrace();
        }
    } // end method

    /***
     * React to the custom AddCommandRedirectEvent which is used when we need to add
     * a new command redirect via /aa_addredirect.
     *
     * @param e The actual event.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void addRedirect(final AAAddCommandRedirectEvent e) {
        try {
            if (
                    "com.martinambrus.adminAnything.commands.Aa_addredirect".equals(e.getCallerName()) ||
                AA_API.pluginHasFullApiAccess(AA_API.guessPluginFromClass(Class.forName(e.getCallerName())))
            ) {
                commandRedirects.set(e.getCommandName(), e.getCommandRedirect());
                // NOTE: the actual redirects list gets updated via AdjustListenerPriorities
            }
        } catch (ClassNotFoundException | InvalidClassException e1) {
            e1.printStackTrace();
        }
    } // end method

    /***
     * React to the custom RemoveCommandRedirectEvent which is used when we need to remove
     * a redirect from the config via /aa_delredirect.
     *
     * @param e The actual event.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void removeCommandRedirect(final AARemoveCommandRedirectEvent e) {
        try {
            if (
                    "com.martinambrus.adminAnything.commands.Aa_delredirect".equals(e.getCallerName()) ||
                AA_API.pluginHasFullApiAccess(AA_API.guessPluginFromClass(Class.forName(e.getCallerName())))
            ) {
                if (commandRedirects.contains(e.getCommandLine())) {
                    commandRedirects.set(e.getCommandLine(), null);
                    // reload list of command redirects
                    commandRedirectsList = new ArrayList<String>(commandRedirects.getKeys(false));
                }
            }
        } catch (ClassNotFoundException | InvalidClassException e1) {
            e1.printStackTrace();
        }
    } // end method

    /***
     * React to the custom SaveCommandRedirectsEvent which is used when we need to save
     * the list of redirects to the config file. This event is fired in /aa_addredirect.
     *
     * @param e The actual event.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void saveCommandRedirects(final AASaveCommandRedirectsEvent e) {
        try {
            commandRedirects.save(new File(AA_API.getAaDataDir(), commandRedirectsConfigFileName));
            // clear command caches
            Bukkit.getPluginManager().callEvent(new AAReloadEvent("checkcommandconflicts")); //NON-NLS
            // reload commandPreprocessor internal variables
            Bukkit.getPluginManager().callEvent(new AAReloadEvent("commandPreprocessor")); //NON-NLS
        } catch (final IOException ex) {
            e.getCommandSender().sendMessage(
                ChatColor.RED + AA_API.__("error.general-for-chat"));
            Bukkit.getLogger().severe('[' + AA_API.getAaName()
                + "] " + AA_API.__("config.error-cannot-save-config", "command redirects")); //NON-NLS
            ex.printStackTrace();
        }
    } // end method

    /***
     * React to the custom RemoveDisabledCommandEvent which is used when we need to remove
     * a disabled command from the config via /aa_enablecommand.
     *
     * @param e The actual event.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void enableCommand(final AARemoveDisabledCommandEvent e) {
        try {
            if (
                    "com.martinambrus.adminAnything.commands.Aa_enablecommand".equals(e.getCallerName()) ||
                AA_API.pluginHasFullApiAccess(AA_API.guessPluginFromClass(Class.forName(e.getCallerName())))
            ) {
                if (commandRemovalsList.contains(e.getCommandName())) {
                    commandRemovalsList.remove(e.getCommandName());
                    // no AdjustListenerPriorities is called for this one, so we need to
                    // update the configuration manually
                    commandRemovals.set("commands", commandRemovalsList); //NON-NLS
                }
            }
        } catch (ClassNotFoundException | InvalidClassException e1) {
            e1.printStackTrace();
        }
    } // end method

    /***
     * React to the custom SaveDisabledCommandsEvent which is used when we need to save
     * the list of disabled commands to the config file. This event is fired in /aa_enablecommand.
     *
     * @param e The actual event.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void saveDisabledCommands(final AASaveDisabledCommandsEvent e) {
        try {
            // update commands in the removals config
            commandRemovals.set("commands", commandRemovalsList); //NON-NLS
            // save config into its respective file
            commandRemovals.save(new File(AA_API.getAaDataDir(), commandRemovalsConfigFileName));
            // clear command caches
            Bukkit.getPluginManager().callEvent(new AAReloadEvent("checkcommandconflicts")); //NON-NLS
            // reload commandPreprocessor internal variables
            Bukkit.getPluginManager().callEvent(new AAReloadEvent("commandPreprocessor")); //NON-NLS
        } catch (final IOException ex) {
            e.getCommandSender().sendMessage(
                ChatColor.RED + AA_API.__("error.general-for-chat"));
            Bukkit.getLogger().severe('[' + AA_API.getAaName()
                + "] " + AA_API.__("config.error-cannot-save-config", "disabled commands")); //NON-NLS
            ex.printStackTrace();
        }
    } // end method

    /***
     * React to the custom AddCommandOverrideEvent which is used when we need to add
     * a new command override (i.e. to fix one command to be called from a certain plugin)
     * via /aa_fixcommand.
     *
     * @param e The actual event.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void addOverride(final AAAddCommandOverrideEvent e) {
        try {
            if (
                    "com.martinambrus.adminAnything.commands.Aa_fixcommand_runnable".equals(e.getCallerName()) ||
                AA_API.pluginHasFullApiAccess(AA_API.guessPluginFromClass(Class.forName(e.getCallerName())))
            ) {
                commandOverrides.set(e.getCommandName(), e.getCommandOverride());
                commandOverridesList = new ArrayList<String>(commandOverrides.getKeys(false));
            }
        } catch (ClassNotFoundException | InvalidClassException e1) {
            e1.printStackTrace();
        }
    } // end method

    /***
     * React to the custom RemoveCommandOverrideEvent which is used when we need to remove
     * an override from the config via /aa_unfixcommand.
     *
     * @param e The actual event.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void removeCommandOverride(final AARemoveCommandOverrideEvent e) {
        try {
            if (
                    "com.martinambrus.adminAnything.commands.Aa_unfixcommand".equals(e.getCallerName()) ||
                AA_API.pluginHasFullApiAccess(AA_API.guessPluginFromClass(Class.forName(e.getCallerName())))
            ) {
                if (commandOverrides.contains(e.getCommandName())) {
                    commandOverrides.set(e.getCommandName(), null);
                    // reload list of command overrides
                    commandOverridesList = new ArrayList<String>(commandOverrides.getKeys(false));
                }
            }
        } catch (ClassNotFoundException | InvalidClassException e1) {
            e1.printStackTrace();
        }
    } // end method

    /***
     * React to the custom SaveCommandOverridesEvent which is used when we need to save
     * the list of command overrides (i.e. fixes) to the config file. This event is fired in /aa_fixcommand.
     *
     * @param e The actual event.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void saveCommandOverrides(final AASaveCommandOverridesEvent e) {
        try {
            commandOverrides.save(new File(AA_API.getAaDataDir(), commandOverridesConfigFileName));
            // clear command caches
            Bukkit.getPluginManager().callEvent(new AAReloadEvent("checkcommandconflicts")); //NON-NLS
            // reload commandPreprocessor internal variables
            Bukkit.getPluginManager().callEvent(new AAReloadEvent("commandPreprocessor")); //NON-NLS
        } catch (final IOException ex) {
            e.getCommandSender().sendMessage(
                ChatColor.RED + AA_API.__("error.general-for-chat"));
            Bukkit.getLogger().severe('[' + AA_API.getAaName()
                + "] " + AA_API.__("config.error-cannot-save-config", "command overrides")); //NON-NLS
            ex.printStackTrace();
        }
    } // end method

    /***
     * React to the custom RemoveCommandMuteEvent which is used when we need to remove
     * a muted command from the config via /aa_mutecommand.
     *
     * @param e The actual event.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void removeCommandMute(final AARemoveCommandMuteEvent e) {
        try {
            if (
                    "com.martinambrus.adminAnything.commands.Aa_unmutecommand".equals(e.getCallerName()) ||
                AA_API.pluginHasFullApiAccess(AA_API.guessPluginFromClass(Class.forName(e.getCallerName())))
            ) {
                if (commandMutesList.contains(e.getCommandLine())) {
                    commandMutesList.remove(e.getCommandLine());
                    commandMutes.set("commands", commandMutesList); //NON-NLS
                }
            }
        } catch (ClassNotFoundException | InvalidClassException e1) {
            e1.printStackTrace();
        }
    } // end method

    /**
     * Reloads the mutes map for faster access to muted commands.
     */
    private void reloadCommandMutesMap() {
        commandMutesMap = new HashMap<String, Boolean>();
        if (!commandMutesList.isEmpty()) {
            for (String aCommandMutesList : commandMutesList) {
                commandMutesMap.put(aCommandMutesList, true);
            }
        }
    } // end method

    /**
     * React to the custom SaveMutedCommandsEvent which is used when we need to save
     * the list of muted commands to the config file. This event is fired in /aa_mutecommand.
     *
     * @param e The actual event.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void saveCommandMutes(final AASaveMutedCommandsEvent e) {
        try {
            // set and save mutes into the config file
            commandMutes.set("commands", commandMutesList); //NON-NLS
            commandMutes.save(new File(AA_API.getAaDataDir(), commandMutesConfigFileName));

            // reload the mutes map
            reloadCommandMutesMap();

            // clear command caches
            Bukkit.getPluginManager().callEvent(new AAReloadEvent("checkcommandconflicts")); //NON-NLS
            // reload commandPreprocessor internal variables
            Bukkit.getPluginManager().callEvent(new AAReloadEvent("commandPreprocessor")); //NON-NLS
        } catch (final IOException ex) {
            e.getCommandSender().sendMessage(
                ChatColor.RED + AA_API.__("error.general-for-chat"));
            Bukkit.getLogger().severe('[' + AA_API.getAaName()
                + "] " + AA_API.__("config.error-cannot-save-config", "command mutes")); //NON-NLS
            ex.printStackTrace();
        }
    } // end method

    /***
     * React to the custom AddHelpDisabledCommandEvent which is used when we need to add a command
     * to the config list of commands hidden from the /aa_playercommands listing.
     *
     * @param e The actual event.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void addHelpDisabledCommand(final AAAddHelpDisabledCommandEvent e) {
        try {
            if (
                "com.martinambrus.adminAnything.commands.Aa_disablehelpcommand".equals(e.getCallerName()) ||
                    AA_API.pluginHasFullApiAccess(AA_API.guessPluginFromClass(Class.forName(e.getCallerName())))
            ) {
                commandHelpDisablesList.add(e.getCommandName());
                // no AdjustListenerPriorities is called for this one, so we need to
                // update the configuration manually
                commandHelpDisables.set("commands", commandHelpDisablesList); //NON-NLS
            }
        } catch (ClassNotFoundException | InvalidClassException e1) {
            e1.printStackTrace();
        }
    } // end method

    /***
     * React to the custom RemoveHelpDisabledCommandEvent which is used when we need to remove from config
     * a command that was previously hidden from /aa_playercommands listing.
     *
     * @param e The actual event.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void enableHelpDisabledCommand(final AARemoveHelpDisabledCommandEvent e) {
        try {
            if (
                "com.martinambrus.adminAnything.commands.Aa_enablehelpcommand".equals(e.getCallerName()) ||
                    AA_API.pluginHasFullApiAccess(AA_API.guessPluginFromClass(Class.forName(e.getCallerName())))
            ) {
                if (commandHelpDisablesList.contains(e.getCommandName())) {
                    commandHelpDisablesList.remove(e.getCommandName());
                    // no AdjustListenerPriorities is called for this one, so we need to
                    // update the configuration manually
                    commandHelpDisables.set("commands", commandHelpDisablesList); //NON-NLS
                }
            }
        } catch (ClassNotFoundException | InvalidClassException e1) {
            e1.printStackTrace();
        }
    } // end method

    /***
     * React to the custom SaveCommandHelpDisablesEvent which is used when we need to save
     * the list of commands hidden from /aa_playercommands into the config file.
     * This event is fired in /aa_disablehelpcommand.
     *
     * @param e The actual event.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void saveHelpDisablesCommands(final AASaveCommandHelpDisablesEvent e) {
        try {
            // update commands in the help disables config
            commandHelpDisables.set("commands", commandHelpDisablesList); //NON-NLS
            // save config into its respective file
            commandHelpDisables.save(new File(AA_API.getAaDataDir(), commandHelpDisablesConfigFileName));
        } catch (final IOException ex) {
            e.getCommandSender().sendMessage(
                ChatColor.RED + AA_API.__("error.general-for-chat"));
            Bukkit.getLogger().severe('[' + AA_API.getAaName()
                + "] " + AA_API.__("config.error-cannot-save-config", "disabled help commands")); //NON-NLS
            ex.printStackTrace();
        }
    } // end method

    /***
     * React to the custom ReloadEvent with a parameter set to "virtualperms",
     * which reloads the list of virtual permissions when they change.
     *
     * @param e The actual reload event with message that says who is this reload for.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void reload(final AAReloadEvent e) {
        final String msg = e.getMessage();
        if ("virtualperms".equals(msg)) { //NON-NLS
            loadCommandsListFromConfig("virtualperms"); //NON-NLS
        }
    } // end method

} // end class