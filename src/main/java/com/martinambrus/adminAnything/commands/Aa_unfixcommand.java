package com.martinambrus.adminAnything.commands;

import com.martinambrus.adminAnything.AA_API;
import com.martinambrus.adminAnything.events.AAReloadEvent;
import com.martinambrus.adminAnything.events.AARemoveCommandOverrideEvent;
import com.martinambrus.adminAnything.events.AASaveCommandOverridesEvent;
import mkremins.fanciful.FancyMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

/**
 * Command which removes override previously added via /aa_fixcommand.
 *
 * @author Martin Ambrus
 */
public class Aa_unfixcommand extends AbstractCommand {

    /***
     * /aa_unfixcommand - removes override previously added via /aa_fixcommand.
     *
     * @param sender The player who is calling this command.
     * @param cmd The actual command that is being executed.
     * @param unused Name of the command which is being executed.
     * @param args Any arguments passed to this command.
     *
     * @return Returns true if the override was removed, false otherwise.
     */
    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String unused, final String[] args) {
        if (!super.onCommand(sender, cmd, unused, args)) {
            return true;
        }

        //noinspection HardCodedStringLiteral
        if (!AA_API.isFeatureEnabled("fixcommand")) {
            sender.sendMessage(ChatColor.RED + AA_API.__("general.feature-disabled"));
            return true;
        }

        // if we don't have any parameters, show all commands fixed via AA
        //noinspection HardCodedStringLiteral
        final Set<String> configKeys = AA_API.getCommandsConfigurationKeys("overrides", false);
        if (0 == args.length) {
            if (configKeys.isEmpty()) {
                sender
                    .sendMessage(ChatColor.GREEN + AA_API.__("commands.unfixc-no-commands-fixed", AA_API.getAaName()));
            } else {
                final FancyMessage toSend           = new FancyMessage(AA_API
                    .__("commands.unfixc-commands-currently-fixed", AA_API.getAaName()) + ": ").color(ChatColor.YELLOW);
                final String[]     keyz             = configKeys.toArray(new String[configKeys.size() - 1]);
                final int          keyzLengthMinus1 = keyz.length - 1;

                // iterate over all fixed commands and show clickable actions
                // to unfix them (if supported, plain text otherwise)
                for (int i = 0; i < keyzLengthMinus1; i++) {
                    toSend
                        .then(keyz[i] + ", ")
                        .color(ChatColor.WHITE)
                        .command("/aa_unfixcommand " + keyz[i]) //NON-NLS
                        .tooltip(AA_API.__("commands.unfixc-click-to-restore"));
                }

                // add last record
                toSend
                    .then(keyz[keyz.length - 1])
                    .color(ChatColor.WHITE)
                    .command("/aa_unfixcommand " + keyz[keyz.length - 1]) //NON-NLS
                    .tooltip(AA_API.__("commands.unfixc-click-to-restore"));

                toSend.send(sender);
            }

            return (!(sender instanceof ConsoleCommandSender));
        }

        // list of commands we were able to remove override for
        final Collection<String> done = new ArrayList<String>();

        // list of commands we were NOT able to remove override for
        final Collection<String> ko = new ArrayList<String>();

        // remove requested command overrides one by one
        for (final String arg : args) {

            //noinspection HardCodedStringLiteral
            if (AA_API.getCommandsList("overrides").contains(arg)) {
                Bukkit.getPluginManager().callEvent( new AARemoveCommandOverrideEvent(arg) );
                done.add(arg);
            } else {
                if (!done.contains(arg) && !ko.contains(arg)) {
                    ko.add(arg);
                }
            }
        }

        // show info about the overrides we could remove
        if (!done.isEmpty()) {
            sender.sendMessage(ChatColor.GREEN + AA_API
                .__("commands.unfixc-done", AA_API.getAaName()) + ": " + ChatColor.WHITE + String.join(", ", done));
            Bukkit.getPluginManager().callEvent(new AAReloadEvent("checkcommandconflicts")); //NON-NLS
        }

        // show info about the overrides that were not present
        if (!ko.isEmpty()) {
            sender.sendMessage(
                ChatColor.RED +
                    AA_API.__(
                        "commands.unfixc-notfixed-commands",
                        AA_API.getAaName()
                    ) +
                    ": " + //NON-NLS
                    ChatColor.WHITE +
                    String.join(", ", ko));
        }

        // save config
        Bukkit.getPluginManager().callEvent( new AASaveCommandOverridesEvent(sender) );
        return true;
    } // end method

} // end class