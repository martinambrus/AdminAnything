package com.martinambrus.adminAnything.listeners;

import com.comphenix.protocol.PacketType.Play.Server;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.martinambrus.adminAnything.AA_API;
import com.martinambrus.adminAnything.Reflections;
import com.martinambrus.adminAnything.events.AAReloadEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Logic to replace player chat packets
 * with their updated versions if we have
 * chat nick GUI enabled in the config.
 *
 * This class uses ProtocolLib.
 *
 * @author Martin Ambrus
 */
final class chatNickGUIRunnable implements Runnable {

    /**
     * Instance of {@link com.martinambrus.adminAnything.AdminAnything}.
     */
    private final Plugin plugin;

    /**
     * Since reflections are being used to fetch
     * all the GSON-related objects from their correct
     * locations, this object will hold the main GSON instance.
     */
    Object gson;

    /**
     * Since reflections are being used to fetch
     * all the GSON-related objects from their correct
     * locations, this method will hold the fromJson method
     * reference from the GSON object.
     */
    Method fromJson;

    /**
     * Since reflections are being used to fetch
     * all the GSON-related objects from their correct
     * locations, this method will hold the toJson method
     * reference from the GSON object.
     */
    Method toJson;

    /**
     * Since reflections are being used to fetch
     * all the GSON-related objects from their correct
     * locations, this map will hold the root map
     * reference from the GSON object.
     */
    Map<String, Object> javaRootMapObject;

    /**
     * Holds an instance of ProtocolLib's protocol manager.
     */
    private static ProtocolManager protocolManager;

    /**
     * The actual PacketAdapter instance that we created, so we can also unregister it
     * on plugin reload.
     */
    private static PacketAdapter adapter;

    /**
     * Constructor, stores instance of AdminAnything for further use.
     *
     * @param plugin Instance of {@link com.martinambrus.adminAnything.AdminAnything}.
     */
    chatNickGUIRunnable(final Plugin plugin) {
        this.plugin = plugin;
    } // end method

    /**
     * Gets the ProtocolManager instance of the ProtocolLib library plugin.
     *
     * @return Returns the ProtocolManager instance of the ProtocolLib library plugin.
     */
    static ProtocolManager getProtocolManager() {
        return protocolManager;
    } // end method

    /***
     * The logic which replaces player chat packets by our updated ones.
     */
    @SuppressWarnings({"OverlyComplexAnonymousInnerClass", "HardCodedStringLiteral"})
    @Override
    public void run() {
        // find the GSON object
        gson = Reflections.getSimpleClass(new String[]{
                // mc 1.8+
                "com.google.gson.Gson",
                // mc 1.7
                "net.minecraft.util.com.google.gson.Gson"
        }, null);

        // load fromJson from the GSON dynamically-loaded class
        try {
            fromJson = gson.getClass().getMethod("fromJson",
                    String.class, Class.class);
        } catch (SecurityException | NoSuchMethodException e) {
            e.printStackTrace();
            Bukkit.getLogger().severe(
                ChatColor.RED + AA_API.__("error.general-for-chat"));

            return;
        }

        // load toJson from the GSON dynamically-loaded class
        try {
            toJson = gson.getClass().getMethod("toJson",
                    Object.class);
        } catch (SecurityException | NoSuchMethodException e) {
            e.printStackTrace();
            Bukkit.getLogger().severe(ChatColor.RED + AA_API.__("error.general-for-chat"));
            return;
        }

        // get instance of ProtocolManager
        protocolManager = ProtocolLibrary.getProtocolManager();

        // add packet listener that will update chat packets with our data
        adapter = new PacketAdapter(plugin, ListenerPriority.HIGHEST, Server.CHAT) {
            @SuppressWarnings({"unchecked", "HardCodedStringLiteral"})
            @Override
            public void onPacketSending(final PacketEvent event) {
                // check that the player is still online
                if (!event.getPlayer().isOnline()) {
                    return;
                }

                if ((null != chatNickClicks.getLastPlayer()) && AA_API
                    .checkPerms(event.getPlayer(), "aa.allownickgui", false)) {

                    // only react to the correct chat event
                    if (event.getPacketType() == Server.CHAT) {
                        final WrappedChatComponent chat = event.getPacket().getChatComponents().read(0);

                        // load the JSON object from GSON
                        try {
                            javaRootMapObject = (Map<String, Object>) fromJson
                                .invoke(gson, chat.getJson(), Map.class);
                        } catch (IllegalArgumentException | IllegalAccessException
                            | InvocationTargetException e) {
                            e.printStackTrace();
                            Bukkit.getLogger().severe(ChatColor.RED
                                + AA_API.__("error.general-for-chat"));

                            return;
                        } catch (NullPointerException npe) {
                            // NPE could happen if other plugins interfere with AA
                            // or if the player we're trying to send this packet to has already left the game
                            // and we cannot get their username
                            return;
                        }

                        // add our [A] link to the existing chat object
                        final Map<String, String> openingBracket = new LinkedHashMap<String, String>();
                        final Map<String, Object> action = new LinkedHashMap<String, Object>();
                        final Map<String, String> clickEvent = new LinkedHashMap<String, String>();
                        final Map<String, Object> hoverEvent = new LinkedHashMap<String, Object>();
                        final Map<String, String> hoverText = new LinkedHashMap<String, String>();

                        openingBracket.put("text", "[");
                        openingBracket.put("color", "gray");
                        try {
                            ((List<Map<String, String>>) javaRootMapObject
                                .get("extra")).add(0, openingBracket);
                        } catch (final NullPointerException ex) {
                            // if the extra part of the message does not exist, we won't be able to adjust it
                            // and it most probably originates from a plugin, not from a player
                            // ... notably the React plugin works this way and generates NullPointers here
                            return;
                        }

                        // it's possible that we lost name of the last player who sent a chat message
                        // if the server lagged and so we would generate a NPE below... so, let's handle that here
                        if ( null == chatNickClicks.getLastPlayer() ) {
                            return;
                        }

                        final String cmd = "/aa_gui " + Bukkit.getPlayer(chatNickClicks.getLastPlayer()).getName();
                        action.put("text", "A");
                        action.put("color", "green");

                        clickEvent.put("action", AA_API.__("commands.run-command"));
                        clickEvent.put("value", cmd);

                        action.put("clickEvent", clickEvent);

                        hoverText.put("text", AA_API.__("commands.click-to-open-chest-gui"));
                        hoverEvent.put("action", "show_text");
                        hoverEvent.put("value", hoverText);

                        action.put("hoverEvent", hoverEvent);
                        ((List<Map<String, Object>>) javaRootMapObject
                            .get("extra")).add(1, action);

                        final Map<String, String> closingBracket = new LinkedHashMap<String, String>();
                        closingBracket.put("text", "]");
                        closingBracket.put("color", "gray");
                        ((List<Map<String, String>>) javaRootMapObject
                            .get("extra")).add(2, closingBracket);

                        // store the updated chat message as JSON again
                        String updatedJSON;
                        try {
                            updatedJSON = (String) toJson.invoke(
                                gson,
                                javaRootMapObject);
                        } catch (IllegalArgumentException | IllegalAccessException
                            | InvocationTargetException e) {
                            e.printStackTrace();
                            Bukkit.getLogger().severe(ChatColor.RED
                                + AA_API.__("error.general-for-chat"));

                            return;
                        }

                        chat.setJson(updatedJSON);

                        // replace the packet by our version
                        event.getPacket().getChatComponents().write(0, chat);
                    }
                }
            } // end method

            @Override
            public void onPacketReceiving(final PacketEvent event) {
            } // end method
        };

        protocolManager.addPacketListener(adapter);

    } // end method

    /***
     * React to the custom ReloadEvent which is fired when <b><i>/aa_reload</i></b> gets executed
     * or when we enable AA for the first time.
     *
     * @param e The actual reload event with message that says who is this reload for.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void reload(final AAReloadEvent e) {
        protocolManager.removePacketListener(adapter);
    } // end method

} // end class