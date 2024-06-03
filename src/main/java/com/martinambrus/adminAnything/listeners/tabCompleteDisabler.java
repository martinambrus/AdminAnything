package com.martinambrus.adminAnything.listeners;

import com.martinambrus.adminAnything.AA_API;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Disables tab completions for commands that players
 * don't have access to.
 *
 * This variant is for MC servers 1.13 and above, as 1.13 was the first MC version
 * introduce the new PlayerCommandSendEvent event.
 *
 * @author Martin Ambrus
 */
final public class tabCompleteDisabler implements Listener {

    /**
     * Instance of {@link com.martinambrus.adminAnything.AdminAnything}.
     */
    private final Plugin plugin;

    /**
     * Constructor, stores instance of AdminAnything for further use
     * and activates the actual functionality.
     *
     * @param plugin Instance of {@link com.martinambrus.adminAnything.AdminAnything}.
     */
    public tabCompleteDisabler(final Plugin plugin) {
        this.plugin = plugin;
    } // end method

    @EventHandler(priority = EventPriority.HIGHEST)
    public void sendUpdatedTabCompletions(final PlayerCommandSendEvent e) {
        // if AA is not yet initialized, just bail out, since the initialization procedure in TabComplete class
        // will load tab completions automatically
        if (AA_API.isWarmingUp()) {
            return;
        }

        // if this player has the permission to bypass tab completion disable, we're good
        if (AA_API.checkPerms(e.getPlayer(), "aa.fulltabcomplete", false)){
            return;
        }

        // get commands available to this player and remove all completions
        // that the player shouldn't see
        Collection<String> completions = e.getCommands();
        List<String> cmdsAvailable  = AA_API.getPlayerAvailableCommands( e.getPlayer() );
        List<String> disabledCommands = AA_API.getCommandsList("removals");
        List<String> removeCompletions = new ArrayList<String>();
        for (String cmd : completions) {
            String completion_clear = ( cmd.contains(":") ? cmd.split(":")[1] : cmd );
            if (disabledCommands.contains(cmd) || disabledCommands.contains(completion_clear) || ( !cmdsAvailable.contains(cmd) && !cmdsAvailable.contains(completion_clear) ) )  {
                removeCompletions.add(cmd);
            }
        }

        // now remove all completions that we found out should not be included for this player
        // from the original commands collection
        for (String cmd : removeCompletions) {
            completions.remove(cmd);
        }

        // NOTE: we cannot add into the completions list due to this event's specification,
        // so commands from permdescriptions.yml will not show in 1.13+ versions tab-completion
    } // end method

} // end class