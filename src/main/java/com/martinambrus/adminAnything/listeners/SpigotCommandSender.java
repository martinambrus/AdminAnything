package com.martinambrus.adminAnything.listeners;

import org.bukkit.command.CommandSender;

/**
 * A simple extension to the CommandSender which is used
 * in VirtualCommandSender class.
 *
 * @author Martin Ambrus
 */
@SuppressWarnings({"SameReturnValue", "InterfaceWithOnlyOneDirectInheritor"})
interface SpigotCommandSender extends CommandSender {
    Spigot spigot();
} // end class