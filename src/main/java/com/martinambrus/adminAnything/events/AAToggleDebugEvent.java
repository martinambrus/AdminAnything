package com.martinambrus.adminAnything.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event which is fired up when someone
 * requests to toggle the debug mode of AdminAnything
 * on or off.
 *
 * @author Martin Ambrus
 */
public class AAToggleDebugEvent extends Event {

    /**
     * List of all event handlers activated for this event.
     */
    private static final HandlerList handlers = new HandlerList();

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