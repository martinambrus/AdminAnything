package com.martinambrus.adminAnything.commands;

import com.martinambrus.adminAnything.AA_API;
import com.martinambrus.adminAnything.events.AAReloadEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

/**
 * Command which will show current AA version either
 * to the player or console running this command.
 *
 * @author Martin Ambrus
 */
public class Aa_version extends AbstractCommand {

    /***
     * /aa_version - shows current AdminAnything version
     *
     * @param sender The player who is calling this command.
     * @param cmd The actual command that is being executed.
     * @param unused Name of the command which is being executed.
     * @param args Any arguments passed to this command.
     *
     * @return Always returns true.
     */
    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String unused, final String[] args) {
        if (!super.onCommand(sender, cmd, unused, args)) {
            return true;
        }

        if (!AA_API.isFeatureEnabled("version")) {
            sender.sendMessage(ChatColor.RED + AA_API.__("general.feature-disabled"));
            return true;
        }

        //noinspection HardCodedStringLiteral
        sender.sendMessage(ChatColor.AQUA + AA_API.getAaName() + ' ' + AA_API.__("general.version") + ' ' + AA_API
            .getAaVersion());

        // fire up the reload event for the updater to refresh current version and potentially inform us about a new one
        Bukkit.getPluginManager().callEvent(new AAReloadEvent("Updater")); //NON-NLS

        return true;
    } // end method

} // end class