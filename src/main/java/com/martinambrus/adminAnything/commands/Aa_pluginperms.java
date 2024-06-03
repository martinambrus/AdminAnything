package com.martinambrus.adminAnything.commands;

import com.martinambrus.adminAnything.AA_API;
import com.martinambrus.adminAnything.Constants;
import com.martinambrus.adminAnything.Utils;
import mkremins.fanciful.FancyMessage;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.rmi.AccessException;
import java.util.*;
import java.util.Map.Entry;

/**
 * Command which shows all permissions
 * for the plugin given.
 *
 * @author Martin Ambrus
 */
public class Aa_pluginperms extends AbstractCommand {

    /**
     * A custom permission description YML config file
     * for plugins that do not store their perms in their
     * plugin.yml file.
     */
    public FileConfiguration permsFromConfig;

    /**
     * Constructor, loads custom permissions configuration.
     */
    public Aa_pluginperms(Plugin aa) {
        permsFromConfig = AA_API.getManualPermDescriptionsConfig();
    }

    /***
     * /aa_pluginperms - displays all permissions for the given plugin
     *
     * @param sender The player who is calling this command.
     * @param cmd The actual command that is being executed.
     * @param unused Name of the command which is being executed.
     * @param args Any arguments passed to this command.
     *
     * @return Returns true if permissions could be show, false otherwise.
     */
    @SuppressWarnings("ConstantConditions")
    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String unused, final String[] args) {
        if (!super.onCommand(sender, cmd, unused, args)) {
            return true;
        }

        //noinspection HardCodedStringLiteral
        if (!AA_API.isFeatureEnabled("pluginperms")) {
            sender.sendMessage(ChatColor.RED + AA_API.__("general.feature-disabled"));
            return true;
        }

        // check for at least one parameter
        if (1 > args.length) {
            sender.sendMessage(
                ChatColor.RED + AA_API.__("commands.pluginperms-no-plugin-name"));
            return false;
        }

        // check if we can actually locate the plugin
        final Plugin plugin = AA_API.getPluginIgnoreCase(args[0]);
        if (null == plugin) {
            sender.sendMessage(ChatColor.RED + AA_API.__("commands.pluginperms-plugin-not-found"));
            return false;
        }

        // prepare plugin name to check for
        final String pluginName = plugin.getName().toLowerCase();

        // pagination
        final double maxPerPage = ((sender instanceof ConsoleCommandSender) ? 100.0 : AA_API.getMaxRecordsPerPage());
        final String intRegex = Constants.INT_REGEX.toString();
        int requestedPage = (((1 < args.length) && args[args.length - 1].matches(intRegex))
                ? Integer.parseInt(args[args.length - 1]) : 1);

        // this one is used to generate next/previous links
        // by using the same output as the input we've been given
        int requestedPageOriginal = requestedPage;

        // no nullpointers for you :P
        if (0 >= requestedPage) {
            requestedPage = 1;
            requestedPageOriginal = requestedPage;
        }

        // prepare the search string against which to compare player's permissions
        final List<String> search = new ArrayList<String>(Arrays.asList(args));

        // remove the first parameter (plugin name)
        search.remove(0);

        // remove pagination, if found
        if (args[args.length - 1].matches(intRegex)) {
            search.remove(search.size() - 1);
        }

        // create a text representation of the search string
        final String searchString = String.join(" ", search);

        // messages are stored temporarily for sorting purposes
        final List<String> messages = new ArrayList<String>();
        final Map<String, String> perms = new HashMap<String, String>();

        // go the plugin.yml route first
        final List<Permission> permList = plugin.getDescription().getPermissions();
        for (final Permission permission : permList) {
            // bail out if we're not searching for this one
            if (null != searchString && !searchString.isEmpty() && !permission.getName().contains(searchString)) {
                continue;
            }

            perms.put(permission.getName(), permission.getDescription());
        }

        // the try to load any additional permissions for commands that were loaded dynamically
        try {
            for (final Entry<String, Command> pair : AA_API.getCommandMapCopy().entrySet()) {
                // only load permissions for the given plugin
                if (!AA_API.getClearCommand(pair.getKey().toLowerCase())[0].equals(pluginName)) {
                    continue;
                }

                // prepare a clear command name, without any colons
                final String clearCommandName = (pair.getKey().contains(":")
                                                 ? pair.getKey().substring(pair.getKey().indexOf(':') + 1)
                                                 : pair.getKey());

                // load permissions from the internal config file and fallback to the getPermission() method if nothing is found
                String perm = null;
                String desc = null;
                final String defaultPerm = pair.getValue().getPermission();
                final List<String> tmpPerms = permsFromConfig
                    .getStringList("manualPermissions." + pluginName + '.' + clearCommandName); //NON-NLS

                if (!tmpPerms.isEmpty()) {
                    for (final String tmpPerm : tmpPerms) {
                        // make sure it's not a custom command description
                        if (!tmpPerm.startsWith("$")) {
                            perm = (tmpPerm.contains("=") ? tmpPerm.substring(0, tmpPerm.indexOf('=')) : tmpPerm);
                            desc = (tmpPerm.contains("=") ?
                                    tmpPerm.substring(tmpPerm.indexOf('=') + 1) : "");
                        }
                    }
                } else if (null != defaultPerm && !defaultPerm.isEmpty()) {
                    perm = defaultPerm;
                    desc = pair.getValue().getDescription();
                }

                // bail out if we're not searching for this one
                if (null != perm && null != searchString && !searchString.isEmpty() && !perm.contains(searchString)) {
                    continue;
                }

                if (null != perm && !perms.containsKey(perm)) {
                    perms.put(perm, desc);
                }
            }
        } catch (final IllegalAccessException | NoSuchMethodException | SecurityException
                | InvocationTargetException | AccessException e) {
            sender.sendMessage(
                ChatColor.RED + AA_API.__("error.general-for-chat"));
            e.printStackTrace();
        }

        // load any permissions from the manual list, if any
        //noinspection HardCodedStringLiteral
        ConfigurationSection cs = permsFromConfig.getConfigurationSection("manualPermissions." + pluginName);
        if (null != cs) {
            Set<String> configKeys = cs.getKeys(false);
            if (!configKeys.isEmpty()) {
                for (String clearCommandName : configKeys) {
                    String perm = null;
                    String desc = null;
                    final List<String> tmpPerms = permsFromConfig
                        .getStringList("manualPermissions." + pluginName + '.' + clearCommandName); //NON-NLS

                    if (!tmpPerms.isEmpty()) {
                        for (final String tmpPerm : tmpPerms) {
                            // make sure it's not a custom command description
                            if (!tmpPerm.startsWith("$")) {
                                perm = (tmpPerm.contains("=") ? tmpPerm.substring(0, tmpPerm.indexOf('=')) : tmpPerm);
                                desc = (tmpPerm.contains("=") ?
                                        tmpPerm.substring(tmpPerm.indexOf('=')) :
                                        "");
                            }
                        }
                    }

                    // bail out if we're not searching for this one
                    if (null != perm && null != searchString && !searchString.isEmpty() && !perm
                        .contains(searchString)) {
                        continue;
                    }

                    if (null != perm && !perms.containsKey(perm)) {
                        perms.put(perm, desc);
                    }
                }
            }
        }

        // sort them alphabetically
        final Map<String, String> sortedPerms = new TreeMap<String, String>(perms);

        // create messages to be sent out
        for (final Entry<String, String> pair : sortedPerms.entrySet()) {
            messages.add("- " + ChatColor.GOLD + pair.getKey() + '\n' + ChatColor.RESET + "    -> " + ChatColor.WHITE
                    + pair.getValue());
        }

        // calculate pagination data
        String pages = String.valueOf(Math.ceil(messages.size() / maxPerPage));
        pages = pages.substring(0, pages.indexOf('.'));
        final int pagesInt = Integer.parseInt(pages);

        if (requestedPage > pagesInt) {
            requestedPage = pagesInt - 1;
        } else {
            requestedPage--;
        }

        // cut messages, so only the ones we requested are shown
        int fromIndex = (int) Math.max(0, requestedPage * maxPerPage);
        final int toIndex = (int) Math.min(messages.size(), (requestedPage + 1) * maxPerPage);

        if (fromIndex >= toIndex) {
            fromIndex = toIndex - 1;
        }

        // start sending
        sender.sendMessage("");

        final FancyMessage topMessage = new FancyMessage("== ").color(ChatColor.WHITE); //NON-NLS

        // left arrow navigation
        Utils.addChatTopNavigation(sender, fromIndex, topMessage, cmd, args, intRegex, requestedPageOriginal, true);

        // header
        topMessage
            .then(AA_API.__("commands.pluginperms-perms-for-plugin", ChatColor.WHITE + args[0]))
            .color(ChatColor.YELLOW);

        topMessage
            .then(" (" + (requestedPage + 1) + ' ' + AA_API.__("general.of") + ' ' + pages + ')') //NON-NLS
            .color(ChatColor.YELLOW);

        // right arrow navigation
        Utils.addChatTopNavigation(sender, fromIndex, topMessage, cmd, args, intRegex, requestedPageOriginal, false);

        topMessage.then(" ==").send(sender); //NON-NLS

        sender.sendMessage("");

        if (!messages.isEmpty()) {
            for (final String msg : messages.subList(fromIndex, toIndex)) {
                sender.sendMessage(msg);
            }
        } else {
            sender.sendMessage(ChatColor.RED + AA_API.__("commands.pluginperms-no-perms"));
        }

        return true;
    } // end method

} // end class