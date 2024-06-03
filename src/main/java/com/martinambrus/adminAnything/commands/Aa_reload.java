package com.martinambrus.adminAnything.commands;

import com.martinambrus.adminAnything.AA_API;
import com.martinambrus.adminAnything.events.AAReloadEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

/**
 * Command which sends a reload event that in turn reloads
 * all data for AdminAnything.
 *
 * @author Martin Ambrus
 */
public class Aa_reload extends AbstractCommand {

    /**
     * /aa_reload - sends event to reload the AA configuration
     *
     * @param sender The player who is calling this command.
     * @param cmd The actual command that is being executed.
     * @param unused Name of the command which is being executed.
     * @param args Any arguments passed to this command.
     *
     * @return Always returns true.
     */
    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String unused, final String[] args) {
        if (!super.onCommand(sender, cmd, unused, args)) {
            return true;
        }

        //noinspection HardCodedStringLiteral
        if (!AA_API.isFeatureEnabled("reload")) {
            sender.sendMessage(ChatColor.RED + AA_API.__("general.feature-disabled"));
            return true;
        }

        sender.sendMessage(ChatColor.GREEN + AA_API.__("commands.reload-init", AA_API.getAaName()));

        // fire up the reload event to clear up caches
        Bukkit.getScheduler()
              .scheduleSyncDelayedTask(Bukkit.getPluginManager().getPlugin(AA_API.getAaName()), new Runnable() {

            @Override
            public void run() {
                // perform the check
                Bukkit.getPluginManager().callEvent(new AAReloadEvent(""));

                sender.sendMessage(ChatColor.GREEN + AA_API.__("commands.reload-complete", AA_API.getAaName()));
            }

        }, 20); // 0 = will be run as soon as the server finished loading

        return true;
    } // end method

} // end class
