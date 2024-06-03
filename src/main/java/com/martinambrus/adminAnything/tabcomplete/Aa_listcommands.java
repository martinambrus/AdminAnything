package com.martinambrus.adminAnything.tabcomplete;

import com.martinambrus.adminAnything.AA_API;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Tab completion for the /aa_listcommands command.
 * This will only work on Minecraft servers 1.6+
 *
 * @author Martin Ambrus
 */
public class Aa_listcommands implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender commandSender, Command command, String alias, String[] args) {
        // auto-complete plugin name
        if (args[args.length - 1].matches("(pl|plug|plugin):.*")) {
            List<String> completions = new ArrayList<String>();

            // the prefix that was used to auto-complete this parameter
            String prefix   = args[args.length - 1].substring(0, args[args.length - 1].indexOf(':') + 1);

            // the actual plugin name, without the pl:, plug: or plugin: prefix
            String plugName = args[args.length - 1].replaceAll(prefix, "");

            StringUtil.copyPartialMatches(prefix + plugName, AA_API.getServerPlugins(prefix), completions);
            Collections.sort(completions);

            return completions;
        }

        // auto-complete yes/no parameters
        if (args[args.length - 1].matches("(desc|description|showdescription|showdescriptions|al|aliases|showaliases|perm|perms|permission|permissions|permdesc|permissiondescriptions|permissionsdescriptions|usg|usage|showusage|multiline):.*")) {
            // the prefix that was used to auto-complete this parameter
            String prefix = args[args.length - 1].substring(0, args[args.length - 1].indexOf(":") + 1);

            List<String> completions = new ArrayList<String>();
            StringUtil.copyPartialMatches(
                args[args.length - 1],
                new ArrayList<String>(Arrays.asList(prefix + "yes", prefix + "no")),
                completions
            );

            return completions;
        }

        // at last, try to auto-complete from list of parameters for /aa_listcommands
        List<String> completions = new ArrayList<String>();
        StringUtil.copyPartialMatches(
            args[args.length - 1],
            new ArrayList<String>(
                Arrays.asList(
                    "pg",
                    "page",
                    "pl",
                    "plug",
                    "plugin",
                    "desc",
                    "description",
                    "showdescription",
                    "showdescriptions",
                    "al",
                    "aliases",
                    "showaliases",
                    "perm",
                    "perms",
                    "permission",
                    "permissions",
                    "permdesc",
                    "permissiondescriptions",
                    "permissionsdescriptions",
                    "usg",
                    "usage",
                    "showusage",
                    "search",
                    "multiline")
            ),
            completions
        );

        return completions;
    } // end method

} // end class