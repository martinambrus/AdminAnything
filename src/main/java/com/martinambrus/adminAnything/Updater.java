package com.martinambrus.adminAnything;

import com.martinambrus.adminAnything.events.AAReloadEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Spigot plugin version checker. Compares latest version on Spigot
 * with the current one and warns in console and chat as needed.
 *
 * @author Martin Ambrus
 */
final class Updater implements Runnable, Listener {

    /**
     * Set to FALSE after first time AA shows information about
     * getting update information. This is to prevent console spam
     * when update interval is too short.
     */
    private boolean firstRun = true;

    /**
     * Determines whether a newer version is available, in which case
     * we'll notify people with the appropriate permissions in-game.
     */
    private String newVersionAvailable;

    /**
     * URL of this resource on Spigot.
     */
    @SuppressWarnings("HardCodedStringLiteral")
    private final String spigotURL = "https://www.spigotmc.org/resources/adminanything.19436";

    /**
     * API URL for this resource on Spigot to check for new versions.
     */
    private final String spigotAPICheckURL = "https://api.spigotmc.org/legacy/update.php?resource=19436";

    /**
     * {@link com.martinambrus.adminAnything.AdminAnything AdminAnything} instance.
     */
    private final AdminAnything plugin;

    /**
     * Scheduled task ID, used for periodic update checks.
     */
    private BukkitTask scheduledTaskID = null;

    /**
     * True if we registered this class to listen for player joins,
     * false otherwise.
     */
    private boolean eventListenerRegistered = false;

    /**
     * Constructor.
     * Starts listening to player join events to possibly
     * inform them about a new version availability.
     *
     * @param aa {@link com.martinambrus.adminAnything.AdminAnything AdminAnything} instance.
     */
    Updater(final AdminAnything aa) {
        plugin = aa;

        // do all the things that are usually done when aa_reload is performed
        onReload();
    } // end method

    /**
     * Sends an in-game player chat information about new version of the plugin.
     *
     * @param player The player we want to inform about new AA version.
     */
    private void tellNewVersionInChat(final CommandSender player) {
        player.sendMessage(ChatColor.AQUA +
            AA_API.__(
                "chat.updater-new-version-available",
                ChatColor.YELLOW + newVersionAvailable + ChatColor.AQUA,
                ChatColor.YELLOW + AA_API.getAaName() + ChatColor.AQUA,
                ChatColor.WHITE + spigotURL)
        );
    } // end method

    /**
     * Asks the Spigot HTTP API for newest version of this plugin
     * and notifies console if any are found.
     */
    @Override
    public void run() {
        if (firstRun) {
            Bukkit.getLogger().info('[' + AA_API.getAaName() + "] " + AA_API.__("updater.checking-for-updates"));
        }

        try {
            final HttpURLConnection con = (HttpURLConnection) new URL(this.spigotAPICheckURL).openConnection();

            con.setDoOutput(true);
            con.setRequestMethod("GET"); //NON-NLS

            final String version = new BufferedReader(new InputStreamReader(con.getInputStream())).readLine();
            // only version number would be the output of this, so it'll always be less than 10 characters
            if (10 >= version.length()) {
                final String aaVersion = plugin.getDescription().getVersion();
                final VersionComparator cmp = new VersionComparator();

                if (cmp.compare(version, aaVersion) == 1) {
                    Bukkit.getLogger().warning(
                        '[' + plugin.getName() + "] " +
                            AA_API.__("updater.new-version-available", version, spigotURL, aaVersion)
                    );
                    newVersionAvailable = version;
                } else {
                    if (firstRun) {
                        Bukkit.getLogger().info('[' + plugin.getName() + "] " + AA_API.__("updater.no-new-version"));
                    }
                }
            } else {
                Bukkit.getLogger().warning('[' + plugin.getName()
                    + "] " + AA_API.__("updater.update-check-failed"));
            }
        } catch (final Exception ex) {
            Bukkit.getLogger().warning('[' + plugin.getName()
                + "] " + AA_API.__("updater.update-check-failed"));
        }

        firstRun = false;
    } // end method

    /**
     * Registers event listener for the join event and starts
     * a scheduled task that will check for this plugin's updated
     * version online every hour.
     *
     * @param forceShowVersionInfo If set, an one-time update check will be triggered.
     */
    void onReload(final boolean... forceShowVersionInfo) {
        if (!eventListenerRegistered) {
            // start listening to player joins
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            eventListenerRegistered = true;
        }

        if (!plugin.getConf().isDisabled("autoupdate")) { //NON-NLS
            if (null == scheduledTaskID) {
                scheduledTaskID = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this, 20,
                    (AA_API.getConfigString("updateInterval") == null ? 3600 * 2 : Integer.parseInt(AA_API.getConfigString("updateInterval"))) * 20);
            } else if (0 < forceShowVersionInfo.length) {
                Bukkit.getScheduler().runTaskAsynchronously( this.plugin, new Runnable() {
                    @Override
                    public void run() {
                        Updater.this.run();
                    }
                });
            }
        } else {
            if (null != scheduledTaskID) {
                scheduledTaskID.cancel();
                scheduledTaskID = null;
            }
        }
    } // end method

    /***
     * Disables update checking. Used when disabling the plugin.
     */
    void unregister() {
        if (null != scheduledTaskID) {
            scheduledTaskID.cancel();
            scheduledTaskID = null;
        }
    } // end method

    /**
     * Returns the newVersionAvailable value, be it null
     * or a real new version string.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * {@code
     * String newVersion = this.getAvailableNewVersion();
     * if (newVersion != null) {
     *     Bukkit.getLogger().log("info", "New AdminAnything version (" + newVersion + ") was released. Download your copy today!");
     * }
     * </pre>
     *
     * @return Returns the value of newVersionAvailable. It will be null if no new version
     *         is available, a real version number string otherwise.
     */
    String getAvailableNewVersion() {
        return newVersionAvailable;
    } // end method

    /***
     * Notifies everyone with the right permission about new version availability.
     *
     * @param e A player join event holding the player information, from which name is used
     *          to determine whether to notify the player about a new version or not.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void notifyNewVersion(final PlayerJoinEvent e) {
        if ((null != scheduledTaskID) && (null != newVersionAvailable)
            && AA_API.checkPerms(e.getPlayer(), "aa.notifynewversion", false)) { //NON-NLS
            tellNewVersionInChat(e.getPlayer());
        }
    } // end method

    /***
     * React to the a custom "reload" event for which we'll reload our version information.
     *
     * @param e The actual new AAReloadEvent event.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void reload(final AAReloadEvent e) {
        run();
    } // end method

} // end class