package com.martinambrus.adminAnything;

import com.google.common.collect.ImmutableMap;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * File configuration class, used to read and write
 * configuration of AdminAnything from/into a YAML config file.
 *
 * @author Martin Ambrus
 */
@SuppressWarnings("HardCodedStringLiteral")
final class ConfigFileAdapter extends ConfigAbstractAdapter {

    // configuration adapter to provide YAML methods used by AdminAnything
    private ConfigSectionFileAdapter conf;

    // file name of the YAML configuration file for AdminAnything
    private final String configFileName = "config-file.yml";

    // file name of the sample YAML configuration file for AdminAnything
    private final String configFileNameSample = "config-file-sample.yml";

    // because this warning about ProtocolLib can come from multiple places,
    // we'll set this to true after the first one, so we don't duplicate them
    private boolean protocolLibWarningShown = false;

    /**
     * Constructor, loads plugin configuration.
     *
     * @param aa The singleton instance of AdminAnything.
     */
    ConfigFileAdapter(final AdminAnything aa) {
        plugin = aa;

        // copy over sample config file with comments in all instances
        this.plugin.saveResource(this.configFileNameSample, true);

        // check for old configuration
        FileConfiguration initialConfig = YamlConfiguration.loadConfiguration(new File(AA_API.getAaDataDir(), "config.yml"));

        // this holds the reference to the config.yml file, which now only contains a single option,
        // as everything is stored in config-file.yml
        File oldConfigFile = new File(AA_API.getAaDataDir(), "config.yml");

        // old config file found
        if ( oldConfigFile.exists() && null == initialConfig.getString("configBackend") ) {
            // move the old config to a new file
            File newConfigFile = new File( AA_API.getAaDataDir(), this.configFileName );
            oldConfigFile.renameTo( newConfigFile );
            oldConfigFile.delete();

            // add defaults in the renamed config file,
            // as our plugin might have been upgraded and new keys would be missing
            this.updateDefaults();

            // create a new file in place of the old one, pointing to the file-based configuration
            this.plugin.saveResource( "config.yml", true );

            // reload the plugin config
            initialConfig = aa.getConfig();
        } else {
            // check if we're running AA for the first time,
            // in which case copy over the sample file-based config file
            File newConfigFile = new File( AA_API.getAaDataDir(), this.configFileName );
            if ( !newConfigFile.exists() ) {
                // no file-based config file found, copy over the sample one
                Path copied   = Paths.get(AA_API.getAaDataDir() + "/" + this.configFileName);
                Path original = Paths.get(AA_API.getAaDataDir() + "/" + this.configFileNameSample);
                try {
                    Files.copy(original, copied, StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ex) {
                    Bukkit.getConsoleSender().sendMessage( AA_API.__("config.error-cannot-save-config") );
                    ex.printStackTrace();
                    Bukkit.getPluginManager().disablePlugin( plugin );
                    return;
                }

                if ( !newConfigFile.exists() ) {
                    Bukkit.getConsoleSender().sendMessage( AA_API.__("config.error-cannot-save-config", AA_API.getAaName()) );
                    Bukkit.getConsoleSender().sendMessage( "Sample file could not be copied over to the file configuration YAML file." );
                    Bukkit.getPluginManager().disablePlugin( plugin );
                    return;
                }

                // store the config.yml file
                this.plugin.saveResource( "config.yml", true );
            } else {
                // copy current config into a backup file in case it contains errors
                // in which case it would be reset to the default config with default values
                // (and so we can restore and fix it)
                Path copied       = Paths.get( AA_API.getAaDataDir() + "/" + this.configFileName + ".backup");
                Path original     = Paths.get(AA_API.getAaDataDir() + "/" + this.configFileName);
                try {
                    Files.copy(original, copied, StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ex) {
                    AA_API.generateConsoleWarning( AA_API.__("config.error-cannot-save-backup-config", AA_API.getAaName()) );
                }

                // new config file found, only update any missing default values
                this.updateDefaults();
            }
        }

        // load config from file
        this.reloadConfig();

        yml = plugin.getDescription();

        // set debug from config
        debug = conf.getBoolean(CONFIG_VALUES.DEBUGENABLED.toString());

        // load the plugin name, as it's being used everywhere for logging purposes
        pluginName = yml.getName();

        // build a list of disabled features
        if (null != conf.getConfigurationSection("features")) {
            for (final String feature : conf.getConfigurationSection("features").getKeys(false)) {
                if (!conf.getBoolean("features." + feature + ".enabled")) {
                    // found a disabled feature
                    disabledFeatures.add(feature);
                    disabledFeatures.addAll(conf.getStringList("features." + feature + ".linked"));
                }
            }
        }
    } //end method

    /**
     * Shows warning about ProtocolLib not being enabled,
     * while chat actions dependent on it are.
     */
    private void showProtocolLibWarning() {
        if ( !protocolLibWarningShown ) {
            protocolLibWarningShown = true;
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {

                @Override
                public void run() {
                    AA_API.generateConsoleWarning("\n\n"
                        + "**************************************************\n"
                        + "** " + AA_API.__("config.protocollib-not-enabled.1") + "\n\n"
                        + "** " + AA_API.__("config.protocollib-not-enabled.2") + '\n'
                        + "** " + AA_API.__("config.protocollib-not-enabled.3") + '\n'
                        + "** " + AA_API.__("config.protocollib-not-enabled.4") + '\n'
                        + "** " + AA_API
                        .__("config.protocollib-not-enabled.5", AA_API.getAaDataDir() + File.separator) + '\n'
                        + "**************************************************\n");
                }
            }, 0); // 0 = will be run as soon as the server finished loading
        }
    }

    /**
     * Updates default keys in a potentially old config file.
     */
    void updateDefaults() {
        // copy any defaults into the renamed config that might be missing due to plugin upgrades
        File newConfigFile                = new File( AA_API.getAaDataDir(), this.configFileName );
        FileConfiguration newConfig       = YamlConfiguration.loadConfiguration( newConfigFile );
        InputStream       defConfigStream = this.plugin.getResource( this.configFileName );
        if (defConfigStream != null) {
            newConfig.setDefaults( YamlConfiguration.loadConfiguration( new InputStreamReader(defConfigStream, StandardCharsets.UTF_8) ) );
        }
        newConfig.options().copyDefaults(true);

        try {
            newConfig.save(newConfigFile);
        } catch (Throwable ex) {
            Bukkit.getLogger().severe('[' + AA_API.getAaName()
                + "] Could not save configuration file. Please report the following to the plugin's author..."); //NON-NLS
            ex.printStackTrace();
        }
    }

    /**
     * Sets this plugin's debug state.
     *
     * @param value New value for plugin's debug state - true to turn ON, false to turn OFF.
     */
    void setDebug(final boolean value) {
        debug = value;
        conf.set(CONFIG_VALUES.DEBUGENABLED.toString(), debug);
    } //end method

    /**
     * Gets user YAML plugin configuration for AdminAnything.
     *
     * @return Returns user YAML plugin configuration for AdminAnything
     * from the config.yml file located in [pluginsFolder]/AdminAnything.
     */
    ConfigSectionAbstractAdapter getConf() {
        return conf;
    } //end method

    /**
     * Loads list of all plugins that are enabled full access to AdminAnything's API,
     * including all data adjusting event calls (i.e. to add/remove records to/from lists
     * of fixed, muted, disabled... commands etc.).
     */
    void loadFullApiAccessPlugins() {
        if (AA_API.isFeatureEnabled("apifullaccess") && (null == fullApiAccessPlugins || AA_API.getDebug())) {
            // reset cached list
            fullApiAccessPlugins = new ArrayList<String>();

            // load click commands from config and make them all lowercase for easier matching
            for (String pluginName : conf.getStringList("pluginsWithFullWriteAccessViaAPI")) {
                fullApiAccessPlugins.add(pluginName.toLowerCase());
            }
        }
    } // end method

    /**
     * Loads list of IP ban commands from the config file, since different MC versions
     * tend to use different ban-ip commands and the server could even have its own flavor
     * of ban-ip from a plugin.
     *
     * @return Returns list of all commands AdminAnything should consider as IP-ban ones.
     */
    Collection<String> getBanIpCommandsList() {
        if ((null == banIpCommandsList || AA_API.getDebug())) {
            // load commands from config
            banIpCommandsList = conf.getStringList("banIpCommands");
        }

        return Collections.unmodifiableList(banIpCommandsList);
    } // end method

    /**
     * Loads join and click event links that will be displayed in chat
     * next to player username's if enabled in config.
     */
    void loadJoinLeaveClickLinks() {
        if (AA_API.isFeatureEnabled("chatjoinleaveclicks")) {
            // reset cached commands
            joinClickCommands = new LinkedHashMap<String, Map<String, String>>();
            leaveClickCommands = new LinkedHashMap<String, Map<String, String>>();

            // check and update any and all outdated config values
            if ( null != AA_API.getConfigSectionKeys( "chatJoinLeaveActions" ) ) {
                for ( final String key : conf.getConfigurationSection( "chatJoinLeaveActions" ).getKeys( false ) ) {
                    // check if this is join or leave command
                    final String actionType = AA_API.getConfigString( "chatJoinLeaveActions." + key + ".type" );

                    // if there is no type, we need to update the config from an old one
                    if ( null == actionType ) {
                        joinClickCommands.put( key, new HashMap<String, String>() {
                            private static final long serialVersionUID = 4875857308441254761L;

                            {
                                put( "color", AA_API.getConfigString( "chatJoinLeaveActions." + key + ".color" ) );
                                put( "command", AA_API.getConfigString( "chatJoinLeaveActions." + key + ".command" ) );
                            }
                        } );

                        conf.set( "chatJoinLeaveActions." + key + ".type", "join" );
                        conf.saveConfig();
                    }
                }

                // load click commands from config
                for ( final String key : AA_API.getConfigSectionKeys( "chatJoinLeaveActions" ) ) {
                    // check if this is join or leave command
                    final String actionType = AA_API.getConfigString( "chatJoinLeaveActions." + key + ".type" );

                    final Map<String, String> chatAction = new HashMap<String, String>() {
                        private static final long serialVersionUID = 4875857308441254761L;

                        {
                            put( "color", AA_API.getConfigString( "chatJoinLeaveActions." + key + ".color" ) );
                            put( "command", AA_API.getConfigString( "chatJoinLeaveActions." + key + ".command" ) );
                        }
                    };

                    if ( "join".equals( actionType ) ) {
                        joinClickCommands.put( key, chatAction );
                    } else {
                        leaveClickCommands.put( key, chatAction );
                    }
                }
            }

            // make these 2 maps immutable
            joinClickCommands = ImmutableMap.copyOf(joinClickCommands);
            leaveClickCommands = ImmutableMap.copyOf(leaveClickCommands);
        }
    } // end method

    /**
     * Loads chat nick click event links for an in-game chest GUI
     * if enabled in config.
     */
    void loadNickGUIItems() {
        // reset cached items
        guiItems = new LinkedHashMap<String, Map<String, String>>();

        // load items from config
        if ( null != AA_API.getConfigSectionKeys("chatNickGUI") ) {
            for ( final String key : AA_API.getConfigSectionKeys( "chatNickGUI" ) ) {
                guiItems.put( key, new HashMap<String, String>() {
                    private static final long serialVersionUID = 7743453330980166294L;

                    {
                        put( "item", AA_API.getConfigString( "chatNickGUI." + key + ".item" ) );
                        put( "title", AA_API.getConfigString( "chatNickGUI." + key + ".title" ) );
                        put( "command", AA_API.getConfigString( "chatNickGUI." + key + ".command" ) );
                        put( "permission", AA_API.getConfigString( "chatNickGUI." + key + ".permission" ) );
                    }
                } );
            }
        }

        // make items immutable
        guiItems = ImmutableMap.copyOf(guiItems);

        // warn when no ProtocolLib is found
        if ((null == Bukkit.getPluginManager().getPlugin("ProtocolLib"))) {
            showProtocolLibWarning();
        }
    } // end method

    /**
     * Loads chat nick click event links that will be displayed in chat
     * next to player username's if enabled in config.
     */
    void loadNickClickLinks() {
        // reset cached commands
        clickCommands = new LinkedHashMap<String, Map<String, String>>();

        // load click commands from config
        if ( null != AA_API.getConfigSectionKeys("chatNickClickActions") ) {
            for ( final String key : AA_API.getConfigSectionKeys( "chatNickClickActions" ) ) {
                clickCommands.put( key, new HashMap<String, String>() {
                    private static final long serialVersionUID = -6417879481787301768L;

                    {
                        put( "color", AA_API.getConfigString( "chatNickClickActions." + key + ".color" ) );
                        put( "command", AA_API.getConfigString( "chatNickClickActions." + key + ".command" ) );
                        put( "permission", AA_API.getConfigString( "chatNickClickActions." + key + ".permission" ) );
                    }
                } );
            }
        }

        // check if we need to upgrade config and remove the old action item
        final String oldClickAction = conf.getString("chatNickClickAction");
        if (null != oldClickAction) {
            conf.set("chatNickClickAction", null);
            clickCommands.put("A", new HashMap<String, String>() {
                private static final long serialVersionUID = -9072110484116127066L;

                {
                    put("color", "aqua");
                    put("command", oldClickAction);
                }
            });

            // if the old A command is same as current C command, don't add it
            if ((null != clickCommands.get("C")) && !clickCommands.get("C").get("command").equals(oldClickAction)) {
                conf.set("chatNickClickActions.A.color", "aqua");
                conf.set("chatNickClickActions.A.command", oldClickAction);
            }

            conf.saveConfig();
        }

        // make click commands immutable
        clickCommands = ImmutableMap.copyOf(clickCommands);

        // warn when no ProtocolLib is found
        if ((null == Bukkit.getPluginManager().getPlugin("ProtocolLib"))) {
            showProtocolLibWarning();
        }
    } // end method

    /***
     * Closes any DB or file connections that the config class
     * have open upon disabling the plugin.
     */
    void onClose() {
        // nothing needed for file configuration
    } // end mehod

    /**
     * Reloads configuration from the source, overwriting any old values we might have cached in memory.
     * Used when AdminAnything is being disabled to prevent storing old values into the DB.
     */
    void reloadConfig() {
        File configFile = new File(AA_API.getAaDataDir(), configFileName);
        conf = new ConfigSectionFileAdapter( YamlConfiguration.loadConfiguration( configFile ) );
        conf.setConfigFile( configFile );
    } // end method

    /**
     * Retrieves max number of records to be shown per single page when showing
     * paginated results in player chat.
     *
     * @return Returns max number of records to be shown per single page when showing
     *     paginated results in player chat.
     */
    double getChatMaxPerPageRecords() {
        return conf.getDouble("chatMaxPerPageRecords");
    }

} // end class