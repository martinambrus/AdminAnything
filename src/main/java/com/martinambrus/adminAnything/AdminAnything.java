package com.martinambrus.adminAnything;

import com.martinambrus.adminAnything.events.AAReloadEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * AdminAnything - the conflict-resolving tool for administrators
 * of Bukkit-based Minecraft servers which conveniently includes
 * a virtual assistant.
 *
 * @author Martin Ambrus
 */
@SuppressWarnings("ClassWithTooManyDependencies")
public final class AdminAnything extends JavaPlugin implements Listener {

    /**
     * Determines whether AA is still warming up (i.e. waiting for all plugins
     * to load, so it can adjust all required listener priorities), or it's
     * already loaded and all commands can now start working as intended.
     */
    private boolean warmingUp = true;

    /**
     * Instance of {@link com.martinambrus.adminAnything.ConfigAbstractAdapter}.
     */
    private ConfigAbstractAdapter config = null;

    /**
     * Instance of {@link com.martinambrus.adminAnything.Language}.
     */
    private Language lang = null;

    /**
     * Instance of {@link com.martinambrus.adminAnything.Commands}.
     */
    Commands commands = null;

    /**
     * Instance of {@link com.martinambrus.adminAnything.CommandListeners}.
     */
    private CommandListeners commandListeners = null;

    /**
     * Instance of {@link com.martinambrus.adminAnything.Permissions}
     */
    private Permissions perms = null;

    /**
     * Instance of {@link com.martinambrus.adminAnything.Plugins}
     */
    private Plugins pluginUtils = null;

    /**
     * Instance of {@link com.martinambrus.adminAnything.Listeners}
     */
    private Listeners listeners = null;

    /**
     * Instance of {@link com.martinambrus.adminAnything.TabComplete}
     */
    private TabComplete tabComplete = null;

    /**
     * Instance of {@link com.martinambrus.adminAnything.InventoryManager}
     */
    private InventoryManager inventoryManager = null;

    /**
     * Instance of {@link com.martinambrus.adminAnything.Updater}
     */
    private Updater updater = null;

    /***
     * Determines whether custom metrics have already been started.
     * This is present, so even if AA is reloaded (disabled and re-enabled
     * on the server itself), we won't be duplicating metrics.
     */
    private boolean metricsStarted = false;

    /***
     * Called by the server when a plugin is loaded
     * and ready for some action.
     */
    @Override
    public void onEnable() {
        // initialize the API
        new AA_API(this);

        // check, update and set current valid plugin configuration
        Config configBootstrap = new Config(this);
        this.config = configBootstrap.getConfigAdapter();

        // load translations
        lang = new Language(this);
        if (!lang.init()) {
            // bail out if we couldn't load the language file (AA will be disabled automatically)
            return;
        }

        // initialize the listeners registrator and utils class
        listeners = new Listeners(this);
        listeners.init();

        // initialize permissions handler (Vault or native)
        perms = new Permissions(this);

        // initialize Plugin-related utilities
        pluginUtils = new Plugins(this);

        // initialize Inventory-related utilities
        inventoryManager = new InventoryManager(this);

        // enable listeners for commands to allow for adding, removing
        // and saving ignored, overridden, redirected, muted... commands
        commandListeners = new CommandListeners();
        listeners.startRequiredListener("coreCommandListeners", commandListeners); //NON-NLS

        // register executors for commands
        commands = new Commands(this);
        if (commands.registerCommandExecutors(config)) {
            // bail out if we're disabling AA due to a missing command (AA will be disabled automatically)
            return;
        }

        // register tab completer
        tabComplete = new TabComplete(this);
        tabComplete.registerTabCompleters();

        // check for native overrides system being enabled
        // and delay showing to user at the end of server load,
        // so it's well visible
        Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable() {

            @Override
            public void run() {
                // perform the check
                commands.checkNativeOverrides();
            }

        }, 0); // 0 = will be run as soon as the server finished loading

        // enable update checker
        updater = new Updater(this);

        // start collecting some metrics
        if (!metricsStarted) {
            new CustomMetrics(this).initMetrics();
            metricsStarted = true;
        }

        // set firstRun to false after we run AA for the first time
        if (AA_API.getConfigBoolean("firstRun")) { //NON-NLS
            getExternalConf().set("firstRun", false); //NON-NLS
            getExternalConf().saveConfig();
        }

        // we're enabled!
        //noinspection HardCodedStringLiteral
        Bukkit.getLogger()
              .info(config.getPluginName() + " v" + config.getInternalConf().getVersion() + ' ' + AA_API
                  .__("general.enabled"));

        // make sure to reload our caches after the server is loaded and then some time after that
        // ... otherwise, we could load ourselves before plugins that we strive to adjust
        //     listeners for and furthermore, we could hit-and-miss some plugins for commands
        //     on the server when we already have some commands fixed / muted / disabled etc.
        // ... this is due to the fact that on startup, every config is checked against all commands
        //     on the server and if some plugins are not loaded at that point yet, we'll miss their commands
        Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable() {

            @Override
            public void run() {
                if (AdminAnything.this.warmingUp) {
                    Bukkit.getLogger()
                          .info("[" + config.getPluginName() + "] " + AA_API.__("startup.will-start-in-10-seconds"));
                }

                Bukkit.getScheduler().scheduleSyncDelayedTask(AdminAnything.this, new Runnable() {

                    @Override
                    public void run() {
                        AdminAnything.this.commands.clearCommandToPluginMap();
                        AdminAnything.this.commands.clearContainingPluginsCache();
                        Bukkit.getScheduler().runTaskAsynchronously( AdminAnything.this, new Runnable() {
                            @Override
                            public void run() {
                                AdminAnything.this.tabComplete.init(null);
                            }
                        });

                        if (AdminAnything.this.warmingUp) {
                            AdminAnything.this.warmingUp = false;
                            Bukkit.getLogger().info( "[" + config.getPluginName() + "] " + AA_API
                                .__("startup.now-active", AA_API.getAaName()));

                            // activate the ASM method injector - used to silence commands
                            //CustomMethodInjector.main( AdminAnything.this );
                        }
                    }

                }, 20 * 10); // wait a couple of seconds before we load...
            }

        }, 0); // 0 = will be run as soon as the server finished loading
    } // end method

    /***
     * Called by the server when a plugin needs to be unloaded.
     * This is usually called on a server stop or reload, the latter
     * of which is EVIL and should never be used! :-P
     */
    @Override
    public void onDisable() {
        this.warmingUp = true;

        // load config from the config file, since we don't want to save old values
        config.reloadConfig();
        reloadConfig();

        // unregister all registered AA commands and clear command caches
        if (null != commands && null != config) {
            commands.unregisterCommandExecutors(config);
            commands.clearContainingPluginsCache();
            commands.clearCommandToPluginMap();
        }

        // unregister the Vault connection
        if (null != perms) {
            perms.unregisterVaultServiceProvider();
        }

        // unregister event listeners for AA
        if (null != listeners) {
            listeners.unregisterListeners();
        }

        // stop update checking
        if (null != updater) {
            updater.unregister();
        }

        // terminate configuration DB connection, if any
        config.onClose();

        // reset internal variables, should we re-enable this plugin again,
        // so they get re-loaded correctly
        config = null;
        commands = null;
        commandListeners = null;
        pluginUtils = null;
        perms = null;
        listeners = null;
        updater = null;
        tabComplete = null;
        inventoryManager = null;

        // inform (via console) that we're disabled now
        Bukkit.getLogger()
              .info((null != config ? config.getPluginName() + " v" + config.getInternalConf().getVersion() : //NON-NLS
                     "AdminAnything") + ' ' + AA_API.__("general.disabled")); //NON-NLS

        lang = null;
    } // end method

    /***
     * Wrapper method to {@link com.martinambrus.adminAnything.Config#getConfigAdapter()}.
     * Gets user YAML plugin configuration (config.yml) for AdminAnything.
     *
     * @return Returns plugin configuration for AdminAnything, loaded either from
     *         the config.yml file located in [pluginsFolder]/AdminAnything
     *         or from a DB backend.
     */
    ConfigSectionAbstractAdapter getExternalConf() {
        return config.getConf();
    } // end method

    /***
     * Gets the actual instance of {@link com.martinambrus.adminAnything.ConfigAbstractAdapter}.
     *
     * @return Instance of {@link com.martinambrus.adminAnything.ConfigAbstractAdapter}.
     */
    ConfigAbstractAdapter getConf() {
        return config;
    } // end method

    /**
     * Gets the actual instance of {@link com.martinambrus.adminAnything.Language}.
     *
     * @return Instance of {@link com.martinambrus.adminAnything.Language}.
     */
    Language getLang() {
        return lang;
    } // end method

    /***
     * Gets the state of current debug mode state.
     *
     * @return True if debug is enabled, false otherwise.
     */
    boolean getDebug() {
        return config.getDebug();
    } // end method

    /***
     * Gets the actual instance of {@link com.martinambrus.adminAnything.Plugins}.
     *
     * @return Instance of {@link com.martinambrus.adminAnything.Plugins}.
     */
    Plugins getPluginUtils() {
        return pluginUtils;
    } // end method

    /***
     * Gets the actual instance of {@link com.martinambrus.adminAnything.InventoryManager}.
     *
     * @return Instance of {@link com.martinambrus.adminAnything.InventoryManager}.
     */
    InventoryManager getInventoryManager() {
        return inventoryManager;
    } // end method

    /***
     * Gets the actual instance of {@link com.martinambrus.adminAnything.Listeners}.
     *
     * @return Instance of {@link com.martinambrus.adminAnything.Listeners}.
     */
    Listeners getListenerUtils() {
        return listeners;
    } // end method

    /***
     * Gets the actual instance of {@link com.martinambrus.adminAnything.Permissions}.
     *
     * @return Instance of {@link com.martinambrus.adminAnything.Permissions}.
     */
    Permissions getPermissionUtils() {
        return perms;
    } // end method

    /***
     * Gets the actual instance of {@link com.martinambrus.adminAnything.Commands}.
     *
     * @return Instance of {@link com.martinambrus.adminAnything.Commands}.
     */
    Commands getCommandsUtils() {
        return commands;
    } // end method

    /***
     * Gets the actual instance of {@link com.martinambrus.adminAnything.CommandListeners}.
     *
     * @return Instance of {@link com.martinambrus.adminAnything.CommandListeners}.
     */
    CommandListeners getCommandListenersUtils() {
        return commandListeners;
    } // end method

    /***
     * Gets the actual instance of {@link com.martinambrus.adminAnything.TabComplete}.
     *
     * @return Instance of {@link com.martinambrus.adminAnything.TabComplete}.
     */
    TabComplete getTabCompletUtils() {
        return tabComplete;
    } // end method

    /***
     * Gets the actual instance of {@link com.martinambrus.adminAnything.Updater}.
     *
     * @return Instance of {@link com.martinambrus.adminAnything.Updater}.
     */
    Updater getUpdater() {
        return updater;
    } // end method

    /**
     * Reacts to the reload event and clears all available caches.
     */
    void onReload() {
        // simply call disable and enable procedures
        Bukkit.getPluginManager().disablePlugin(this);
        Bukkit.getPluginManager().enablePlugin(this);
    } // end method

    /***
     * React to the custom ReloadEvent which is fired when <b><i>/aa_reload</i></b> gets executed
     * or when we enable AA for the first time.
     *
     * @param e The actual reload event with message that says who is this reload for.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void reload(final AAReloadEvent e) {
        final String msg = e.getMessage();
        if (null != msg && msg.isEmpty()) {
            onReload();
        }
    } // end method

    /**
     * Returns TRUE if the initial warmup of AdminAnything is not yet passed,
     * FALSE otherwise. This is because we need to wait for all plugins before
     * we can adjust their listener priorities and tab-completers.
     *
     * @return Returns TRUE if the initial warmup of AdminAnything is not yet passed, FALSE otherwise.
     */
    boolean isWarmingUp() {
        return this.warmingUp;
    }

} // end class