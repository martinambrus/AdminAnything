package com.martinambrus.adminAnything;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.util.List;

/**
 * A semi-compatible ConfigurationSection adapter
 * which contains only those functions that AA code
 * really uses (e.g. getBoolean(), getString(), etc.)
 *
 * This adapter is for a file-based YAML plugin configuration
 * stored in the config.yml file in Plugins/AdminAnything.
 *
 * @author Martin Ambrus
 */
@SuppressWarnings("HardCodedStringLiteral")
class ConfigSectionFileAdapter extends ConfigSectionAbstractAdapter {

    /**
     * YAML file configuration representation managed in memory.
     */
    private FileConfiguration yml;

    /**
     * Configuration file into which the YAML representation is saved.
     */
    private File configFile;

    /**
     * Constructor.
     * Takes a YAML file configuration class of a Plugin
     * and stores it locally, so we can use its methods.
     *
     * @param ymlConfig The actual FileConfiguration of a Plugin to store.
     */
    ConfigSectionFileAdapter(FileConfiguration ymlConfig) {
        this.yml = ymlConfig;
    } // end method

    /**
     * Sets configuration file into which we'll be saving the YAML representation
     * of AdminAnything's configuration.
     *
     * @param configFile Configuration file where the actual configuration is stored on the disk.
     */
    void setConfigFile( File configFile ) {
        this.configFile = configFile;
    }

    /***
     * Returns a boolean value from the configuration.
     *
     * @param key Path to the configuration key to return the value from.
     *
     * @return Returns the boolean value of the configuration key.
     */
    boolean getBoolean(String key) {
        return this.yml.getBoolean(key);
    }  // end method

    /***
     * Returns a string value from the configuration.
     *
     * @param key Path to the configuration key to return the value from.
     *
     * @return Returns the string value of the configuration key.
     */
    String getString(String key) {
        return this.yml.getString(key);
    } // end method

    /***
     * Returns a string value from the configuration.
     *
     * @param key Path to the configuration key to return the value from.
     * @param def Default value if string is not found in the configuration.
     *
     * @return Returns the string value of the configuration key.
     */
    String getString(String key, String def) {
        return this.yml.getString(key, def);
    } // end method

    /***
     * Returns a string list from the configuration.
     *
     * @param key Path to the configuration key to return the value from.
     *
     * @return Returns the string list of the configuration key.
     */
    List<String> getStringList(String key) {
        return this.yml.getStringList(key);
    } // end method

    /***
     * Returns an integer value from the configuration.
     *
     * @param key Path to the configuration key to return the value from.
     *
     * @return Returns the integer value of the configuration key.
     */
    int getInt(String key) {
        return this.yml.getInt(key);
    } // end method

    /***
     * Returns a double value from the configuration.
     *
     * @param key Path to the configuration key to return the value from.
     *
     * @return Returns the double value of the configuration key.
     */
    double getDouble(String key) {
        return this.yml.getDouble(key);
    } // end method

    /***
     * Returns an iterable key-value configuration section.
     *
     * @param path Path to the configuration key to return the value from.
     *
     * @return Returns the iterable key-value configuration section.
     */
    ConfigurationSection getConfigurationSection(String path) {
        return this.yml.getConfigurationSection(path);
    } // end method

    /**
     * Sets a new value for the configuration path.
     *
     * @param path The actual path to change the value for.
     * @param value Any object representing the new value for this path. Usually String or boolean.
     */
    void set(String path, Object value) {
        this.yml.set(path, value);
    } // end method

    /**
     * Determines whether the configuration contains the given path.
     *
     * @param path The path to look for in the configuration.
     *
     * @return Returns true if the path is found in the config, false otherwise.
     */
    boolean contains(String path) {
        return this.yml.contains(path);
    } // end method

    /**
     * Saves the file-based configuration.
     */
    void saveConfig() {
        try {
            this.yml.save(configFile);
        } catch (Throwable ex) {
            Bukkit.getLogger().severe('[' + AA_API.getAaName()
                + "] " + AA_API.__("config.error-cannot-save-config", AA_API.getAaName())); //NON-NLS
            ex.printStackTrace();
        }
    } // end method

} // end class