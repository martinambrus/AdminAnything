package com.martinambrus.adminAnything.commands;

import com.martinambrus.adminAnything.AA_API;
import com.martinambrus.adminAnything.Constants;
import mkremins.fanciful.FancyMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.InvalidClassException;
import java.lang.reflect.InvocationTargetException;
import java.rmi.AccessException;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The actual logic behind showing a list of commands
 * to the player or console who initiated this command.
 *
 * @author Martin Ambrus
 */
@SuppressWarnings({"OverlyComplexClass", "ConstantConditions"})
public class Aa_listcommands_runnable implements Runnable {

    /**
     * The player who is calling this command.
     */
    private final CommandSender sender;

    /**
     * Any arguments passed to this command.
     */
    private final String[] args;

    /**
     * The command name (i.e. /aa_listcommands) used to display
     * help pages and the such. This is here, since a player can run
     * either the full /aa_listcommand or the /lc alias, and we want to
     * provide a valid help text in accordance to the initial input.
     */
    private final Command cmd;

    /**
     * Default value determining whether to show command descriptions
     * when this command is run.
     */
    private static boolean showDescriptionsDefault           = true;

    /**
     * Default value determining whether to show command aliases
     * when this command is run.
     */
    private static boolean showAliasesDefault                = false;

    /**
     * Default value determining whether to show command permissions
     * when this command is run.
     */
    private static boolean showPermissionsDefault            = false;

    /**
     * Default value determining whether to show permission descriptions
     * when this command is run.
     */
    private static boolean showPermissionDescriptionsDefault = true;

    /**
     * Default value determining whether to show command usage
     * when this command is run.
     */
    private static boolean showUsageDefault                  = false;

    /**
     * Default value determining whether to display commands in a multiline
     * mode - which is usually used when many of the above defaults are true
     * and there is a lot of info to display for each command.
     */
    private static boolean multilineDefault                  = false;

    /**
     * Pagination.
     */
    private int requestedPage = -1;

    /**
     * This one is used to generate next/previous links
     * by using the same output as the input we've been given
     */
    private int requestedPageOriginal;

    /**
     * Here comes the rest of the filters
     * of which defaults you can see above.
     */
    private boolean showDescriptions = showDescriptionsDefault;
    private boolean moreLines = multilineDefault;
    private boolean showAliases = showAliasesDefault;
    private boolean showPerms = showPermissionsDefault;
    private boolean showPermDescriptions = showPermissionDescriptionsDefault;
    private boolean showUsage = showUsageDefault;

    /**
     * Maximum records to show per page.
     */
    private double maxPerPage = AA_API.getMaxRecordsPerPage();

    /**
     * If we're searching by description, this string will contain
     * what we're searching for.
     */
    private String descriptionSearch = null;

    /**
     * Configuration file handle for the static permission descriptions
     * which are used for popular plugins that do not actually have these
     * descriptions in their plugin.yml file (mostly because they handle their
     * commands dynamically).
     */
    public FileConfiguration permsFromConfig;

    /**
     * Constructor, takes all the parameters needed for this all to work
     * correctly :)
     *
     * @param sender The player who is calling this command.
     * @param args Any arguments passed to this command.
     * @param cmd The command name (i.e. /aa_listcommands, /lc)
     *            used to display help pages and the such.
     */
    @SuppressWarnings("HardCodedStringLiteral")
    Aa_listcommands_runnable(final CommandSender sender, final String[] args, final Command cmd) {
        // load local variables
        this.sender = sender;
        this.args = args;
        this.cmd = cmd;

        // load static permissions descriptions from the yml file
        permsFromConfig = AA_API.getManualPermDescriptionsConfig();

        // load filter defaults
        if (AA_API.configContainsKey("listcommandsDefaults.showdescriptions")) {
            showDescriptionsDefault = AA_API.getConfigBoolean("listcommandsDefaults.showdescriptions");
            showDescriptions = showDescriptionsDefault;
        }

        if (AA_API.configContainsKey("listcommandsDefaults.showaliases")) {
            showAliasesDefault = AA_API.getConfigBoolean("listcommandsDefaults.showaliases");
            showAliases = showAliasesDefault;
        }

        if (AA_API.configContainsKey("listcommandsDefaults.showpermissions")) {
            showPermissionsDefault = AA_API.getConfigBoolean("listcommandsDefaults.showpermissions");
            showPerms = showPermissionsDefault;
        }

        if (AA_API.configContainsKey("listcommandsDefaults.showpermissiondescriptions")) {
            showPermissionDescriptionsDefault = AA_API
                .getConfigBoolean("listcommandsDefaults.showpermissiondescriptions");
            showPermDescriptions = showPermissionDescriptionsDefault;
        }

        if (AA_API.configContainsKey("listcommandsDefaults.showusage")) {
            showUsageDefault = AA_API.getConfigBoolean("listcommandsDefaults.showusage");
            showUsage = showUsageDefault;
        }

        if (AA_API.configContainsKey("listcommandsDefaults.multiline")) {
            multilineDefault = AA_API.getConfigBoolean("listcommandsDefaults.multiline");
            moreLines = multilineDefault;
        }
    } // end method

    /**
     * Returns current command arguments with the filter
     * given in this method's parameter replaced
     * by the given value.
     *
     * For example, if we get this input: "/lc perms:yes aliases:no",
     * we can use this method to make it: "/lc perms:no aliases:yes" like this:
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * String.join(" ", this.replaceParam(this.args, new String[] { "perm", "perms", "permission", "permissions" }, "perms:no"));
     * </pre>
     *
     * @param args The original list of arguments for /aa_listcommands.
     * @param toReplace The actual list of all possible variations for the filter we want to replace.
     * @param replaceWith The new value to replace this filer with.
     *
     * @return Returns a list of parameters with the requested filter replaced by the requested value.
     */
    private Collection<String> replaceParam(final String[] args, final String[] toReplace, final String replaceWith) {
        final Collection<String> newArgs = new ArrayList<String>();

        // iterate over all arguments
        for (final String arg : args) {
            boolean doesContain = false;
            for (final String replaceFind : toReplace) {
                if (arg.contains(replaceFind + ':')) {
                    doesContain = true;
                    break;
                }
            }

            // if this arguments is not in the list of those
            // to be removed and replaced, use it
            if (!doesContain) {
                newArgs.add(arg);
            }
        }

        // now add the new filter value
        newArgs.add(replaceWith);

        return newArgs;
    } // end method

    /**
     * Checks for any filters given for this command
     * and saves them for futher use.
     *
     * @param includedPlugins Pointer to a list of plugins to only include in the listing.
     * @param excludedPlugins Pointer to a list of plugins to not include in the listing.
     *
     * @return Returns true if all filters given were correct,
     *         false with an error message sent to the sender otherwise.
     */
    @SuppressWarnings("HardCodedStringLiteral")
    private boolean checkAndSetFilters(final Map<String, Boolean> includedPlugins, final Map<String, Boolean> excludedPlugins) {
        // first of all, check that we don't have a search argument in place
        // and if we do and it contains quotes, build a single argument out
        // of that input with spaces replaced by ^^, so we can easily split them later
        String[]     newArgs;
        final String flatArgs = String.join(" ", args);
        //noinspection HardCodedStringLiteral
        if (flatArgs.contains(AA_API.__("general.search") + ":\"")) {
            final Pattern      p = Pattern.compile(AA_API.__("general.search") + ":\"([^\"]+)\"");
            final Matcher m = p.matcher(flatArgs);
            final StringBuffer s = new StringBuffer();
            if (m.find()) {
                //System.out.println(m.group(1));
                m.appendReplacement(
                    s,
                    AA_API.__("general.search") +
                        ":\"" + m.group(1).replace(" ", "^^") + //NON-NLS
                        '"'
                );
            }

            // replace original arguments
            newArgs = s.toString().split(Pattern.quote(" "));
        } else {
            newArgs = args;
        }

        // iterate over all arguments and prepare filters
        for (String arg : newArgs) {
            if (!arg.contains(":")) {
                // check for page number
                if (!arg.matches(Constants.INT_REGEX.toString())) {
                    // invalid parameter
                    sender.sendMessage(ChatColor.RED + AA_API.__("commands.listcommands-invalid-parameter", arg));
                    sender.sendMessage(
                        ChatColor.YELLOW + AA_API.__("commands.listcommands-more-info"));
                    return false;
                } else {
                    // don't override the page parameter itself
                    if (-1 < requestedPage) {
                        continue;
                    }
                    arg = AA_API.__("general.page") + ':' + arg;
                }
            }

            final String[] spl = arg.split(Pattern.quote(":")); //NON-NLS
            if (2 > spl.length) {
                // invalid parameters value
                sender.sendMessage(
                    ChatColor.RED + AA_API.__("commands.listcommands-empty-parameter") +
                        ": " + arg //NON-NLS
                );
                return false;
            }

            // handle parameter according to its name
            //noinspection HardCodedStringLiteral
            if ("pg".equals(spl[0]) || spl[0].equals(AA_API.__("general.page"))) {
                /*
                 Pagination filter.
                */

                requestedPage = (spl[1].matches(Constants.INT_REGEX.toString()) ? Integer.parseInt(spl[1]) : 1);
                requestedPageOriginal = requestedPage;
            } else if ("pl".equals(spl[0]) || "plug".equals(spl[0]) || spl[0]
                .equals(AA_API.__("general.plugin"))) { //NON-NLS
                /*
                  Plugin name(s) filter.
                */

                String parsedPluginName;
                String lowerParsedPluginName;

                // single plugin to include/exclude
                if (!spl[1].contains(",")) {
                    if (spl[1].startsWith("-")) {
                        parsedPluginName = spl[1].substring(1);
                        excludedPlugins.put(parsedPluginName.toLowerCase(), true);
                    } else {
                        parsedPluginName = spl[1];
                        includedPlugins.put(parsedPluginName.toLowerCase(), true);
                    }

                    lowerParsedPluginName = parsedPluginName.toLowerCase();

                    // validate
                    //noinspection HardCodedStringLiteral
                    if (!lowerParsedPluginName.startsWith("core") && !lowerParsedPluginName.startsWith("bukkit")
                        && !lowerParsedPluginName.startsWith("spigot")
                        && !lowerParsedPluginName.startsWith("minecraft")
                        && (null == AA_API.getPluginIgnoreCase(parsedPluginName))) {
                        sender.sendMessage(
                            ChatColor.RED + AA_API.__("commands.listcommands-plugin-not-found") + ": " //NON-NLS
                                + ChatColor.GREEN + spl[1]);
                        return false;
                    }

                    if (includedPlugins.containsKey(lowerParsedPluginName)
                        && excludedPlugins.containsKey(lowerParsedPluginName)) {
                        sender.sendMessage(
                            ChatColor.RED + AA_API
                                .__("commands.listcommands-cannot-include-and-exlude-same-plugin") + ": " //NON-NLS
                                + ChatColor.GREEN + spl[1]);
                        return false;
                    }
                } else {
                    // multiple plugins to include/exclude
                    final String[] subPlugins = spl[1].replace(", ", ",").split(Pattern.quote(","));
                    for (final String subplug : subPlugins) {
                        if (subplug.startsWith("-")) {
                            parsedPluginName = subplug.substring(1);
                            excludedPlugins.put(parsedPluginName.toLowerCase(), true);
                        } else {
                            parsedPluginName = subplug;
                            includedPlugins.put(parsedPluginName.toLowerCase(), true);
                        }

                        lowerParsedPluginName = parsedPluginName.toLowerCase();

                        // validate
                        if (!lowerParsedPluginName.startsWith("core") //NON-NLS
                            && !lowerParsedPluginName.startsWith("bukkit") //NON-NLS
                            && !lowerParsedPluginName.startsWith("spigot") //NON-NLS
                            && !lowerParsedPluginName.startsWith("minecraft") //NON-NLS
                            && (null == AA_API.getPluginIgnoreCase(parsedPluginName))) {
                            sender.sendMessage(
                                ChatColor.RED + AA_API.__("commands.listcommands-plugin-not-found") + ": " //NON-NLS
                                    + ChatColor.GREEN + spl[1]);
                            return false;
                        }

                        if (includedPlugins.containsKey(lowerParsedPluginName)
                            && excludedPlugins.containsKey(lowerParsedPluginName)) {
                            sender.sendMessage(ChatColor.RED
                                + AA_API
                                .__("commands.listcommands-cannot-include-and-exlude-same-plugin") + ": " //NON-NLS
                                + ChatColor.GREEN + spl[1]);
                            return false;
                        }
                    }
                }
            } else if (
                "desc".equals(spl[0]) || //NON-NLS
                    spl[0].equals(AA_API.__("general.description")) ||
                    spl[0].equals(AA_API.__("commands.listcommands-showdescription")) ||
                    spl[0].equals(AA_API.__("commands.listcommands-showdescriptions"))
                ) {
                /*
                 Show plugin descriptions filter.
                */

                //noinspection HardCodedStringLiteral
                showDescriptions = !AA_API.__("general.no").equalsIgnoreCase(spl[1]) && !"false"
                    .equalsIgnoreCase(spl[1])
                    && !"n".equalsIgnoreCase(spl[1]);
            } else if (
                "al".equals(spl[0]) || //NON-NLS
                    spl[0].equals(AA_API.__("general.aliases")) ||
                    spl[0].equals(AA_API.__("commands.listcommands-showaliases"))
                ) {
                /*
                 Show plugin aliases filter.
                */

                //noinspection HardCodedStringLiteral
                showAliases = AA_API.__("general.yes").equalsIgnoreCase(spl[1]) || "true".equalsIgnoreCase(spl[1])
                    || "y".equalsIgnoreCase(spl[1]);
            } else //noinspection HardCodedStringLiteral
                if (
                    "perm".equals(spl[0]) ||
                        "perms".equals(spl[0]) ||
                        spl[0].equals(AA_API.__("general.permission")) ||
                        spl[0].equals(AA_API.__("general.permissions"))
                    ) {
                /*
                 Show permissions filter.
                */

                    //noinspection HardCodedStringLiteral
                    showPerms = AA_API.__("general.yes").equalsIgnoreCase(spl[1]) || "true".equalsIgnoreCase(spl[1])
                        || "y".equalsIgnoreCase(spl[1]);
                } else //noinspection HardCodedStringLiteral
                    if (
                        "permdesc".equals(spl[0]) ||
                            spl[0].equals(AA_API.__("commands.listcommands-permissiondescriptions")) ||
                            spl[0].equals(AA_API.__("commands.listcommands-permissionsdescriptions"))
                        ) {
                /*
                 Show permission descriptions filter.
                */

                        //noinspection HardCodedStringLiteral
                        showPermDescriptions = !AA_API.__("general.no").equalsIgnoreCase(spl[1]) && !"false"
                            .equalsIgnoreCase(spl[1])
                            && !"n".equalsIgnoreCase(spl[1]);
                    } else //noinspection HardCodedStringLiteral
                        if (
                            "usg".equals(spl[0]) ||
                                spl[0].equals(AA_API.__("general.usage")) ||
                                spl[0].equals(AA_API.__("commands.listcommands-showusage"))
                            ) {
                /*
                 Show commands usage filter.
                */

                            //noinspection HardCodedStringLiteral
                            showUsage = AA_API.__("general.yes").equalsIgnoreCase(spl[1]) || "true"
                                .equalsIgnoreCase(spl[1])
                                || "y".equalsIgnoreCase(spl[1]);
                        } else if (spl[0].equals(AA_API.__("general.search"))) {
                /*
                 Search in description filter.
                */

                            descriptionSearch = spl[1].replace("^^", " ").replace("\"", "");
                        } else if (spl[0].equals(AA_API.__("commands.listcommands-multiline"))) {
                /*
                 Activate multiline output filter.
                */

                            //noinspection HardCodedStringLiteral
                            if (AA_API.__("general.yes").equalsIgnoreCase(spl[1]) || "true".equalsIgnoreCase(spl[1])
                                || "y".equalsIgnoreCase(spl[1])) {
                                // adjust maximum records per page, since we're going into multiple lines now
                                maxPerPage = ((sender instanceof ConsoleCommandSender) ? 50.0 : maxPerPage);
                                moreLines = true;
                            } else {
                                moreLines = false;
                            }
                        } else {
                            //noinspection HardCodedStringLiteral
                            sender.sendMessage(ChatColor.RED + AA_API
                                .__("commands.listcommands-unrecognized-parameter") + ": " + arg);
                            sender.sendMessage(
                                ChatColor.YELLOW + AA_API.__("commands.listcommands-more-info"));
                            return false;
            }
        }

        return true;
    } // end method

    /**
     * Fills-in the messages map given to this method
     * which will in turn be used to show to the player/console.
     *
     * @param messages The actual map of messages to return the the command called.
     * @param includedPlugins Pointer to a list of plugins to only include in the listing.
     * @param excludedPlugins Pointer to a list of plugins to not include in the listing.
     * @param doneCommandIDs De-duplication map pointer.
     */
    @SuppressWarnings({"NonConstantStringShouldBeStringBuffer", "ConstantConditions", "UnusedAssignment"})
    private void prepareMessages(final Map<String, FancyMessage> messages,
                                 final Map<String, Boolean> includedPlugins,
                                 final Map<String, Boolean> excludedPlugins,
                                 final Map<Integer, Boolean> doneCommandIDs) {
        // iterate over all loaded commands from the commandMap
        // and load their names, plugins and aliases
        try {
            // if we're using "permdesc" or "usage" filter but not "multiline" one, show a warning
            if (!moreLines && (showPermDescriptions || showUsage)) {
                sender.sendMessage(
                    ChatColor.RED + AA_API.__( "error.command-listing-multiline-required-1" )
                    + ChatColor.WHITE + " /aa_listcommands " + ChatColor.RED
                    + AA_API.__( "error.command-listing-multiline-required-2" )
                );
                Thread.currentThread().sleep(2500);
            }

            for (final Entry<String, Command> pair : AA_API.getAugmentedCommandMap().entrySet()) {
                String key = pair.getKey();
                final String pluginName = AA_API.getPluginForCommand(pair.getKey(), pair.getValue());
                String pluginCorePrefix = null;

                // used when we're showing usage descriptions
                // and any of the descriptions are actually multiline
                // ... this variable is used to split such descriptions
                //     into smaller pieces and add them to messages
                //     as separate items to prevent going over the maximum
                //     number of records per page and flooding the chat
                int descCounter = 1;

                // if the above variable is used, we'll have multiple keys
                // with the same command name in messages, so we use this variable
                // to actually make them distinct
                int keyAutoIncrementCounter = 0;

                // strip out the initial colon from commands that start on one (like :ping)
                if (key.startsWith(":")) {
                    key = key.substring(1);
                }

                // if we can't determine plugin name for a command in the CommandMap,
                // let's bail out and let people know.
                if (null == pluginName) {
                    sender.sendMessage(ChatColor.RED + AA_API.__("error.general-for-chat"));
                    Bukkit.getLogger().severe('[' + AA_API.getAaName()
                        + "] " + AA_API.__("plugin.error-plugin-for-command-not-found") + ": " + pair.getKey());
                    return;
                }

                final String lowerCasePluginName = pluginName.toLowerCase();

                // if we have a core command, let's save where it comes from,
                // so we can display it correctly in the listing
                //noinspection HardCodedStringLiteral
                if (("core".equals(lowerCasePluginName) || "minecraft".equals(lowerCasePluginName)
                        || "bukkit".equals(lowerCasePluginName) || "spigot".equals(lowerCasePluginName))) {
                    pluginCorePrefix = (key.contains(":") ? key.substring(0, key.indexOf(':')) : null);
                }

                // check if we only requested specific plugin's commands
                //noinspection HardCodedStringLiteral
                if (
                        // don't include commands from excluded plugins
                        excludedPlugins.containsKey(lowerCasePluginName) || (
                                // included plugins filter does not contain our current plugin
                                !includedPlugins.isEmpty() && !includedPlugins.containsKey(lowerCasePluginName)
                                // we're not searching for "core" plugins or we are but this one doesn't start on any of the right prefixes
                                && !(includedPlugins.containsKey("core") && (lowerCasePluginName.startsWith("core")
                                        || lowerCasePluginName.startsWith("bukkit") || lowerCasePluginName.startsWith("spigot")
                                        || lowerCasePluginName.startsWith("minecraft"))))) {
                    // not a command from the plugin we requested, bail out
                    continue;
                }

                // prepare a clear, unprefixed command name version
                final String clearCommandName = (pair.getKey().contains(":")
                                                 ? pair.getKey().substring(pair.getKey().indexOf(':') + 1)
                                                 : pair.getKey());

                // check if we're not finding only a certain text in descriptions
                if (null != descriptionSearch) {
                    final String desc = pair.getValue().getDescription();
                    if (descriptionSearch.startsWith("//")) {
                        descriptionSearch = descriptionSearch.substring(1);
                    }

                    // did we use a wildcard?
                    if (descriptionSearch.contains("*")) {
                        boolean      matchFound = false;
                        final String unstarred  = descriptionSearch.replace("*", "");

                        if (descriptionSearch.startsWith("*")) {
                            final String lowerCaseDesc = desc.toLowerCase();
                            final String lowerCaseUnstarred = unstarred.toLowerCase();
                            if (clearCommandName.toLowerCase().endsWith(unstarred)
                                || lowerCaseDesc.contains(lowerCaseUnstarred + ' ')
                                    || lowerCaseDesc.endsWith(lowerCaseUnstarred)) {
                                matchFound = true;
                            }
                        }

                        if (descriptionSearch.endsWith("*")) {
                            final String lowerCaseDesc = desc.toLowerCase();
                            final String lowerCaseUnstarred = unstarred.toLowerCase();
                            if (clearCommandName.toLowerCase().startsWith(unstarred)
                                || lowerCaseDesc.contains(' ' + lowerCaseUnstarred)
                                    || lowerCaseDesc.startsWith(lowerCaseUnstarred)) {
                                matchFound = true;
                            }
                        }

                        if (!matchFound) {
                            continue;
                        }
                    } else {
                        if (!clearCommandName.toLowerCase().contains(descriptionSearch)
                            && !desc.contains(descriptionSearch)) {
                            continue;
                        }
                    }
                }

                // prepare real command name which can be executed for the output clickable action text
                final String commandName = '/' + clearCommandName;
                FancyMessage out = new FancyMessage(commandName).color(ChatColor.GREEN)
                                                                      .command("/aa_actions " + clearCommandName) //NON-NLS
                                                                      .tooltip(ChatColor.GREEN + "/" + clearCommandName
                                                                          + ChatColor.RESET + " - " + //NON-NLS
                                                                          AA_API
                                                                              .__("commands.listcommands-show-available-actions"));

                // core commands get special treatment
                if ((includedPlugins.size() != 1 || (null != pluginCorePrefix))
                    && !moreLines) {
                    //noinspection HardCodedStringLiteral
                    out.then(AA_API.getConfigString("listCommandsSeparator", " ") + '[' + ((null != pluginCorePrefix) ?
                                    AA_API.__("general.core") + " - " + pluginCorePrefix + ':' + clearCommandName
                                                               : pluginName) + "]")
                       .color(ChatColor.GRAY)
                       .command("/aa_listcommands pl:" + pluginName + (showDescriptions ?
                                                                       " desc:" + AA_API.__("general.yes") : "")
                           + (showAliases ? " al:" + AA_API.__("general.yes") : "") + (showPerms ? " perm:" + AA_API
                           .__("general.yes") : "")
                           + (moreLines && showPermDescriptions ? " permdesc:" + AA_API.__("general.yes") : "")
                           + (moreLines && showUsage ? " usg:" + AA_API.__("general.yes") : "")
                           + (moreLines ?
                              ' ' + AA_API.__("commands.listcommands-multiline") + ':' + AA_API.__("general.yes") : ""))
                       .tooltip(AA_API
                           .__("commands.listcommands-show-commands-for") + ' ' + ChatColor.AQUA + pluginName);
                }

                // adjust aliases listing
                final List<String> aliases = pair.getValue().getAliases();
                // check that this is indeed a command and not an alias and that it's not been added to the listing yet
                if (doneCommandIDs.containsKey(pair.getValue().hashCode())
                    || ((null != aliases) && aliases.contains(clearCommandName))) {
                    // alias found or command already added, let's bail out here or we'd duplicate this command's listing
                    continue;
                } else {

                    // mark this command as done, so we don't duplicate it
                    // ... this is because the commandMap contains both versions of the command,
                    //     one without the plugin prefix and one with it (i.e. /essentials:repair AND /repair)
                    doneCommandIDs.put(pair.getValue().hashCode(), true);
                }

                // add description
                if (showDescriptions) {
                    out.then(AA_API.getConfigString("listCommandsSeparator", " ") + pair.getValue().getDescription());
                }

                // add plug-in
                if (moreLines) {
                    //noinspection HardCodedStringLiteral
                    out.then("\n-> ").color(ChatColor.WHITE).then(AA_API.__("general.plugin") + ": ")
                       .color(ChatColor.GRAY)
                       .then('[' + pluginName
                           + ((null != pluginCorePrefix) ? " - " + pluginCorePrefix + ':' + clearCommandName
                                                         : "")
                           + ']')
                       .color(ChatColor.GRAY)
                       .command("/aa_listcommands pl:" + pluginName + (showDescriptions ?
                                                                       " desc:" + AA_API.__("general.yes") : "")
                           + (showAliases ? " al:" + AA_API.__("general.yes") : "") + (showPerms ? " perm:" + AA_API
                           .__("general.yes") : "")
                           + (moreLines && showPermDescriptions ? " permdesc:" + AA_API.__("general.yes") : "")
                           + (moreLines && showUsage ? " usg:" + AA_API.__("general.yes") : "")
                           + (moreLines ?
                              ' ' + AA_API.__("commands.listcommands-multiline") + ':' + AA_API.__("general.yes") : ""))
                       .tooltip(AA_API
                           .__("commands.listcommands-show-commands-for") + ' ' + ChatColor.AQUA + pluginName);
                }

                // add usage
                if (showUsage && moreLines) {
                    //noinspection HardCodedStringLiteral
                    String[] usageLines = pair.getValue().getUsage().replace("<command>", clearCommandName).split(Pattern.quote("\n"));

                    //noinspection HardCodedStringLiteral
                    out.then("\n-> ").color(ChatColor.WHITE)
                       .then(AA_API.__("general.usage") + ": " + usageLines[0]);

                    // if the usage is multiline (such as is the case of PermissionsEX usage description),
                    // we need to split it and hack it into separate message buffers, otherwise we could go
                    // beyond what we have set as maximum records per page
                    if (1 < usageLines.length) {
                        messages.put(clearCommandName + pair.getKey() + keyAutoIncrementCounter++, out);
                        out = new FancyMessage("");

                        // create next message up until the maximum records per page
                        // minus a few lines left for header
                        for (String usageLine : usageLines) {
                            out.then('\n' + usageLine);

                            // if we reached maximum number of records per page (minus a few lines),
                            // let's start a new message
                            if (descCounter++ > maxPerPage - 5) {
                                messages.put(clearCommandName + pair.getKey() + keyAutoIncrementCounter++, out);
                                out = new FancyMessage("");
                                descCounter = 0;
                            }
                        }

                        messages.put(clearCommandName + pair.getKey() + keyAutoIncrementCounter++, out);
                        out = new FancyMessage("");
                    }
                }

                // add aliases
                if (showAliases && ((null != aliases) && !aliases.isEmpty())) {
                    if (moreLines) {
                        out.then("\n-> ").color(ChatColor.WHITE).then(AA_API.__("general.aliases") + ':')
                           .color(ChatColor.DARK_PURPLE);
                    }

                    out.then(AA_API.getConfigString("listCommandsSeparator", " ") + "[").color(ChatColor.WHITE);

                    // iterate over all aliases for the current command
                    for (int i = 0; i < (aliases.size() - 1); i++) {
                        final String alias = aliases.get(i);
                        if (!alias.equals(clearCommandName)) {
                            //noinspection HardCodedStringLiteral
                            out.then('/' + alias + "; ").color(ChatColor.AQUA).command("/aa_actions " + alias)
                               .formattedTooltip(new FancyMessage(ChatColor.AQUA + "/" + alias + ChatColor.RESET
                                   + " - " + AA_API.__("commands.listcommands-show-available-actions")));
                        }

                    }

                    // add the last alias
                    final String alias = aliases.get(aliases.size() - 1);
                    if (!alias.equals(clearCommandName)) {
                        //noinspection HardCodedStringLiteral
                        out.then('/' + alias).color(ChatColor.AQUA).command("/aa_actions " + alias)
                           .formattedTooltip(new FancyMessage(
                               ChatColor.AQUA + "/" + alias + ChatColor.RESET + " - " + AA_API
                                   .__("commands.listcommands-show-available-actions")));
                    }

                    out.then("]").color(ChatColor.WHITE);
                }

                // add permissions
                if (showPerms) {
                    // load permissions from the internal config file and fallback to the getPermission() method if nothing is found
                    String perm = null;
                    final String defaultPerm = pair.getValue().getPermission();
                    //noinspection HardCodedStringLiteral
                    final List<String> tmpPerms = permsFromConfig
                        .getStringList("manualPermissions." + lowerCasePluginName + '.' + clearCommandName);

                    if (moreLines) {
                        if (null != tmpPerms && !tmpPerms.isEmpty()) {
                            perm = ChatColor.WHITE.toString();
                            for (final String tmpPerm : tmpPerms) {
                                // make sure it's not a custom command description
                                if (!tmpPerm.startsWith("$")) {
                                    perm = showPermDescriptions ? perm + "\n   --> " + ChatColor.YELLOW
                                        + tmpPerm.replace("=", " = " + ChatColor.WHITE) :
                                           perm + "\n   --> " + (tmpPerm.contains("=")
                                                                 ? tmpPerm.substring(0, tmpPerm.indexOf('=')) :
                                                                 tmpPerm);
                                }
                            }
                        } else if (null != defaultPerm && !defaultPerm.isEmpty()) {
                            perm = ChatColor.WHITE + "[" + ChatColor.YELLOW + defaultPerm + ChatColor.WHITE + ']';
                        }
                    } else {
                        if (null != tmpPerms && !tmpPerms.isEmpty()) {
                            StringBuilder permBuilder = new StringBuilder(ChatColor.WHITE + AA_API.getConfigString("listCommandsSeparator", " ") + "[" + ChatColor.YELLOW);
                            for (final String tmpPerm : tmpPerms) {
                                permBuilder.append(
                                    tmpPerm.contains("=") ? tmpPerm.substring(0, tmpPerm.indexOf('=')) : tmpPerm)
                                           .append(", ");
                            }
                            perm = permBuilder.toString();
                            perm = perm.substring(0, perm.length() - 2) + ChatColor.WHITE + ']';
                        } else if (null != defaultPerm && !defaultPerm.isEmpty()) {
                            perm = ChatColor.WHITE + AA_API.getConfigString("listCommandsSeparator", " ") + "[" + ChatColor.YELLOW + defaultPerm + ChatColor.WHITE + ']';
                        }
                    }

                    //noinspection HardCodedStringLiteral
                    out.then((null != perm && perm.isEmpty() ? "" :
                              (moreLines ?
                               "\n-> " + ChatColor.YELLOW + AA_API.__("general.permissions") + ": " + ChatColor.WHITE : "")
                                  + ChatColor.YELLOW + (null == perm ? " " + AA_API.__("commands.listcommands-no-permissions") : perm) + "" + ChatColor.WHITE));
                }

                // if command is fixed (overridden), let people know
                //noinspection HardCodedStringLiteral
                if (AA_API.isFeatureEnabled("fixcommand") && AA_API.getCommandsList("overrides").contains(clearCommandName)) {
                    //noinspection HardCodedStringLiteral
                    String overrideValue = AA_API.getCommandsConfigurationValue("overrides", clearCommandName);
                    overrideValue = overrideValue.substring(0, overrideValue.indexOf(':'));

                    if (!overrideValue.equals(pluginName) && !overrideValue.equals(pluginCorePrefix)) {
                        out.then(
                            " "
                                + ChatColor.RED
                                + AA_API.__(
                                "commands.listcommands-command-fixed-use-instead",
                                ChatColor.WHITE,
                                ChatColor.YELLOW
                                    + ("core".equals(lowerCasePluginName) ? pluginCorePrefix : //NON-NLS
                                       lowerCasePluginName) + ':' //NON-NLS
                                    + ChatColor.GREEN + clearCommandName + ChatColor.WHITE)
                        );
                    }
                }

                if (moreLines) {
                    out.then("\n ");
                }

                messages.put(clearCommandName + pair.getKey() + keyAutoIncrementCounter++, out);
            }
        } catch (final IllegalArgumentException | InvalidClassException | IllegalAccessException | NoSuchMethodException | SecurityException | InvocationTargetException | AccessException | InterruptedException e) {
            sender.sendMessage(ChatColor.RED + AA_API.__("error.general-for-chat"));
            e.printStackTrace();
        }

    } // end method

    /**
     * The actual logic behind listing server commands.
     */
    @SuppressWarnings({"ConstantConditions", "HardCodedStringLiteral"})
    @Override
    public void run() {
        // console gets to show a lot more lines than chat
        // due to chat's space restrictions
        maxPerPage = ((sender instanceof ConsoleCommandSender) ? 100.0 : AA_API.getMaxRecordsPerPage());

        // the actual map of messages that will be outputted to the command caller
        final Map<String, FancyMessage> messages = new HashMap<String, FancyMessage>();

        // de-duplication variable
        final Map<Integer, Boolean> doneCommandIDs = new HashMap<Integer, Boolean>();

        // if a filter to include only certain plugins is used, this map will contain their names
        final Map<String, Boolean> includedPlugins = new HashMap<String, Boolean>();

        // if a filter to exclude certain plugins is used, this map will contain their names
        final Map<String, Boolean> excludedPlugins = new HashMap<String, Boolean>();

        // check and parse filters
        if (0 < args.length) {
            if (!checkAndSetFilters(includedPlugins, excludedPlugins)) {
                return;
            }
        }

        // no nullpointers for you :P
        if (0 >= requestedPage) {
            requestedPage = 1;
            requestedPageOriginal = requestedPage;
        }

        // prepare messages to show to the command caller
        prepareMessages(messages, includedPlugins, excludedPlugins, doneCommandIDs);

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
        int       fromIndex = (int) Math.max(0, requestedPage * maxPerPage);
        final int toIndex   = (int) Math.min(messages.size(), (requestedPage + 1) * maxPerPage);

        // don't go beyond the maximum messages we have
        if (fromIndex >= toIndex) {
            fromIndex = toIndex - 1;
        }

        sender.sendMessage("");

        // prepare header
        final FancyMessage topMessage = new FancyMessage("== ").color(ChatColor.WHITE);

        // navigation left arrows
        if (sender instanceof Player) {
            if (fromIndex > 1) {
                topMessage.then(AA_API.__("general.previous-character") + " ").color(ChatColor.AQUA);
            }

            final String             newCmd  = '/' + cmd.getName() + ' ';
            final Collection<String> newArgs = new ArrayList<String>();

            for (final String arg : args) {
                //noinspection HardCodedStringLiteral
                if (arg.matches(Constants.INT_REGEX.toString()) || arg.contains(AA_API.__("general.page") + ':') || arg
                    .contains("pg:")) {
                    continue;
                } else {
                    newArgs.add(arg);
                }
            }
            newArgs.add(String.valueOf(requestedPageOriginal - 1));

            topMessage.command(newCmd + String.join(" ", newArgs))
                      .tooltip(
                          AA_API.__("chat.navigation-show-next-prev-page",
                              AA_API.__("chat.navigation-previous"),
                              AA_API.__("general.page")
                          )
                      );
        }

        // title
        topMessage.then(AA_API.__("commands.listcommands-commands")).color(ChatColor.YELLOW);

        if (!includedPlugins.isEmpty()) {
            topMessage.then(' ' + AA_API.__("general.from") + ' ' + String.join(", ", includedPlugins.keySet()))
                      .color(ChatColor.YELLOW);
        }

        if (!excludedPlugins.isEmpty()) {
            topMessage.then(", " + AA_API.__("general.except") + ' ' + String.join(", ", excludedPlugins.keySet()))
                      .color(ChatColor.YELLOW);
        }

        topMessage.then(" (" + (requestedPage + 1) + ' ' + AA_API.__("general.of") + ' ' + pages + ')')
                  .color(ChatColor.YELLOW);

        // navigation right arrows
        if ((sender instanceof Player) && (toIndex < messages.size())) {
            final String             newCmd  = '/' + cmd.getName() + ' ';
            final Collection<String> newArgs = new ArrayList<String>();

            for (final String arg : args) {

                //noinspection HardCodedStringLiteral
                if (arg.matches(Constants.INT_REGEX.toString()) || arg.contains(AA_API.__("general.page") + ':') || arg
                    .contains("pg:")) {
                    continue;
                } else {
                    newArgs.add(arg);
                }
            }
            newArgs.add(String.valueOf(requestedPageOriginal + 1));

            topMessage.then(" " + AA_API.__("general.next-character")).color(ChatColor.AQUA).command(newCmd + String.join(" ", newArgs))
                      .tooltip(
                          AA_API.__("chat.navigation-show-next-prev-page",
                              AA_API.__("chat.navigation-next"),
                              AA_API.__("general.page")
                          )
                      );
        }

        topMessage.then(" ==").send(sender);

        // show filters
        final FancyMessage filtersMessage = new FancyMessage("");

        if (showDescriptions) {
            filtersMessage
                .then(' ' + AA_API.__("commands.listcommands-desc"))
                .color(ChatColor.GREEN)
                .tooltip(AA_API.__("commands.listcommands-hide-descriptions"))
                .command('/' + cmd.getName() + ' '
                        + String.join(" ", replaceParam(args,
                    new String[]{
                        "desc", //NON-NLS
                        AA_API.__("general.description"),
                        AA_API.__("commands.listcommands-showdescription"),
                        AA_API.__("commands.listcommands-showdescriptions")
                    },
                    "desc:" + AA_API.__("general.no")) //NON-NLSy
                    )
                );
        } else {
            filtersMessage
                .then(' ' + AA_API.__("commands.listcommands-desc")) //NON-NLS
                .color(ChatColor.RED)
                .tooltip(AA_API.__("commands.listcommands-show-descriptions"))
                .command('/' + cmd.getName() + ' '
                        + String.join(" ", replaceParam(args,
                    new String[]{
                        "desc", //NON-NLS
                        AA_API.__("general.description"),
                        AA_API.__("commands.listcommands-showdescription"),
                        AA_API.__("commands.listcommands-showdescriptions")
                    },
                    "desc:" + AA_API.__("general.yes")) //NON-NLS
                    )
                );
        }

        if (showAliases) {
            filtersMessage
                .then(' ' + AA_API.__("commands.listcommands-alias")) //NON-NLS
                .color(ChatColor.GREEN)
                .tooltip(AA_API.__("commands.listcommands-hide-aliases"))
                .command('/' + cmd.getName() + ' '
                        + String.join(" ", replaceParam(args,
                    new String[]{
                        "al", //NON-NLS
                        AA_API.__("general.aliases"),
                        AA_API.__("commands.listcommands-showaliases")
                    },
                    "al:" + AA_API.__("general.no")) //NON-NLS
                    )
                );
        } else {
            filtersMessage
                .then(' ' + AA_API.__("commands.listcommands-alias")) //NON-NLS
                .color(ChatColor.RED)
                .tooltip(AA_API.__("commands.listcommands-show-aliases"))
                .command('/' + cmd.getName() + ' '
                        + String.join(" ", replaceParam(args,
                    new String[]{
                        "al", //NON-NLS
                        AA_API.__("general.aliases"),
                        AA_API.__("commands.listcommands-showaliases")
                    },
                    "al:" + AA_API.__("general.yes")) //NON-NLS
                    )
                );
        }

        if (showPerms) {
            filtersMessage
                .then(' ' + AA_API.__("commands.listcommands-perms")) //NON-NLS
                .color(ChatColor.GREEN)
                .tooltip(AA_API.__("commands.listcommands-hide-perms"))
                .command('/' + cmd.getName() + ' '
                        + String.join(" ", replaceParam(args,
                    new String[]{
                        "perm", //NON-NLS
                        "perms", //NON-NLS
                        AA_API.__("general.permission"),
                        AA_API.__("general.permissions")
                    },
                    "perm:" + AA_API.__("general.no")) //NON-NLS
                    )
                );
        } else {
            filtersMessage
                .then(' ' + AA_API.__("commands.listcommands-perms"))
                .color(ChatColor.RED)
                .tooltip(AA_API.__("commands.listcommands-show-perms"))
                .command('/' + cmd.getName() + ' '
                        + String.join(" ", replaceParam(args,
                    new String[]{
                        "perm", //NON-NLS
                        "perms", //NON-NLS
                        AA_API.__("general.permission"),
                        AA_API.__("general.permissions")
                    },
                    "perm:" + AA_API.__("general.yes"))
                    )
                );
        }

        if (moreLines) {
            if (showPermDescriptions) {
                filtersMessage
                    .then(' ' + AA_API.__("commands.listcommands-permdesc")) //NON-NLS
                    .color(ChatColor.GREEN)
                    .tooltip(AA_API.__("commands.listcommands-hide-permdescriptions"))
                    .command('/' + cmd.getName() + ' ' +
                        String.join(" ", replaceParam(args,
                            new String[]{
                                "permdesc", //NON-NLS
                                AA_API.__("commands.listcommands-permissiondescriptions"),
                                AA_API.__("commands.listcommands-permissionsdescriptions")
                            },
                            "permdesc:" + AA_API.__("general.no")) //NON-NLS
                        )
                    );
            } else {
                filtersMessage
                    .then(' ' + AA_API.__("commands.listcommands-permdesc"))
                    .color(ChatColor.RED)
                    .tooltip(AA_API.__("commands.listcommands-show-permdescriptions"))
                    .command('/' + cmd.getName() + ' ' +
                        String.join(" ", replaceParam(args,
                            new String[]{
                                "permdesc", //NON-NLS
                                AA_API.__("commands.listcommands-permissiondescriptions"),
                                AA_API.__("commands.listcommands-permissionsdescriptions")
                            },
                            "permdesc:" + AA_API.__("general.yes"))
                        )
                    );
            }

            if (showUsage) {
                filtersMessage
                    .then(' ' + AA_API.__("commands.listcommands-usage")) //NON-NLS
                    .color(ChatColor.GREEN)
                    .tooltip(AA_API.__("commands.listcommands-hide-usage"))
                    .command('/' + cmd.getName() + ' ' +
                        String.join(" ", replaceParam(args,
                            new String[]{
                                "usg", //NON-NLS
                                AA_API.__("general.usage"),
                                AA_API.__("commands.listcommands-showusage")
                            },
                            "usg:" + AA_API.__("general.no")))); //NON-NLS
            } else {
                filtersMessage
                    .then(' ' + AA_API.__("commands.listcommands-usage"))
                    .color(ChatColor.RED)
                    .tooltip(AA_API.__("commands.listcommands-show-usage"))
                    .command('/' + cmd.getName() + ' ' +
                        String.join(" ", replaceParam(args,
                            new String[]{
                                "usg", //NON-NLS
                                AA_API.__("general.usage"),
                                AA_API.__("commands.listcommands-showusage")
                            },
                            "usg:" + AA_API.__("general.yes"))
                        )
                    );
            }
        }

        if (moreLines) {
            filtersMessage
                .then(' ' + AA_API.__("commands.listcommands-multiline-capital")) //NON-NLS
                .color(ChatColor.GREEN)
                .tooltip(AA_API.__("commands.listcommands-multiline-switch-to-single"))
                .command('/' + cmd.getName() + ' ' +
                    String.join(" ", replaceParam(args,
                        new String[]{
                            AA_API.__("commands.listcommands-multiline")
                        },
                        AA_API.__("commands.listcommands-multiline") +
                            ':' + AA_API.__("general.no") //NON-NLS
                        )
                    )
                );
        } else {
            filtersMessage
                .then(' ' + AA_API.__("commands.listcommands-multiline-capital"))
                .color(ChatColor.RED)
                .tooltip(AA_API.__("commands.listcommands-multiline-switch-to-multi"))
                .command('/' + cmd.getName() + ' ' +
                    String.join(" ", replaceParam(args,
                        new String[]{
                            AA_API.__("commands.listcommands-multiline")
                        },
                        AA_API.__("commands.listcommands-multiline") +
                            ':' + AA_API.__("general.yes") //NON-NLS
                        )
                    )
                );
        }

        if (sender instanceof Player) {
            filtersMessage
                .then(' ' + AA_API.__("commands.listcommands-search")) //NON-NLS
                .color(ChatColor.AQUA)
                .tooltip(AA_API.__("commands.listcommands-search-in-commands"))
                .suggest('/' + cmd.getName() + ' ' +
                    String.join(" ", replaceParam(args,
                        new String[]{
                            AA_API.__("general.search")
                        },
                        AA_API.__("general.search") + ':')
                    )
                );
        }

        filtersMessage.send(sender);

        sender.sendMessage("");

        if ((null != messages) && !messages.isEmpty()) {
            // sort messages, so same commands are displayed together
            final Map<String, FancyMessage> sortedMessages = new TreeMap<String, FancyMessage>(messages);
            int sendCounter = 0;

            // send them all out
            for (final Entry<String, FancyMessage> pair : sortedMessages.entrySet()) {
                if ((sendCounter >= fromIndex) && (sendCounter < toIndex)) {
                    pair.getValue().send(sender);
                }
                sendCounter++;
            }

            if (sender instanceof Player) {
                sender.sendMessage("");
                filtersMessage.send(sender);
                topMessage.send(sender);
            }
        } else {
            sender.sendMessage(ChatColor.GREEN + AA_API.__("commands.listcommands-no-commands-found"));
        }
    } // end method

} // end class