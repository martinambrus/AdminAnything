package com.martinambrus.adminAnything;

import com.martinambrus.adminAnything.ConfigAbstractAdapter.CONFIG_VALUES;
import org.bstats.bukkit.Metrics.SimplePie;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.Callable;

/**
 * Plugin statistics that uses Bastian's bStats Metrics library
 * and provides extended information for various online graphs.
 *
 * @author Rojel
 * @author martinambrus
 */
final class CustomMetrics {

    /**
     * Configuration option for disabled metrics options.
     */
    private static final String disabledValue = "Not In Use";

    /**
     * Configuration option for enabled metrics options.
     */
    private static final String enabledValue = "In Use";

    /**
     * The plugin for which these statistics are being collected.
     */
    private final JavaPlugin plugin;

    /**
     * Constructor. Saves references to plugin and its configuration.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * final CustomMetrics customMetrics = new CustomMetrics(yourPluginInstance);
     * customMetrics.initMetrics();
     * </pre>
     *
     * @param plugin - The plugin for which statistics are being collected.
     */
    CustomMetrics(final JavaPlugin plugin) {
        this.plugin = plugin;
    } //end method

    /**
     * Adds a new Graph into BStats metrics using the name and the value provided.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * customMetrics.addBcstatsGraph(bcStatsMetricsInstance, "PluginVersion", 1.21);
     * </pre>
     *
     * @param bmetrics BStats Metrics class instance.
     * @param graphID ID of the graph to add.
     * @param graphValue Value for the graph to send.
     */
    private void addBcstatsGraph(final org.bstats.bukkit.Metrics bmetrics, final String graphID, final String graphValue) {
        bmetrics.addCustomChart(new SimplePie(graphID, new Callable<String>() {
            @Override
            public String call() {
                return graphValue;
            }
        }));
    } //end method

    /**
     * Initialization routine for BStats.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * final CustomMetrics customMetrics = new CustomMetrics(yourPluginInstance, WESVConfigurationInstance);
     * customMetrics.initBStats();
     * </pre>
     */
    private void initBStats() {
        final org.bstats.bukkit.Metrics bmetrics = new org.bstats.bukkit.Metrics(plugin);

        // create graph for Virtual Permisssions
        addBcstatsGraph(bmetrics, "virtual_permissions", //NON-NLS
            AA_API.getCommandsList("virtualperms").isEmpty() ? disabledValue : enabledValue); //NON-NLS

        // create graph for Nickname Links In Player Chat
        addBcstatsGraph(bmetrics, "nickname_links_in_player_chat", //NON-NLS
            AA_API.getConfigBoolean(CONFIG_VALUES.CHATNICKLINKSENABLED.toString())
            ? enabledValue : disabledValue);

        // create graph for Chest GUI
        addBcstatsGraph(bmetrics, "chest_gui", //NON-NLS
            AA_API.getConfigBoolean(CONFIG_VALUES.CHESTGUIENABLED.toString())
            ? enabledValue : disabledValue);

        // create graph for Join and Leave Admin Links In Player Chat
        addBcstatsGraph(bmetrics, "join_and_leave_admin_links_in_pl", //NON-NLS
            AA_API.getConfigBoolean(CONFIG_VALUES.CHATJOINLEAVELINKSENABLED.toString())
            ? enabledValue : disabledValue);

        // create graph for Kick After IP-Ban
        addBcstatsGraph(bmetrics, "kick_after_ip-ban", //NON-NLS
            AA_API.getConfigBoolean(CONFIG_VALUES.CHATKICKAFTERIPBANENABLED.toString())
            ? enabledValue : disabledValue);

        // create graph for Commands Disabling
        addBcstatsGraph(bmetrics, "commands_disabling", //NON-NLS
            AA_API.getCommandsList("removals").isEmpty() ? disabledValue : enabledValue); //NON-NLS

        // create graph for Commands Duplication Fixing
        addBcstatsGraph(bmetrics, "commands_duplication_fixing", //NON-NLS
            AA_API.getCommandsList("overrides").isEmpty() ? disabledValue : enabledValue); //NON-NLS

        // create graph for Commands Duplication Ignore Feature
        addBcstatsGraph(bmetrics, "commands_duplication_ignore_feat", //NON-NLS
            AA_API.getCommandsList("ignores").isEmpty() ? disabledValue : enabledValue); //NON-NLS

        // create graph for Command Muting
        addBcstatsGraph(bmetrics, "command_muting", //NON-NLS
            AA_API.getCommandsList("mutes").isEmpty() ? disabledValue : enabledValue); //NON-NLS
    } //end method

    /**
     * Initialization method for all supported metrics.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * final CustomMetrics customMetrics = new CustomMetrics(yourPluginInstance, AdminAnythingConfigurationInstance);
     * customMetrics.initMetrics();
     * </pre>
     */
    void initMetrics() {
        initBStats();
    } //end method

} // end class