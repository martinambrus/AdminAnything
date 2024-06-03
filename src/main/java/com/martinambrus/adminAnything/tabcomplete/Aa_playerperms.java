package com.martinambrus.adminAnything.tabcomplete;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tab completion for the /aa_playerperms command.
 * This will only work on Minecraft servers 1.6+
 *
 * @author Martin Ambrus
 */
public class Aa_playerperms implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender commandSender, Command command, String alias, String[] args) {
        // auto-complete player name
        List<String> completions = new ArrayList<String>();
        List<String> playerNames = new ArrayList<String>();

        for (Player p : Bukkit.getServer().getOnlinePlayers()) {
            playerNames.add(p.getName());
        }

        if (playerNames.isEmpty()) {
            // no players on server
            return playerNames;
        }

        StringUtil.copyPartialMatches(args[0], playerNames, completions);
        Collections.sort(completions);

        return completions;
    } // end method

} // end class