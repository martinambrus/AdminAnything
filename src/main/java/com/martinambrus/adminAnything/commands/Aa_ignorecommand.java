package com.martinambrus.adminAnything.commands;

import com.martinambrus.adminAnything.AA_API;
import com.martinambrus.adminAnything.events.AAAddIgnoredCommandEvent;
import com.martinambrus.adminAnything.events.AASaveCommandIgnoresEvent;
import mkremins.fanciful.FancyMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Adds a command to the list of ignored commands,
 * so it won't be checked when /aa_checkcommandconflicts is run.
 *
 * @author Martin Ambrus
 */
public class Aa_ignorecommand extends AbstractCommand {

    /***
     * /aa_ignorecommand - adds a command to the list of ignored commands wich are not checked when command conflict are being displayed
     *
     * @param sender The player who is calling this command.
     * @param cmd The actual command that is being executed.
     * @param unused Name of the command which is being executed.
     * @param args Any arguments passed to this command.
     *
     * @return Returns true if the command was added to the list of ignores,
     *         false otherwise.
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

        // check if we have at least one parameter present
        if (1 > args.length) {
            sender.sendMessage(ChatColor.RED + AA_API.__("commands.ignore-enter-command"));
            return false;
        }

        // list of commands we were able to successfully add to the ignore list
        final Collection<String> done = new ArrayList<String>();

        // list of commands we were NOT able to successfully add to the ignore list,
        // possibly because they are already in it
        final Collection<String> ko = new ArrayList<String>();

        // pointer to the actual list of ignored commands
        //noinspection HardCodedStringLiteral
        final List<String> commandIgnoresList = AA_API.getCommandsList("ignores");

        // add each given command into the list of ignored commands
        for (final String arg : args) {
            if (!commandIgnoresList.contains(arg.toLowerCase())) {
                Bukkit.getPluginManager().callEvent( new AAAddIgnoredCommandEvent(arg.toLowerCase()) );
                done.add(arg);
            } else {
                ko.add(arg);
            }
        }

        // let the player know about all the newly ignored commands
        if (!done.isEmpty()) {
            sender.sendMessage(ChatColor.GREEN + AA_API.__("commands.ignore-done") + ": " //NON-NLS
                    + ChatColor.WHITE + String.join(", ", done));

            if (sender instanceof Player) {
                new FancyMessage('[' + AA_API.__("general.undo") + ']')
                    .color(ChatColor.AQUA)
                    .tooltip(
                        AA_API.__("commands.ignore-remove-from-list",
                            String.join(", ", done)
                        )
                    )
                    .command("/aa_unignorecommand " + String.join(" ", done)) //NON-NLS
                    .send(sender);
            }
        }

        // let the player know about all the commands that were already ignored
        // and thus did not need to be added into the list
        if (!ko.isEmpty()) {
            sender.sendMessage(ChatColor.RED
                + AA_API.__("commands.ignore-not-ignored") + ": " //NON-NLS
                    + ChatColor.WHITE + String.join(", ", ko));
        }

        Bukkit.getPluginManager().callEvent( new AASaveCommandIgnoresEvent(sender) );
        return true;
    } // end method

} // end class