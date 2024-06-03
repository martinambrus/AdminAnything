package com.martinambrus.adminAnything.commands;

import com.martinambrus.adminAnything.AA_API;
import com.martinambrus.adminAnything.events.AAToggleDebugEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

/**
 * Toggles the debug mode of AdminAnything.
 *
 * @author Martin Ambrus
 */
public class Aa_debug extends AbstractCommand {

    /***
     * /aa_debug - enables or disables the debug mode of AdminAnything
     *
     * @param sender The player who is calling this command.
     * @param cmd The actual command that is being executed.
     * @param unused Name of the command which is being executed.
     * @param args Any arguments passed to this command.
     *
     * @return Always returns true;
     */
    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String unused, final String[] args) {
        if (!super.onCommand(sender, cmd, unused, args)) {
            return true;
        }

        //noinspection HardCodedStringLiteral
        if (!AA_API.isFeatureEnabled("debug")) {
            sender.sendMessage(ChatColor.RED + AA_API.__("general.feature-disabled"));
            return true;
        }

        //noinspection HardCodedStringLiteral
        Bukkit.getPluginManager().callEvent(new AAToggleDebugEvent());
        sender.sendMessage(
            ChatColor.GREEN +
                AA_API.__(
                    "commands.debug-on-off-message",
                    (AA_API.getDebug() ? AA_API.__("general.enabled") : AA_API.__("general.disabled")) +
                        '.'
                )
        );

        return true;
    } // end method

} // end class