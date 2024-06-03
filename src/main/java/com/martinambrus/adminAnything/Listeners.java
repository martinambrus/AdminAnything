package com.martinambrus.adminAnything;

import com.martinambrus.adminAnything.events.AAAdjustListenerPrioritiesEvent;
import com.martinambrus.adminAnything.events.AAToggleDebugEvent;
import com.martinambrus.adminAnything.listeners.chatJoinLeaveClicks;
import com.martinambrus.adminAnything.listeners.chatKickAfterIpBan;
import com.martinambrus.adminAnything.listeners.chatNickClicks;
import com.martinambrus.adminAnything.listeners.easyChatNickClicks;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;

import java.io.InvalidClassException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.rmi.AccessException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything listeners-related that's not tied to commands
 * and concrete functionality is in this (helper) class.
 *
 * @author Martin Ambrus
 */
final class Listeners implements Listener {

    /**
     * The AdminAnything plugin instance pointer.
     */
    private final Plugin plugin;

    /**
     * Multiple commands can use the same listener (example: /aa_fixcommand, /aa_mutecommand,
     * /aa_ignorecommand and /aa_disablecommand), so this variable will record which
     * listeners are already used and prevent double or triple registering of the same
     * listener functionality.
     */
    private final Map<String, Listener> registeredListeners = new HashMap<String, Listener>();

    /**
     * Constructor, stores an instance of {@link com.martinambrus.adminAnything.AdminAnything}
     * and enables all relevant listeners.
     *
     * @param aa The singleton instance of {@link com.martinambrus.adminAnything.AdminAnything}.
     */
    Listeners(final Plugin aa) {
        plugin = aa;
    } //end method

    /**
     * Initializes and activates all AdminAnything listeners.
     */
    void init() {
        // activate core listening functionality
        startRequiredListener("coreListeners", this); //NON-NLS
        startRequiredListener("AdminAnything", (Listener) this.plugin); //NON-NLS
        // this listener is used for DB-based configs which store changed configs into a DB directly after
        // changing them locally in a file
        startRequiredListener("AdminAnythingConfig", ((AdminAnything) this.plugin).getConf()); //NON-NLS

        // enable chat-nick-onclick actions
        if ( !((AdminAnything) plugin).getConf().isDisabled("chatnicklinks") || !((AdminAnything) plugin).getConf().isDisabled("chatnickgui") ) { //NON-NLS
            new chatNickClicks(plugin);
            try {
                Class.forName( "com.martinambrus.easyChat.events.ECChatEvent" );
                new easyChatNickClicks( plugin );
            } catch (ClassNotFoundException ex) {
                // EasyChat not installed, so the listener class fails due to a non-existant class
                // ... it's all ok, we can ignore this here :)
            }
        }

        // enable chat-join-leave-nick-onclick actions
        if (!((AdminAnything) plugin).getConf().isDisabled("chatjoinleaveclicks")) { //NON-NLS
            new chatJoinLeaveClicks(plugin);
        }

        // enable kick-after-ip-ban listener
        if (!((AdminAnything) plugin).getConf().isDisabled("chatKickAfterIpBan")) { //NON-NLS
            new chatKickAfterIpBan(plugin);
        }

        // enable tab-complete disabler - if we have ProtocolLib installed on server
        /*if (!((AdminAnything) plugin).getConf().isDisabled("tabcompletedisable") && (null != Bukkit.getPluginManager().getPlugin("ProtocolLib"))) { //NON-NLS
            new tabCompleteDisablerProtocolLib(plugin);
        }*/
    } // end method

    /***
     * Registers a listener if that listener was not registered already.
     * Used to lazy-register listeners from commands and methods that expect them to be registered.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * // register an event listener to display join and leave click links next to nicknames in chat
     * this.startRequiredListener("chatJoinLeaveClicks");
     * </pre>
     *
     * @param listenerName ClassName of the listener to register. This class will then be loaded
     *                     from com.martinambrus.adminAnything.listeners.[[ listenerName ]]
     */
    void startRequiredListener(final String listenerName) {
        if (!isListenerRegistered(listenerName)) {
            try {
                final Class<?>       cl               = Class.forName("com.martinambrus.adminAnything.listeners." + listenerName);
                final Constructor<?> cons             = cl.getConstructor(Plugin.class);
                final Listener       listenerInstance = (Listener) cons.newInstance(plugin);

                // only register this listener if it did not provide call to startRequiredListener() in its own constructor,
                // in which case it'd already be registered
                if (!isListenerRegistered(listenerName)) {
                    registeredListeners.put(listenerName, listenerInstance);
                    plugin.getServer().getPluginManager().registerEvents(listenerInstance, plugin);
                }
            } catch (final Throwable e) {
                Bukkit.getLogger()
                      .severe('[' + ((AdminAnything) plugin).getConf().getPluginName()
                          + "] " + AA_API.__("error.listener-not-found", listenerName));
                e.printStackTrace();
                Bukkit.getServer().getPluginManager().disablePlugin(plugin);
            }
        }
    } //end method

    /***
     * Registers a listener if that listener was not registered already.
     * Used to lazy-register listeners from commands and methods that expect them to be registered.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * this.startRequiredListener("yourListenerName", yourListenerClassInstance);
     * </pre>
     *
     * @param listenerName          ClassName of the listener to register. This class will then be loaded
     *                              from com.martinambrus.adminAnything.listeners.[[ listenerName ]]
     * @param listenerClassInstance The actual instance of the listener class we'd like to register.
     *                              This method signature is used by event listener classes themselves
     *                              to self-register as needed (e.g. lazy-self-registering).
     */
    void startRequiredListener(final String listenerName, final Listener listenerClassInstance) {
        if (!isListenerRegistered(listenerName)) {
            registeredListeners.put(listenerName, listenerClassInstance);
            plugin.getServer().getPluginManager().registerEvents(listenerClassInstance, plugin);
        }
    } //end method

    /***
     * Unregisters all event listeners registered by this plugin.
     * Used when disabling AA.
     */
    void unregisterListeners() {
        if (!registeredListeners.isEmpty()) {
            final Iterable<HandlerList> hl             = HandlerList.getHandlerLists();
            for (final HandlerList l : hl) {
                for (final RegisteredListener r : l.getRegisteredListeners()) {
                    for (final Map.Entry<String, Listener> pair : registeredListeners.entrySet()) {
                        if (r.getListener().equals(pair.getValue())) {
                            l.unregister(r);

                            if (((AdminAnything) plugin).getDebug()) {
                                //noinspection UseOfSystemOutOrSystemErr,ObjectToString
                                Utils.logDebug("AA listener unregistered: " + r, (AdminAnything) plugin); //NON-NLS
                            }
                        }
                    }
                }
            }

            registeredListeners.clear();
        }
    } //end method

    /**
     * Checks whether a listener is already registered.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * {@code
     * if (!this.isListenerRegistered("chatJoinLeaveClicks")) {
     *   // register an event listener to display join and leave click links next to nicknames
     *   // in chat
     * }
     * </pre>
     *
     * @param listenerName Name of the listener to check for.
     *
     * @return Returns true if the given listener name is already registered, false otherwise.
     */
    boolean isListenerRegistered(final String listenerName) {
        return registeredListeners.containsKey(listenerName);
    } // end method

    /***
     * Reacts to the custom AAAdjustListenerPrioritiesEvent which is fired whenever we need
     * the {@link com.martinambrus.adminAnything.Commands#adjustListenerPriorities(String[], String, boolean, List, List)} method to fire.
     *
     * @param e The actual event from which we'll take additional details, such as the list of commands
     *          to check and adjust priorities for and the such..
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void adjustListenerPriorities(final AAAdjustListenerPrioritiesEvent e) {
        try {
            ((AdminAnything) plugin).getCommandsUtils()
                                    .adjustListenerPriorities(e.getArgs(), e.getListName(), e.getIgnoreLeadingSlash(), e
                                            .getOkList(), e.getKoList());

            // send any OK messages we may have been given to send out
            if ((null != e.getSender()) && (null != e.getOkMessages()) && null != e.getOkList() && !e.getOkList()
                                                                                                     .isEmpty()) {
                final CommandSender sender = e.getSender();

                for (String message : e.getOkMessages()) {
                    sender.sendMessage(message);
                }
            }
        } catch (AccessException | InvalidClassException | IllegalAccessException | NoSuchFieldException | IllegalArgumentException | SecurityException | NoSuchMethodException | InvocationTargetException e1) {
            // if we received a sender alongside with this event,
            // let them know something went wrong
            if (null != e.getSender()) {
                e.getSender().sendMessage(
                    ChatColor.RED + AA_API.__("error.general-for-chat"));
            }

            Bukkit.getLogger().severe(ChatColor.RED + AA_API.__("error.general-for-chat"));
            e1.printStackTrace();
        }
    } // end method

    /***
     * Reacts to the custom AAToggleDebugEvent which is fired whenever we need
     * to toggle AdminAnything's debugging facilities on or off.
     *
     * @param e The actual debug event.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void toggleDebug(final AAToggleDebugEvent e) {
        ((AdminAnything) plugin).getConf().setDebug(!((AdminAnything) plugin).getConf().getDebug());
        ((AdminAnything) plugin).getConf().getConf().saveConfig();
    } // end method

} // end class