package com.martinambrus.adminAnything.tabcomplete;

import com.martinambrus.adminAnything.AA_API;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import java.lang.reflect.InvocationTargetException;
import java.rmi.AccessException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Tab completion for the /aa_addredirect command.
 * This will only work on Minecraft servers 1.6+
 *
 * @author Martin Ambrus
 */
public class Aa_addredirect implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender commandSender, Command command, String alias, String[] args) {
        // no completion if we have too many arguments or any of them contain a quote
        if (2 < args.length || Arrays.asList(args).contains("\"")) {
            //noinspection ReturnOfNull
            return null;
        }

        List<String> completions = new ArrayList<String>();
        try {
            StringUtil
                .copyPartialMatches((1 == args.length ? args[0] : args[1]), AA_API.getServerCommands(), completions);
        } catch (AccessException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            // don't show anything unless we're debugging, since this is a non game-breaking error
            if (AA_API.getDebug()) {
                Bukkit.getLogger()
                      .warning(AA_API.__("error.tabcomplete-could-not-load-commands", command.getName(), alias));
                e.printStackTrace();
            }
        }

        Collections.sort(completions);
        return completions;
    } // end method

} // end class