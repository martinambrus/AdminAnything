package com.martinambrus.adminAnything.commands;

import com.martinambrus.adminAnything.AA_API;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

/**
 * Creates an override for a command, so it's always
 * called from the plugin which the Admin chooses for it.
 *
 * @author Martin Ambrus
 */
public class Aa_fixcommand extends AbstractCommand {

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
     *
     * @param aa Instance of {@link com.martinambrus.adminAnything.AdminAnything}.
     */
    public Aa_fixcommand(final Plugin aa) {
        plugin = aa;
        AA_API.startRequiredListener("commandPreprocessor"); //NON-NLS
    }

    /***
     * /aa_fixCommand - hard-wires the given command to the requested plugin's command (i.e. /home to Essentials:home)
     *
     * @param sender The player who is calling this command.
     * @param cmd The actual command that is being executed.
     * @param unused Name of the command which is being executed.
     * @param args Any arguments passed to this command.
     *
     * @return Returns true if we could create the command override, false otherwise.
     */
    @SuppressWarnings("deprecation")
    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String unused, final String[] args) {
        if (!super.onCommand(sender, cmd, unused, args)) {
            return true;
        }

        if (!AA_API.isFeatureEnabled("fixcommand")) { //NON-NLS
            sender.sendMessage(ChatColor.RED + AA_API.__("general.feature-disabled"));
            return true;
        }

        // check if we have the first parameter present
        if (1 > args.length) {
            sender.sendMessage(ChatColor.RED + AA_API.__("commands.fix-enter-command-to-fix"));
            return false;
        }

        // check if we have all parameters
        if (2 <= args.length) {
            // check if we have the command name as well
            if (!args[1].contains(".") && !args[1].contains(":")) {
                // check that we've provided a valid plugin name to fix this command to
                if (null != AA_API.getPluginIgnoreCase(args[1])) {
                    // the player provided a valid plugin, update the second argument
                    // to contain the same command as the one we're trying to fix
                    args[1] += ':' + args[0];
                } else {
                    sender.sendMessage(
                        ChatColor.RED +
                            AA_API.__(
                                "commands.command-not-found-on-server",
                                ChatColor.WHITE + args[1] + ChatColor.RED
                            )
                    );
                    return false;
                }
            }
        }

        // don't allow to override AA's core commands
        if (AA_API.isAaCoreCommand(new String[] { args[0] })) {
            sender.sendMessage(ChatColor.RED + AA_API.__("commands.no-core-manipulation"));
            return false;
        }

        // check whether this command is not fixed already
        if (AA_API.getCommandsList("overrides").contains(args[0].toLowerCase())) { //NON-NLS
            sender.sendMessage(ChatColor.RED + AA_API.__("commands.fix-already-fixed"));
            return false;
        }

        // do the fix in a separate task to save energy of the main thread
        Bukkit.getScheduler().runTask(plugin, new Aa_fixcommand_runnable(sender, args));
        return true;
    } // end method

} // end class