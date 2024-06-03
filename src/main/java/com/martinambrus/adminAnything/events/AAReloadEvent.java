package com.martinambrus.adminAnything.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event which is fired up when someone requested
 * a reload of AdminAnything via /aa_reload.
 *
 * @author Martin Ambrus
 */
public class AAReloadEvent extends Event {

    /**
     * The actual identifier which will be used
     * to determine who should react to this event.
     *
     * In AdminAnything, this is usually name of the feature
     * that should react and reload its configuration.
     */
    private final String message;

    /**
     * List of all event handlers activated for this event.
     */
    private static final HandlerList handlers = new HandlerList();

    /**
     * Constructor, stores internal variables.
     *
     * @param triggerTarget The actual identifier which will be used
     *                      to determine who should react to this event.
     */
    public AAReloadEvent(final String triggerTarget) {
        this.message = triggerTarget;
    } // end method

    /**
     * Getter for message.
     *
     * @return Returns the actual identifier which will be used
     *         to determine who should react to this event.
     */
    public String getMessage() {
        return this.message;
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

} // end method