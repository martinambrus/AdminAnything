package com.martinambrus.adminAnything;

import com.martinambrus.adminAnything.events.*;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * File configuration class, used to read and write
 * configuration of AdminAnything from/into a database.
 *
 * @author Martin Ambrus
 */
@SuppressWarnings("HardCodedStringLiteral")
final class ConfigMySQLAdapter extends ConfigAbstractAdapter implements Runnable, Listener {

    /**
     * Instance of {@link com.martinambrus.adminAnything.SQLManager}.
     */
    SQLManager sql;

    /**
     * Scheduled task ID, used for periodic DB checks.
     */
    private BukkitTask scheduledTaskID = null;

    /**
     * Stores last update timestamps for all DB-based configs.
     * Used to reload the plugin when a config changes, so AA can run with latest changes.
     */
    private HashMap<String, String> lastUpdates = new HashMap<String, String>();

    /**
     * When set to true, database changes are ignored and not loaded.
     * This is used when we're initially transferring updated config back into the DB,
     * so we don't unnecessarily reload AA on start again.
     */
    public boolean ignoreDBChangedOnce = false;

    /**
     * Constructor, loads plugin configuration.
     *
     * @param aa The singleton instance of AdminAnything.
     */
    ConfigMySQLAdapter(final AdminAnything aa) {
        this.plugin = aa;
        this.sql = new SQLManager( aa );

        // nothing to be done if we don't have a connection
        if (this.sql.connected()) {
            // create the config table
            this.sql.query("CREATE TABLE IF NOT EXISTS " + this.sql.getPrefix() + "config ("
                + "`config_type` varchar(25) NOT NULL COMMENT 'type of configuration, i.e. main, disabled commands etc.',"
                + "`config` mediumtext NOT NULL COMMENT 'the YAML representation of the configuration',"
                + "`last_change_ts` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT 'the last change for this configuration option, updates to current timestamp on any change',"
                + "PRIMARY KEY `config_type` (`config_type`) USING BTREE"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='YAML configs representation for the AdminAnything Spigot Plugin'", new Object[0]
            );

            // load all config options and put them into their respective files
            ResultSet    rs = this.sql.query_res("SELECT * FROM " + this.sql.getPrefix() + "config");
            boolean hasRecords = false;
            List<String> copiedConfigs = new ArrayList<String>();

            try {
                while (rs.next()) {
                    hasRecords = true;
                    String path = this.configs.get(rs.getString("config_type"));

                    if (null != path) {
                        // check if this path was set to be synced with the database
                        if ( this.sql.getDbConfig().getBoolean("sync_" + rs.getString("config_type")) ) {
                            try {
                                path = AA_API.getAaDataDir() + "/" + path;

                                // replace any non-breaking spaces by normal spaces
                                // for example PHPMyAdmin would (thanks to its in-place editing feature) save
                                // every other space as NBSP (because edited HTML cannot display 2 spaces after each other)
                                Files.write(Paths.get(path), rs.getString("config").replaceAll(String.valueOf((char) 160), " ").getBytes());

                                // save last update timestamp
                                this.lastUpdates.put(rs.getString("config_type"), rs.getString("last_change_ts"));

                                // mark it as stored in the DB
                                copiedConfigs.add(rs.getString("config_type"));
                            } catch (Throwable ex) {
                                Bukkit.getLogger().warning(plugin.getConf().getPluginName() + ' ' + AA_API.__( "error.sql-could-not-save-config") );

                                if ( AA_API.getDebug() ) {
                                    ex.printStackTrace();
                                }
                            }
                        } else {
                            Bukkit.getLogger().info("[AdminAnything] not updating sync-disabled configuration file " + path);
                        }
                    } else {
                        Bukkit.getLogger().warning( "[AdminAnything] An invalid configuration identifier was received from the database (" + rs.getString("config_type") + "). If you manually rewrote the database identifier, please make corrections or remove record with this identifier from the database." );
                    }
                }

                // if no DB records were found, copy current configurations into the DB
                if (!hasRecords) {
                    for (final Map.Entry<String, String> pair : this.configs.entrySet()) {
                        writeConfigFileIntoDB(pair.getKey());
                    }
                }

                // if no manual permission descriptions configuration record was found in the DB,
                // copy it there now, as this one does not get saved into the DB anywhere else in AA
                if (!copiedConfigs.contains("permDescriptions") && this.sql.getDbConfig().getBoolean("sync_permDescriptions")) {
                    // copy default permdescriptions file into AA's folder if not found
                    File permDescFile = new File( AA_API.getAaDataDir(), this.configs.get("permDescriptions") );
                    if (!permDescFile.exists()) {
                        plugin.saveResource( this.configs.get("permDescriptions"), true );
                    }

                    // write its contents into the DB
                    writeConfigFileIntoDB("permDescriptions");

                    // save last update timestamp
                    rs = this.sql.query_res("SELECT last_change_ts FROM " + this.sql.getPrefix() + "config WHERE config_type = \"permDescriptions\"");
                    rs.next();
                    this.lastUpdates.put("permDescriptions", rs.getString("last_change_ts"));
                }
            } catch (SQLException ex) {
                Bukkit.getLogger().info(plugin.getConf().getPluginName() + ' ' + AA_API.__("error.sql-write-error") + " " + AA_API.__("error.sql-db-error-using-file-config"));

                if ( AA_API.getDebug() ) {
                    ex.printStackTrace();
                }
            }

            // register class as events listener
            plugin.getServer().getPluginManager().registerEvents(this, plugin);

            // create scheduled task to check for any DB config changes
            this.scheduledTaskID = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this, 20, this.sql.getDbConfig().getInt("sync_check_interval_seconds") * 20);
        }
    } //end method

    /**
     * Updates database configuration with the data from a config file stored locally.
     * This method is used when no records are found in the database as well as during
     * initialization of AdminAnything to make sure the main config is preset with any
     * newly added keys (when the plugin is updated).
     *
     * @param configFileKey Key from the configs HashMap.
     */
    public void writeConfigFileIntoDB(String configFileKey) {
        if (this.sql.connected()) {
            File existingConfigFile = new File(AA_API.getAaDataDir(), this.configs.get(configFileKey));
            if (existingConfigFile.exists() && this.sql.getDbConfig().getBoolean("sync_" + configFileKey)) {
                try {
                    String fileContent = new String(Files
                        .readAllBytes(Paths.get(existingConfigFile.getAbsolutePath())));
                    this.sql.query(
                        "INSERT INTO " + this.sql
                            .getPrefix() + "config SET config_type = ?, config = ? ON DUPLICATE KEY UPDATE config = ?",
                        configFileKey,
                        fileContent,
                        fileContent
                    );
                } catch (Throwable ex) {
                    Bukkit.getLogger()
                          .warning(plugin.getConf().getPluginName() + ' ' + AA_API.__("error.sql-write-error"));

                    if (AA_API.getDebug()) {
                        ex.printStackTrace();
                    }
                }
            }
        }
    } // end method

    /**
     * Checks for updates to any of the DB configs and reloads AA
     * if changes are found (while informing about this in console).
     */
    @Override
    public void run() {
        if (this.sql.connected()) {
            try {
                boolean changesFound = false;
                ResultSet rs = this.sql.query_res("SELECT config_type, last_change_ts FROM " + this.sql.getPrefix() + "config");
                while (rs.next()) {
                    // check current against latest
                    if (
                        null != this.lastUpdates.get(rs.getString("config_type")) &&
                        !this.lastUpdates.get(rs.getString("config_type")).equals(rs.getString("last_change_ts"))
                    ) {
                        if (ignoreDBChangedOnce) {
                            // we chose to ignore this change, so just update the timestamp
                            this.lastUpdates.put(rs.getString("config_type"), rs.getString("last_change_ts"));
                        }
                        changesFound = true;
                    }
                }

                // if we found changes, let's reload AA
                if (changesFound) {
                    if (!ignoreDBChangedOnce) {
                        Bukkit.getLogger().info('[' + AA_API.getAaName() + "] " + AA_API
                            .__("config.sql-changes-detected-reloading"));

                        // fire up the reload event to clear up caches
                        Bukkit.getPluginManager().callEvent(new AAReloadEvent(""));

                    } else {
                        ignoreDBChangedOnce = false;
                    }
                }
            } catch (SQLException ex) {
                Bukkit.getLogger().info('[' + AA_API.getAaName() + "] " + AA_API.__("error.sql-could-not-get-latest-config-data"));

                if ( AA_API.getDebug() ) {
                    ex.printStackTrace();
                }
            }
        }
    } // end method

    /**
     * Sets this plugin's debug state.
     *
     * @param value New value for plugin's debug state - true to turn ON, false to turn OFF.
     */
    void setDebug(boolean value) {
        // not used in the MySQL DB adapter
    } // end method

    /**
     * Gets user plugin configuration for AdminAnything.
     *
     * @return Returns plugin configuration for AdminAnything, loaded either from
     *     the config.yml file located in [pluginsFolder]/AdminAnything
     *     or from a DB backend.
     */
    ConfigSectionAbstractAdapter getConf() {
        // not used in the MySQL DB adapter
        return null;
    } // end method

    /**
     * Loads list of all plugins that are enabled full access to AdminAnything's API,
     * including all data adjusting event calls (i.e. to add/remove records to/from lists
     * of fixed, muted, disabled... commands etc.).
     */
    void loadFullApiAccessPlugins() {
        // not used in the MySQL DB adapter
    } // end method

    /**
     * Loads list of IP ban commands from the config file, since different MC versions
     * tend to use different ban-ip commands and the server could even have its own flavor
     * of ban-ip from a plugin.
     *
     * @return Returns list of all commands AdminAnything should consider as IP-ban ones.
     */
    Collection<String> getBanIpCommandsList() {
        // not used in the MySQL DB adapter
        return null;
    } // end method

    /**
     * Loads join and click event links that will be displayed in chat
     * next to player username's if enabled in config.
     */
    void loadJoinLeaveClickLinks() {
        // not used in the MySQL DB adapter
    } // end method

    /**
     * Loads chat nick click event links that will be displayed in chat
     * next to player username's if enabled in config.
     */
    void loadNickClickLinks() {
        // not used in the MySQL DB adapter
    } // end method

    /**
     * Loads chat nick click event links for an in-game chest GUI
     * if enabled in config.
     */
    void loadNickGUIItems() {
        // not used in the MySQL DB adapter
    } // end method

    /***
     * Closes any DB or file connections that the config class
     * have open upon disabling the plugin.
     */
    void onClose() {
        if (this.sql.connected()) {
            // disable scheduled task
            this.scheduledTaskID.cancel();

            // close the connection
            this.sql.close();
        }
    } // end method

    /**
     * Reloads configuration from the source, overwriting any old values we might have cached in memory.
     * Used when AdminAnything is being disabled to prevent storing old values into the DB.
     */
    void reloadConfig() {
        // not used in the MySQL DB adapter
    } // end method

    /**
     * Retrieves max number of records to be shown per single page when showing
     * paginated results in player chat.
     *
     * @return Returns max number of records to be shown per single page when showing
     *     paginated results in player chat.
     */
    double getChatMaxPerPageRecords() {
        // not used in the MySQL DB adapter
        return 10.0;
    } // end method

    /**
     * Stores changed config file contents into the database.
     *
     * @param configType The configuration type from {@link com.martinambrus.adminAnything.ConfigAbstractAdapter#configs}
     *
     * @return Returns TRUE if the configuration file was saved or warns in console and returns FALSE if not.
     */
    Boolean saveConfigChanges(String configType) {
        if (null != this.configs.get( configType )) {
            File existingConfigFile = new File( AA_API.getAaDataDir(), this.configs.get( configType ) );
            if (existingConfigFile.exists() && this.sql.getDbConfig().getBoolean( "sync_" + configType ) ) {
                try {
                    this.sql.query(
                        "REPLACE INTO " + this.sql.getPrefix() + "config SET config = ?, config_type = ?",
                        new String(Files.readAllBytes(Paths.get(existingConfigFile.getAbsolutePath()))),
                        configType
                    );

                    // update the last update TS from DB, since time on the DB server could differ from MC server's time
                    ResultSet rs = this.sql.query_res("SELECT config_type, last_change_ts FROM " + this.sql.getPrefix() + "config WHERE config_type = ?", configType);
                    while (rs.next()) {
                        this.lastUpdates.put(rs.getString("config_type"), rs.getString("last_change_ts"));
                    }
                } catch (Throwable ex) {
                    Bukkit.getLogger().warning( plugin.getConf().getPluginName() + ' ' + AA_API.__("error.sql-write-error") );

                    if (AA_API.getDebug()) {
                        ex.printStackTrace();
                    }
                }
            }

            return true;
        } else {
            Bukkit.getLogger().info( plugin.getConf().getPluginName() + ' ' + AA_API.__("error.sql-could-not-save-config-into-db", configType) );
            return false;
        }
    } // end of method

    /**
     * React to the custom SaveIgnoredCommandsEvent which is used when we need to save
     * the list of ignored commands to the config file. This event is fired in /aa_ignorecommand.
     *
     * @param e The actual event.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void saveIgnoreCommands(final AASaveCommandIgnoresEvent e) {
       this.saveConfigChanges("ignores");
    } // end method

    /**
     * React to the custom SaveVirtualPermsEvent which is used when we need to save
     * the list of virtual permissions to the config file. This event is fired in /aa_addperm.
     *
     * @param e The actual event.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void saveVirtualPerms(final AASaveVirtualPermsEvent e) {
        this.saveConfigChanges("perms");
    } // end method

    /**
     * React to the custom SaveCommandRedirectsEvent which is used when we need to save
     * the list of redirects to the config file. This event is fired in /aa_addredirect.
     *
     * @param e The actual event.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void saveCommandRedirects(final AASaveCommandRedirectsEvent e) {
        this.saveConfigChanges("redirects");
    } // end method

    /**
     * React to the custom SaveDisabledCommandsEvent which is used when we need to save
     * the list of disabled commands to the config file. This event is fired in /aa_enablecommand.
     *
     * @param e The actual event.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void saveDisabledCommands(final AASaveDisabledCommandsEvent e) {
        this.saveConfigChanges("disables");
    } // end method

    /**
     * React to the custom SaveCommandOverridesEvent which is used when we need to save
     * the list of command overrides (i.e. fixes) to the config file. This event is fired in /aa_fixcommand.
     *
     * @param e The actual event.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void saveCommandOverrides(final AASaveCommandOverridesEvent e) {
        this.saveConfigChanges("overrides");
    } // end method

    /**
     * React to the custom SaveMutedCommandsEvent which is used when we need to save
     * the list of muted commands to the config file. This event is fired in /aa_mutecommand.
     *
     * @param e The actual event.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void saveCommandMutes(final AASaveMutedCommandsEvent e) {
        this.saveConfigChanges("mutes");
    } // end method

    /**
     * React to the custom SaveCommandHelpDisablesEvent which is used when we need to save
     * the list of commands hidden from /aa_playercommands into their config file.
     * This event is fired in /aa_disablehelpcommand.
     *
     * @param e The actual event.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void saveCommandHelpDisables(final AASaveCommandHelpDisablesEvent e) {
        this.saveConfigChanges("helpDisables");
    } // end method

    /**
     * React to the custom AAToggleDebugEvent which is used when debug is turned on/off.
     *
     * @param e The actual event.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void saveConfigOnDebugChange(final AAToggleDebugEvent e) {
        this.saveConfigChanges("main");
    } // end method

} // end class