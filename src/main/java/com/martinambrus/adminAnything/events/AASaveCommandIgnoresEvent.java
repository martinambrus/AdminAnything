package com.martinambrus.adminAnything.events;

import org.bukkit.command.CommandSender;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event which is fired up when we need to save
 * list of ignored commands.
 *
 * @author Martin Ambrus
 */
public class AASaveCommandIgnoresEvent extends Event {

    /**
     * Command sender who initiated the command leading
     * to saving the configuration.
     */
    private final CommandSender sender;

    /**
     * List of all event handlers activated for this event.
     */
    private static final HandlerList handlers = new HandlerList();

    /**
     * Constructor, stores internal variables.
     *
     * @param sender Command sender who initiated the command leading
     *               to saving the configuration.
     */
    public AASaveCommandIgnoresEvent(final CommandSender sender) {
        this.sender = sender;
    } // end method

    /**
     * Getter for command sender.
     *
     * @return Returns the command sender who initiated the command leading
     *         to saving the configuration.
     */
    public CommandSender getCommandSender() {
        return this.sender;
    } // end

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