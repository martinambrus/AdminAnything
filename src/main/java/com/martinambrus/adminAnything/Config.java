package com.martinambrus.adminAnything;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/**
 * Configuration class helper, contains utilities based around plugin configuration.
 *
 * @author Martin Ambrus
 */
@SuppressWarnings("HardCodedStringLiteral")
final class Config {

    /**
     * Instance of {@link com.martinambrus.adminAnything.AdminAnything}.
     */
    private final AdminAnything plugin;

    /**
     * Instance of {@link com.martinambrus.adminAnything.ConfigAbstractAdapter}.
     * Will contain the correct configuration adapter (DB, file-based), based on a setting in the main config file.
     */
    private ConfigAbstractAdapter config = null;

    /**
     * Constructor, checks plugin configuration and updates it as necessary.
     * This will check for upgraded plugin with old configuration as well as
     * fill-in any missing config file values.
     *
     * @param aa The singleton instance of AdminAnything.
     */
    Config(final AdminAnything aa) {
        this.plugin = aa;

        // initialize and load user configuration
        FileConfiguration initialConfig = YamlConfiguration.loadConfiguration(new File(AA_API.getAaDataDir(), "config.yml"));
        String backend = initialConfig.getString("configBackend");

        // save the default DB config file, if it doesn't exist
        if (!new File(AA_API.getAaDataDir(), "config-db.yml").exists()) {
            this.plugin.saveResource("config-db.yml", true);
        }

        // if we have old config still in place, set config adapter automatically to file
        // and let it do the initial upgrade
        if (null == backend) {
            backend = "file";
        }

        switch (backend) {
            case "file": this.config = new ConfigFileAdapter(this.plugin);
                break;

            // if we're using a database, let's first create a DB adapter which updates the actual local file config
            // files and then return the actual config file adapter with the latest configuration
            case "db":
                // check which DB adapter should we use
                FileConfiguration dbConfig = YamlConfiguration.loadConfiguration(new File(AA_API.getAaDataDir(), "config-db.yml"));

                switch (dbConfig.getString("db_type")) {
                    case "mysql":
                        ConfigMySQLAdapter mysql = new ConfigMySQLAdapter(this.plugin);
                        this.config = new ConfigFileAdapter(this.plugin);
                        // once the config file adapter is created, the config file would be updated
                        // with any newly added keys... so we'll make sure to transfer them to the DB
                        mysql.ignoreDBChangedOnce = true;
                        mysql.writeConfigFileIntoDB("main");
                        // this will set ignoreDBChangedOnce back to false, while updating last change dates as well
                        // if the main configuration has changed
                        mysql.run();
                        // ... but we'll still unset the single ignore flag here in case config didn't change,
                        //     in which case the DB check would fail to update local configs the next time
                        //     a DB change occured
                        mysql.ignoreDBChangedOnce = false;
                        break;

                    default: Bukkit.getLogger().severe("[AdminAnything] " + AA_API.__("error.sql-no-suitable-adapter"));
                        this.config = new ConfigFileAdapter(this.plugin);
                        return;
                }

                break;

            default: Bukkit.getLogger().severe("[AdminAnything] " + AA_API.__("error.config-no-suitable-configuration-found"));
                Bukkit.getPluginManager().disablePlugin(this.plugin);
                return;
        }
    } //end method

    /**
     * Returns the actual used configuration adapter, so it can be set in the main AdminAnything class.
     *
     * @return Returns instance of ConfigAbstractAdapter.
     */
    ConfigAbstractAdapter getConfigAdapter() {
        return this.config;
    }

} // end class