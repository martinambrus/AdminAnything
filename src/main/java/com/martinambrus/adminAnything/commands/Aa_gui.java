package com.martinambrus.adminAnything.commands;

import com.martinambrus.adminAnything.AA_API;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Displays nick GUI for the given player.
 *
 * @author Martin Ambrus
 */
public class Aa_gui extends AbstractCommand {

    /**
     * Instance of {@link com.martinambrus.adminAnything.AdminAnything}.
     */
    private final Plugin plugin;

    /**
     * Constructor, stores the instance of {@link com.martinambrus.adminAnything.AdminAnything}
     * for futher use.
     *
     * @param aa Instance of {@link com.martinambrus.adminAnything.AdminAnything}.
     */
    public Aa_gui(final Plugin aa) {
        this.plugin = aa;
    } // end method

    /***
     * /aa_gui - displays nick GUI for the given player
     *
     * @param sender The player who is calling this command.
     * @param cmd The actual command that is being executed.
     * @param unused Name of the command which is being executed.
     * @param args Any arguments passed to this command.
     *
     * @return Always returns true.
     */
    @SuppressWarnings("deprecation")
    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String unused, final String[] args) {
        if (!super.onCommand(sender, cmd, unused, args)) {
            return true;
        }

        //noinspection HardCodedStringLiteral
        if (!AA_API.isFeatureEnabled("chatnickguicommand")) {
            sender.sendMessage(ChatColor.RED + AA_API.__("general.feature-disabled"));
            return true;
        }

        if ( !(sender instanceof Player) ) {
            sender.sendMessage(ChatColor.RED + AA_API.__("commands.actions-players-only"));
            return true;
        }

        // check that we have exactly 1 parameter
        if ( args.length < 1 ) {
            sender.sendMessage(ChatColor.RED + AA_API.__("commands.provide-player"));
            return true;
        }

        // check that our parameter is an online player
        Player p = Bukkit.getPlayer( args[ 0 ] );
        if ((null == p) || !p.isOnline()) {
            sender.sendMessage(ChatColor.RED + AA_API.__("commands.online-players-only"));
            return false;
        }

        // create and show the chest GUI
        ((Player) sender).openInventory( AA_API.createGUIPlayerInventory( (Player) sender, args[ 0 ] ) );

        return true;
    } // end method

} // end class