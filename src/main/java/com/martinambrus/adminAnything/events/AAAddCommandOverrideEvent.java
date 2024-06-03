package com.martinambrus.adminAnything.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event which is fired up when we need to add a new
 * override for a conflicting command via /aa_fixcommand.
 *
 * As a security measure, this event will only be accepted
 * by AdminAnything if it originates from the correct AdminAnything
 * command class.
 *
 * @author Martin Ambrus
 */
public class AAAddCommandOverrideEvent extends Event {

    /**
     * Name of the command we want to direct to a specific plugin.
     */
    private final String commandName;

    /**
     * The actual command we want to run when this conflicting command
     * originating from multiple plugin is called.
     */
    private final String commandOverride;

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
     * @param commandName Name of the command we want to direct to a specific plugin.
     * @param commandOverride The actual command we want to run when this conflicting command
     *                        originating from multiple plugin is called.
     */
    public AAAddCommandOverrideEvent(final String commandName, final String commandOverride) {
        this.commandName = commandName;
        this.commandOverride = commandOverride;

        // get caller name, so we can be sure we're coming from a valid AA command class
        try {
            final com.martinambrus.adminAnything.instrumentation.MySecurityManager mm = new com.martinambrus.adminAnything.instrumentation.MySecurityManager();
            this.callerName = mm.getCallerClassName(2);
        } catch (final java.lang.Exception exc) {}
    } // end method

    /**
     * Getter for command name.
     *
     * @return Returns name of the command we want to direct to a specific plugin.
     */
    public String getCommandName() {
        return this.commandName;
    } // end method

    /**
     * Getter for command override.
     *
     * @return Returns the actual command we want to run when this conflicting command
     *         originating from multiple plugin is called.
     */
    public String getCommandOverride() {
        return this.commandOverride;
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