package com.martinambrus.adminAnything.commands;

import com.martinambrus.adminAnything.AA_API;
import com.martinambrus.adminAnything.events.AAAddCommandOverrideEvent;
import com.martinambrus.adminAnything.events.AAAdjustListenerPrioritiesEvent;
import com.martinambrus.adminAnything.events.AASaveCommandOverridesEvent;
import mkremins.fanciful.FancyMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.InvalidClassException;
import java.lang.reflect.InvocationTargetException;
import java.rmi.AccessException;
import java.util.*;

/**
 * The actual logic behind fixing (overriding) a command
 * to make sure it gets called from the plugin the admin wants.
 *
 * @author Martin Ambrus
 */
public class Aa_fixcommand_runnable implements Runnable {

    /**
     * The player who is calling this command.
     */
    CommandSender sender;

    /**
     * Any arguments passed to this command.
     */
    String[] args;

    /**
     * Constructor, stores the command sender
     * and command arguments, so they can be re-used.
     *
     * @param sender The player who is calling this command.
     * @param args Any arguments passed to this command.
     */
    Aa_fixcommand_runnable(final CommandSender sender, final String[] args) {
        this.sender = sender;
        this.args = args;
    }

    /**
     * The actual logic used to fix (override) a command.
     */
    @Override
    public void run() {
        List<String> containingPlugins;
        try {
            containingPlugins = AA_API.getCommandContainingPlugins(args[0].toLowerCase(), true);
        } catch (AccessException | IllegalAccessException | InvalidClassException | NoSuchMethodException
                | SecurityException | InvocationTargetException e1) {
            sender.sendMessage(
                ChatColor.RED + AA_API.__("error.general-for-chat"));
            e1.printStackTrace();
            return;
        }

        // if there is only 1 parameter, display all plugins that contain the requested command
        if (1 == args.length) {
            if ((null != containingPlugins) && !containingPlugins.isEmpty()) {
                sender.sendMessage(
                    AA_API.__(
                        "commands.fixcommand-command-found-in",
                        ChatColor.YELLOW + "/" + args[0] + ChatColor.RESET
                    ) + ": " //NON-NLS
                );

                for (String containingPluginName : containingPlugins) {
                    containingPluginName = AA_API.getCleanPluginName(containingPluginName, true);
                    new FancyMessage(ChatColor.GREEN + " -> " + containingPluginName)
                        .command("/aa_fixcommand " + args[0] + ' ' + containingPluginName + ':' + args[0]) //NON-NLS
                        .tooltip(
                            AA_API.__(
                                "commands.fixcommand-click-to-fix",
                                args[0],
                                containingPluginName + ':' + args[0]
                            )
                        )
                        .send(sender);
                }
            } else {
                sender.sendMessage(ChatColor.RED + AA_API
                    .__("commands.command-not-found-on-server", ChatColor.YELLOW + "/" + args[0] + ChatColor.RED));
            }

            return;
        }

        // parse the user input
        final String[] spl = AA_API.getClearCommand(args[1]);

        // check for empty command after plugin name
        if (spl.length != 2) {
            sender.sendMessage(ChatColor.RED + AA_API.__("commands.fixcommand-enter-both-commands"));
            return;
        }

        // check if user provided both, plugin and command name
        if (null != spl[1] && spl[1].isEmpty()) {
            sender.sendMessage(ChatColor.RED + AA_API
                .__("commands.fixcommand-command-not-recognized", AA_API.getAaName()));
            return;
        }

        // first, let's see if we can find the plugin used to hard-wire this command to
        final String conflictingCommand = args[0];
        final String plugName           = spl[0];
        final String lowerCasePlugName  = spl[0].toLowerCase();
        String cmdName                  = spl[1].toLowerCase();

        /*
        TODO: fix at a later stage, when someone needs to fix/disable/whatever commands from permdescriptions.yml
        // if we have more arguments than 2, we might be hitting a sub-command tab-completion
        // from the permdescriptions.yml file, so let's adjust the spl
        if (args.length > 2) {
            for (int i = 2; i < args.length; i++) {
                cmdName += " " + args[i];
            }
        }*/

        // if a player tries to use "Core" as plugin name, tell them to use the "minecraft:commandName" equivalent
        if (AA_API.__("general.core").toLowerCase().equals(lowerCasePlugName)) {
            sender.sendMessage(
                ChatColor.YELLOW +
                    AA_API.__("commands.fixcommand-core-cannot-be-used.1") + '\n' +
                    ChatColor.WHITE +
                    AA_API.__("commands.fixcommand-core-cannot-be-used.2") +
                    ": " //NON-NLS
            );

            // list all fix possibilities
            for (String foundPluginName : containingPlugins) {
                foundPluginName = AA_API.getCleanPluginName(foundPluginName, true);
                new FancyMessage(ChatColor.GREEN + " -> " + foundPluginName + ChatColor.WHITE)
                    .command("/aa_fixcommand " + args[0] + ' ' + foundPluginName + ':' + args[0]) //NON-NLS
                    .tooltip(
                        AA_API.__("commands.fixcommand-click-to-fix", args[0]) +
                            foundPluginName + ':' + args[0]
                    )
                    .send(sender);
            }
            return;
        }

        // get the actual plugin to hard-wire the command to
        final Plugin p = AA_API.getPluginIgnoreCase(plugName);
        if (
            null == p &&
                !(
                    "minecraft".equals(lowerCasePlugName) || //NON-NLS
                        "bukkit".equals(lowerCasePlugName) || //NON-NLS
                        "spigot".equals(lowerCasePlugName) //NON-NLS
                )
            ) {
            // plugin not found and it's not a core prefix either - show error message
            sender.sendMessage(
                ChatColor.RED +
                    AA_API.__(
                        "plugin.not-found",
                        ChatColor.YELLOW + plugName + ChatColor.RED
                    )
            );
            return;
        }

        // check that the plugin actually contains the command to redirect the original to
        List<String> fixedCommandContainingPlugins = null;
        try {
            fixedCommandContainingPlugins = AA_API.getCommandContainingPlugins(cmdName);
        } catch (AccessException | IllegalAccessException | InvalidClassException | NoSuchMethodException
                | SecurityException | InvocationTargetException e1) {
            sender.sendMessage(
                ChatColor.RED + AA_API.__("error.general-for-chat"));
            e1.printStackTrace();
        }

        // prepare lowercase list of plugins, since we can use any case on the command line
        final Collection<String> lowerCaseContainingPlugins = new ArrayList<String>();
        for (final String lowerPlugName : Objects.requireNonNull(fixedCommandContainingPlugins)) {
            // core plugins need to have their names replaced
            lowerCaseContainingPlugins.add(AA_API.getCleanPluginName(lowerPlugName, false).toLowerCase());
        }

        // the plugin contains our command to redirect to - let's proceed
        if (!containingPlugins.isEmpty() && lowerCaseContainingPlugins.contains(lowerCasePlugName)) {
            Bukkit.getPluginManager()
                  .callEvent(new AAAddCommandOverrideEvent(conflictingCommand.toLowerCase(), plugName + ':' + cmdName));
            Bukkit.getPluginManager().callEvent(new AASaveCommandOverridesEvent(sender));

            // list of commands that were successfully fixed
            final List<String> done = new ArrayList<String>();

            // list of commands we were not able to fix
            final List<String> ko = new ArrayList<String>();

            // adjust listener priorities to make sure our override gets pre-processed by AdminAnything
            Bukkit.getPluginManager().callEvent( new AAAdjustListenerPrioritiesEvent(
                            sender,
                    Collections
                        .singletonList(
                            ChatColor.GREEN +
                                AA_API.__(
                                    "commands.fixcommand-done",
                                    ChatColor.WHITE + "/" + conflictingCommand + ChatColor.GREEN,
                                    ChatColor.WHITE + plugName + ':' + (cmdName.startsWith("/") ? cmdName.substring(1) :
                                                                        cmdName) + ChatColor.GREEN
                                )
                        ),
                    new String[] { conflictingCommand.toLowerCase() },
                    null,
                    true,
                    done,
                    ko
                    )
                    );

            if (sender instanceof Player) {
                new FancyMessage('[' + AA_API.__("general.undo") + ']').color(ChatColor.AQUA)
                                                                       .tooltip(AA_API
                                                                           .__("commands.fixcommand-restore", conflictingCommand))
                                                                       .command("/aa_unfixcommand " + conflictingCommand) //NON-NLS
                                                                       .send(sender);
            }
        } else {
            sender.sendMessage(
                ChatColor.RED +
                    AA_API.__(
                        "commands.fixcommand-unable",
                        AA_API.getAaName(),
                        ChatColor.YELLOW + "/" + conflictingCommand + ChatColor.RED,
                        ChatColor.GREEN + plugName + ChatColor.RED,
                        ChatColor.YELLOW + cmdName + ChatColor.RED
                    )
            );
        }
    } // end method

} // end class