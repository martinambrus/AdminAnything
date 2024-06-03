package com.martinambrus.adminAnything;

import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.io.InvalidClassException;
import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Plugins-related tooling. Mostly used to find out from which JAR file and package
 * is a plugin loaded, then also for some caching and case-insensitive server plugin search.
 *
 * @author Martin Ambrus
 */
final class Plugins implements Listener {

    /**
     * Instance of {@link com.martinambrus.adminAnything.AdminAnything}.
     */
    private final AdminAnything aa;

    /**
     * Cache of each plugin's main class location within their JAR files.
     *
     * Used to determine which plugin is a command being called from if we
     * cannot determine this in any other way.
     *
     * Holds a [plugin's class jar location] <> [plugin name] (case unchanged)
     * relationship.
     */
    private Map<String, String> classPathsCache;

    /**
     * Cache of lowercase plugin names.
     *
     * Used when we need to lookup a plugin and we don't know what its
     * case originally was.
     *
     * Holds a [lowercased plugin name] <> [original plugin name] relationship.
     */
    private Map<String, String> pluginNamesCache;

    /**
     * Set of all plugin on the server. This is here, so we have a cached version
     * of them and don't need to request a keyset from the PluginManager every time
     * we need to invoke an autocomplete.
     */
    private Set<String> serverPlugins;

    /**
     * To save memory, the above caches as cleared up every X minutes.
     * This is the setting variable to say how fast AA should clear them.
     *
     * This variable is in minutes.
     */
    private final int cleanupTimeout = 15; // minutes

    /**
     * Reference to the actual cleanup task that clears up our caches.
     */
    static int cleanupTask = -1;

    /**
     * Constructor. Makes sure that we have our plugin's reference stored locally.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * // usage in AdminAnything main class
     * this.plugins = new Plugins(this);
     * </pre>
     *
     * @param plugin Instance of {@link com.martinambrus.adminAnything.AdminAnything AdminAnything}.
     */
    Plugins(final AdminAnything plugin) {
        aa = plugin;
    } //end method

    /***
     * A simple method to initialize the cache of class paths for each
     * of the plugins on the server.
     */
    private void initClassPathCache() {
        if (null == classPathsCache) {
            classPathsCache = new HashMap<String, String>();
        }

        for (final Plugin pl : Bukkit.getPluginManager().getPlugins()) {
            try {
                classPathsCache.put(parsePluginJARLocation(pl.getClass()), pl.getName());
            } catch (final InvalidClassException e) {
                Bukkit.getLogger().severe(AA_API.__("plugin.error-class-location-not-found"));
                e.printStackTrace();
            }
        }
    } // end method

    /***
     * Loads the class paths cache if not already loaded and returns it.
     *
     * @return The actual class paths map containing locations of all plugin classes.
     */
    private Map<String, String> getPluginClassPathsMap() {
        if ((null == classPathsCache) || classPathsCache.isEmpty()) {
            initClassPathCache();
            setupCleanupTasks();
        }

        return classPathsCache;
    } // end method

    /***
     * Cache lowercase names of plugins on the server.
     */
    private void initPluginNamesCache() {
        if (null == pluginNamesCache) {
            pluginNamesCache = new HashMap<String, String>();

            for (final Plugin p : Bukkit.getPluginManager().getPlugins()) {
                pluginNamesCache.put(p.getName().toLowerCase(), p.getName());
            }

            setupCleanupTasks();
        }
    } // end method

    /**
     * Clears up internal caches after some data has been cached
     * and a predefined timeout has passed to save memory resources
     * on the server.
     */
    @SuppressWarnings("IntegerMultiplicationImplicitCastToLong")
    private void setupCleanupTasks() {
        if (-1 != cleanupTask) {
            // cancel the old cleanup task, so we can create a new one
            // and we don't end up setting cached values and cleaning them up
            // all the time
            Bukkit.getScheduler().cancelTask(cleanupTask);
            cleanupTask = -1;
        }

        // now setup a new cleanup task
        cleanupTask = Bukkit.getScheduler().scheduleSyncDelayedTask(aa, new Runnable() {

            @Override
            public void run() {
                clearPluginClassPathsMap();
                clearPluginNamesCache();
                cleanupTask = -1;
            }

        }, cleanupTimeout * (20 * 60));
    } // end method

    URL getClassLocationFromAnywhere(Object o) {
        if ( o == null ) {
            return null;
        }
        Class<?> c = o.getClass();
        ClassLoader loader = c.getClassLoader();
        if ( loader == null ) {
            // Try the bootstrap classloader - obtained from the ultimate parent of the System Class Loader.
            loader = ClassLoader.getSystemClassLoader();
            while ( loader != null && loader.getParent() != null ) {
                loader = loader.getParent();
            }
        }
        if (loader != null) {
            String name = c.getCanonicalName();
            URL resource = loader.getResource(name.replace(".", "/") + ".class");
            if ( resource != null ) {
                return resource;
            }
        }

        return null;
    }

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
    String parsePluginJARLocation(final Class<?> commandClass) throws InvalidClassException {
        URL commandLocation = commandClass
            .getResource('/' + commandClass.getName().replace('.', '/') + ".class"); //NON-NLS

        // if we can't determine class' JAR location from its name, let's try to do it through its classLoader
        // ... this is the case for plugins that use hackery and magic to register their commands, such as AsyncWorldEdit
        if (null == commandLocation) {
            Utils.logDebug(
                "Could not determine location for " + commandClass + ", trying classLoader route...", //NON-NLS
                aa);
            commandLocation = commandClass
                .getResource('/' + commandClass.getClassLoader().getClass().getName()
                                               .replace('.', '/') + ".class"); //NON-NLS
        }

        // if we can't determine this commandClass' JAR location still, try to split its name
        // and start cutting it off part by part, retaining shorter representation for it
        // every time until there's nothing to cut off... hopefully this way we can find
        // this class' JAR file at the end
        if (null == commandLocation) {
            Utils.logDebug("Still nothing, going for last resort - className trimming...", aa); //NON-NLS

            String spl = commandClass.getName();
            while (null != spl && !spl.isEmpty()) {
                commandLocation = commandClass.getResource('/' + spl.replace('.', '/') + ".class"); //NON-NLS
                Utils.logDebug("Trying " + spl + ": " + commandLocation, aa); //NON-NLS

                spl =
                    (null == commandLocation) && (-1 < spl.lastIndexOf('.')) ? spl.substring(0, spl.lastIndexOf('.')) :
                    "";
            }
        }

        // last try - this will most probably return that the location of our class is in the root classLoader,
        // and that the class name of our class is the generic java.lang.Class, since for example the PlaceholderAPI
        // plugin will load its classes, so their classLoader is not accessible to us
        if (null == commandLocation) {
            commandLocation = getClassLocationFromAnywhere( commandClass );
        }

        // if we still can't find the JAR location, we're doomed :=o
        if (null == commandLocation) {
            throw new InvalidClassException(
                    "Class location for the class " + commandClass + " could not be determined!");
        }

        if ( commandLocation.getPath().indexOf('!') > -1 ) {
            return commandLocation.getPath().substring(0, commandLocation.getPath().indexOf('!'));
        } else {
            return commandLocation.getPath();
        }
    } // end method

    /**
     * Clears the class paths cache.
     */
    void clearPluginClassPathsMap() {
        classPathsCache = null;
    } // end method

    /**
     * Clears the lowercased plugin names cache.
     */
    void clearPluginNamesCache() {
        pluginNamesCache = null;
    } // end method

    /***
     * Returns plugin name from the cache of lowercased plugins
     * as opposed to the Bukkit.getPluginManager().getPlugin() case-sensitive getting.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * // realPluginName will be set to "AdminAnything" for this one
     * String realPluginName = aa_plugin_instance.getPluginIgnoreCase("ADminAnyThINg");
     * </pre>
     *
     * @param pluginName Name of the plugin (in any text case, i.e. plugin / Plugin / PLuGIn)
     *                   we wish to retrieve the original name for.
     *
     * @return Returns the original name of the given plugin as it's known on the server.
     */
    Plugin getPluginIgnoreCase(final String pluginName) {
        initPluginNamesCache();

        return pluginNamesCache.containsKey(pluginName.toLowerCase()) ?
               Bukkit.getPluginManager().getPlugin(pluginNamesCache.get(pluginName.toLowerCase())) : null;
    } // end method

    /***
     * Returns a clear name of the plugin from a string which may contain the plugin along with
     * a command (essentials:ban) or along with a core alias (Core -> minecraft:ban).
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * // essentials will be set to "essentials"
     * String essentials = aa_plugin_instance.getPluginUtils().getCleanPluginName("essentials:ban");
     *
     * // ban will be set to "ban"
     * String ban = aa_plugin_instance.getPluginUtils().getCleanPluginName("Core -> ban");
     * </pre>
     *
     * @param pluginWithCommand    The actual plugin name with the command we're trying to get a clean name for.
     * @param returnRealPluginName If true, this method will return plugin name with the correct case,
     *                             otherwise it returns plugin name that's given via the command line.
     *
     * @return Returns a clear name of the plugin from a string which may contain prefixes.
     */
    String getCleanPluginName(String pluginWithCommand, final boolean returnRealPluginName) {
        // remove the Core prefix
        if (pluginWithCommand.startsWith(AA_API.__("general.core") + " -> ")) {
            pluginWithCommand = pluginWithCommand.replace(AA_API.__("general.core") + " -> ", "");
        }

        // check for plugin name including command
        String[] spl = null;
        if (pluginWithCommand.contains(":")) {
            spl = pluginWithCommand.split(Pattern.quote(":"));
            pluginWithCommand = spl[0];
        }

        // it's possible to check for a name from a dot-separated command,
        // such as Essentials.god
        if (pluginWithCommand.contains(".")) {
            spl = pluginWithCommand.split(Pattern.quote("."));
            pluginWithCommand = spl[0];
        }

        // if we need the real plugin name returned (with the correct letter case),
        // let's do just that
        if (returnRealPluginName) {
            // for built-in commands, use the "plugin name" that we've received,
            // otherwise look it up
            if (!"minecraft".equals(pluginWithCommand) && !"spigot".equals(pluginWithCommand) && !"bukkit"
                .equals(pluginWithCommand)) {
                Plugin pl = getPluginIgnoreCase(pluginWithCommand);
                if (null != pl) {
                    pluginWithCommand = pl.getName();
                } else if (null != spl) {
                    // this seems to be a core command, display it as such
                    pluginWithCommand = String.join(":", spl);
                } else {
                    pluginWithCommand = null;
                }
            }
        }

        return pluginWithCommand;
    } // end method

    /**
     * Attempts to retrieve a plugin name by using this plugin's class location
     * in the class paths cache.
     *
     * This method is used when we cannot determine the name of a plugin
     * that is firing a command because it uses its own classloader.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * // usage in Commands class
     * Command value = [value_from_a_method_call];
     * String commandLocationParsed = this.plugin.getPluginUtils().parsePluginJARLocation(value.getClass());
     *
     * String pluginName = this.plugin.getPluginUtils().getPluginNameViaClassPathsMap(commandLocationParsed);
     * </pre>
     *
     * @param commandLocation Location of the plugin's class in the class paths map.
     *                        Can be retrieved via {@link com.martinambrus.adminAnything.Plugins#parsePluginJARLocation(Class)}.
     *
     * @return Returns the actual plugin name as determined from its location in the class paths.
     */
    String getPluginNameViaClassPathsMap(final String commandLocation) {
        // load the class paths cache if not loaded yet
        if ((null == classPathsCache) || classPathsCache.isEmpty()) {
            getPluginClassPathsMap();
        }

        // determine plugin's name from the cache
        return classPathsCache.containsKey(commandLocation) ? classPathsCache.get(commandLocation) :
               AA_API.__("general.core");
    } // end method

    /**
     * Checks for existance of a command location key in the class map.
     * Since we don't want to expose the whole map, as it would be writable
     * by anyone, we just expose this method directly here.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * {@code
     * if (this.classMapContainsKey(someRandomAdminAnythingCommandClassMapLocation)) {
     *     pluginName = this.getClassMapKey(someRandomAdminAnythingCommandClassMapLocation);
     * }
     * </pre>
     *
     * @param key The key we want to check for in the class map.
     *
     * @return Returns true if the given key exists within the class map, false otherwise.
     */
    boolean classMapContainsKey(final String key) {
        return getPluginClassPathsMap().containsKey(key);
    } // end method

    /**
     * Retrieves a key from the class map cache.
     * Since we don't want to expose the whole map, as it would be writable
     * by anyone, we just expose this method directly here.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * {@code
     * if (this.classMapContainsKey(someRandomAdminAnythingCommandClassMapLocation)) {
     *     pluginName = this.getClassMapKey(someRandomAdminAnythingCommandClassMapLocation);
     * }
     * </pre>
     *
     * @param key The key we want to get from the class map.
     *
     * @return Returns the requested key from the class map.
     */
    String getClassMapKey(final String key) {
        return getPluginClassPathsMap().get(key);
    } // end method

    /**
     * Retrieves a set of all plugin names on the server.
     *
     * @return Returns a set with names of all the plugins on the server.
     */
    Iterable<String> getServerPlugins() {
        return getServerPlugins("");
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
    Iterable<String> getServerPlugins(String... prependBy) {
        // cache server plugins
        if (null == this.serverPlugins) {
            this.serverPlugins = new HashSet<String>();

            for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
                this.serverPlugins.add(p.getName());
            }


            this.serverPlugins = Collections.unmodifiableSet(this.serverPlugins);
        }

        // if we're prepending plugin names by a prefix,
        // return a temporary iterable that will contain prefixed plugin names
        if (!"".equals(prependBy[0])) {
            Set<String> tmpServerPlugins = new HashSet<String>();

            for (String plugName : this.serverPlugins) {
                tmpServerPlugins.add(prependBy[0] + plugName);
            }

            return tmpServerPlugins;
        } else {
            return this.serverPlugins;
        }
    } // end method

} // end class