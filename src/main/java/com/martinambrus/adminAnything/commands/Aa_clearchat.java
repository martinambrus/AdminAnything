package com.martinambrus.adminAnything.commands;

import com.martinambrus.adminAnything.AA_API;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

/**
 * Clears chat or console of all the text present.
 *
 * @author Martin Ambrus
 */
public class Aa_clearchat extends AbstractCommand {

    /***
     * /aa_clearchat - clears the player's (console's) chat
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

        if (!AA_API.isFeatureEnabled("clearchat")) { //NON-NLS
            sender.sendMessage(ChatColor.RED + AA_API.__("general.feature-disabled"));
            return true;
        }

        for (int x = 0; 120 > x; x++) {
            sender.sendMessage("");
        }

        return true;
    } // end method

} // end class