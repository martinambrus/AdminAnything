package com.martinambrus.adminAnything.commands;

import com.martinambrus.adminAnything.AA_API;
import com.martinambrus.adminAnything.Utils;
import com.martinambrus.adminAnything.events.AARemoveHelpDisabledCommandEvent;
import com.martinambrus.adminAnything.events.AASaveCommandHelpDisablesEvent;
import mkremins.fanciful.FancyMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import java.util.*;

/**
 * Command which removes a command from the list
 * of commands that should not be shown in the /aa_playercommands
 * listing/
 *
 * @author Martin Ambrus
 */
public class Aa_enablehelpcommand extends AbstractCommand {

    /***
     * /aa_enablehelpcommand - removes the given command(s) from the list of commands
     *                         that should not be shown in the /aa_playercommands
     *
     * @param sender The player who is calling this command.
     * @param cmd The actual command that is being executed.
     * @param unused Name of the command which is being executed.
     * @param args Any arguments passed to this command.
     *
     * @return Returns true if we could restore the given set of commands, false othwerise.
     */
    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String unused, final String[] args) {
        if (!super.onCommand(sender, cmd, unused, args)) {
            return true;
        }

        //noinspection HardCodedStringLiteral
        if (!AA_API.isFeatureEnabled("enablehelpcommand")) {
            sender.sendMessage(ChatColor.RED + AA_API.__("general.feature-disabled"));
            return true;
        }

        //noinspection HardCodedStringLiteral
        final List<String> helpDisablesCommandsList = AA_API.getCommandsList("helpDisables");

        // create a map with groups and commands, so we can either show them all
        // or search them, group by group - depending on what player wanted to do
        // we'll store all hidden commands in a map separated by permission group
        Map<String, List<String>> permGroupsAndCommandsMap = new HashMap<String, List<String>>();

        // iterate over all hidden commands and prepare them
        // for the output
        for (int i = 0; i < helpDisablesCommandsList.size(); i++) {
            // split the group and command and store them
            String group = helpDisablesCommandsList.get(i).substring(0, helpDisablesCommandsList.get(i).indexOf('.')).toLowerCase();
            String commandLine = helpDisablesCommandsList.get(i).substring(helpDisablesCommandsList.get(i).indexOf('.') + 1).toLowerCase();

            // create new ArrayList for this command group, if not set yet
            if (null == permGroupsAndCommandsMap.get(group)) {
                permGroupsAndCommandsMap.put(group, new ArrayList<String>());
            }

            // add the command to this group's list
            permGroupsAndCommandsMap.get(group).add(commandLine);
        }

        // sort permission groups alphabetically
        permGroupsAndCommandsMap = new TreeMap<String, List<String>>(permGroupsAndCommandsMap);

        // if we have no parameters, show all commands in the help disables list
        if (0 == args.length) {
            if (helpDisablesCommandsList.isEmpty()) {
                sender.sendMessage(ChatColor.GREEN + AA_API.__("commands.disablehc-nothing-hidden"));
            } else {
                //noinspection HardCodedStringLiteral
                new FancyMessage(AA_API.__("commands.disablehc-listing") + ":")
                    .color(ChatColor.GREEN)
                    .send(sender);

                // output to the command sender
                for (final Map.Entry<String, List<String>> pair : permGroupsAndCommandsMap.entrySet()) {
                    sender.sendMessage("");
                    new FancyMessage(AA_API.__("commands.disablehc-listing-for-group", ChatColor.GREEN + pair.getKey()))
                        .color(ChatColor.WHITE)
                        .send(sender);

                    for (int i = 0; i < pair.getValue().size(); i++) {
                        new FancyMessage("- " + pair.getValue().get(i))
                            .color(ChatColor.GOLD)
                            .command("/aa_enablehelpcommand " + pair.getKey() + "." + pair.getValue().get(i)) //NON-NLS
                            .tooltip(AA_API.__("commands.disablehc-listing-click-to-restore", "\"" + pair.getValue().get(i) + "\""))
                            .send(sender);
                    }
                }
            }

            sender.sendMessage("");

            return (!(sender instanceof ConsoleCommandSender));
        }

        // list of commands that we could remove
        final Collection<String> done = new ArrayList<String>();

        // list of commands that were not present in the config
        final Collection<String> ko = new ArrayList<String>();

        // removed requested commands, one by one
        for (final String arg : Utils.compactQuotedArgs(args)) {
            String argLower = arg.toLowerCase();
            // if the player requested a direct key removal, with the group name in front,
            // try to find it and remove it
            if (arg.contains(".") && helpDisablesCommandsList.contains(argLower)) {
                Bukkit.getPluginManager().callEvent( new AARemoveHelpDisabledCommandEvent(argLower) );
                int dotPos = arg.indexOf('.');
                done.add(arg.substring(dotPos + 1) + " (" + arg.substring(0, dotPos) + ")");
            } else {
                // either no group was defined in this request
                // or the command has dots in it, so we have to check all groups
                // for the given command
                boolean recordFound = false;

                for (final Map.Entry<String, List<String>> pair : permGroupsAndCommandsMap.entrySet()) {
                    if (pair.getValue().contains(argLower)) {
                        Bukkit.getPluginManager().callEvent( new AARemoveHelpDisabledCommandEvent(pair.getKey() + "." + argLower) );
                        done.add(arg + " (" + pair.getKey() + ")");
                        recordFound = true;
                    }
                }

                // if we failed to find the command, add it to the KO list
                if (!recordFound) {
                    if (!done.contains(arg) && !ko.contains(arg)) {
                        ko.add(arg);
                    }
                }
            }
        }

        // show info about commands we removed from the config
        if (!done.isEmpty()) {
            //noinspection HardCodedStringLiteral
            sender.sendMessage(ChatColor.GREEN + AA_API.__("commands.disablehc-listing-done") + ": " + ChatColor.WHITE + String
                 .join(", ", done));
        }

        // show info about commands that were not present in the config
        if (!ko.isEmpty()) {
            //noinspection HardCodedStringLiteral
            sender
                .sendMessage(ChatColor.RED + AA_API.__("commands.disablehc-listing-not-ignored") + ": " + ChatColor.WHITE + String
                     .join(", ", ko));
        }

        // save the config
        Bukkit.getPluginManager().callEvent( new AASaveCommandHelpDisablesEvent(sender) );
        return true;
    } // end method

} // end class