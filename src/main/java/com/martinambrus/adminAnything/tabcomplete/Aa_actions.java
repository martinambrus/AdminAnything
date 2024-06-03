package com.martinambrus.adminAnything.tabcomplete;

import com.martinambrus.adminAnything.Utils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

/**
 * Tab completion for the /aa_actions command.
 * This will only work on Minecraft servers 1.6+
 *
 * @author Martin Ambrus
 */
public class Aa_actions implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender commandSender, Command command, String alias, String[] args) {
        if (1 < args.length) {
            //noinspection ReturnOfNull
            return null;
        }

        return Utils.getServerCommandCompletions(args[0], command, alias);
    } // end method

} // end class