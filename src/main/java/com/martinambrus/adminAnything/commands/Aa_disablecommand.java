package com.martinambrus.adminAnything.commands;

import com.martinambrus.adminAnything.AA_API;
import com.martinambrus.adminAnything.events.AAAdjustListenerPrioritiesEvent;
import com.martinambrus.adminAnything.events.AAReloadEvent;
import com.martinambrus.adminAnything.events.AASaveCommandRedirectsEvent;
import com.martinambrus.adminAnything.events.AASaveDisabledCommandsEvent;
import mkremins.fanciful.FancyMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds a command to the list of disabled commands
 * and stores this in a config file.
 *
 * @author Martin Ambrus
 */
public class Aa_disablecommand extends AbstractCommand {

    /**
     * Instance of {@link com.martinambrus.adminAnything.AdminAnything}.
     */
    private final Plugin plugin;

    /**
     * Constructor, starts the commandPreprocessor listener
     * if it was not started yet, so our fixed, muted, disabled...
     * commands can get pre-processed by AdminAnything.
     *
     * Also stores the reference to AdminAnything instance, as we'll
     * need it to create a runnable task later.
     */
    public Aa_disablecommand(final Plugin aa) {
        plugin = aa;
        //noinspection HardCodedStringLiteral
        AA_API.startRequiredListener("commandPreprocessor");
    }

    /***
     * /aa_disablecommand - disables a command, so it's ignored on the server
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
        if (!AA_API.isFeatureEnabled("disablecommand")) {
            sender.sendMessage(ChatColor.RED + AA_API.__("general.feature-disabled"));
            return true;
        }

        // check for at least 1 command to disable
        if (1 > args.length) {
            sender.sendMessage(ChatColor.RED + AA_API.__("commands.disablec-provide-command"));
            return false;
        }

        // never allow disabling of AA's own commands
        if (AA_API.isAaCoreCommand(args)) {
            sender.sendMessage(ChatColor.RED + AA_API.__("commands.no-core-manipulation"));
            return false;
        }

        // if the command is already disabled, do nothing
        //noinspection HardCodedStringLiteral
        if (AA_API.getCommandsList("removals").contains(args[0])) {
            sender.sendMessage(ChatColor.RED + AA_API.__("commands.disablec-already-disabled"));
            return true;
        }

        // list of commands that were successfully disabled
        final List<String> done = new ArrayList<String>();

        // list of commands we were not able to disable
        final List<String> ko = new ArrayList<String>();

        // add slash to each of the commands, if we don't have slashed yet
        final String[] newArgs    = new String[args.length];
        final int      argsLength = args.length;

        for (int i = 0; i < argsLength; i++) {
            if (!args[i].startsWith("/") || !(sender instanceof Player)) {
                newArgs[i] = '/' + args[i];
            } else {
                newArgs[i] = args[i];
            }
        }

        // adjust listener priorities to make sure our disabled commands gets pre-processed by AdminAnything
        Bukkit.getPluginManager().callEvent( new AAAdjustListenerPrioritiesEvent(
                sender,
                null,
                newArgs,
                "removals", //NON-NLS
                false,
                done,
                ko
            )
        );

        // update the config file
        Bukkit.getPluginManager().callEvent( new AASaveDisabledCommandsEvent(sender) );

        // call reload event, so commandPreprocessor will pick up the newly-added disabled commands
        Bukkit.getPluginManager().callEvent(new AAReloadEvent("commandPreprocessor")); //NON-NLS

        // inform the player about successful disables
        if (!done.isEmpty()) {
            //noinspection HardCodedStringLiteral
            sender.sendMessage(ChatColor.GREEN + AA_API.__("commands.disable-done") + ": "
                + ChatColor.WHITE + String.join(", ", done));

            if ((sender instanceof Player)) {
                new FancyMessage('[' + AA_API.__("general.undo") + ']').color(ChatColor.AQUA)
                                                                       .tooltip(AA_API
                                                                           .__("commands.disable-reenable") + " '" + String
                                                                               .join(", ", done) + '\'')
                                                                       .command("/aa_enablecommand " + String.join(" ", done))
                                                                       .send(sender);
            }
        }

        // inform the player about unsuccessful disables
        if (!ko.isEmpty()) {
            sender
                .sendMessage(ChatColor.RED + AA_API.__("commands.disable-not-disabled") + ": " + ChatColor.WHITE + String
                    .join(", ", ko));
        }

        // save the config
        Bukkit.getPluginManager().callEvent( new AASaveCommandRedirectsEvent(sender) );

        return true;
    } // end method

} // end class