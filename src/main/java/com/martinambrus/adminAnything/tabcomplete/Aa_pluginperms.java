package com.martinambrus.adminAnything.tabcomplete;

import com.martinambrus.adminAnything.AA_API;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tab completion for the /aa_pluginperms command.
 * This will only work on Minecraft servers 1.6+
 *
 * @author Martin Ambrus
 */
public class Aa_pluginperms implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender commandSender, Command command, String alias, String[] args) {
        // no completion if we have none or too many arguments
        if (args.length == 0 || 2 < args.length) {
            //noinspection ReturnOfNull
            return null;
        }

        // auto-complete plugin name
        List<String> completions = new ArrayList<String>();
        StringUtil.copyPartialMatches(args[0], AA_API.getServerPlugins(), completions);
        Collections.sort(completions);

        return completions;
    } // end method

} // end class