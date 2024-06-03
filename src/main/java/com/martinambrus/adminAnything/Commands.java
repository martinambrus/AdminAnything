package com.martinambrus.adminAnything;

import com.martinambrus.adminAnything.events.AAReloadEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InvalidClassException;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.rmi.AccessException;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Pattern;

/**
 * Commands-related utilities and methods. Used to seek and find commands from all plugins,
 * search for their duplicates, overwrites and other inconsistencies.
 *
 * @author Martin Ambrus
 */
@SuppressWarnings({"OverlyComplexClass", "IntegerMultiplicationImplicitCastToLong", "VariableNotUsedInsideIf"})
final class Commands {

    /**
     * Instance of {@link com.martinambrus.adminAnything.AdminAnything}.
     */
    private final Plugin plugin;

    /**
     * File that holds permissions and command descriptions of plugins
     * that do not list these in their plugin.yml file.
     */
    private final String manualPermDescriptionsFileName = "permdescriptions.yml";

    /**
     * The actual loaded manual permissions and command descriptions configuration
     * from a local file.
     */
    private FileConfiguration manualPermDescriptionsConf = null;

    /**
     * {@link org.bukkit.command.CommandMap CommandMap} of the underlying
     * Spigot/CraftBukkit server. Contains all commands currently usable on the server.
     */
    private static Field commandMap;

    /**
     * Augmented {@link org.bukkit.command.CommandMap CommandMap} of the underlying
     * Spigot/CraftBukkit server. Contains all commands currently usable on the server,
     * also including all commands for plugin on the server from the permdescriptions.yml file.
     */
    private Map<String, Command> augmentedCommandMap = null;

    /**
     * {@link org.bukkit.command.SimpleCommandMap.knownCommands Map} of all known server and plugin commands.
     *
     * <b>NOTE:</b> this map may not include server core commands, since at least
     * MC <= 1.7 did not register them in any standard way. In such situations,
     * core commands will be spoon-fed back to this list manually via AdminAnything
     * on the first `knownCommands` init.
     */
    @SuppressWarnings("JavadocReference")
    private static Field knownCommands;

    /**
     * Cache for all plugins that contain the given command.
     * Command name is the `key`, plugin names is the list in `value`.
     */
    private static Map<String, List<String>> containingPluginsCache;

    /**
     * Cache for all commands for a plugin. This should be quicker
     * to iterate than the full CommandMap when we need autocomplete
     * of commands from a certain plugin.
     */
    private static Map<String, List<String>> pluginCommandsCache;

    /**
     * Cache for command (key) and its corresponding plugin (value).
     *
     * This is a temporary (60s) cache to prevent many lookups
     * for same commands, should the same AA command which does this
     * kind of lookups be executed multiple times in succession.
     */
    static Map<String, String> commandToPluginMap;

    /**
     * Caches cleanup timeout in minutes.
     * Used so we don't necessarily keep the cache in if AA is not used often.
     */
    private static final int cleanupTimeout = 15;

    /**
     * Set of all commands on the server, loaded from the commands map.
     * This is here, so we have a cached version of them and don't need
     * to request a keyset from the command map itself every time we need
     * to invoke an autocomplete.
     */
    private static Set<String> serverCommands;

    /**
     * Reference to the actual cleanup task that clears up our caches.
     */
    static int cleanupTask = -1;

    /**
     * Constructor, creates a new instance of the Commands class
     * and registers command executors for all AA commands found.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * {@code
     * // usage in AdminAnything main class
     * this.commands = new Commands(this);
     *
     * if (!this.commands.registerCommandExecutors(this.config)) {
     *     // bail out if we're disabling AA due to a missing command
     *     return;
     * }
     * }
     * </pre>
     *
     * @param aa Instance of {@link com.martinambrus.adminAnything.AdminAnything AdminAnything}.
     */
    Commands(final Plugin aa) {
        plugin = aa;
    } //end method

    /**
     * Makes the private commandMap and knownCommands server's fields
     * accessible to our plugin, so we can work with what commands
     * are currently registered and known.
     *
     * @throws AccessException When commandMap or knownCommands fields cannot be
     *         made accessible via Reflection.
     */
    private void initCommandsMap() throws AccessException {
        if (null != commandMap) {
            return;
        }

        // initialize our fields
        try {
            commandMap = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMap.setAccessible(true);

            knownCommands = SimpleCommandMap.class.getDeclaredField("knownCommands");
            knownCommands.setAccessible(true);
        } catch (final Exception e) {
            // something very wrong happened here, we need the stack trace,
            // so the user can send it to us
            e.printStackTrace();
            throw new AccessException("Something unexpected happened and " + AA_API.getAaName()
            + " was unable to work out all of the enabled commands. Please send the above error to the plugin author,"
            + "so this can be promptly fixed for you. Thanks :)");
        }
    } //end method

    /***
     * If we don't find server commands in the command map (which can be easily checked via the "say" command),
     * we'll stuff those into knownCommands map of the server manually. This usually happens with MC < 1.8
     *
     * @throws IllegalAccessException When we try to access non-existing item in the commandMap.
     * @throws IllegalArgumentException When we pass an invalid argument to the {@link org.bukkit.command.CommandMap commandMap's getCommand()} method.
     * @throws SecurityException When we don't have permissions to access the {@link org.bukkit.command.CommandMap commandMap's getCommand()} method.
     * @throws NoSuchMethodException When {@link org.bukkit.command.CommandMap commandMap's getCommand()} method does not exist.
     * @throws InvocationTargetException When {@link org.bukkit.command.CommandMap commandMap's getCommand()} method was invoked on an invalid object.
     */
    private Map<String, Command> forceRegisterServerCommands(final Map<String, Command> knownCommands)
            throws IllegalArgumentException, IllegalAccessException, NoSuchMethodException, SecurityException,
            InvocationTargetException {

        // the say commands will be missing if we're not registering core commands via commandMap (MC < 1.8)
        if (null == knownCommands.get("say")) { //NON-NLS
            final Object commandMapAccess = commandMap.get(Bukkit.getServer());
            final Method getCommand       = commandMapAccess.getClass().getMethod("getCommand", String.class);

            // iterate over all basic commands and try to put them in
            for (final String coreCommand : Constants.BUILTIN_COMMANDS) {
                final Object commandMapCommand = getCommand.invoke(commandMapAccess, coreCommand);
                if (null != commandMapCommand) {
                    knownCommands.put(coreCommand, (Command) commandMapCommand);
                }
            }
        }

        return knownCommands;
    } //end method

    /**
     * Used on AdminAnything startup (i.e. in onEnable).
     * Parses all commands that are in plugin.yml file for this plugin
     * and registers their executors one by one to the correct classes.
     * This is possible, as classes follow the same naming convention
     * as commands, i.e. Aa_fixcommand.class = /aa_fixcommand.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * {@code
     * // usage in AdminAnything main class
     * this.commands = new Commands(this);
     *
     * if (!this.commands.registerCommandExecutors(this.config)) {
     *     // bail out if we're disabling AA due to a missing command
     *     return;
     * }
     * </pre>
     *
     * @param config Plugin configuration class instance.
     * @return Returns true if registration was successful, false in case of any errors.
     */
    boolean registerCommandExecutors(final ConfigAbstractAdapter config) {
        // we can only register commands from plugin.yml file
        if (null != config.getInternalConf().getCommands()) {
            // iterate all commands and register them one by one
            for (final String cmd : AA_API.getCommandsKeySet()) {
                // don't register disabled commands
                if (!config.isDisabled(cmd.replaceAll("aa_", ""))) { //NON-NLS
                    try {
                        final Class<?> cl = Class.forName("com.martinambrus.adminAnything.commands." + Utils.capitalize(cmd));
                        final Constructor<?> cons = cl.getConstructor(Plugin.class);

                        CommandExecutor co = (CommandExecutor) cons.newInstance(plugin);
                        ((JavaPlugin) plugin).getCommand(cmd).setExecutor(co);

                        // if this command executor also implements a listener,
                        // activate it
                        if (co instanceof Listener) {
                            ((AdminAnything) plugin).getListenerUtils()
                                                    .startRequiredListener(Utils.capitalize(cmd), (Listener) co);
                        }
                    } catch (final NoSuchMethodException e) {
                        // not every command has a constructor
                        try {
                            final Class<?> cl = Class.forName("com.martinambrus.adminAnything.commands." + Utils.capitalize(cmd));

                            CommandExecutor co = (CommandExecutor) cl.getConstructor().newInstance();
                            ((JavaPlugin) plugin).getCommand(cmd).setExecutor(co);

                            // if this command executor also implements a listener,
                            // activate it
                            if (co instanceof Listener) {
                                ((AdminAnything) plugin).getListenerUtils()
                                                        .startRequiredListener(Utils.capitalize(cmd), (Listener) co);
                            }
                        } catch (final Throwable e1) {
                            Bukkit.getLogger().severe('[' + config.getPluginName()
                                + "] " + AA_API.__("error.command-not-found", cmd));
                            Bukkit.getServer().getPluginManager().disablePlugin(plugin);
                            e1.printStackTrace();

                            return true;
                        }
                    } catch (final Throwable e) {
                        Bukkit.getLogger().severe('[' + config.getPluginName()
                            + "] " + AA_API.__("error.command-not-found", cmd));
                        Bukkit.getServer().getPluginManager().disablePlugin(plugin);
                        e.printStackTrace();

                        return true;
                    }
                }
            }
        }

        return false;
    } //end method

    /**
     * Unregisters all commands initially bound to this plugin.
     * Used during plugin disable.
     *
     *  <br><br><strong>Example:</strong>
     * <pre>
     * // initialize Config & Commands (onEnable() method)
     * this.config = new Config(this);
     * this.commands = new Commands(this);
     *
     * // ... some more code, other methods etc.
     *
     * // onDisable() method
     * this.commands.unregisterCommandExecutors(this.config);
     * </pre>
     *
     * @param config Plugin configuration class instance.
     */
    void unregisterCommandExecutors(final ConfigAbstractAdapter config) {
        // we can only unregister commands from plugin.yml file
        CommandMap cmap = null;

        try {
            // if not initialized yet, we cannot unregister our commands
            if (null == commandMap) {
                this.initCommandsMap();
            }

            cmap = (CommandMap) commandMap.get(Bukkit.getServer());
        } catch (IllegalArgumentException | IllegalAccessException | NullPointerException | AccessException e) {
            if (AA_API.getDebug()) {
                Bukkit.getLogger()
                      .severe('[' + config.getPluginName() + "] " + AA_API
                          .__("error.command-cannot-unregister-without-map"));
                e.printStackTrace();
                return;
            }
        }

        if (null != cmap && null != config.getInternalConf().getCommands()) {
            // iterate all commands and unregister them one by one
            for (final String cmd : AA_API.getCommandsKeySet()) {
                // no need to unregister a disabled command
                if (!config.isDisabled(cmd.replaceAll("aa_", ""))) { //NON-NLS
                    try {
                        ((JavaPlugin) plugin).getCommand(cmd)
                                             .unregister((CommandMap) commandMap.get(Bukkit.getServer()));
                    } catch (IllegalArgumentException | IllegalAccessException e) {
                        Bukkit.getLogger()
                              .severe('[' + config.getPluginName() + "] " + AA_API
                                  .__("error.command-cannot-unregister", cmd));
                    }
                }
            }
        }
    } //end method

    /***
     * Checks whether native commands/aliases overrides are in use for this installation
     * and warns the server admin via console.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * // usage in AdminAnything main class
     * this.commands = new Commands(this);
     * this.commands.checkNativeOverrides(this);
     * </pre>
     */
    void checkNativeOverrides() {
        // warn about alias overrides if Bukkit's commands override system is in use
        String aliasesFileName;
        File aliasesFile;
        aliasesFileName = "commands.yml"; //NON-NLS

        // check that we actually have an aliases file to look into first
        aliasesFile = new File(aliasesFileName);
        if (aliasesFile.exists()) {

            final FileConfiguration permsFromConfig = YamlConfiguration.loadConfiguration(aliasesFile);
            //noinspection HardCodedStringLiteral
            if (null != permsFromConfig.getConfigurationSection("aliases")) {

                //noinspection HardCodedStringLiteral
                final Set<String> aliasKeyz = permsFromConfig.getConfigurationSection("aliases")
                                                             .getKeys(false);
                if ((1 < aliasKeyz.size()) || !aliasKeyz.contains("icanhasbukkit")) { //NON-NLS
                    // looks like we're using Bukkit's commands override system... warn the user
                    Bukkit.getLogger()
                          .warning('[' + AA_API.getAaName() + "]\n\n"
                            + "**************************************************\n"
                              + "** " + AA_API.__("startup.command-override-in-use-1") + "\n\n"
                              + "** " + AA_API.__("startup.command-override-in-use-2") + '\n'
                              + "** " + AA_API.__("startup.command-override-in-use-3", AA_API.getAaName()) + '\n'
                              + "** " + AA_API.__("startup.command-override-in-use-4", aliasesFileName) + '\n'
                            + "**************************************************\n");
                }
            }
        }
    } //end method

    /**
     * Initializes (if needed) and returns the commandMap with all commands
     * currently known to the server.
     *
     * @return Returns map of known commands from server and plugins.
     *
     * @throws IllegalAccessException When access is denied to the {@link org.bukkit.command.CommandMap commandMap} or {@link org.bukkit.command.SimpleCommandMap.knownCommands knownCommands} field.
     * @throws NoSuchMethodException When there is a get() method missing from the {@link org.bukkit.command.SimpleCommandMap.knownCommands knownCommands} field.
     * @throws SecurityException When private fields cannot be accessed due to a security restriction.
     * @throws InvocationTargetException When we try to invoke get() on {@link org.bukkit.command.SimpleCommandMap.knownCommands knownCommands} with invalid {@link org.bukkit.command.CommandMap commandMap} parameter.
     * @throws AccessException When we don't have the permission to access the {@link org.bukkit.command.SimpleCommandMap.knownCommands knownCommands} field of {@link org.bukkit.command.CommandMap commandMap}.
     */
    @SuppressWarnings({"unchecked", "JavadocReference"})
    Map<String, Command> getCommandMap() throws IllegalAccessException, NoSuchMethodException,
    SecurityException, InvocationTargetException, AccessException {
        try {
            initCommandsMap();
            return forceRegisterServerCommands(
                    (Map<String, Command>) knownCommands.get(commandMap.get(Bukkit.getServer())));
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException(e);
        } catch (final IllegalAccessException e) {
            throw new IllegalAccessException();
        }
    } //end method

    /**
     * Augments a temporary copy of a commandMap with manually-crafter commands from permdescriptions.yml file,
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
    Map<String, Command> getAugmentedCommandMap()
        throws InvocationTargetException, NoSuchMethodException, AccessException, IllegalAccessException {

        if (null != this.augmentedCommandMap) {
            return this.augmentedCommandMap;
        }

        // load custom permission descriptions for permissions of those plugins
        // which do not include their description in their plugin.yml
        //noinspection HardCodedStringLiteral
        FileConfiguration permsFromConfig = AA_API.getManualPermDescriptionsConfig();

        // create a temporary copy of the commandMap
        final Map<String, Command> commandMapCopy = AA_API.getCommandMapCopy();

        // add all commands that were manually-crafted in the permdescriptions.yml file
        // which aren't registered in the commandMap and of which plugin actually runs on this server
        // into the commandMap copy, so we can simply use the for loop to display all commands - even
        // custom ones - in one go
        for (String manualPluginName : permsFromConfig.getConfigurationSection("manualPermissions").getKeys(false)) {
            // check if we actually have this plugin on our server
            Plugin owningPlugin = AA_API.getPluginIgnoreCase(manualPluginName);
            if (null == owningPlugin) {
                // this plugin is not present on the server
                continue;
            }

            // go command by command and check if we're missing any of them in the command map
            for (String manualCommandName : permsFromConfig.getConfigurationSection("manualPermissions." + manualPluginName).getKeys(false)) {
                // we use prefixed command names here or otherwise we'll fail to determine the correct plugin
                // for these pseudo-commands below
                String mapManualCommandName = manualPluginName + ":" + manualCommandName;
                if (null == commandMapCopy.get(mapManualCommandName)) {
                    // found a command we need to add, let's prepare its internals
                    List<String> manualPerms = permsFromConfig.getStringList("manualPermissions." + manualPluginName + "." + manualCommandName);

                    // load the first line for this command and check if it's actually a command description
                    Iterator<String> permsIterator = manualPerms.iterator();
                    String manualDescription = permsIterator.next();

                    if (!manualDescription.startsWith("$")) {
                        // not an actual command description but a permission
                        manualDescription = "";
                    } else {
                        // remove the $ sign from this command's description
                        manualDescription = manualDescription.substring(1);
                    }

                    // load the topmost permission, as commands can only have a single permission on the server
                    String manualPerm = "";
                    if (permsIterator.hasNext()) {
                        manualPerm = permsIterator.next();
                        manualPerm = (manualPerm.indexOf('=') > -1 ? manualPerm.substring(0, manualPerm.indexOf('=')) : manualPerm);
                    }

                    commandMapCopy.put(mapManualCommandName, new AASymbolicCommand(manualCommandName, owningPlugin, manualDescription, manualPerm));
                }
            }
        }

        this.augmentedCommandMap = commandMapCopy;
        return this.augmentedCommandMap;
    }

    /**
     * Clears the cached values of {@link org.bukkit.command.SimpleCommandMap.knownCommands knownCommands}
     * and {@link org.bukkit.command.CommandMap commandMap} fields.
     */
    @SuppressWarnings("JavadocReference")
    private void clearCommandMap() {
        commandMap = null;
        this.augmentedCommandMap = null;
        knownCommands = null;
    } //end method

    /**
     * Returns the plugin name from the given class,
     * which should be one of the command is was
     * just being executed.
     *
     * @param clazz Class of the command being executed.
     *
     * @return Returns the name of plugin containing the command currently called.
     * @throws InvalidClassException When this plugin's class file is not found in the current classLoader.
     */
    String guessPluginFromClass(final Class<?> clazz) throws InvalidClassException {
        final String commandLocationParsed = ((AdminAnything) plugin).getPluginUtils().parsePluginJARLocation(clazz);

        return ((AdminAnything) plugin).getPluginUtils().getPluginNameViaClassPathsMap(commandLocationParsed);
    } // end method

    /***
     * Returns name of the plugin to which the given command belongs.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * {@code
     * for (final Entry<String, Command> pair : this.getCommandMap().entrySet()) {
     *     Bukkit.getLogger().log("info", "Command '" + pair.getKey() + "' is from the plugin '" + this.getPluginForCommand(pair.getKey(), pair.getValue()) + "'");
     * }
     * </pre>
     *
     * @param key The commands name to lookup a plugin name for.
     * @param value The actual command we're trying to lookup a plugin for. Used for a short-circuited
     *              version of plugin checking when {@link org.bukkit.command.Command Command} is actually
     *              an instance of {@link org.bukkit.command.PluginCommand PluginCommand}, from which the plugin
     *              name retrieval is really trivial.
     *
     * @return Returns name of the plugin to which the given command belongs.
     * @throws InvalidClassException When this plugin's class file is not found in the current classLoader.
     */
    String getPluginForCommand(String key, final Command value) throws InvalidClassException {
        if (null == commandToPluginMap) {
            commandToPluginMap = new HashMap<String, String>();
        }

        if (commandToPluginMap.containsKey(key) && !((AdminAnything) plugin).getDebug()) {
            return commandToPluginMap.get(key);
        }

        // strip out the initial colon from commands that start on one (like :ping)
        if (key.startsWith(":")) {
            key = key.substring(1);
        }

        // a cache of all class paths for all plugins loaded - only used when we can't
        // determine the original plugin for this command otherwise and need to traverse
        // these paths to see if any of them matches
        String pluginName = null;

        // try to get plugin name from this command as a PluginCommand
        if (value instanceof PluginCommand) {
            pluginName = ((PluginIdentifiableCommand) value).getPlugin().getName();
        } else {

            // custom command class implementation, check if we have a prefix to lookup the plugin from
            if (key.contains(":")) {

                // check for core commands
                final String prefix = key.substring(0, key.indexOf(':'));
                if ("minecraft".equalsIgnoreCase(prefix) || "bukkit".equalsIgnoreCase(prefix) //NON-NLS
                    || "spigot".equalsIgnoreCase(prefix)) { //NON-NLS
                    // this is a core command
                    pluginName = prefix;
                } else {

                    // not a core command, continue search
                    final Plugin p = ((AdminAnything) plugin).getPluginUtils().getPluginIgnoreCase(prefix);
                    if (null != p) {
                        pluginName = p.getName();
                    }
                }
            }

            // plugin name still not determined, use a class name guessing method
            if (null == pluginName && null != value) {
                // non-prefixed, non-standard command
                // ... we can only guess by its classname location here
                pluginName = guessPluginFromClass(value.getClass());
            }
        }

        // cache the result
        commandToPluginMap.put(key, pluginName);

        // make sure we clean up after ourselves
        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {

            @Override
            public void run() {
                commandToPluginMap = null;
            }

        }, cleanupTimeout * (20 * 60));

        return pluginName;
    } //end method

    /***
     * Returns a list of plugins that contain the given command.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * {@code
     * for (final Entry<String, Command> pair : this.getCommandMap().entrySet()) {
     *     Bukkit.getLogger().log("info", "Command '" + pair.getKey() + "' is from the plugin '" + this.getPluginForCommand(pair.getKey(), pair.getValue()) + "'");
     * }
     * </pre>
     *
     * @param command The actual command we need to check all plugins for.
     * @param extendCorePluginNames If true, an extended name with a "Core -> " prefix is returned in the listing, otherwise
     *                              only the clear command name will be returned.
     *
     * @return Returns a list of plugins that contain the command in question.
     * @throws AccessException AccessException When we don't have the permission to access the {@link org.bukkit.command.SimpleCommandMap.knownCommands knownCommands} field of {@link org.bukkit.command.CommandMap commandMap}.
     * @throws IllegalAccessException When access is denied to the {@link org.bukkit.command.CommandMap commandMap} or {@link org.bukkit.command.SimpleCommandMap.knownCommands knownCommands} field.
     * @throws InvalidClassException When a class for any of the server plugins cannot be found within the current classLoader.
     * @throws InvocationTargetException When we try to invoke get() on {@link org.bukkit.command.SimpleCommandMap.knownCommands knownCommands} with invalid {@link org.bukkit.command.CommandMap commandMap} parameter.
     * @throws SecurityException When private fields cannot be accessed due to a security restriction.
     * @throws NoSuchMethodException When there is a get() method missing from the {@link org.bukkit.command.SimpleCommandMap.knownCommands knownCommands} field.
     */
    @SuppressWarnings("JavadocReference")
    List<String> getCommandContainingPlugins(final String command, final boolean... extendCorePluginNames)
            throws AccessException, IllegalAccessException, InvalidClassException, NoSuchMethodException,
            SecurityException, InvocationTargetException {

        if (null == containingPluginsCache) {
            containingPluginsCache = new HashMap<String, List<String>>();
        }

        if (containingPluginsCache.containsKey(command)) {
            return containingPluginsCache.get(command);
        }

        final List<String> containingPlugins = new ArrayList<String>();
        String             pluginName;

        // iterate over all plugins to find which of them contain our searched-for command
        for (final Entry<String, Command> pair : getCommandMap().entrySet()) {
            String key = pair.getKey();

            // strip out the initial colon from commands that start on one (like :ping)
            if (key.startsWith(":")) {
                key = key.substring(1);
            }

            // let's not care about commands other than the one we're looking for
            if (
                    // when this is not the command we're looking for
                    !key.equals(command) &&
                    // or it is a command we're looking for but from a different plugin
                    !(
                        key.contains(":") && key.substring(key.indexOf(':') + 1).equals(command)
                    ) &&
                    // or actually an alias
                    !pair.getValue().getAliases().contains(command)
                    ) {
                // sometimes, the actual key can be registered as alias
                // for example, Essentials:heal registers itself as command eheal
                // but still contains the key "name" set to the original "heal" command
                if (pair.getValue().getName().equals(command)) {
                    key = pair.getValue().getName();
                } else {
                    continue;
                }
            }

            pluginName = getPluginForCommand(key, pair.getValue());

            if (null == pluginName) {
                throw new AccessException('[' + AA_API.getAaName()
                + "] Plugin for the following command was not found: " + key);
            } else {
                //noinspection ConstantConditions,HardCodedStringLiteral
                if ("minecraft".equals(pluginName) || "spigot".equals(pluginName) || "bukkit"
                    .equals(pluginName)
                    || null != pluginName && pluginName.isEmpty()) {
                    containingPlugins
                        .add(AA_API.__("general.core") + (0 < extendCorePluginNames.length ? " -> " + key :
                                                          ""));
                } else {
                    if (!containingPlugins.contains(pluginName)) {
                        containingPlugins.add(pluginName);
                    }
                }
            }
        }

        // cache the result
        containingPluginsCache.put(command, containingPlugins);

        // make sure we clean up after ourselves
        if (-1 != cleanupTask) {
            // cancel the old cleanup task, so we can create a new one
            // and we don't end up setting cached values and cleaning them up
            // all the time
            Bukkit.getScheduler().cancelTask(cleanupTask);
            cleanupTask = -1;
        }

        // now setup a new cleanup task
        cleanupTask = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {

            @Override
            public void run() {
                clearContainingPluginsCache();
                cleanupTask = -1;
            }

        }, cleanupTimeout * (20 * 60));

        return containingPlugins;
    } //end method

    /**
     * Clears the plugins which contain commands cache.
     */
    void clearContainingPluginsCache() {
        containingPluginsCache = null;
    } //end method

    /**
     * Clears the commands to plugins cache.
     */
    void clearCommandToPluginMap() {
        commandToPluginMap = null;
    } //end method

    /***
     * Used to load all plugins that use the requested commands, so we can cancel their
     * player and console preprocess events in case they are being used to handle
     * their commands instead of CommandMap.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * {@code
     * ArrayList<String> okList = new ArrayList<String>();
     * ArrayList<String> koList = new ArrayList<String>();
     *
     * this.adjustListenerPriorities(
     *      someCommandSender,
     *      Arrays.asList(
     *          "I will be shown to the command sender if everything goes well.",
     *          "I will also be shown to the command sender if everything goes well."
     *      ),
     *      new String[] { "/essentials:ban" },
     *      null, // this would be a list against which to check essentials:ban, for example list of disabled commands
     *      true, // don't ignore the initial slash
     *      okList, // this list will get updated by all commands for which this adjustment succeeded
     *      koList // this list will get updated by all commands for which this adjustment failed
     * );
     * </pre>
     *
     * @param args List of commands to check and adjust (if neccessary) event priorities for.
     * @param listName Name of the {@link java.util.ArrayList} from {@link com.martinambrus.adminAnything.CommandListeners}
     *                 class where all the commands we adjust priorities here for should be added.
     *                 Can be null if this method is called from outside the {@link com.martinambrus.adminAnything.CommandListeners}
     *                 class.
     * @param ignoreLeadingSlash If true, leading slash is ignored (i.e. not stripped) when working with given commands in the `args` array.
     * @param okList If not null, all commands that had their listener priorities successfully amended will be put here.
     * @param koList If not null, all commands that we failed to change listener priorities for will be put here.

     * @return Returns an array with 2 items - index 0 contains a list of commands for which listener priorities were adjusted successfully,
     *         index 1 contains a list of commands for which listener priorities were not adjusted successfully (such as those already adjusted
     *         or previously present in the `listName` {@link java.util.ArrayList}).
     *
     * @throws AccessException AccessException When we don't have the permission to access the {@link org.bukkit.command.SimpleCommandMap.knownCommands knownCommands} field of {@link org.bukkit.command.CommandMap commandMap}.
     * @throws IllegalAccessException When access is denied to the {@link org.bukkit.command.CommandMap commandMap} or {@link org.bukkit.command.SimpleCommandMap.knownCommands knownCommands} field.
     * @throws SecurityException When private fields cannot be accessed due to a security restriction.
     * @throws NoSuchFieldException When there is no such field as {@link org.bukkit.command.CommandMap commandMap} or {@link org.bukkit.command.SimpleCommandMap.knownCommands knownCommands}.
     * @throws IllegalArgumentException When we pass an invalid argument to the {@link org.bukkit.command.CommandMap commandMap's getCommand()} method.
     */
    @SuppressWarnings({"JavadocReference", "ConstantConditions", "MismatchedQueryAndUpdateOfCollection"})
    void adjustListenerPriorities(final String[] args, String listName, final boolean ignoreLeadingSlash,
                                  final List<String> okList, final List<String> koList)
                    throws IllegalAccessException, AccessException, NoSuchFieldException, IllegalArgumentException,
                    SecurityException, InvalidClassException, NoSuchMethodException, InvocationTargetException {
        // set up some local variables
        List<String>             theList;
        boolean                  reloadLists           = false;
        final Collection<String> containingPluginsList = new ArrayList<String>();

        // the actual list to check for commands which need an adjustment of their listener priorities
        theList = null == listName ? new ArrayList<String>() :
                  ((AdminAnything) plugin).getCommandListenersUtils().getListReference(listName);

        for (String arg : args) {
            // don't worry about empty arguments, which will be passed from the getClearCommand() method
            // as the second parameter when a clear command is already passed to it
            if (null == arg || arg.isEmpty()) {
                continue;
            }

            // remove initial slash (/) from the command, should one reside there
            if (arg.startsWith("/") && !ignoreLeadingSlash) {
                arg = arg.substring(1);
            }

            final String argOriginal = arg;

            // if this is a command with parameters (for example when coming from /aa_mutecommand's initialization adjustListenerPriorities() method),
            // we need to adjust the argument, so it only contains the actual command name
            if (arg.contains(" ")) {
                arg = arg.substring(0, arg.indexOf(' '));
            }

            final String       lowerCaseCommand  = argOriginal.toLowerCase();
            final List<String> containingPlugins = getCommandContainingPlugins(arg.toLowerCase());

            // we've found plugins that contain this command
            if (!containingPlugins.isEmpty()) {
                for (final String plug : containingPlugins) {
                    if (!containingPluginsList.contains(plug)) {
                        containingPluginsList.addAll(containingPlugins);
                    }
                }

                if (!theList.contains(lowerCaseCommand)) {
                    if (null != okList) {
                        okList.add(argOriginal);
                    }
                } else {
                    if ((null != okList) && (null != koList) && !okList.contains(argOriginal) && !koList
                        .contains(argOriginal)) {
                        okList.add(argOriginal);
                    }
                }
            } else if (null != koList) {
                koList.add(argOriginal);
            }

            // add this to the given list of commands, if any
            if (!containingPlugins.isEmpty() && !theList.contains(lowerCaseCommand)) {
                // we'll have to reload internal lists
                // of the commandPreprocessor in order to
                // include the newly added configuration
                reloadLists = true;

                // virtual permission lists must be handled differently,
                // as we add permission:command_line records to the config,
                // not command:replacement records - so we handle it elsewhere
                if (null != listName && !"virtualperms".equals(listName)) { //NON-NLS
                    theList.add(lowerCaseCommand);
                }
            }
        }

        // reload commandPreprocessor lists, if needed
        if (reloadLists) {
            Bukkit.getPluginManager().callEvent(new AAReloadEvent("commandPreprocessor")); //NON-NLS
        }

        // we need to make sure that event cancellation is considered for player and console command preprocessing events
        // in all the plugins that contain the command we're working with
        for (final String s : containingPluginsList) {
            // nothing to do for core plugins
            if ( s.startsWith(AA_API.__("general.core")) ) {
                continue;
            }

            final Iterable<HandlerList> hl = HandlerList.getHandlerLists();
            for (final HandlerList l : hl) {
                for (RegisteredListener r : l.getRegisteredListeners()) {
                    if (!r.getPlugin().getName().equals(s)) {
                        // we're only interested in re-registering events from the plugins that contain our command
                        continue;
                    }

                    // this could consume a bit more processing power, but there's no other way to make sure...
                    for (final Method m : r.getListener().getClass().getDeclaredMethods()) {
                        for (final Class<?> c : m.getParameterTypes()) {
                            if ((c.getName().endsWith("ServerCommandEvent") //NON-NLS
                                || c.getName().endsWith("PlayerCommandPreprocessEvent")) //NON-NLS
                                    && (!r.isIgnoringCancelled())) {
                                // plugin is not ignoring cancelled events - we need to fix this
                                try {
                                    // HPWP compatibility - https://www.spigotmc.org/resources/1-7-1-16-x-hereteres-per-world-plugins.88018/
                                    if ( r.getClass().getSimpleName().equals( "HPWPRegisteredListener" ) ) {
                                        Method hpwp_get_delegate = r.getClass().getDeclaredMethod( "getDelegate" );
                                        r = (RegisteredListener) hpwp_get_delegate.invoke( r );
                                    }

                                    Field fPriority;
                                    Field fIgnoreCancelled;

                                    // set priority to low if it's on lowest, as AA needs to be
                                    // able to catch commands coming out from this plugin and cancel them
                                    fPriority = r.getClass().getDeclaredField("priority");
                                    fPriority.setAccessible(true);

                                    if ( fPriority.get(r) == EventPriority.LOWEST ) {
                                        fPriority.set(r, EventPriority.LOW);
                                    }

                                    // Essentials' PowerTools will fail to work if we tamper with Essentials' ignoreCancelled flag in command events
                                    // and I found out that Essentials doesn't cause any trouble when this flag is left alone
                                    if ( !s.equalsIgnoreCase( "essentials" ) ) {
                                        // set ignoreCancelled to true if this plugin did not set
                                        // ignoreCancelled for its command listeners - command events need to be ignored when cancelled
                                        // on event bubbling, so we can actually redirect / fix commands in AA
                                        fIgnoreCancelled = r.getClass().getDeclaredField( "ignoreCancelled" );
                                        fIgnoreCancelled.setAccessible( true );

                                        if ( !fIgnoreCancelled.getBoolean(r) ) {
                                            fIgnoreCancelled.setBoolean( r, true );
                                        }
                                    }
                                } catch (final NoSuchFieldException e) {
                                    // Bukkit API changed?
                                    e.printStackTrace();
                                    throw new NoSuchFieldException('[' + AA_API.getAaName()
                                    + "] API error 03 - could not ensure that event handlers of the plugin " + s
                                    + " will not intercept one of the disabled commands: "
                                    + String.join(", ", args) + " .");
                                } catch (final SecurityException e) {
                                    // not possible to get the executor
                                    e.printStackTrace();
                                    throw new SecurityException('[' + AA_API.getAaName()
                                    + "] Security exception - could not ensure that event handlers of the plugin "
                                    + s + " will not intercept one of the disabled commands: "
                                    + String.join(", ", args) + " .");
                                }
                            }
                        }
                    }
                }
            }
        }

        final Collection<List<String>> ret = new ArrayList<List<String>>();
        if ((null != okList) && (null != koList)) {
            ret.add(okList);
            ret.add(koList);
        }

    } //end method

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
     * if (this.isAaCoreCommand("aa_reload")) {
     *     Bukkit.getLogger().log("info", "Command /aa_reload() is indeed an AdminAnything core command :)");
     * }
     * </pre>
     *
     * @param cmd The command to check.
     *
     * @return Returns true if the command in question comes from AdminAnything, false otherwise.
     */
    boolean isAaCoreCommand(final String[] cmd) {
        boolean isCoreCommand = false;

        for (final String s : cmd) {
            if (plugin.getDescription().getCommands().containsKey(s.toLowerCase())) {
                isCoreCommand = true;
            }
        }

        return isCoreCommand;
    } //end method

    /***
     * Returns a clear command name from the command given.
     * Used when a command is prefixed (i.e. minecraft:ban or essentials.ban)
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * // will hold "ban"
     * String clearBan = this.getClearCommand("minecraft:ban");
     * </pre>
     *
     * @param cmd The actual command to return stripped of dots and colons.
     *
     * @return Returns a command that's stripped-down version of the original commands,
     *         i.e. without any dots or colons.
     */
    String[] getClearCommand(final String cmd) {
        if (cmd.contains(".")) {
            return cmd.split(Pattern.quote("."));
        } else if (cmd.contains(":")) {
            return cmd.split(Pattern.quote(":"));
        } else {
            return new String[] { cmd, "" };
        }
    } //end method

    /**
     * Retrieves a list of all commands on the server.
     *
     * @return Returns a list of all registered command names on the server.
     *
     * @throws IllegalAccessException    When access is denied to the {@link org.bukkit.command.CommandMap commandMap} or {@link org.bukkit.command.SimpleCommandMap.knownCommands knownCommands} field.
     * @throws NoSuchMethodException     When there is a get() method missing from the {@link org.bukkit.command.SimpleCommandMap.knownCommands knownCommands} field.
     * @throws InvocationTargetException When we try to invoke get() on {@link org.bukkit.command.SimpleCommandMap.knownCommands knownCommands} with invalid {@link org.bukkit.command.CommandMap commandMap} parameter.
     * @throws AccessException           When we don't have the permission to access the {@link org.bukkit.command.SimpleCommandMap.knownCommands knownCommands} field of {@link org.bukkit.command.CommandMap commandMap}.
     */
    @SuppressWarnings("JavadocReference")
    Iterable<String> getServerCommands()
        throws AccessException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        if (null == serverCommands || AA_API.getDebug()) {
            serverCommands = new HashSet<String>();
            Map<Integer, Boolean> doneCommandIDs = new HashMap<Integer, Boolean>();
            for (final Entry<String, Command> pair : AA_API.getCommandMapCopy().entrySet()) {
                String key = pair.getKey();

                // strip out the initial colon from commands that start on one (like :ping)
                if (key.startsWith(":")) {
                    key = key.substring(1);
                }

                final String clearCommandName = (key.contains(":") ? key.substring(key.indexOf(':') + 1) :
                                                 pair.getKey());

                // check that this is indeed a command and not an alias and that it's not been added to completion yet
                final List<String> aliases = pair.getValue().getAliases();
                if (doneCommandIDs.containsKey(pair.getValue().hashCode())
                    || ((null != aliases) && aliases.contains(clearCommandName))) {
                    // alias found or command already added, let's bail out here or we'd duplicate it in the completion
                    continue;
                } else {
                    // mark this command as done, so we don't duplicate it
                    // ... this is because the commandMap contains both versions of the command,
                    //     one without the plugin prefix and one with it (i.e. /essentials:repair AND /repair)
                    doneCommandIDs.put(pair.getValue().hashCode(), true);
                    serverCommands.add(clearCommandName);
                }
            }

            serverCommands = Collections.unmodifiableSet(serverCommands);
        }

        return serverCommands;
    } // end method

    /**
     * Retrieves a list of all commands for the given plugin.
     *
     * @param plugin The plugin name for which to retrieve commands.
     *
     * @return Returns a list of commands for the given plugin or null if none are found.
     * @throws IllegalAccessException    When access is denied to the {@link org.bukkit.command.CommandMap commandMap} or {@link org.bukkit.command.SimpleCommandMap.knownCommands knownCommands} field.
     * @throws NoSuchMethodException     When there is a get() method missing from the {@link org.bukkit.command.SimpleCommandMap.knownCommands knownCommands} field.
     * @throws InvocationTargetException When we try to invoke get() on {@link org.bukkit.command.SimpleCommandMap.knownCommands knownCommands} with invalid {@link org.bukkit.command.CommandMap commandMap} parameter.
     * @throws AccessException           When we don't have the permission to access the {@link org.bukkit.command.SimpleCommandMap.knownCommands knownCommands} field of {@link org.bukkit.command.CommandMap commandMap}.
     * @throws InvalidClassException     When this plugin's class file is not found in the current classLoader.
     */
    @SuppressWarnings("JavadocReference")
    Iterable<String> getPluginCommands(String plugin) throws AccessException, IllegalAccessException,
        NoSuchMethodException, InvocationTargetException, InvalidClassException {
        if (null == pluginCommandsCache || !pluginCommandsCache.containsKey(plugin) || AA_API.getDebug()) {
            // don't reset unless we really need to
            if (AA_API.getDebug() || null == pluginCommandsCache) {
                pluginCommandsCache = new HashMap<String, List<String>>();
            }

            Map<Integer, Boolean> doneCommandIDs = new HashMap<Integer, Boolean>();
            for (final Entry<String, Command> pair : AA_API.getAugmentedCommandMap().entrySet()) {
                String       key             = pair.getKey();
                final String localPluginName = AA_API.getPluginForCommand(pair.getKey(), pair.getValue());

                // strip out the initial colon from commands that start on one (like :ping)
                if (key.startsWith(":")) {
                    key = key.substring(1);
                }

                final String clearCommandName = (key.contains(":") ? key.substring(key.indexOf(':') + 1) : key);

                // check that this is indeed a command and not an alias and that it's not been added to completion yet
                final List<String> aliases = pair.getValue().getAliases();
                if (doneCommandIDs.containsKey(pair.getValue().hashCode())
                    || ((null != aliases) && aliases.contains(clearCommandName))) {
                    // alias found or command already added, let's bail out here or we'd duplicate it in the completion
                    continue;
                } else {
                    // mark this command as done, so we don't duplicate it
                    // ... this is because the commandMap contains both versions of the command,
                    //     one without the plugin prefix and one with it (i.e. /essentials:repair AND /repair)
                    doneCommandIDs.put(pair.getValue().hashCode(), true);

                    if (!pluginCommandsCache.containsKey(localPluginName)) {
                        pluginCommandsCache.put(localPluginName, new ArrayList<String>());
                    }

                    pluginCommandsCache.get(localPluginName).add(clearCommandName);
                }
            }
        }

        return pluginCommandsCache.containsKey(plugin) ?
               Collections.unmodifiableSet(new HashSet<String>(pluginCommandsCache.get(plugin))) : null;
    } // end method

    /**
     * Loads and returns configuration file for manual commands and their permissions for plugins that don't have these
     * in their plugin.yml file.
     */
    FileConfiguration getManualPermDescConfig() {
        // if we're already loaded, just return the config
        if (null != this.manualPermDescriptionsConf) {
            return this.manualPermDescriptionsConf;
        }

        // not loaded yet, let's load the config
        this.manualPermDescriptionsConf = YamlConfiguration.loadConfiguration(new File(AA_API.getAaDataDir(), this.manualPermDescriptionsFileName));

        // get our config file that's valid for current version of our plugin from the JAR file
        InputStream defManualPermDescriptionsConfigStream = this.plugin.getResource( this.manualPermDescriptionsFileName );
        if (defManualPermDescriptionsConfigStream != null) {
            this.manualPermDescriptionsConf.setDefaults( YamlConfiguration.loadConfiguration( new InputStreamReader(defManualPermDescriptionsConfigStream, StandardCharsets.UTF_8) ) );
        }

        // copy all new keys that we could have in current plugin version's conf file into the existing one on the HDD
        this.manualPermDescriptionsConf.options().copyDefaults(true);

        // save the config file locally
        try {
            this.manualPermDescriptionsConf.save( AA_API.getAaDataDir() + "/" + this.manualPermDescriptionsFileName );
        } catch (Throwable ex) {
            // even if we can't save the config, we still have it in memory, so we can continue below
            Bukkit.getLogger().severe('[' + AA_API.getAaName()
                + "] Could not save manual permissions and commands configuration file. Please report the following to the plugin's author..."); //NON-NLS
            ex.printStackTrace();
        }

        return this.manualPermDescriptionsConf;
    }

} // end class