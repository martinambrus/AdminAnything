package com.martinambrus.adminAnything.listeners;

import com.martinambrus.adminAnything.AA_API;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Pattern;

/**
 * Listens to command events and kicks a player
 * who'se been IP-banned from the server out,
 * so they cannot do any more damaga - if enabled.
 *
 * @author Martin Ambrus
 */
public class chatKickAfterIpBan implements Listener {

    /**
     * A map of player usernames <> player IPs, used to
     * kick all players with a certain IP after that IP
     * has been banned from the server.
     */
    Map<String, String> playerIPs;

    /**
     * Name of this feature, used when reloading AA.
     */
    @SuppressWarnings("HardCodedStringLiteral")
    private final String featureName = "chatKickAfterIpBan";

    /**
     * Instance of {@link com.martinambrus.adminAnything.AdminAnything}.
     */
    private final Plugin plugin;

    /**
     * Constructor, stores instance of AdminAnything for further use.
     *
     * @param aa Instance of {@link com.martinambrus.adminAnything.AdminAnything}.
     */
    public chatKickAfterIpBan(final Plugin aa) {
        plugin = aa;

        if (AA_API.isFeatureEnabled(featureName)) {
            // if we've not registered ourselves, do it now
            if (!AA_API.isListenerRegistered(featureName)) {
                AA_API.startRequiredListener(featureName, this);
            }
        }
    } // end method

    /**
     * Checks whether a command is one that bans IP from the server
     * and kicks all players who connected from that IP, if this feature
     * is enabled.
     *
     * @param cmd The command that was executed on the server,
     *            including any parameters.
     */
    private void checkAndKick(String cmd) {
        if (!AA_API.isFeatureEnabled(featureName)) {
            return;
        }

        // if we have multiple command parameters, build them into an array
        final String[] args;
        if (cmd.contains(" ")) {
            final String[]     spl       = cmd.split(Pattern.quote(" "));
            final int          splLength = spl.length;

            cmd = spl[0];
            final List<String> params = new ArrayList<String>(Arrays.asList(spl).subList(1, splLength));

            args = params.toArray(new String[params.size()]);
        } else {
            args = new String[0];
        }

        // with only single parameter, kick the player out of game after an ip-ban
        if (AA_API.getBanIpCommandsList().contains(cmd) && (1 == args.length)) {

            // for performance reasons, run this in a new thread as to not bother
            // the main one
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {

                @Override
                public void run() {
                    final ConsoleCommandSender csender = Bukkit.getConsoleSender();

                    // iterate over all IPs kick players who match the newly-banned IP
                    for (final Entry<String, String> pair : playerIPs.entrySet()) {
                        if (pair.getValue().equals(args[0])) {
                            Bukkit.dispatchCommand(csender, "kick " + pair.getKey()); //NON-NLS
                        }
                    }
                }

            }, 5);

        }
    } // end method

    /***
     * If the command is an ip-ban one, check if the player is still in game
     * and kick them out if they are.
     *
     * @param e The actual player command event to work with.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void redirectPlayerCommand(final PlayerCommandPreprocessEvent e) {
        checkAndKick(e.getMessage().substring(1).toLowerCase());
    } // end method

    /***
     * If the command is an ip-ban one, check if the player is still in game
     * and kick them out if they are.
     *
     * @param e The actual console command event to work with.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void redirectConsoleCommand(final ServerCommandEvent e) {
        checkAndKick(e.getCommand().toLowerCase());
    } // end method

    /***
     * Adds IP of a player that joins the game to the IP cache.
     *
     * @param e The actual player join event to work with.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void addPlayerIP(final PlayerJoinEvent e) {
        if (null == playerIPs) {
            playerIPs = new HashMap<String, String>();
        }

        playerIPs.put(e.getPlayer().getName(), e.getPlayer().getAddress().getAddress().getHostAddress());
    } // end method

    /***
     * Removes IP of a leaving player from the IP cache.
     *
     * @param e The actual player quit event to work with.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void addPlayerIP(final PlayerQuitEvent e) {
        try {
            playerIPs.remove(e.getPlayer().getName());
        } catch (NullPointerException ex) {
            // sometimes we get exceptions here if player closes their game
            // instead of disconnecting and we won't have a name here - so we'll
            // just silently ignore this
        }
    } // end method

} // end class