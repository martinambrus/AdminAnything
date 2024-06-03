package com.martinambrus.adminAnything.events;

import org.bukkit.command.CommandSender;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.List;

/**
 * Event which is fired up when we need to re-assign
 * listener priorities for some of server's plugins
 * and make them recognize event cancellation for the
 * PlayerCommandPreprocessEvent and ServerCommandEvent events.
 *
 * @author Martin Ambrus
 */
public class AAAdjustListenerPrioritiesEvent extends Event {

    /**
     * The command sender that's running a command which
     * in turns requires listener priorities to be adjusted.
     */
    private final CommandSender sender;

    /**
     * List of messages to show to the player when adjusting
     * priorities succeeds.
     */
    private final List<String> okMessages;

    /**
     * Any arguments passed to the original command that in turn
     * called this event.
     */
    private final String[] args;

    /**
     * Name of the list of commands which we'll update upon
     * successful adjustment of priorities. For AdminAnything,
     * this would be for example a list of overridden, muted,
     * redirected... commands.
     */
    private final String listName;

    /**
     * A pointer to the list of messages into which we save
     * names of commands and plugins that we've successfully
     * processed.
     */
    private final List<String> okList;

    /**
     * A pointer to the list of messages into which we save
     * names of commands and plugins that we've failed to process
     * for any reason.
     */
    private final List<String> koList;

    /**
     * Variable to determine whether we should ignore a leading
     * slash (/) in all of the given commands when updating
     * listener priorities.
     */
    private final boolean ignoreLeadingSlash;

    /**
     * List of all event handlers activated for this event.
     */
    private static final HandlerList handlers = new HandlerList();

    /**
     * Constructor, stores internal variables.
     *
     * @param sender The command sender that's running a command which
     *               in turns requires listener priorities to be adjusted.
     * @param okMessages List of messages to show to the player when adjusting
     *                   priorities succeeds.
     * @param args Any arguments passed to the original command that in turn
     *             called this event.
     * @param listName Name of the list of commands which we'll update upon
     *                successful adjustment of priorities. For AdminAnything,
     *                this would be for example a list of overridden, muted,
     *                redirected... commands.
     * @param ignoreLeadingSlash Variable to determine whether we should ignore a leading
     *                           slash (/) in all of the given commands when updating
     *                           listener priorities.
     * @param okList A pointer to the list of messages into which we save
     *               names of commands and plugins that we've successfully
     *               processed.
     * @param koList A pointer to the list of messages into which we save
     *               names of commands and plugins that we've failed to process
     *               for any reason.
     */
    public AAAdjustListenerPrioritiesEvent(final CommandSender sender, final List<String> okMessages,
            final String[] args, final String listName, final boolean ignoreLeadingSlash,
            final List<String> okList, final List<String> koList) {
        this.okMessages = okMessages;
        this.sender = sender;
        this.args = args;
        this.listName = listName;
        this.ignoreLeadingSlash = ignoreLeadingSlash;
        this.okList = okList;
        this.koList = koList;
    } // end method

    /**
     * Getter for OK messages.
     *
     * @return Returns a list of messages to show to the player when adjusting
     *         priorities succeeds.
     */
    public Iterable<String> getOkMessages() {
        return okMessages;
    } // end method

    /**
     * Getter for command sender.
     *
     * @return Returns the command sender that's running a command which
     *         in turns requires listener priorities to be adjusted.
     */
    public CommandSender getSender() {
        return sender;
    } // end method

    /**
     * Getter for command arguments.
     *
     * @return Returns Any arguments passed to the original command that in turn
     *                 called this event.
     */
    public String[] getArgs() {
        return args;
    } // end method

    /**
     * Getter for the list of overrides / mutes / redirects...
     *
     * @return Returns name of thelist of commands which we'll update upon
     *         successful adjustment of priorities. For AdminAnything,
     *         this would be for example a list of overridden, muted,
     *         redirected... commands.
     */
    public String getListName() {
        return listName;
    } // end method

    /**
     * Getter for the OK list.
     *
     * @return Returns a pointer to the list of messages into which we save
     *         names of commands and plugins that we've successfully
     *         processed.
     */
    public List<String> getOkList() {
        return okList;
    } // end method

    /**
     * Getter for the KO list.
     *
     * @return Returns a pointer to the list of messages into which we save
     *         names of commands and plugins that we've failed to process
     *         for any reason.
     */
    public List<String> getKoList() {
        return koList;
    } // end method

    /**
     * Getter for leading slash ignoring flag.
     *
     * @return Returns a variable which determines whether we should
     *         ignore a leading slash (/) in all of the given commands
     *         when updating listener priorities.
     */
    public boolean getIgnoreLeadingSlash() {
        return ignoreLeadingSlash;
    } // end method

    /**
     * Getter for list of all handlers for this event.
     */
    @Override
    public HandlerList getHandlers() {
        return handlers;
    } // end method

    /**
     * Getter for list of all handlers for this event.
     *
     * @return Returns list of handlers for this event.
     */
    public static HandlerList getHandlerList() {
        return handlers;
    } // end method

} // end class