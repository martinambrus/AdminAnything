package com.martinambrus.adminAnything;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

/**
 * A semi-compatible ConfigurationSection adapter
 * which contains only those functions that AA code
 * really uses (e.g. getBoolean(), getString(), etc.)
 *
 * @author Martin Ambrus
 */
@SuppressWarnings("HardCodedStringLiteral")
abstract class ConfigSectionAbstractAdapter {

    /***
     * Returns a boolean value from the configuration.
     *
     * @param key Path to the configuration key to return the value from.
     *
     * @return Returns the boolean value of the configuration key.
     */
    abstract boolean getBoolean(String key);

    /***
     * Returns a string value from the configuration.
     *
     * @param key Path to the configuration key to return the value from.
     *
     * @return Returns the string value of the configuration key.
     */
    abstract String getString(String key);

    /***
     * Returns a string value from the configuration.
     *
     * @param key Path to the configuration key to return the value from.
     * @param def Default value if string is not found in the configuration.
     *
     * @return Returns the string value of the configuration key.
     */
    abstract String getString(String key, String def);

    /***
     * Returns a string list from the configuration.
     *
     * @param key Path to the configuration key to return the value from.
     *
     * @return Returns the string list of the configuration key.
     */
    abstract List<String> getStringList(String key);

    /***
     * Returns an iterable key-value configuration section.
     *
     * @param path Path to the configuration key to return the value from.
     *
     * @return Returns the iterable key-value configuration section.
     */
    abstract ConfigurationSection getConfigurationSection(String path);

    /**
     * Sets a new value for the configuration path.
     *
     * @param path The actual path to change the value for.
     * @param value Any object representing the new value for this path. Usually String or boolean.
     */
    abstract void set(String path, Object value);

    /**
     * Determines whether the configuration contains the given path.
     *
     * @param path The path to look for in the configuration.
     *
     * @return Returns true if the path is found in the config, false otherwise.
     */
    abstract boolean contains(String path);

    /**
     * Saves the plugin configuration.
     */
    abstract void saveConfig();

} // end class