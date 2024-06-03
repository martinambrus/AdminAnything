package com.martinambrus.adminAnything.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event which is fired up when we need to remove
 * a command from the list of ignored commands
 * via /aa_unignorecommand.
 *
 * As a security measure, this event will only be accepted
 * by AdminAnything if it originates from the correct AdminAnything
 * command class.
 *
 * @author Martin Ambrus
 */
public class AARemoveIgnoredCommandEvent extends Event {

    /**
     * Name of the command to un-ignore.
     */
    private final String commandName;

    /**
     * Security variable.
     * Holds the name of the calling class, so the event receiver
     * can validate that this event came from a valid AA command class.
     */
    private String callerName = null;

    /**
     * List of all event handlers activated for this event.
     */
    private static final HandlerList handlers = new HandlerList();

    /**
     * Constructor, stores internal variables and calculates the caller
     * class name for security validation.
     *
     * @param commandName Name of the command to un-ignore.
     */
    public AARemoveIgnoredCommandEvent(final String commandName) {
        this.commandName = commandName;

        // get caller name, so we can be sure we're coming from a valid AA command class
        try {
            final com.martinambrus.adminAnything.instrumentation.MySecurityManager mm = new com.martinambrus.adminAnything.instrumentation.MySecurityManager();
            this.callerName = mm.getCallerClassName(2);
        } catch (final java.lang.Exception exc) {}
    } // end method

    /**
     * Getter for command name.
     *
     * @return Returns the name of the command to un-ignore.
     */
    public String getCommandName() {
        return this.commandName;
    } // end method

    /**
     * Getter for the securely-calculated caller's class name.
     *
     * @return Returns the name of the calling class.
     */
    public String getCallerName() {
        return this.callerName;
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