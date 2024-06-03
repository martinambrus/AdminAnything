package com.martinambrus.adminAnything.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event which is fired up when we need to add a new
 * virtual permissions via /aa_addperm.
 *
 * As a security measure, this event will only be accepted
 * by AdminAnything if it originates from the correct AdminAnything
 * command class.
 *
 * @author Martin Ambrus
 */
public class AAAddVirtualPermEvent extends Event {

    /**
     * Name of the new permission.
     */
    private final String permName;

    /**
     * The actual command line for which this permission
     * should be checked.
     */
    private final String commandLine;

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
     * @param permName Name of the new permission.
     * @param commandLine The actual command line for which this permission
     *                    should be checked.
     */
    public AAAddVirtualPermEvent(final String permName, final String commandLine) {
        this.commandLine = commandLine;
        this.permName = permName;

        // get caller name, so we can be sure we're coming from a valid AA command class
        try {
            final com.martinambrus.adminAnything.instrumentation.MySecurityManager mm = new com.martinambrus.adminAnything.instrumentation.MySecurityManager();
            this.callerName = mm.getCallerClassName(2);
        } catch (final java.lang.Exception exc) {}
    } // end method

    /**
     * Getter for permission name.
     *
     * @return Returns name of the new permission.
     */
    public String getPermName() {
        return this.permName;
    } // end method

    /**
     * Getter for the command line.
     *
     * @return Returns the actual command line for which
     *         this permission should be checked.
     */
    public String getCommandLine() {
        return this.commandLine;
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