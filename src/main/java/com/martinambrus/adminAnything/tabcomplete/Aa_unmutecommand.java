package com.martinambrus.adminAnything.tabcomplete;

import com.martinambrus.adminAnything.Utils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

/**
 * Tab completion for the /aa_unmutecommand command.
 * This will only work on Minecraft servers 1.6+
 *
 * @author Martin Ambrus
 */
public class Aa_unmutecommand implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender commandSender, Command command, String alias, String[] args) {
        //noinspection HardCodedStringLiteral
        return Utils.getListValueCompletions(args[args.length - 1], "mutes", command, alias, args);
    } // end method

} // end class