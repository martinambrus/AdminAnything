package com.martinambrus.adminAnything.listeners;

import com.martinambrus.adminAnything.AA_API;
import mkremins.fanciful.FancyMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.Map.Entry;

/**
 * Listens to player join and leave events
 * and adjusts player's nickname to add links
 * next to their username with actions defined
 * in the config file.
 *
 * @author Martin Ambrus
 */
public class chatJoinLeaveClicks implements Listener {

    /**
     * Name of this feature, used in checking for it being
     * enabled or disabled on the server.
     */
    @SuppressWarnings("HardCodedStringLiteral")
    final String featureName = "chatjoinleaveclicks";

    /**
     * Instance of {@link com.martinambrus.adminAnything.AdminAnything}.
     */
    private final Plugin plugin;

    /**
     * Constructor, stores instance of AdminAnything for further use.
     *
     * @param aa Instance of {@link com.martinambrus.adminAnything.AdminAnything}.
     */
    public chatJoinLeaveClicks(final Plugin aa) {
        this.plugin = aa;

        if (AA_API.isFeatureEnabled(this.featureName)) {
            // reload config values
            AA_API.loadJoinLeaveClickLinks();

            // if we've not registered ourselves, do it now
            if (!AA_API.isListenerRegistered(this.featureName)) {
                AA_API.startRequiredListener(this.featureName, this);
            }
        }
    } // end method

    /***
     * Prepares a list of commands to run when clicked on a link in chat
     * next to player's nickname.
     *
     * @param clickCommandsMap The map of all commands and their names to show in chat.
     * @param msg The actual FancyMessage to update with these clickable commands texts.
     * @param pName Name of the player for who's nick we're adding these commands in chat.
     */
    private void prepareCommandLinks(Map<String, Map<String, String>> clickCommandsMap,
                                     FancyMessage msg, CharSequence pName) {
        boolean firstCommand = true;
        for (final Entry<String, Map<String, String>> pair : clickCommandsMap.entrySet()) {
            //noinspection NonConstantStringShouldBeStringBuffer
            String fancyText;
            try {
                // if someone provides wrong color name, don't fall apart but replace it by aqua
                //noinspection HardCodedStringLiteral
                fancyText = ChatColor.valueOf(pair.getValue().get("color").toUpperCase()) + pair.getKey();
            } catch (final Throwable ex) {
                // wrong text color in config, use aqua
                fancyText = ChatColor.AQUA + pair.getKey() + ChatColor.WHITE;
            }

            if (!firstCommand) {
                // if we've started the message already, prepend it with a comma
                fancyText = ChatColor.WHITE + ", " + fancyText;
            }

            firstCommand = false;
            msg.then(fancyText);

            // add command
            //noinspection HardCodedStringLiteral
            final String cmd = pair.getValue().get("command").replace("%PLAYER%", pName);
            msg.command(cmd).tooltip(AA_API.__("commands.click-to-run", ChatColor.AQUA + cmd));
        }
    } // end method

    /***
     * Adds clickable links after join message to everyone with the right permission.
     *
     * @param e The actual join event to work with.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void addJoinMessage(final PlayerJoinEvent e) {
        final Map<String, Map<String, String>> joinClickCommands = AA_API.getChatJoinActionsMap();

        if (!AA_API.isFeatureEnabled(featureName) || joinClickCommands.isEmpty()) {
            return;
        }

        // this will contain the full FancyMessage with all links from config
        final FancyMessage msg = new FancyMessage(e.getPlayer().getDisplayName() + " (");
        final String pName = e.getPlayer().getName();

        // prepare all command links
        prepareCommandLinks(joinClickCommands, msg, pName);

        msg.then(ChatColor.WHITE + ")");

        for (final Player p : Bukkit.getServer().getOnlinePlayers()) {
            //noinspection HardCodedStringLiteral
            if (AA_API.checkPerms(p, "aa.allowjoinleaveclick", false)) {
                // permission found, send the on-click message
                msg.send(p);
            }
        }
    } // end method

    /***
     * Adds clickable pardon links after leave message to everyone with the right permission.
     *
     * @param e The actual event to work with.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void addLeaveMessage(final PlayerQuitEvent e) {
        final Map<String, Map<String, String>> leaveClickCommands = AA_API.getChatLeaveActionsMap();

        if (!AA_API.isFeatureEnabled(this.featureName) || leaveClickCommands.isEmpty()) {
            return;
        }

        // this will contain the full FancyMessage with all links from config
        final FancyMessage msg = new FancyMessage(e.getPlayer().getDisplayName() + " (");
        final String pName = e.getPlayer().getName();

        // prepare all command links
        prepareCommandLinks(leaveClickCommands, msg, pName);

        msg.then(ChatColor.WHITE + ")");

        for (final Player p : Bukkit.getServer().getOnlinePlayers()) {
            //noinspection HardCodedStringLiteral
            if (AA_API.checkPerms(p, "aa.allowjoinleaveclick", false)) {
                // permission found, send the on-click message
                msg.send(p);
            }
        }
    } // end method

} // end class