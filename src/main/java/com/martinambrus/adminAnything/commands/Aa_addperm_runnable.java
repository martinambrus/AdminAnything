package com.martinambrus.adminAnything.commands;

import com.martinambrus.adminAnything.AA_API;
import com.martinambrus.adminAnything.events.AAAddVirtualPermEvent;
import com.martinambrus.adminAnything.events.AAAdjustListenerPrioritiesEvent;
import com.martinambrus.adminAnything.events.AAReloadEvent;
import com.martinambrus.adminAnything.events.AASaveVirtualPermsEvent;
import mkremins.fanciful.FancyMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * An actual logic to the /aa_addperm command to allow this
 * to be run in a separate thread and not mess about in the
 * main one, since we have no important actions to do in there.
 *
 * @author Martin Ambrus
 */
public class Aa_addperm_runnable implements Runnable {

    /**
     * Any arguments passed to this command.
     */
    private final String[] args;

    /**
     * The player who is calling this command.
     */
    private final CommandSender sender;

    public Aa_addperm_runnable(final String[] args, final CommandSender sender) {
        this.args = args;
        this.sender = sender;
    }

    /**
     * The actual logic behind adding extra permissions to commands.
     */
    @Override
    public void run() {
        // parse user input
        final String[] spl = AA_API.getClearCommand(args[1]);

        // check for empty command after plugin name
        if (spl.length != 2) {
            sender.sendMessage(ChatColor.RED + AA_API.__("commands.addperm-enter-command"));
            return;
        }

        // remove permission and store the command, including any parameters
        final String[] cmdLine = new String[args.length - 1];
        System.arraycopy(args, 1, cmdLine, 0, args.length - 1);

        // add the command and permission to the plugin configuration
        final String cmdLineString = String.join(" ", cmdLine);
        Bukkit.getPluginManager().callEvent( new AAAddVirtualPermEvent(args[0], cmdLineString) );
        Bukkit.getPluginManager().callEvent( new AASaveVirtualPermsEvent(sender) );

        // list of permissions that were successfully added
        List<String> done = new ArrayList<>();

        // list of permissions we were not able to add
        List<String> ko = new ArrayList<>();

        // adjust listener priorities to make sure our command gets pre-processed by AdminAnything
        Bukkit.getPluginManager().callEvent( new AAAdjustListenerPrioritiesEvent(
                sender,
                // these will be sent to the player upon successful adjustment
                Arrays.asList(
                    ChatColor.GREEN +
                        AA_API.__("commands.addperm-done.1",
                            ChatColor.WHITE + args[0] + ChatColor.GREEN,
                            ChatColor.WHITE + cmdLineString + ChatColor.GREEN
                        ),
                    ChatColor.YELLOW +
                        AA_API.__("commands.addperm-done.2",
                            ChatColor.WHITE + args[0] + ChatColor.YELLOW,
                            ChatColor.WHITE + cmdLineString + ChatColor.YELLOW)
                ),
                new String[] { spl[0] },
                "virtualperms", //NON-NLS
                true,
                done,
                ko
                )
                );

        // reload virtual permissions from the config
        //noinspection HardCodedStringLiteral
        Bukkit.getPluginManager().callEvent( new AAReloadEvent("virtualperms") );

        if (ko.isEmpty()) {
            // show an undo clickable action if a player has called the command
            if (sender instanceof Player) {
                new FancyMessage('[' + AA_API.__("general.undo") + ']')
                    .color(ChatColor.AQUA)
                    .tooltip(AA_API.__("commands.addperm-undo", args[0]))
                    .command("/aa_delperm " + args[0]).send(sender); //NON-NLS
            }
        } else {
            // something's gone wrong
            sender.sendMessage(ChatColor.RED + AA_API
                .__("commands.addperm-error", AA_API.getAaName()) + ": " + ChatColor.WHITE + String.join(" ", ko));
        }
    } // end method

} // end class