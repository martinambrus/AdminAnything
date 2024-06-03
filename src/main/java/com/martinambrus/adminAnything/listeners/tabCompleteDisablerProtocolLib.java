package com.martinambrus.adminAnything.listeners;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.martinambrus.adminAnything.AA_API;
import com.martinambrus.adminAnything.events.AAReloadEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.StringUtil;

import java.util.*;

/**
 * Disables tab completions for commands that players
 * don't have access to.
 *
 * This variant is for MC servers 1.12.2 and below, as 1.12.2 was the last MC version
 * to actually utilize the use of client tab complete packets.
 * Newer versions added a native event which works quite differently.
 *
 * This class uses ProtocolLib.
 *
 * @author Martin Ambrus
 */
final public class tabCompleteDisablerProtocolLib implements Listener {

    /**
     * Instance of {@link com.martinambrus.adminAnything.AdminAnything}.
     */
    private final Plugin plugin;

    /**
     * Holds an instance of ProtocolLib's protocol manager.
     */
    private static ProtocolManager protocolManager;

    /**
     * Holds the actual text that was sent from player to the server to be tab-completed,
     * as we'll need it when adding relevant custom commands from permdescriptions.yml into
     * the tab completion outgoing packet.
     */
    private Map<String, String> playerTabCompleteTextRequest = new HashMap<String, String>();

    /**
     * The actual PacketAdapter instance for incoming tab-complete packets that we created,
     * so we can also unregister it on plugin reload.
     */
    private static PacketAdapter inAdapter;

    /**
     * The actual PacketAdapter instance for outgoing tab-complete packets that we created,
     * so we can also unregister it on plugin reload.
     */
    private static PacketAdapter outAdapter;

    /**
     * Constructor, stores instance of AdminAnything for further use
     * and activates the actual functionality.
     *
     * @param plugin Instance of {@link com.martinambrus.adminAnything.AdminAnything}.
     */
    public tabCompleteDisablerProtocolLib(final Plugin plugin) {
        this.plugin = plugin;
        protocolManager = ProtocolLibrary.getProtocolManager();

        // create an adapter for receiving a player tab-complete request packet
        inAdapter = new PacketAdapter(plugin, ListenerPriority.NORMAL, PacketType.Play.Client.TAB_COMPLETE) {
            @Override
            public void onPacketReceiving(PacketEvent e){
                if (e.getPacketType() == PacketType.Play.Client.TAB_COMPLETE){
                    PacketContainer packet              = e.getPacket();
                    String          tabCompleteChatText = packet.getSpecificModifier(String.class).read(0);
                    tabCompleteDisablerProtocolLib.this.playerTabCompleteTextRequest.put(e.getPlayer().getName(), tabCompleteChatText);
                }
            }
        };

        // create an adapter for adjusting the actual tab-complete packets
        outAdapter = new PacketAdapter(plugin, ListenerPriority.HIGHEST, PacketType.Play.Server.TAB_COMPLETE) {
            @Override
            public void onPacketSending(PacketEvent e){
                if (e.getPacketType() == PacketType.Play.Server.TAB_COMPLETE){
                    // if this player has the permission to bypass tab completion disable, we're good
                    if (AA_API.checkPerms(e.getPlayer(), "aa.fulltabcomplete", false)){
                        return;
                    }

                    String[] completions = e.getPacket().getStringArrays().read(0);

                    // get commands available to this player and remove all completions
                    // that the player shouldn't see
                    List<String> cmdsAvailable = AA_API.getPlayerAvailableCommands( e.getPlayer() );
                    List<String> newCompletions = new ArrayList<String>();
                    List<String> disabledCommands = AA_API.getCommandsList("removals");
                    for (String cmd : completions) {
                        String commandToCheck = cmd.startsWith("/") ? cmd.substring(1) : cmd;
                        if (!disabledCommands.contains(commandToCheck) && cmdsAvailable.contains(commandToCheck)) {
                            newCompletions.add(cmd);
                        }
                    }

                    // add commands that are added via our permdescriptions.yml file only,
                    // as they would not be present in the original completions array and tab-complete
                    // would then be inconsistent with /aa_playercommands
                    List<String> manualCompletions = new ArrayList<String>();
                    for (String cmd : cmdsAvailable) {
                        if (!disabledCommands.contains(cmd) && !newCompletions.contains("/" + cmd)) {
                            manualCompletions.add("/" + cmd);
                        }
                    }

                    // only copy manual completions that actually match the requested tab-complete text
                    StringUtil.copyPartialMatches(
                        tabCompleteDisablerProtocolLib.this.playerTabCompleteTextRequest.get(e.getPlayer().getName()),
                        manualCompletions,
                        newCompletions
                    );

                    e.getPacket().getStringArrays().write(0, newCompletions.toArray( new String[newCompletions.size()] ));
                }
            }
        };

        protocolManager.addPacketListener(inAdapter);
        protocolManager.addPacketListener(outAdapter);
    } // end method

    /***
     * React to the custom ReloadEvent which is fired when <b><i>/aa_reload</i></b> gets executed
     * or when we enable AA for the first time.
     *
     * @param e The actual reload event with message that says who is this reload for.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void reload(final AAReloadEvent e) {
        protocolManager.removePacketListener(inAdapter);
        protocolManager.removePacketListener(outAdapter);
    } // end method

} // end class