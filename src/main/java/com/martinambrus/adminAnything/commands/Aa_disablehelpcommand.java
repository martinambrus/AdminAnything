package com.martinambrus.adminAnything.commands;

import com.martinambrus.adminAnything.AA_API;
import com.martinambrus.adminAnything.Utils;
import com.martinambrus.adminAnything.events.AAAddHelpDisabledCommandEvent;
import com.martinambrus.adminAnything.events.AASaveCommandHelpDisablesEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

/**
 * Adds a command to the list of commands disabled from showing
 * in the /aa_playercommands listing and stores this in a config file.
 *
 * @author Martin Ambrus
 */
public class Aa_disablehelpcommand extends AbstractCommand {

    /***
     * /aa_disablehelpcommand - disables a command from showing up in /aa_playercommands
     *
     * @param sender The player who is calling this command.
     * @param cmd The actual command that is being executed.
     * @param unused Name of the command which is being executed.
     * @param args Any arguments passed to this command.
     *
     * @return Returns true if the command(s) disables were added
     *         to the config, false otherwise.
     */
    @SuppressWarnings("deprecation")
    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String unused, final String[] args) {
        if (!super.onCommand(sender, cmd, unused, args)) {
            return true;
        }

        //noinspection HardCodedStringLiteral
        if (!AA_API.isFeatureEnabled("disablehelpcommand")) {
            sender.sendMessage(ChatColor.RED + AA_API.__("general.feature-disabled"));
            return true;
        }

        // check for at least 1 parameter - the command to remove from /aa_playercommands listinc
        if (1 > args.length) {
            sender.sendMessage(ChatColor.RED + AA_API.__("commands.disablehc-provide-command"));
            return false;
        }

        // if the command is already hidden, do nothing
        String permGroup = "global";

        // set permission group to the correct one, if it was provided
        String[] compactedArgs = Utils.compactQuotedArgs( args );
        if (1 < compactedArgs.length) {
            // player provided a permission group, let's set it
            permGroup = compactedArgs[compactedArgs.length - 1].toLowerCase();
        }

        //noinspection HardCodedStringLiteral
        if (AA_API.getCommandsList("helpDisables").contains(permGroup + '.' + compactedArgs[0].toLowerCase())) {
            sender.sendMessage(ChatColor.RED + AA_API.__("commands.disablehc-already-hidden") + (permGroup.equals("global") ? "" : " " + AA_API.__("commands.disablehc-already-hidden-for-group", permGroup)) + ".");
            return true;
        }

        // add the command with its group into the config file
        Bukkit.getPluginManager().callEvent( new AAAddHelpDisabledCommandEvent( (permGroup + '.' + compactedArgs[0]).toLowerCase()) );

        // inform the sender about successful operation result
        sender.sendMessage(ChatColor.GREEN + AA_API.__("commands.disablehc-listing-hidden-ok", ChatColor.WHITE + compactedArgs[0] + ChatColor.GREEN) + " " + (permGroup.equals("global") ? ChatColor.WHITE + AA_API.__("commands.disablehc-listing-hidden-ok-globally") : AA_API.__("commands.disablehc-listing-hidden-ok-for-group", ChatColor.WHITE + compactedArgs[1])));

        // save the config
        Bukkit.getPluginManager().callEvent( new AASaveCommandHelpDisablesEvent(sender) );

        return true;
    } // end method

} // end class