package com.martinambrus.adminAnything.commands;

import com.martinambrus.adminAnything.AA_API;
import com.martinambrus.adminAnything.Utils;
import com.martinambrus.adminAnything.events.AAReloadEvent;
import com.martinambrus.adminAnything.events.AARemoveCommandRedirectEvent;
import com.martinambrus.adminAnything.events.AASaveCommandRedirectsEvent;
import mkremins.fanciful.FancyMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

/**
 * Removes a command redirect that was previously added
 * to AdminAnything via the /aa_addredirect command.
 *
 * @author Martin Ambrus
 */
public class Aa_delredirect extends AbstractCommand {

    /**
     * Shows all existing command redirects
     * to the command sender.
     *
     * @param sender The actual player/console who's calling this command.
     */
    private void showExistingRedirects(final CommandSender sender) {
        //noinspection HardCodedStringLiteral
        final Set<String> redirects = AA_API.getCommandsConfigurationKeys("redirects", false);
        if (redirects.isEmpty()) {
            sender.sendMessage(ChatColor.GREEN + AA_API.__("commands.delredir-nothing-redirected", AA_API.getAaName()));
        } else {
            //noinspection HardCodedStringLiteral
            final FancyMessage toSend = new FancyMessage(AA_API
                .__("commands.delredir-redirected-commands", AA_API.getAaName()) + ": ").color(ChatColor.YELLOW);
            final String[]     keyz             = redirects.toArray(new String[redirects.size() - 1]);
            final int          keyzLengthMinus1 = keyz.length - 1;

            for (int i = 0; i < keyzLengthMinus1; i++) {
                toSend
                    .then(keyz[i] + ", ")
                    .color(ChatColor.WHITE)
                    .command("/aa_delredirect " + keyz[i]) //NON-NLS
                    .tooltip(AA_API.__("commands.delredir-click-to-remove"));
            }

            // add last record
            toSend
                .then(keyz[keyz.length - 1])
                .color(ChatColor.WHITE)
                .command("/aa_delredirect " + keyz[keyz.length - 1]) //NON-NLS
                .tooltip(AA_API.__("commands.delredir-click-to-remove"));

            toSend.send(sender);
        }
    } // end method

    /***
     * /aa_delredirect - removes a command redirect
     *
     * @param sender The player who is calling this command.
     * @param cmd The actual command that is being executed.
     * @param unused Name of the command which is being executed.
     * @param args Any arguments passed to this command.
     *
     * @return Returns true if the command redirect could have been deleted,
     *         false otherwise.
     */
    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String unused, String[] args) {
        if (!super.onCommand(sender, cmd, unused, args)) {
            return true;
        }

        //noinspection HardCodedStringLiteral
        if (!AA_API.isFeatureEnabled("redirectcommand")) {
            sender.sendMessage(ChatColor.RED + AA_API.__("general.feature-disabled"));
            return true;
        }

        // if we want to delete a redirect for a command with parameter,
        // compact arguments to this command, so we do that correctly
        args = Utils.compactQuotedArgs(args);

        // show existing redirects if no parameters were passed
        if (0 == args.length) {
            showExistingRedirects(sender);
            return false;
        }

        // check which redirects could have been deleted
        // and create OK and KO lists to tell the player
        final Collection<String> done = new ArrayList<String>();
        final Collection<String> ko   = new ArrayList<String>();

        for (final String arg : args) {
            //noinspection HardCodedStringLiteral
            if (AA_API.getCommandsList("redirects").contains(arg)) {
                Bukkit.getPluginManager().callEvent( new AARemoveCommandRedirectEvent(arg) );
                done.add(arg);
            } else {
                if (!done.contains(arg) && !ko.contains(arg)) {
                    ko.add(arg);
                }
            }
        }

        if (!done.isEmpty()) {
            //noinspection HardCodedStringLiteral
            sender.sendMessage(ChatColor.GREEN + AA_API
                .__("commands.delredir-done", AA_API.getAaName()) + ": " + ChatColor.WHITE + String.join(", ", done));
            //noinspection HardCodedStringLiteral
            Bukkit.getPluginManager().callEvent( new AAReloadEvent("checkcommandconflicts") );
        }

        if (!ko.isEmpty()) {
            //noinspection HardCodedStringLiteral
            sender.sendMessage(ChatColor.RED + AA_API
                .__("commands.delredir-commands-not-redirected", AA_API.getAaName()) + ": " + ChatColor.WHITE + String
                    .join(", ", ko));
        }

        Bukkit.getPluginManager().callEvent( new AASaveCommandRedirectsEvent(sender) );
        return true;
    } // end method

} // end class