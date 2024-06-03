package com.martinambrus.adminAnything.commands;

import com.martinambrus.adminAnything.AA_API;
import com.martinambrus.adminAnything.events.AARemoveDisabledCommandEvent;
import com.martinambrus.adminAnything.events.AASaveDisabledCommandsEvent;
import mkremins.fanciful.FancyMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Removes a command previously disabled via /aa_disablecommand
 * from the list of disabled commands.
 *
 * @author Martin Ambrus
 */
public class Aa_enablecommand extends AbstractCommand {

    /**
     * Shows all existing disabled commands
     * to the command sender.
     *
     * @param sender The actual player/console who's calling this command.
     */
    private void showExistingDisables(final CommandSender sender, final List<String> commandRemovalsList) {
        if (commandRemovalsList.isEmpty()) {
            sender
                .sendMessage(ChatColor.GREEN + AA_API.__("commands.enablec-no-commands-disabled", AA_API.getAaName()));
        } else {
            final FancyMessage toSend = new FancyMessage(AA_API
                .__("commands.enabled-currently-disabled-commands", AA_API.getAaName()) + ": ").color(ChatColor.YELLOW);

            // iterate through all disabled commands and show them to the player
            for (int i = 0; i < (commandRemovalsList.size() - 1); i++) {
                toSend
                    .then(commandRemovalsList.get(i) + ", ")
                    .color(ChatColor.WHITE)
                    .command("/aa_enablecommand " + commandRemovalsList.get(i)) //NON-NLS
                    .tooltip(AA_API.__("commands.enabled-click-to-reenable"));
            }

            // add last record
            toSend
                .then(commandRemovalsList.get(commandRemovalsList.size() - 1))
                .color(ChatColor.WHITE)
                .command("/aa_enablecommand " + commandRemovalsList.get(commandRemovalsList.size() - 1)) //NON-NLS
                .tooltip(AA_API.__("commands.enabled-click-to-reenable"));

            toSend.send(sender);
        }
    } // end method

    /***
     * /aa_enableCommand - removes the disabled command from list of disabled commands, so it can function normally again
     *
     * @param sender The player who is calling this command.
     * @param cmd The actual command that is being executed.
     * @param unused Name of the command which is being executed.
     * @param args Any arguments passed to this command.
     *
     * @return Returns true if the command disable was deleted, false otherwise.
     */
    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String unused, final String[] args) {
        if (!super.onCommand(sender, cmd, unused, args)) {
            return true;
        }

        if (!AA_API.isFeatureEnabled("disablecommand")) { //NON-NLS
            sender.sendMessage(ChatColor.RED + AA_API.__("general.feature-disabled"));
            return true;
        }

        // list all disabled commands if none was supplied
        //noinspection HardCodedStringLiteral
        final List<String> commandRemovalsList = AA_API.getCommandsList("removals");
        if (0 == args.length) {
            showExistingDisables(sender, commandRemovalsList);
            return false;
        }

        // list of commands that were successfully re-enabled
        final Collection<String> done = new ArrayList<String>();

        // list of commands that were not re-enabled, since they were not disabled
        final Collection<String> ko = new ArrayList<String>();

        // fill-in the above lists
        for (final String arg : args) {
            if (commandRemovalsList.contains(arg)) {
                Bukkit.getPluginManager().callEvent( new AARemoveDisabledCommandEvent(arg) );
                done.add(arg);
            } else {
                if (!done.contains(arg) && !ko.contains(arg)) {
                    ko.add(arg);
                }
            }
        }

        // inform about commands we could re-enable
        if (!done.isEmpty()) {
            sender.sendMessage(ChatColor.GREEN + AA_API.__("commands.enabled-done") + ": " + ChatColor.WHITE + String
                    .join(", ", done));
        }

        // inform about commands we could NOT re-enable
        if (!ko.isEmpty()) {
            sender.sendMessage(ChatColor.RED + AA_API
                .__("commands.enabled-not-disabled", AA_API.getAaName()) + ": " + ChatColor.WHITE + String
                .join(", ", ko));
        }

        Bukkit.getPluginManager().callEvent( new AASaveDisabledCommandsEvent(sender) );
        return true;
    } // end method

} // end class