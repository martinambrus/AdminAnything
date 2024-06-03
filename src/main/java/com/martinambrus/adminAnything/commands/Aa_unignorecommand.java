package com.martinambrus.adminAnything.commands;

import com.martinambrus.adminAnything.AA_API;
import com.martinambrus.adminAnything.events.AARemoveIgnoredCommandEvent;
import com.martinambrus.adminAnything.events.AASaveCommandIgnoresEvent;
import mkremins.fanciful.FancyMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Command which removes a command from the list
 * of ignored commands, i.e. the list used to
 * ignore certain commands when running duplicate
 * checks via /aa_checkcommandconflicts.
 *
 * @author Martin Ambrus
 */
public class Aa_unignorecommand extends AbstractCommand {

    /***
     * /aa_unignorecommand - removes the given command(s) from the list of commands ignored from conflicts check
     *
     * @param sender The player who is calling this command.
     * @param cmd The actual command that is being executed.
     * @param unused Name of the command which is being executed.
     * @param args Any arguments passed to this command.
     *
     * @return Returns true if we could un-ignore the given set of commands, false othwerise.
     */
    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String unused, final String[] args) {
        if (!super.onCommand(sender, cmd, unused, args)) {
            return true;
        }

        //noinspection HardCodedStringLiteral
        if (!AA_API.isFeatureEnabled("ignorecommand")) {
            sender.sendMessage(ChatColor.RED + AA_API.__("general.feature-disabled"));
            return true;
        }

        //noinspection HardCodedStringLiteral
        final List<String> ignoreCommandsList = AA_API.getCommandsList("ignores");

        // if we have no parameters, show all commands in the ignore list
        if (0 == args.length) {
            if (ignoreCommandsList.isEmpty()) {
                sender.sendMessage(ChatColor.GREEN + AA_API.__("commands.unignore-nothing-ignored"));
            } else {
                //noinspection HardCodedStringLiteral
                final FancyMessage toSend = new FancyMessage(AA_API.__("commands.unignore-listing") + ": ")
                    .color(ChatColor.YELLOW);

                // iterate over all ignored commands and prepare them
                // for the output
                for (int i = 0; i < (ignoreCommandsList.size() - 1); i++) {
                    toSend
                        .then(ignoreCommandsList.get(i) + ", ")
                        .color(ChatColor.WHITE)
                        .command("/aa_unignorecommand " + ignoreCommandsList.get(i)) //NON-NLS
                        .tooltip(AA_API.__("commands.unignore-click-to-remove"));
                }

                // add last record
                toSend
                    .then(ignoreCommandsList.get(ignoreCommandsList.size() - 1))
                    .color(ChatColor.WHITE)
                    .command("/aa_unignorecommand " + ignoreCommandsList.get(ignoreCommandsList.size() - 1)) //NON-NLS
                    .tooltip(AA_API.__("commands.unignore-click-to-remove"));

                toSend.send(sender);
            }

            return (!(sender instanceof ConsoleCommandSender));
        }

        // list of ignores that we could remove
        final Collection<String> done = new ArrayList<String>();

        // list of ignores that were not present in the config
        final Collection<String> ko = new ArrayList<String>();

        // un-ignore requested ignores, one by one
        for (final String arg : args) {
            if (ignoreCommandsList.contains(arg)) {
                Bukkit.getPluginManager().callEvent( new AARemoveIgnoredCommandEvent(arg) );
                done.add(arg);
            } else {
                if (!done.contains(arg) && !ko.contains(arg)) {
                    ko.add(arg);
                }
            }
        }

        // show info about ignores we've removed
        if (!done.isEmpty()) {
            //noinspection HardCodedStringLiteral
            sender.sendMessage(ChatColor.GREEN + AA_API.__("commands.unignore-done") + ": " + ChatColor.WHITE + String
                .join(", ", done));
        }

        // show info about ignores that were not present in the config
        if (!ko.isEmpty()) {
            //noinspection HardCodedStringLiteral
            sender
                .sendMessage(ChatColor.RED + AA_API.__("commands.unignore-not-ignored") + ": " + ChatColor.WHITE + String
                    .join(", ", ko));
        }

        // save ignores
        Bukkit.getPluginManager().callEvent( new AASaveCommandIgnoresEvent(sender) );
        return true;
    } // end method

} // end class