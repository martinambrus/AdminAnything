package com.martinambrus.adminAnything.commands;

import com.martinambrus.adminAnything.AA_API;
import com.martinambrus.adminAnything.events.AAReloadEvent;
import com.martinambrus.adminAnything.events.AARemoveVirtualPermissionEvent;
import com.martinambrus.adminAnything.events.AASaveVirtualPermsEvent;
import mkremins.fanciful.FancyMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;

/**
 * Removes a virtual permission that was previously added
 * via the /aa_addperm command.
 *
 * @author Martin Ambrus
 */
public class Aa_delperm extends AbstractCommand {

    /**
     * Shows all existing virtual permissions
     * to the command sender.
     *
     * @param sender The actual player/console who's calling this command.
     */
    private void showExistingPermissions(final CommandSender sender) {
        // show which virtual permissions we have if no arguments were passed
        if (AA_API.getCommandsConfigurationValues("virtualperms").isEmpty()) { //NON-NLS
            sender.sendMessage(ChatColor.GREEN + AA_API.__("commands.delperm-none-found", AA_API.getAaName()));
        } else {
            final List<String> existingPerms = new ArrayList<String>();
            //noinspection HardCodedStringLiteral
            for (final Entry<String, Object> perm : AA_API.getCommandsConfigurationValues("virtualperms")
                                                          .entrySet()) {
                if (perm.getValue() instanceof String) {
                    existingPerms.add(perm.getKey());
                }
            }

            if (existingPerms.isEmpty()) {
                sender.sendMessage(ChatColor.GREEN + AA_API.__("commands.delperm-none-found", AA_API.getAaName()));
            } else {
                @SuppressWarnings("HardCodedStringLiteral") final FancyMessage toSend = new FancyMessage(AA_API
                    .__("commands.delperm-perms-on-server") + ": ").color(ChatColor.YELLOW);

                for (int i = 0; i < (existingPerms.size() - 1); i++) {
                    toSend
                        .then(existingPerms.get(i) + ", ")
                        .color(ChatColor.WHITE)
                        .command("/aa_delperm " + existingPerms.get(i)) //NON-NLS
                        .tooltip(AA_API.__("commands.delperm-click-to-delete"));
                }

                // add last record
                toSend
                    .then(existingPerms.get(existingPerms.size() - 1))
                    .color(ChatColor.WHITE)
                    .command("/aa_delperm " + existingPerms.get(existingPerms.size() - 1)) //NON-NLS
                    .tooltip(AA_API.__("commands.delperm-click-to-delete"));

                toSend.send(sender);
            }
        }
    } // end method

    /***
     * /aa_delperm - removes a virtual permission added via /aa_addperm
     *
     * @param sender The player who is calling this command.
     * @param cmd The actual command that is being executed.
     * @param unused Name of the command which is being executed.
     * @param args Any arguments passed to this command.
     *
     * @return Returns true if we could remove the given permission, false otherwise.
     */
    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String unused, final String[] args) {
        if (!super.onCommand(sender, cmd, unused, args)) {
            return true;
        }

        //noinspection HardCodedStringLiteral
        if (!AA_API.isFeatureEnabled("addperm")) {
            sender.sendMessage(ChatColor.RED + AA_API.__("general.feature-disabled"));
            return true;
        }

        // show existing permissions if no parameter's present
        if (0 == args.length) {
            showExistingPermissions(sender);
            return false;
        }

        // check which permissions could have been deleted
        // and create OK and KO lists to tell the player
        final Collection<String> done = new ArrayList<String>();
        final Collection<String> ko   = new ArrayList<String>();

        for (final String arg : args) {
            //noinspection HardCodedStringLiteral
            if (AA_API.getCommandsConfigurationValues("virtualperms").containsKey(arg)) {
                Bukkit.getPluginManager().callEvent(new AARemoveVirtualPermissionEvent(arg));
                done.add(arg);
            } else {
                if (!done.contains(arg) && !ko.contains(arg)) {
                    ko.add(arg);
                }
            }
        }

        if (!done.isEmpty()) {
            // reload virtual permissions from the config
            //noinspection HardCodedStringLiteral
            Bukkit.getPluginManager().callEvent( new AAReloadEvent("virtualperms") );
            //noinspection HardCodedStringLiteral
            sender.sendMessage(ChatColor.GREEN + AA_API.__("commands.delperm-ok") + ": " + ChatColor.WHITE + String
                .join(", ", done));
        }

        if (!ko.isEmpty()) {
            //noinspection HardCodedStringLiteral
            sender.sendMessage(ChatColor.RED + AA_API.__("commands.delperm-not-found") + ": " + ChatColor.WHITE + String
                    .join(", ", ko));
        }

        Bukkit.getPluginManager().callEvent( new AASaveVirtualPermsEvent(sender) );
        return true;
    } // end method

} // end class