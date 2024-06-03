package com.martinambrus.adminAnything.listeners;

import com.martinambrus.adminAnything.AA_API;
import mkremins.fanciful.FancyMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;

import java.util.*;

/**
 * Listens to player chat events and adjusts player's
 * nicknames to add links next to their username with
 * actions defined in the config file.
 *
 * Also handles chest GUI chat nick link adding into chat.
 *
 * @author Martin Ambrus
 */
public class chatNickClicks implements Listener {

    /**
     * Instance of {@link com.martinambrus.adminAnything.AdminAnything}.
     */
    private final Plugin plugin;

    /**
     * Set to true if ProtocolLib is enabled on the server, false otherwise.
     * It's a bit faster than invoking the Bukkit plugin check every time.
     */
    private boolean haveProtocolLib = false;

    /**
     * Stores UUID of the last player to send a chat message.
     * Used when ProtocolLib is enabled to substitute the send chat packet
     * with the correct username when the %PLAYER% placeholder is used
     * in config.
     */
    static UUID lastPlayer;

    /**
     * Constructor, stores instance of AdminAnything for further use.
     *
     * @param aa Instance of {@link com.martinambrus.adminAnything.AdminAnything}.
     */
    public chatNickClicks(final Plugin aa) {
        plugin = aa;
        haveProtocolLib = (null != Bukkit.getPluginManager().getPlugin("ProtocolLib"));
        boolean nick_clicks_enabled = AA_API.isFeatureEnabled("chatnicklinks");
        boolean chestgui_enabled    = AA_API.isFeatureEnabled("chatnickgui");

        if (nick_clicks_enabled || chestgui_enabled) {

            if ( nick_clicks_enabled ) {
                AA_API.loadNickClickLinks();
            }

            if ( chestgui_enabled ) {
                AA_API.loadNickGUIItems();
            }

            // note: we still need 1 of our 2 listeners below for ProtocolLib
            // to handle player UUIDs
            AA_API.startRequiredListener("chatnicklinks", this);

            // with the help of ProtocolLib, we can stop rewriting chat that was already updated
            // by other chat plugins
            //noinspection HardCodedStringLiteral
            if (haveProtocolLib) {
                if ( nick_clicks_enabled ) {
                    Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new chatNickClicksRunnable(plugin), 20);
                }

                if ( chestgui_enabled ) {
                    Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new chatNickGUIRunnable(plugin), 20);
                }
            }
        }
    } // end method

    /**
     * Returns the UUID of the last player who used chat,
     * so we can update their chat packet with the help of ProtocolLib.
     *
     * @return Returns the UUID of last player to receive a chat packet.
     */
    protected static UUID getLastPlayer() {
        return lastPlayer;
    } // end method

    /***
     * Replaces nicknames by clickable links that perform the desired action.
     *
     * @param e The actual player chat event to work with.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void replaceChatNick(final AsyncPlayerChatEvent e) {
        if ( e.isCancelled() ) {
            return;
        }

        Map<String, Map<String, String>> clickCommands;
        Map<String, Map<String, String>> chestCommands;

        if ( AA_API.isFeatureEnabled("chatnicklinks") ) {
            clickCommands = AA_API.getNickClickActionsMap();
        } else {
            clickCommands = new HashMap<>();
        }

        if ( AA_API.isFeatureEnabled("chatnickgui") ) {
            chestCommands = AA_API.getGUIItemsMap();
        } else {
            chestCommands = new HashMap<>();
        }

        // nothing's clickable in chat
        // ... when no click commands are defined
        // ... also, bail out if we have ProtocolLib active, as there's a different routine prepared for that
        if (
            (
                !AA_API.isFeatureEnabled("chatnicklinks") &&
                !AA_API.isFeatureEnabled("chatnickgui")
            )
            ||
            haveProtocolLib
            ||
            (
                AA_API.isFeatureEnabled("chatnicklinks") &&
                clickCommands.isEmpty()
            )
            ||
            (
                AA_API.isFeatureEnabled("chatnickgui") &&
                chestCommands.isEmpty()
            )
        ) {
            return;
        }

        // holds a list of recipients which we're sending an updated message to
        // in order to remove them from the list of original recipients
        Set<Player> recipients_to_remove = new HashSet<>();

        // update this message for each player with the right permission
        // and remove
        for (final Player p : e.getRecipients()) {
            // this will contain the full FancyMessage with all links from config
            FancyMessage msg = null;

            // add chest GUI chat link
            if ( !chestCommands.isEmpty() && AA_API.checkPerms(p, "aa.allownickgui", false) ) {
                msg = new FancyMessage(ChatColor.GRAY + "[" + ChatColor.GREEN + "A" + ChatColor.GRAY + "]");

                // add command
                //noinspection HardCodedStringLiteral
                final String cmd = "/aa_gui " + e.getPlayer().getName();
                msg.command(cmd).tooltip(AA_API.__("commands.click-to-open-chest-gui"));
            }

            // add nick click actions
            if ( !clickCommands.isEmpty() && AA_API.checkPerms(p, "aa.allowchatnickclick", false) ) {

                boolean nick_click_bracket_done = false;

                // prepare all command links
                for (final Map.Entry<String, Map<String, String>> pair : clickCommands.entrySet()) {
                    // if we have a permission set for this action, check our player for that permission
                    if ( null != pair.getValue().get("permission") && !AA_API.checkPerms(p, pair.getValue().get("permission"), false) ) {
                        // no permission to see this action
                        continue;
                    }

                    String fancyText;
                    try {
                        // if someone provides wrong color name, don't fall apart but replace it by aqua
                        //noinspection HardCodedStringLiteral
                        fancyText = ChatColor.valueOf(pair.getValue().get("color").toUpperCase())
                            + pair.getKey();
                    } catch (final Throwable ex) {
                        // wrong text color in config, use aqua
                        fancyText = ChatColor.AQUA + pair.getKey() + ChatColor.GRAY;
                    }

                    if (null == msg) {
                        // if we've not started the message yet, create it
                        msg = new FancyMessage(ChatColor.GRAY + "[" + fancyText);
                    } else {
                        // if we have the chest GUI link present already, just add the opening bracket
                        if ( !chestCommands.isEmpty() && !nick_click_bracket_done ) {
                            nick_click_bracket_done = true;
                            msg.then( ChatColor.GRAY + "[" + fancyText );
                        } else {
                            // if we started the message already, add to it
                            msg.then(fancyText);
                        }
                    }

                    // add command
                    //noinspection HardCodedStringLiteral
                    final String cmd = pair.getValue().get("command").replace("%PLAYER%", e.getPlayer().getName());
                    msg.command(cmd).tooltip(AA_API.__("commands.click-to-run", ChatColor.AQUA + cmd));
                }
            }

            // nothing was added, so we have either no nick click links and no GUI link
            // or no permissions - send the original message
            if (null != msg) {
                // add player to list of recipients to remove from this event
                recipients_to_remove.add( p );

                // closing bracket, if we need one
                if ( !clickCommands.isEmpty() ) {
                    msg.then(ChatColor.GRAY + "]");
                }

                // get message format and update it with our prefixes
                msg.then(
                    e.getFormat()
                     .replace("%1$s", p.getDisplayName() )
                     .replace("%2$s", e.getMessage() )
                );

                // send the updated message to this player
                msg.send(p);
            }
        }

        // remove recipients to who we've already sent the updated chat message
        for ( Player removal : recipients_to_remove ) {
            try {
                e.getRecipients().remove(removal);
            } catch (Exception ex) {
                // this may or may not work according to the API docs
            }
        }

        // send to console, which is default for MC
        Bukkit.getConsoleSender().sendMessage('<' + e.getPlayer().getDisplayName() + "> " + e.getMessage());

    } // end method

    /***
     * Replaces nicknames by clickable links that perform the desired action.
     *
     * @param e The actual player chat event to work with.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void catchEarlyChatForProtocolLib(final AsyncPlayerChatEvent e) {
        if (
            (
                AA_API.isFeatureEnabled("chatnicklinks") ||
                AA_API.isFeatureEnabled("chatnickgui")
            )
            &&
            haveProtocolLib
        ) {
            // store last player's UUID
            lastPlayer = e.getPlayer().getUniqueId();

            // set up a delayed task to reset last player's UUID after a timeout, when not needed anymore
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {

                @Override
                public void run() {
                    // reset last player back to null, since in 10 ticks, all players should have the message
                    chatNickClicks.this.lastPlayer = null;
                }

            }, 10);
        }
    } // end method

} // end class