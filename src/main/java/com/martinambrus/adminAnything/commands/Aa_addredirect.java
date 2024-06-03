package com.martinambrus.adminAnything.commands;

import com.martinambrus.adminAnything.AA_API;
import com.martinambrus.adminAnything.Utils;
import com.martinambrus.adminAnything.events.AAAddCommandRedirectEvent;
import com.martinambrus.adminAnything.events.AAAdjustListenerPrioritiesEvent;
import com.martinambrus.adminAnything.events.AASaveCommandRedirectsEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.io.InvalidClassException;
import java.lang.reflect.InvocationTargetException;
import java.rmi.AccessException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Command which adds a redirect from one command
 * to another.
 *
 * @author Martin Ambrus
 */
public class Aa_addredirect extends AbstractCommand {

    /***
     * /aa_addredirect - adds a redirect from one command to another
     *
     * @param sender The player who is calling this command.
     * @param cmd The actual command that is being executed.
     * @param unused Name of the command which is being executed.
     * @param args Any arguments passed to this command.
     *
     * @return Returns true if it was possible to add the redirect, false otherwise.
     */
    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String unused, String[] args) {
        if (!super.onCommand(sender, cmd, unused, args)) {
            return true;
        }

        if (!AA_API.isFeatureEnabled("redirectcommand")) { //NON-NLS
            sender.sendMessage(ChatColor.RED + AA_API.__("general.feature-disabled"));
            return true;
        }

        // no arguments provided
        if (1 > args.length) {
            sender.sendMessage(ChatColor.RED + AA_API.__("commands.redirect-enter-command"));
            return false;
        }

        // no second argument provided
        if (2 > args.length) {
            sender.sendMessage(ChatColor.RED + AA_API.__("commands.redirect-enter-redirect", args[0]));
            return false;
        }

        // if the command is already redirected, do nothing
        if (AA_API.getCommandsList("redirects").contains(args[0])) { //NON-NLS
            sender.sendMessage(ChatColor.RED + AA_API.__("commands.redirect-exists"));
            return true;
        }

        // check if we have the original command on the server
        List<String> containingPlugins;
        try {
            containingPlugins = AA_API.getCommandContainingPlugins(args[0].toLowerCase(), true);
            if (null == containingPlugins || containingPlugins.isEmpty()) {
                sender.sendMessage(ChatColor.RED + AA_API
                    .__("commands.command-not-found-on-server", ChatColor.WHITE + args[0] + ChatColor.RED));
                return true;
            }
        } catch (AccessException | IllegalAccessException | InvalidClassException | NoSuchMethodException
                | SecurityException | InvocationTargetException e1) {
            sender.sendMessage(
                ChatColor.RED + AA_API.__("error.general-for-chat"));
            e1.printStackTrace();
            return true;
        }

        // check if we have the command to redirect to on the server
        try {
            String commandToRedirectTo = args[1].toLowerCase();

            // remove quotes if we're aiming to redirect to a command with parameters
            if (commandToRedirectTo.startsWith("\"")) {
                commandToRedirectTo = commandToRedirectTo.substring(1);
            }

            containingPlugins = AA_API.getCommandContainingPlugins(commandToRedirectTo, true);
            if (null == containingPlugins || containingPlugins.isEmpty()) {
                sender.sendMessage(ChatColor.RED + AA_API
                    .__("commands.command-not-found-on-server", ChatColor.WHITE + args[1] + ChatColor.RED));
                return true;
            }
        } catch (AccessException | IllegalAccessException | InvalidClassException | NoSuchMethodException
                | SecurityException | InvocationTargetException e1) {
            sender.sendMessage(
                ChatColor.RED + AA_API.__("error.general-for-chat"));
            e1.printStackTrace();
            return true;
        }

        // cater for people who'd use this command as: /aa_redirect "command" "redirect_here"
        // by removing quotes from those arguments
        args = Utils.compactQuotedArgs( args );

        // list of redirects that were successfully added
        final List<String> done = new ArrayList<String>();

        // list of redirects we were not able to add
        final List<String> ko = new ArrayList<String>();

        // adjust listener priorities to make sure our command gets pre-processed by AdminAnything
        Bukkit.getPluginManager().callEvent( new AAAdjustListenerPrioritiesEvent(
                sender,
                // this will be sent to the player upon successful adjustment
                Collections.singletonList(
                    ChatColor.GREEN +
                        AA_API.__(
                            "commands.redirect-done",
                            ChatColor.WHITE + args[0] + ChatColor.GREEN,
                            ChatColor.WHITE + args[1] + ChatColor.GREEN
                        )
                ),
                new String[] { (args[0].contains(" ") ? args[0].split(Pattern.quote(" "))[0].toLowerCase() : args[0].toLowerCase()) },
                "redirects", //NON-NLS
                true,
                done,
                ko
                )
                );

        // only update lists and config if we actually were able to add the redirect
        if (!done.isEmpty()) {
            // add this redirect to the config file
            // args[0] = command to redirect, args[1] = command to redirect it to
            // ... make sure we only lowercase the command to redirect to if it contains any arguments,
            //     otherwise we'd lowercase those arguments as well
            if ( args[1].contains(" ") ) {
                String[] splitted = args[1].split(Pattern.quote(" "));
                splitted[0] = splitted[0].toLowerCase();
                args[1] = Utils.implode( splitted, " " );
            } else {
                args[1] = args[1].toLowerCase();
            }

            Bukkit.getPluginManager().callEvent( new AAAddCommandRedirectEvent(args[0].toLowerCase(), args[1]) );
            Bukkit.getPluginManager().callEvent( new AASaveCommandRedirectsEvent(sender) );
        } else {
            sender.sendMessage(ChatColor.RED + AA_API
                .__("commands.command-not-found-on-server", ChatColor.WHITE + args[0] + ChatColor.RED));
        }

        return true;
    } // end method

} // end class