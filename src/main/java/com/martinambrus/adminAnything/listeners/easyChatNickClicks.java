package com.martinambrus.adminAnything.listeners;

import com.martinambrus.adminAnything.AA_API;
import com.martinambrus.easyChat.events.ECChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;

/**
 * EasyChat compatibility listener for the chatNickClicks feature.
 *
 * Listens to player chat events and adjusts player's
 * nicknames to add links next to their username with
 * actions defined in the config file.
 *
 * Also handles chest GUI chat nick link adding into chat.
 *
 * @author Martin Ambrus
 */
public class easyChatNickClicks implements Listener {

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
     * Constructor, stores instance of AdminAnything for further use.
     *
     * @param aa Instance of {@link com.martinambrus.adminAnything.AdminAnything}.
     */
    public easyChatNickClicks( final Plugin aa) {
        plugin = aa;
        haveProtocolLib = (null != Bukkit.getPluginManager().getPlugin("ProtocolLib"));
        boolean nick_clicks_enabled = AA_API.isFeatureEnabled("chatnicklinks");
        boolean chestgui_enabled    = AA_API.isFeatureEnabled("chatnickgui");

        if (nick_clicks_enabled || chestgui_enabled) {
            AA_API.startRequiredListener("easychatnickclicks", this);
        }
    } // end method

    /***
     * Adds our nick click action prefixes and gui chat prefix
     * to EasyChat message sending event.
     *
     * @param e The actual player chat event to work with.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void updateEasyChatMessage(final ECChatEvent e) {
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

        // update this message for each player with the right permission
        // and remove
        for ( final Player p : e.getRecipients() ) {
            // add chest GUI chat link
            if ( !chestCommands.isEmpty() && AA_API.checkPerms(p, "aa.allownickgui", false) ) {
                e.prefix_player_message_with_json( p, "{\"text\":\"" + ChatColor.GRAY + "[" + ChatColor.GREEN + "A" + ChatColor.GRAY + "]" + "\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"" + "/aa_gui " + e.getPlayer().getName() + "\"},\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"" + AA_API.__("commands.click-to-open-chest-gui") + "\"}}" );
            }

            // add nick click actions
            if ( !clickCommands.isEmpty() && AA_API.checkPerms(p, "aa.allowchatnickclick", false) ) {

                boolean nick_click_opening_bracket_done = false;

                // prepare all command links
                for (final Map.Entry<String, Map<String, String>> pair : clickCommands.entrySet()) {
                    // if we have a permission set for this action, check our player for that permission
                    if ( null != pair.getValue().get("permission") && !AA_API.checkPerms(p, pair.getValue().get("permission"), false) ) {
                        // no permission to see this action
                        continue;
                    }

                    if ( !nick_click_opening_bracket_done ) {
                        e.prefix_player_message_with_text( p, ChatColor.GRAY + "[" );
                        nick_click_opening_bracket_done = true;
                    }

                    String action_text;
                    try {
                        // if someone provides wrong color name, don't fall apart but replace it by aqua
                        //noinspection HardCodedStringLiteral
                        action_text = ChatColor.valueOf(pair.getValue().get("color").toUpperCase()) + pair.getKey();
                    } catch (final Throwable ex) {
                        // wrong text color in config, use aqua
                        action_text = ChatColor.AQUA + pair.getKey() + ChatColor.GRAY;
                    }

                    // add this action to the message
                    final String cmd = pair.getValue().get("command").replace("%PLAYER%", e.getPlayer().getName());
                    e.prefix_player_message_with_json( p, "{\"text\":\"" + action_text + "\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"" + cmd + "\"},\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"" + AA_API.__("commands.click-to-run", ChatColor.AQUA + cmd) + "\"}}" );
                }

                if ( nick_click_opening_bracket_done ) {
                    e.prefix_player_message_with_text( p, ChatColor.GRAY + "]" );
                }
            }
        }
    } // end method

} // end class