package com.martinambrus.adminAnything.commands;

import com.martinambrus.adminAnything.AA_API;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class AbstractCommand implements CommandExecutor {
    /**
     * Executes the given command, returning its success.
     * <br>
     * If false is returned, then the "usage" plugin.yml entry for this command
     * (if defined) will be sent to the player.
     *
     * @param sender  Source of the command
     * @param command Command which was executed
     * @param label   Alias of the command which was used
     * @param args    Passed command arguments
     *
     * @return true if a valid command, otherwise false
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // check if AA is still in warmup mode and don't let any command actually run until
        // we're fully loaded and all listeners have been adjusted
        if (AA_API.isWarmingUp()) {
            sender.sendMessage(AA_API.__("commands.still-warming-up", AA_API.getAaName()));
            return false;
        }

        return true;
    }
}
