package com.martinambrus.adminAnything.commands;

import com.martinambrus.adminAnything.AA_API;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.io.InvalidClassException;
import java.lang.reflect.InvocationTargetException;
import java.rmi.AccessException;
import java.util.List;

/**
 * Command which allows adding extra permissions for a command
 * with or without any given parameters.
 *
 * @author Martin Ambrus
 */
public class Aa_addperm extends AbstractCommand {

    /**
     * Instance of {@link com.martinambrus.adminAnything.AdminAnything}.
     */
    private final Plugin plugin;

    /**
     * Constructor, takes AdminAnything as a parameter,
     * since we'll be needing it later to set up a delayed
     * task (as not to overload server by all the action :P).
     *
     * Also starts the commandPreprocessor listener
     * if it was not started yet, so our fixed, muted, disabled...
     * commands can get pre-processed by AdminAnything.
     *
     * @param aa Instance of {@link com.martinambrus.adminAnything.AdminAnything}.
     */
    public Aa_addperm(final Plugin aa) {
        plugin = aa;
        AA_API.startRequiredListener("commandPreprocessor"); //NON-NLS
    } // end method

    /***
     * /aa_addperm - adds a new permission node for the exact command line provided
     *
     * @param sender The player who is calling this command.
     * @param cmd The actual command that is being executed.
     * @param unused Name of the command which is being executed.
     * @param args Any arguments passed to this command.
     *
     * @return Returns true if the new permission can be added, false otherwise.
     */
    @SuppressWarnings("deprecation")
    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String unused, final String[] args) {
        if (!super.onCommand(sender, cmd, unused, args)) {
            return true;
        }

        if (!AA_API.isFeatureEnabled("addperm")) { //NON-NLS
            sender.sendMessage(ChatColor.RED + AA_API.__("general.feature-disabled"));
            return true;
        }

        // check for the correct number of command parameters
        if (2 > args.length) {
            sender.sendMessage(ChatColor.RED + AA_API.__("commands.addperm-enter-perm-and-command"));
            return false;
        }

        // check that this permission does not exist already
        if (AA_API.getCommandsConfigurationValues("virtualperms").containsKey(args[0])) { //NON-NLS
            sender.sendMessage(ChatColor.RED + AA_API.__("commands.addperm-perm-exists"));
            return true;
        }

        // check if we have the requested command on the server
        List<String> containingPlugins;
        try {
            containingPlugins = AA_API.getCommandContainingPlugins(args[1], true);
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

        // add permission in a separate thread
        Bukkit.getScheduler().runTask(plugin, new Aa_addperm_runnable(args, sender));
        return true;
    } // end method

} // end class