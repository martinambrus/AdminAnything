package com.martinambrus.adminAnything.tabcomplete;

import com.martinambrus.adminAnything.AA_API;
import com.martinambrus.adminAnything.Utils;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import java.io.InvalidClassException;
import java.lang.reflect.InvocationTargetException;
import java.rmi.AccessException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tab completion for the /aa_fixcommand command.
 * This will only work on Minecraft servers 1.6+
 *
 * @author Martin Ambrus
 */
public class Aa_fixcommand implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender commandSender, Command command, String alias, String[] args) {
        if (2 < args.length) {
            //noinspection ReturnOfNull
            return null;
        }

        // complete a command to redirect
        if (1 == args.length) {
            return Utils.getServerCommandCompletions(args[0], command, alias);
        }

        // complete the Plugin.command combo to redirect to
        if (2 == args.length) {
            // auto-complete plugin name
            List<String> completions = new ArrayList<String>();
            if (!args[1].contains(":") && !args[1].contains(".")) {
                // try to auto-complete with plugin names where this command actually can be found
                try {
                    StringUtil.copyPartialMatches(args[1], AA_API.getCommandContainingPlugins(args[0]), completions);
                } catch (Exception ex) {
                    // if anything bad happens, just auto-complete with a list of all server plugins
                    StringUtil.copyPartialMatches(args[1], AA_API.getServerPlugins(), completions);
                }
                Collections.sort(completions);

                return completions;
            } else {
                String separator  = (args[1].contains(":") ? ":" : ".");
                String pluginName = AA_API.getCleanPluginName(args[1], true);

                // if we chose to fix to a plugin to which this command can be naturally fixed (like /god -> /essentials:god),
                // let's just auto-complete the command name
                try {
                    if (AA_API.getCommandContainingPlugins(args[0]).contains(pluginName)) {
                        completions.add(pluginName + separator + args[0]);
                        return completions;
                    }
                } catch (Exception ex) {
                    // if anything bad happens, just auto-complete with any command from the selected plugin below...
                }

                // if we didn't find the plugin on server, it's probably a typo,
                // so let it be and don't tab-complete
                if (null == pluginName || null == AA_API.getPluginIgnoreCase(pluginName)) {
                    return null;
                }

                // auto-complete any command name from the given plugin
                try {
                    StringUtil.copyPartialMatches(
                        args[1].substring(args[1].indexOf(separator) + 1),
                        AA_API.getPluginCommands(pluginName),
                        completions
                    );
                } catch (final IllegalArgumentException | InvalidClassException | IllegalAccessException | NoSuchMethodException
                    | SecurityException | InvocationTargetException | AccessException e) {
                    commandSender.sendMessage(ChatColor.RED + AA_API.__("error.general-for-chat"));
                    e.printStackTrace();
                    //noinspection ReturnOfNull
                    return null;
                }

                // prepend all these commands by their plugin
                for (int i = 0; i < completions.size(); i++) {
                    completions.set(i, pluginName + separator + completions.get(i));
                }

                // sort and return the result
                Collections.sort(completions);
                return completions;
            }
        }

        //noinspection ReturnOfNull
        return null;
    } // end method

} // end class