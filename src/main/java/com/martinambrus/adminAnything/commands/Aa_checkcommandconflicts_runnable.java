package com.martinambrus.adminAnything.commands;

import com.martinambrus.adminAnything.AA_API;
import com.martinambrus.adminAnything.Constants;
import mkremins.fanciful.FancyMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.InvalidClassException;
import java.lang.reflect.InvocationTargetException;
import java.rmi.AccessException;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Pattern;

/**
 * The actual logic that checks for command conflicts
 * and displays the result to the player or console.
 *
 * @author Martin Ambrus
 */
@SuppressWarnings("OverlyComplexClass")
public class Aa_checkcommandconflicts_runnable implements Runnable {

    /**
     * The player or console who is calling this command.
     */
    private final CommandSender sender;

    /**
     * Any arguments passed to this command.
     */
    private final String[] args;

    /**
     * Instance of {@link com.martinambrus.adminAnything.commands.Aa_checkcommandconflicts}.
     * Used to cache and load cached messages once conflicting commands are loaded.
     */
    public Aa_checkcommandconflicts cccClassInstance;

    /**
     * Instance of {@link com.martinambrus.adminAnything.AdminAnything}.
     */
    private final Plugin plugin;

    /**
     * Logic that checks commands for conflicts.
     *
     * @param sender The player who is calling this command.
     * @param args Any arguments passed to this command.
     * @param aa Instance of {@link com.martinambrus.adminAnything.AdminAnything}.
     */
    public Aa_checkcommandconflicts_runnable(final CommandSender sender, final String[] args, final Plugin aa) {
        this.sender = sender;
        this.args = args;
        plugin = aa;
    } // end method

    /**
     * Check command for duplication and add it to
     * the list of duplicate commands.
     *
     * @param pair The command-name <> command pair from the server's CommandMap.
     * @param pluginName Name of the plugin which contains the given command.
     * @param clearCommandName A clear command name, i.e. "ban" instead of "essentials:ban".
     * @param doneCommands A reference to the list of done commands, which is one of the variables
     *                     used to de-duplicate same commands from different plugins (for example
     *                     WorldGuard VS WorldEdit, as WG duplicates some WE commands in the CommandMap).
     * @param doneCommandsMap A reference to the map of done commands, which is one of the variables
     *                        used to de-duplicate same commands from different plugins (for example
     *                        WorldGuard VS WorldEdit, as WG duplicates some WE commands in the CommandMap).
     * @param doneCommandIDs A reference to the list of done command IDs, which is one of the variables
     *                       used to de-duplicate same commands from different plugins (for example
     *                       WorldGuard VS WorldEdit, as WG duplicates some WE commands in the CommandMap).
     * @param duplicatesCommands A reference to the list of done commands, which is one of the variables
     *                           used to de-duplicate same commands from different plugins (for example
     *                           WorldGuard VS WorldEdit, as WG duplicates some WE commands in the CommandMap).
     *
     * @return Returns true if we've processed the command successfully,
     *         false if we should skip further processing of this particular command.
     */
    private boolean handleDuplicateCommand(final Entry<String, Command>pair, final String pluginName,
                                           final String clearCommandName, final Collection<String> doneCommands, final Map<String, String> doneCommandsMap,
                                           final Map<Integer, Boolean> doneCommandIDs, final Map<String, String> duplicatesCommands) {
        // check if we shouldn't ignore this one
        //noinspection HardCodedStringLiteral
        if (AA_API.isFeatureEnabled("ignorecommand") && AA_API.getCommandsList("ignores").contains(clearCommandName.toLowerCase())) {
            return false;
        }

        // check that this is indeed a command and not an alias and that it's not been added to the listing already
        if (doneCommandIDs.containsKey(pair.getValue().hashCode()) || ((null != pair.getValue().getAliases())
                && pair.getValue().getAliases().contains(clearCommandName))) {
            // alias found or command already added, let's bail out here or we'd duplicate this command's listing
            return false;
        } else {
            // mark this command as done, so we don't duplicate it
            // ... this is because the commandMap contains both versions of the command,
            //     one without the plugin prefix and one with it (i.e. /essentials:repair AND /repair)
            doneCommandIDs.put(pair.getValue().hashCode(), true);
        }

        // check for duplicate commands
        if (doneCommands.contains(clearCommandName)) {
            //noinspection HardCodedStringLiteral
            if (
                !AA_API.getCommandsList("overrides").contains(clearCommandName) &&
                !AA_API.__("general.core").equals(pluginName)
            ) {

                if (duplicatesCommands.containsKey(clearCommandName)) {
                    // simple and stupid (and expensive... good we're caching) check against duplication of plugin name
                    // ... this is because WorldEdit plugins seem to sometimes duplicate WE's own commands under different command hashes,
                    //     so we end up conflicting WE with WE :(
                    if (!duplicatesCommands.get(clearCommandName).contains(pluginName + ", ")) {
                        duplicatesCommands.put(clearCommandName,
                                duplicatesCommands.get(clearCommandName) + pluginName + ", ");
                    }
                } else if (doneCommandsMap.containsKey(clearCommandName)) {
                    // simple and stupid (and expensive... good we're caching) check against duplication of plugin name
                    // ... this is because WorldEdit plugins seem to sometimes duplicate WE's own commands under different command hashes,
                    //     so we end up conflicting WE with WE :(
                    if ((!duplicatesCommands.containsKey(clearCommandName)
                            || !duplicatesCommands.get(clearCommandName).contains(pluginName + ", "))
                            && !doneCommandsMap.get(clearCommandName).contains(pluginName + ", ")) {
                        duplicatesCommands.put(clearCommandName,
                                doneCommandsMap.get(clearCommandName) + pluginName + ", ");
                    }
                } else {
                    // simple and stupid (and expensive... good we're caching) check against duplication of plugin name
                    // ... this is because WorldEdit plugins seem to sometimes duplicate WE's own commands under different command hashes,
                    //     so we end up conflicting WE with WE :(
                    if (!duplicatesCommands.get(clearCommandName).contains(pluginName + ", ")) {
                        duplicatesCommands.put(clearCommandName, pluginName + ", ");
                    }
                }
            }
        } else if (!AA_API.__("general.core").equals(pluginName)) {
            doneCommands.add(clearCommandName);
        }

        // add this command into a temporary command map,
        // so we can list all command duplicates at the end
        doneCommandsMap.put(clearCommandName, pluginName + ", ");

        return true;
    } // end method

    /**
     * Checks whether any plugins override a core command with this one
     * and puts it into a list of such commands if they do.
     *
     * @param pluginName Name of the plugin which contains the given command.
     * @param clearCommandName A clear command name, i.e. "ban" instead of "essentials:ban".
     * @param doneCommandOverrides A reference to the list of commands that override core
     *                     server functionality, which is one of the variables used to de-duplicate
     *                     same commands from different plugins (for example WorldGuard VS WorldEdit,
     *                     as WG duplicates some WE commands in the CommandMap).
     */
    private void handleNativeServerOverrides(final String pluginName, final String clearCommandName, final Map<String, String> doneCommandOverrides) {
        // check out for plugins that override server's own core commands
        //noinspection HardCodedStringLiteral
        final String overrideCheck = AA_API.getCommandsConfigurationValue("overrides", clearCommandName);
        if (
            !AA_API.__("general.core").equals(pluginName) &&
                AA_API.getBuiltInCommands().contains(clearCommandName) &&
                (
                    !AA_API.getCommandsList("overrides").contains(clearCommandName) || //NON-NLS
                        (
                            !overrideCheck.startsWith("minecraft") && //NON-NLS
                                !overrideCheck.startsWith("bukkit") && //NON-NLS
                                !overrideCheck.startsWith("spigot") //NON-NLS
                        )
                ) && !AA_API.getCommandsList("removals").contains(clearCommandName) //NON-NLS
            ) {

            // override for a core command found
            if (doneCommandOverrides.containsKey(clearCommandName)) {
                if (
                    AA_API.isFeatureEnabled("fixcommand") && //NON-NLS
                        AA_API.getCommandsList("overrides").contains(clearCommandName) //NON-NLS
                    ) {
                    //noinspection HardCodedStringLiteral
                    final String theOverride = AA_API.getCommandsConfigurationValue("overrides", clearCommandName);
                    doneCommandOverrides.put(clearCommandName, doneCommandOverrides.get(clearCommandName)
                        + "; " + ChatColor.AQUA + theOverride.substring(0, theOverride.indexOf(':')) + ':'
                            + ChatColor.GREEN
                        + theOverride.substring(theOverride.indexOf(':') + 1));
                } else {
                    doneCommandOverrides.put(clearCommandName, doneCommandOverrides.get(clearCommandName)
                        + "; " + ChatColor.AQUA + pluginName + ':' + ChatColor.GREEN + clearCommandName);
                }
            } else {
                if (
                    AA_API.isFeatureEnabled("fixcommand") && //NON-NLS
                        AA_API.getCommandsList("overrides").contains(clearCommandName) //NON-NLS
                    ) {
                    //noinspection HardCodedStringLiteral
                    final String theOverride = AA_API.getCommandsConfigurationValue("overrides", clearCommandName);
                    doneCommandOverrides.put(clearCommandName,
                        theOverride.substring(0, theOverride.indexOf(':')) + ':' + ChatColor.GREEN
                            + theOverride.substring(theOverride.indexOf(':') + 1
                        ));
                } else {
                    doneCommandOverrides.put(clearCommandName,
                        pluginName + ':' + ChatColor.GREEN + clearCommandName);
                }
            }
        }
    } // end method

    /**
     * Check command for possible duplication of its aliases
     * and add them to the list of duplicate aliases, if found.
     *
     * @param pair The command-name <> command pair from the server's CommandMap.
     * @param pluginName Name of the plugin which contains the given command.
     * @param commandName Name of the actual command to look aliases for.
     * @param doneAliases A reference to the list of done aliases, which is one of the variables
     *                     used to de-duplicate same commands from different plugins (for example
     *                     WorldGuard VS WorldEdit, as WG duplicates some WE commands in the CommandMap).
     * @param doneAliasesMap A reference to the map of done aliases, which is one of the variables
     *                     used to de-duplicate same commands from different plugins (for example
     *                     WorldGuard VS WorldEdit, as WG duplicates some WE commands in the CommandMap).
     * @param duplicatesAliases A reference to the list of duplicated aliases, which is one of the variables
     *                     used to de-duplicate same commands from different plugins (for example
     *                     WorldGuard VS WorldEdit, as WG duplicates some WE commands in the CommandMap).
     * @param doneAliasOverrides A reference to the list of aliases which override core functionality, which is
     *                     one of the variables used to de-duplicate same commands from different plugins
     *                     (for example WorldGuard VS WorldEdit, as WG duplicates some WE commands in the CommandMap).
     */
    private void handleDuplicateAliases(final Entry<String, Command> pair, final String pluginName, final String commandName, final Collection<String> doneAliases, final Map<String, String> doneAliasesMap, final Map<String, String> duplicatesAliases, final Map<String, String> doneAliasOverrides) {
        // check if we've defined any aliases for the current command
        if ((null != pair.getValue().getAliases()) && !pair.getValue().getAliases().isEmpty()) {
            // iterate over all aliases for the current command
            for (final String alias : pair.getValue().getAliases()) {
                // check for duplicate aliases
                final String aliasKey = ChatColor.YELLOW + alias + ChatColor.WHITE +
                    " (" + AA_API.__("commands.checkconflicts-use-instead", commandName) + //NON-NLS
                    ')' + ChatColor.RESET; //NON-NLS
                // add the processed alias into the de-duplication done list
                if (doneAliases.contains(aliasKey)) {
                    if (duplicatesAliases.containsKey(aliasKey)) {
                        duplicatesAliases.put(aliasKey,
                                duplicatesAliases.get(aliasKey) + pluginName + "; ");
                    } else if (doneAliasesMap.containsKey(alias)) {
                        duplicatesAliases.put(aliasKey, doneAliasesMap.get(alias) + pluginName + "; ");
                    } else {
                        duplicatesAliases.put(aliasKey, pluginName + "; ");
                    }
                } else {
                    doneAliases.add(aliasKey);
                }

                // add this alias into a temporary aliases map,
                // so we can list all alias duplicates at the end
                doneAliasesMap.put(alias, pluginName + "; ");

                // check out for aliases that override server's own core commands
                //noinspection HardCodedStringLiteral
                final String aliasOverrideCheck = AA_API.getCommandsConfigurationValue("overrides", alias);
                if (
                    !AA_API.__("general.core").equals(pluginName) &&
                        AA_API.getBuiltInCommands().contains(alias) &&
                        (
                            !AA_API.getCommandsList("overrides").contains(alias) || //NON-NLS
                                (
                                    !aliasOverrideCheck.startsWith("minecraft") && //NON-NLS
                                        !aliasOverrideCheck.startsWith("bukkit") && //NON-NLS
                                        !aliasOverrideCheck.startsWith("spigot") //NON-NLS
                                )
                        )
                    ) {

                    // alias that overrides a server core command found
                    if (doneAliasOverrides.containsKey(alias)) {
                        if (
                            AA_API.isFeatureEnabled("fixcommand") && //NON-NLS
                                AA_API.getCommandsList("overrides").contains(alias) //NON-NLS
                            ) {
                            //noinspection HardCodedStringLiteral
                            final String theOverride = AA_API.getCommandsConfigurationValue("overrides", alias);
                            doneAliasOverrides.put(alias,
                                    doneAliasOverrides.get(alias) + "; "
                                        + theOverride.substring(0, theOverride.indexOf(':')) + ':'
                                            + ChatColor.GREEN + theOverride.substring(
                                        theOverride.indexOf(':') + 1));
                        } else {
                            doneAliasOverrides.put(alias, doneAliasOverrides.get(alias) + "; "
                                + ChatColor.AQUA + pluginName + ':' + ChatColor.DARK_PURPLE + alias);
                        }
                    } else {
                        if (
                            AA_API.isFeatureEnabled("fixcommand") && //NON-NLS
                                AA_API.getCommandsList("overrides").contains(alias) //NON-NLS
                            ) {
                            //noinspection HardCodedStringLiteral
                            final String theOverride = AA_API.getCommandsConfigurationValue("overrides", alias);
                            doneAliasOverrides.put(alias,
                                theOverride.substring(0, theOverride.indexOf(':')) + ':'
                                            + ChatColor.DARK_PURPLE + theOverride.substring(
                                    theOverride.indexOf(':') + 1));
                        } else {
                            doneAliasOverrides.put(alias, pluginName + ':' + ChatColor.DARK_PURPLE + alias);
                        }
                    }
                }
            }
        }
    } // end method

    /**
     * If this is an uncached run (or we need uncached results due to debugging),
     * this method will load and cache all duplicate data.
     */
    @SuppressWarnings("IntegerMultiplicationImplicitCastToLong")
    private void loadAndCacheMessages() {
        if ((null == cccClassInstance.messages) || AA_API.getDebug()) {
            // commands and aliases de-duplication variables
            // ... this is because WorldEdit plugins seem to sometimes duplicate WE's own commands under different command hashes,
            //     so we end up conflicting WE with WE :(
            final Collection<String>    doneCommands         = new ArrayList<String>();
            final Collection<String>    doneAliases          = new ArrayList<String>();
            final Map<String, String>   doneCommandsMap      = new HashMap<String, String>();
            final Map<String, String>   doneAliasesMap       = new HashMap<String, String>();
            final Map<String, String>   doneCommandOverrides = new HashMap<String, String>();
            final Map<String, String>   doneAliasOverrides   = new HashMap<String, String>();
            final Map<Integer, Boolean> doneCommandIDs       = new HashMap<Integer, Boolean>();

            // a map of commands and aliases that are duplicated on the server from multiple plugins
            // and thus pose an identifiable problem
            final Map<String, String> duplicatesCommands = new HashMap<String, String>();
            final Map<String, String> duplicatesAliases = new HashMap<String, String>();

            // if this remains true, the server is clean
            boolean allGood = true;

            try {
                String pluginName;
                for (final Entry<String, Command> pair : AA_API.getCommandMapCopy().entrySet()) {
                    pluginName = AA_API.getPluginForCommand(pair.getKey(), pair.getValue());

                    // we couldn't get a plugin for this one, it's safe not to continue
                    // and rather warn the player and the admin
                    if (null == pluginName) {
                        sender.sendMessage(ChatColor.RED + AA_API.__("error.general-for-chat"));
                        Bukkit.getLogger().severe(
                            '[' + AA_API.getAaName() +
                                "] " +
                                AA_API.__("plugin.error-plugin-for-command-not-found") +
                                ": " + pair.getKey()
                        );
                        return;
                    }

                    // adjust core plugin name for later checks
                    //noinspection HardCodedStringLiteral
                    if ("minecraft".equals(pluginName) || "bukkit".equals(pluginName) || "spigot".equals(pluginName)) {
                        pluginName = AA_API.__("general.core");
                    }

                    // prepare the base command name (without colons, e.g. "ban" instead of "essentials:ban")
                    final String clearCommandName = (pair.getKey().contains(":")
                                                     ? pair.getKey().substring(pair.getKey().indexOf(':') + 1) :
                                                     pair.getKey());
                    final String commandName = ChatColor.GREEN + "/" + clearCommandName + ChatColor.WHITE;

                    // check for duplicates of this command
                    if (!handleDuplicateCommand(pair, pluginName, clearCommandName, doneCommands, doneCommandsMap, doneCommandIDs, duplicatesCommands)) {
                        continue;
                    }

                    // check whether any plugins override a core command with this one
                    handleNativeServerOverrides(pluginName, clearCommandName, doneCommandOverrides);

                    // check for duplicates of aliases for this command
                    handleDuplicateAliases(pair, pluginName, commandName, doneAliases, doneAliasesMap, duplicatesAliases, doneAliasOverrides);
                }
            } catch (final IllegalArgumentException | InvalidClassException | IllegalAccessException
                    | NoSuchMethodException | SecurityException | InvocationTargetException | AccessException e) {
                sender.sendMessage(ChatColor.RED + AA_API.__("error.general-for-chat"));
                e.printStackTrace();
                return;
            }

            // initialize messages, as this is the first, uncached run
            cccClassInstance.messages = new ArrayList<FancyMessage>();

            // do we have any duplicate commands?
            if (!duplicatesCommands.isEmpty()) {
                allGood = false;
                cccClassInstance.messages.add(new FancyMessage(""));

                cccClassInstance.messages
                    .add(new FancyMessage(AA_API.__("commands.checkconflicts-duplicates-found"))
                        .color(ChatColor.RED));

                cccClassInstance.messages.add(new FancyMessage(
                    AA_API.__("commands.checkconflicts-duplicates-info.1") + ' ')
                    .color(ChatColor.RED)
                    .then("/aa_fixcommand") //NON-NLS
                    .color(ChatColor.YELLOW)
                    .then(
                        ' ' +
                            AA_API.__("commands.checkconflicts-duplicates-info.2") + "):" //NON-NLS
                    )
                    .color(ChatColor.RED));

                // list all plugins in which this command is used
                // or the actual fix itself, if fixed commands listing was requested
                for (final Entry<String, String> pair : new TreeMap<String, String>(duplicatesCommands).entrySet()) {
                    final FancyMessage cmdFixBuilder = new FancyMessage("- " + pair.getKey()).color(ChatColor.GOLD)
                                                                                                 .then(
                                                                                                     " (" + //NON-NLS
                                                                                                         AA_API
                                                                                                             .__("commands.checkconflicts-duplicates-used-in") + ' '
                                                                                                 )
                                                                                                 .color(ChatColor.GOLD);

                    final String[] splitted = pair.getValue().substring(0, pair.getValue().length() - 2)
                                                  .split(Pattern.quote(", "));
                    final int sLength = splitted.length;

                    for (int i = 0; i < sLength; i++) {
                        cmdFixBuilder.then(splitted[i] + (i == (sLength - 1) ? "" : ", "))
                                     .color(ChatColor.WHITE)
                                     .command("/aa_fixcommand " + pair //NON-NLS
                                                                       .getKey() + ' ' + splitted[i] + ':' + pair
                                         .getKey())
                                     .tooltip(
                                         AA_API.__("commands.checkconflicts-duplicates-hard-wire") + ' '
                                             + ChatColor.GOLD + pair.getKey() + ChatColor.RESET +
                                             ' ' + AA_API.__("general.to") + ' '
                                             + ChatColor.AQUA + splitted[i] + ':' +
                                             ChatColor.GREEN + pair.getKey()
                                     );
                    }
                    cmdFixBuilder.then(")").color(ChatColor.GOLD);

                    cccClassInstance.messages.add(cmdFixBuilder);
                }
            }

            // do we have any duplicate aliases?
            if (!duplicatesAliases.isEmpty()) {
                allGood = false;
                cccClassInstance.messages.add(new FancyMessage(""));

                cccClassInstance.messages.add(new FancyMessage(
                    AA_API.__("commands.checkconflicts-duplicates-warning"))
                    .color(ChatColor.AQUA));

                cccClassInstance.messages.add(new FancyMessage(
                    AA_API.__("commands.checkconflicts-duplicates-warning-info") + ':')
                    .color(ChatColor.AQUA));

                // list all plugins in which this alias is used
                // or the actual fix itself, if fixed aliases listing was requested
                for (final Entry<String, String> pair : new TreeMap<String, String>(duplicatesAliases).entrySet()) {
                    final FancyMessage aliasFixBuilder = new FancyMessage("- " + pair.getKey()).color(ChatColor.GOLD)
                                                              .then(", ")
                                                              .color(ChatColor.WHITE)
                                                              .then(AA_API.__("general.from") + ' ')
                                                              .color(ChatColor.GOLD);

                    final String[] splitted = pair.getValue().substring(0, pair.getValue().length() - 2)
                                                  .split(Pattern.quote("; "));
                    final int sLength = splitted.length;

                    for (int i = 0; i < sLength; i++) {
                        aliasFixBuilder.then(splitted[i] + (i == (sLength - 1) ? "" : ", "))
                                       .color(ChatColor.WHITE);
                    }

                    cccClassInstance.messages.add(aliasFixBuilder);
                }
            }

            // check if any plugins override core server commands
            if (!doneCommandOverrides.isEmpty() || !doneAliasOverrides.isEmpty()) {
                allGood = false;
                cccClassInstance.messages.add(new FancyMessage(""));

                cccClassInstance.messages.add(new FancyMessage(
                    AA_API.__("commands.checkconflicts-server-commands-overridden"))
                        .color(ChatColor.GREEN));

                cccClassInstance.messages.add(new FancyMessage(
                    AA_API.__("commands.checkconflicts-server-commands-overridden-info"))
                        .color(ChatColor.GREEN));

                cccClassInstance.messages.add(new FancyMessage(""));

                // add info about command overrides
                if (!doneCommandOverrides.isEmpty()) {
                    cccClassInstance.messages.add(new FancyMessage(AA_API.__("commands.checkconflicts-commands")).color(ChatColor.AQUA));
                    for (final Entry<String, String> pair : new TreeMap<String, String>(doneCommandOverrides)
                            .entrySet()) {
                        cccClassInstance.messages.add(new FancyMessage("- ").color(ChatColor.GOLD).then('/' + pair.getKey())
                                                           .color(ChatColor.GREEN).then(' ' + AA_API
                                .__("commands.checkconflicts-overridden-via") + ' ').color(ChatColor.WHITE)
                                                           .then(pair.getValue()).color(ChatColor.AQUA));
                    }

                    cccClassInstance.messages.add(new FancyMessage(""));
                }

                // add info about alias overrides
                if (!doneAliasOverrides.isEmpty()) {
                    cccClassInstance.messages.add(new FancyMessage(AA_API.__("commands.checkconflicts-aliases") + ' ')
                        .color(ChatColor.DARK_PURPLE)
                        .then('(' + AA_API.__("commands.checkconflicts-aliases-info") + ')')
                        .color(ChatColor.WHITE));

                    for (final Entry<String, String> pair : new TreeMap<String, String>(doneAliasOverrides)
                            .entrySet()) {
                        cccClassInstance.messages.add(new FancyMessage("- ").color(ChatColor.GOLD).then('/' + pair.getKey())
                                                           .color(ChatColor.YELLOW).then(' ' + AA_API
                                .__("commands.checkconflicts-overridden-via") + ' ')
                                                           .color(ChatColor.WHITE)
                                                           .then(pair.getValue()).color(ChatColor.AQUA)
                        );
                    }

                    cccClassInstance.messages.add(new FancyMessage(""));
                }
            }

            // all good? do tell! :)
            if (allGood) {
                cccClassInstance.messages.add(
                    new FancyMessage(AA_API.__("commands.checkconflicts-no-problems")).color(ChatColor.GREEN));
            }
        }
    }

    /**
     * The logic behind checking command conflicts on the server.
     */
    @Override
    public void run() {
        // 150 commands per page for console, a lot less for players,
        // since the player chat has a fair amount smaller buffer than the console
        final double maxPerPage = ((sender instanceof ConsoleCommandSender) ? 150.0 : AA_API.getMaxRecordsPerPage());

        // pagination
        int requestedPage = ((0 < args.length)
                             ? (args[0].matches(Constants.INT_REGEX.toString()) ? Integer.parseInt(args[0]) : 1) : 1);

        // this one is used to generate next/previous links
        // by using the same output as the input we've been given
        int requestedPageOriginal = requestedPage;

        // no nullpointers for you :P
        if (0 >= requestedPage) {
            requestedPage = 1;
            requestedPageOriginal = requestedPage;
        }

        // load and cache (if we're not debugging) all conflicts data
        loadAndCacheMessages();

        // calculate pagination data
        String pages = String.valueOf(Math.ceil(cccClassInstance.messages.size() / maxPerPage));
        pages = pages.substring(0, pages.indexOf('.'));
        final int pagesInt = Integer.parseInt(pages);

        // check and adjust the requested page
        if (requestedPage > pagesInt) {
            requestedPage = pagesInt - 1;
        } else {
            requestedPage--;
        }

        // cut messages, so only the ones we requested are shown
        int       fromIndex = (int) Math.max(0, requestedPage * maxPerPage);
        final int toIndex   = (int) Math.min(cccClassInstance.messages.size(), (requestedPage + 1) * maxPerPage);

        if (fromIndex >= toIndex) {
            fromIndex = toIndex - 1;
        }

        sender.sendMessage("");
        final FancyMessage topMessage = new FancyMessage("== ").color(ChatColor.WHITE);

        // pagination left arrow
        if ((sender instanceof Player) && (0 < fromIndex)) {
            topMessage
                .then(AA_API.__("general.previous-character") + " ")
                .color(ChatColor.AQUA)
                .command("/aa_checkcommandconflicts " + (requestedPageOriginal - 1)) //NON-NLS
                .tooltip(
                    AA_API.__("chat.navigation-show-next-prev-page",
                        AA_API.__("chat.navigation-previous"),
                        AA_API.__("general.page")
                    )
                );
        }

        // pagination title
        topMessage.then(AA_API.__("commands.checkconflicts-conflicts-title")).color(ChatColor.YELLOW);
        topMessage.then(" (" + (requestedPage + 1) + ' ' + AA_API.__("general.of") + ' ' + pages + ')')
                  .color(ChatColor.YELLOW);

        // pagination right arrow
        if ((sender instanceof Player) && (toIndex < cccClassInstance.messages.size())) {
            topMessage
                .then(" " + AA_API.__("general.next-character"))
                .color(ChatColor.AQUA)
                .command("/aa_checkcommandconflicts " + (requestedPageOriginal + 1)) //NON-NLS
                .tooltip(AA_API.__("chat.navigation-show-next-prev-page",
                    AA_API.__("chat.navigation-next"),
                    AA_API.__("general.page")
                ));
        }

        topMessage.then(" ==").send(sender);
        sender.sendMessage("");

        // now list all the conflicts that we've previously cached
        if (!cccClassInstance.messages.isEmpty()) {
            int sendCounter = 0;
            for (final FancyMessage msg : cccClassInstance.messages) {
                if ((sendCounter >= fromIndex) && (sendCounter < toIndex)) {
                    msg.send(sender);
                }
                sendCounter++;
            }
        }
    } // end method

} // end class