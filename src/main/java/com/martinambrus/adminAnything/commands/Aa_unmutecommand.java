package com.martinambrus.adminAnything.commands;

import com.martinambrus.adminAnything.AA_API;
import com.martinambrus.adminAnything.Utils;
import com.martinambrus.adminAnything.events.AARemoveCommandMuteEvent;
import com.martinambrus.adminAnything.events.AASaveMutedCommandsEvent;
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
 * Command which removes commands from the list
 * of commands that should be muted.
 *
 * @author Martin Ambrus
 */
public class Aa_unmutecommand extends AbstractCommand {

    /***
     * /aa_unmutecommand - removes a command from the list of muted commands
     *
     * @param sender The player who is calling this command.
     * @param cmd The actual command that is being executed.
     * @param unused Name of the command which is being executed.
     * @param args Any arguments passed to this command.
     *
     * @return Returns true if we could remove the requested command from the mutes list,
     *         false otherwise.
     */
    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String unused, String[] args) {
        if (!super.onCommand(sender, cmd, unused, args)) {
            return true;
        }

        if (!AA_API.isFeatureEnabled("mutecommand")) { //NON-NLS
            sender.sendMessage(ChatColor.RED + AA_API.__("general.feature-disabled"));
            return true;
        }

        // if we have no parameters, list all commands that are currently muted
        final List<String> mutes = AA_API.getCommandsList("mutes"); //NON-NLS
        if (0 == args.length) {
            if (mutes.isEmpty()) {
                sender
                    .sendMessage(ChatColor.GREEN + AA_API.__("commands.unmute-no-commands-muted", AA_API.getAaName()));
            } else {
                final FancyMessage toSend = new FancyMessage(
                    AA_API.__(
                        "commands.unmute-list",
                        AA_API.getAaName()
                    ) + ": " //NON-NLS
                )
                    .color(ChatColor.YELLOW);

                // iterate over list of all muted commands and show them in output
                for (int i = 0; i < (mutes.size() - 1); i++) {
                    toSend
                        .then(mutes.get(i) + ", ")
                        .color(ChatColor.WHITE)
                        .command("/aa_unmutecommand " + mutes.get(i)) //NON-NLS
                        .tooltip(AA_API.__("commands.unmute-click-to-unmute"));
                }

                // add last record
                toSend
                    .then(mutes.get(mutes.size() - 1))
                    .color(ChatColor.WHITE)
                    .command("/aa_unmutecommand " + mutes.get(mutes.size() - 1)) //NON-NLS
                    .tooltip(AA_API.__("commands.unmute-click-to-unmute"));

                toSend.send(sender);
            }

            return (!(sender instanceof ConsoleCommandSender));
        }

        // list of all command mutes we have removed
        final Collection<String> done = new ArrayList<String>();

        // list of all command mutes that were not present in the config
        final Collection<String> ko = new ArrayList<String>();

        // adjust arguments, so muted commands with parameters get unmuted correctly
        args = Utils.compactQuotedArgs(args);

        // unmute each of the requested commands
        for (final String arg : args) {
            if (mutes.contains(arg)) {
                Bukkit.getPluginManager().callEvent( new AARemoveCommandMuteEvent(arg) );
                done.add(arg);
            } else {
                if (!done.contains(arg) && !ko.contains(arg)) {
                    ko.add(arg);
                }
            }
        }

        // list all commands that were successfully un-muted
        if (!done.isEmpty()) {
            sender.sendMessage(
                ChatColor.GREEN +
                    AA_API.__("commands.unmute-done") +
                    ": " + //NON-NLS
                    ChatColor.WHITE + String.join(", ", done)
            );
        }

        // list all mutes that were not present in the config
        if (!ko.isEmpty()) {
            sender.sendMessage(
                ChatColor.RED +
                    AA_API.__("commands.unmute-not-muted", AA_API.getAaName()) +
                    ": " + //NON-NLS
                    ChatColor.WHITE + String.join(", ", ko)
            );
        }

        // reload command mutes map
        Bukkit.getPluginManager().callEvent( new AASaveMutedCommandsEvent(sender) );
        return true;
    } // end method

} // end class