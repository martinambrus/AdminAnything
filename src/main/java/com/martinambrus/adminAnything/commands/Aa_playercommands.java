package com.martinambrus.adminAnything.commands;

import com.martinambrus.adminAnything.AA_API;
import com.martinambrus.adminAnything.Constants;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Command which lists all commands for a single player.
 *
 * @author Martin Ambrus
 */
public class Aa_playercommands extends AbstractCommand {

    /**
     * Instance of {@link com.martinambrus.adminAnything.AdminAnything}.
     */
    private final Plugin plugin;

    /**
     * Constructor, takes AdminAnything as a parameter,
     * since we'll be needing it later to set up a delayed
     * task (as not to overload server by all the action :P).
     *
     * @param aa Instance of {@link com.martinambrus.adminAnything.AdminAnything}.
     */
    public Aa_playercommands(final Plugin aa) {
        plugin = aa;
    } // end method

    /***
     * playercommands - displays list of commands available to the player requested
     *
     * @param sender The player who is calling this command.
     * @param cmd The actual command that is being executed.
     * @param unused Name of the command which is being executed.
     * @param args Any arguments passed to this command.
     *
     * @return Returns true if we could list player's commands, false otherwise.
     */
    @SuppressWarnings("deprecation")
    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String unused, final String[] args) {
        if (!super.onCommand(sender, cmd, unused, args)) {
            return true;
        }

        //noinspection HardCodedStringLiteral
        if (!AA_API.isFeatureEnabled("playercommands")) {
            sender.sendMessage(ChatColor.RED + AA_API.__("general.feature-disabled"));
            return true;
        }

        // check for at least 1 parameter, if we don't have permissions to actually show our own commands
        if (
            (
                !AA_API.checkPerms(sender, "aa.checkplayercommands.own", false) ||
                !(sender instanceof Player)
            ) &&
            1 > args.length
        ) {
            sender.sendMessage(
                ChatColor.RED + AA_API.__("commands.playercommands-provide-player"));
            return false;
        }

        // make sure we requested permissions of an online player
        Player p;
        if (
            sender instanceof Player &&
            (
                (AA_API.checkPerms(sender, "aa.checkplayercommands.own", false) && 1 > args.length) ||
                (AA_API.checkPerms(sender, "aa.checkplayercommands.own", false) && 1 == args.length && args[0].matches(Constants.INT_REGEX.toString()))
            )
        ) {
            p = (Player) sender;
        } else {
            // double-check the aa.checkplayercommands permission if we provided a player name
            if (!AA_API.checkPerms(sender, "aa.checkplayercommands", true)) {
                return true;
            }

            p = Bukkit.getPlayer(args[0]);
        }

        if ((null == p) || !p.isOnline()) {
            sender.sendMessage(ChatColor.RED + AA_API.__("commands.online-players-only"));
            return false;
        } else {
            // check if the player is not OP
            if (p.isOp()) {
                sender.sendMessage(AA_API
                    .__("commands.playercommands-player-is-operator", p.getDisplayName() + ChatColor.GREEN));
                return true;
            }

            // load and display list of commands in a different thread, so we don't
            // make the main thread wait for our calculations
            if (!(sender instanceof Player)) {
                Bukkit.getScheduler().scheduleAsyncDelayedTask(plugin,
                    new Aa_playercommands_runnable(sender, args, cmd, p, plugin));
            } else {
                Bukkit.getScheduler().scheduleSyncDelayedTask(plugin,
                    new Aa_playercommands_runnable(sender, args, cmd, p, plugin));
            }
        }

        return true;
    } // end method

} // end class