package com.martinambrus.adminAnything.commands;

import com.martinambrus.adminAnything.AA_API;
import com.martinambrus.adminAnything.events.AAReloadEvent;
import mkremins.fanciful.FancyMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * Checks commands for conflicts and displays the result
 * either in the in-game chat or into the console.
 *
 * @author Martin Ambrus
 */
public class Aa_checkcommandconflicts extends AbstractCommand implements Listener {

    /**
     * Instance of {@link com.martinambrus.adminAnything.AdminAnything}.
     */
    private final Plugin plugin;

    /**
     * The actual name of this feature that we can use
     * when reloading the plugin in the reload event.
     */
    private final String featureName = "checkcommandconflicts"; //NON-NLS

    /**
     * A cached list of messages to be shown to the player/console
     * as the resulting output.
     */
    public List<FancyMessage> messages;

    /**
     * Time after which we clean up all our generated
     * and cached messaged. This time is in minutes.
     */
    private final int cleanupTimeout = 15; // in minutes

    /**
     * Constructor, stores the instance of {@link com.martinambrus.adminAnything.AdminAnything}
     * for futher use.
     *
     * @param aa Instance of {@link com.martinambrus.adminAnything.AdminAnything}.
     */
    public Aa_checkcommandconflicts(final Plugin aa) {
        this.plugin = aa;

        //noinspection HardCodedStringLiteral
        if (AA_API.isFeatureEnabled("checkcommandconflicts")) {
            //noinspection HardCodedStringLiteral
            if (!AA_API.isListenerRegistered("checkcomanndconflicts")) {
                //noinspection HardCodedStringLiteral
                AA_API.startRequiredListener("checkcomanndconflicts", this);
            }
        }
    } // end method

    /***
     * /aa_checkCommandConflicts - checks and reports all conflicting commands and aliases on the server
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
        if (!AA_API.isFeatureEnabled("checkcommandconflicts")) {
            sender.sendMessage(ChatColor.RED + AA_API.__("general.feature-disabled"));
            return true;
        }

        Aa_checkcommandconflicts_runnable r = new Aa_checkcommandconflicts_runnable(sender, args, plugin);
        r.cccClassInstance = this;

        if (!(sender instanceof Player)) {
            Bukkit.getScheduler().scheduleAsyncDelayedTask(plugin, r);
        } else {
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, r);
        }

        // make sure we clean up after ourselves
        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {

            @Override
            public void run() {
                clearCache();
            }

        }, cleanupTimeout * (20 * 60));

        return true;
    } // end method

    /**
     * Clears our cached messages in response
     * to the reload event.
     */
    void clearCache() {
        messages = null;
    } // end method

    /***
     * React to the custom ReloadEvent which is fired when /aa_reload gets executed
     * or when we enable AA for the first time.
     *
     * @param e The actual reload event with message that says who is this reload for.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void reload(final AAReloadEvent e) {
        final String msg = e.getMessage();
        if (null != msg && (msg.isEmpty() || msg.equals(featureName))) {
            clearCache();
        }
    } // end method

} // end class