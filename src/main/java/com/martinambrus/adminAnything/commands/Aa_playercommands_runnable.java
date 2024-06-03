package com.martinambrus.adminAnything.commands;

import com.martinambrus.adminAnything.AA_API;
import com.martinambrus.adminAnything.Constants;
import com.martinambrus.adminAnything.events.AASaveCommandHelpDisablesEvent;
import mkremins.fanciful.FancyMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;

import java.io.InvalidClassException;
import java.lang.reflect.InvocationTargetException;
import java.rmi.AccessException;
import java.util.*;
import java.util.Map.Entry;

/**
 * The actual logic to list all commands available
 * for the given player.
 *
 * @author Martin Ambrus
 */
public class Aa_playercommands_runnable implements Runnable, Listener {

    /**
     * Instance of {@link com.martinambrus.adminAnything.AdminAnything}.
     */
    private final Plugin plugin;

    /**
     * The player who is calling this command.
     */
    private final CommandSender sender;

    /**
     * Any arguments passed to this command.
     */
    private final String[] args;

    /**
     * The actual command that is being executed.
     */
    private final Command cmd;

    /**
     * The player for who we're listing available commands.
     */
    private final Player p;

    /**
     * A map of commands that should be hidden from the player commands
     * listing, unless there's an explicit permission to show them present
     * for the player. This map has commands grouped by permission groups,
     * global being the default one.
     */
    private Map<String, List<String>> disabledHelpCommandsMap = new HashMap<String, List<String>>();

    /**
     * Name of this command feature, so it can be checked for being enabled.
     */
    private String featureName = "playercommands";

    /**
     * Constructor, sets internal variables to work with
     * as we list all commands available to the requested
     * player.
     *
     * @param sender The player who is calling this command.
     * @param args Any arguments passed to this command.
     * @param cmd The actual command that is being executed.
     * @param p The player for who we're listing available commands.
     * @param plugin Instance of {@link com.martinambrus.adminAnything.AdminAnything}.
     */
    Aa_playercommands_runnable(final CommandSender sender, final String[] args, final Command cmd, final Player p, final Plugin plugin) {
        this.sender = sender;
        this.args = args;
        this.cmd = cmd;
        this.p = p;
        this.plugin = plugin;

        // register this class as a listener, so it can reload its perm groups and commands map on its updates
        if (AA_API.isFeatureEnabled(this.featureName) && !AA_API.isListenerRegistered(this.featureName)) {
            AA_API.startRequiredListener(this.featureName, this);
        }

        // first load of the disabled commands map
        this.reloadHiddenCommandsMap(null);
    } // end method

    /**
     * Prepares a map of commands hidden from this listing
     * from the stored config list and sorts it by groups.
     *
     * Called when this command executor is being registered
     * as well as when the config list is being updated to keep it reloaded.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    private void reloadHiddenCommandsMap(final AASaveCommandHelpDisablesEvent e) {
        // iterate over all hidden commands and prepare them
        // for the output
        List<String> helpDisablesCommandsList = AA_API.getCommandsList("helpDisables");
        for (int i = 0; i < helpDisablesCommandsList.size(); i++) {
            // split the group and command and store them
            String group = helpDisablesCommandsList.get(i).substring(0, helpDisablesCommandsList.get(i).indexOf('.'));
            String commandLine = helpDisablesCommandsList.get(i).substring(helpDisablesCommandsList.get(i).indexOf('.') + 1);

            // create new ArrayList for this command group, if not set yet
            if (null == disabledHelpCommandsMap.get(group)) {
                disabledHelpCommandsMap.put(group, new ArrayList<String>());
            }

            // add the command to this group's list
            disabledHelpCommandsMap.get(group).add(commandLine);
        }
    }

    private FancyMessage addManagementPrefixes(CommandSender sender, String clearCommandName, boolean showingOwnCommands, String rawCommandName, String rawCommandDescription, String pluginName, String pluginCorePrefix) {
        // check the hidden commands map to see if our command is not to be hidden from ordinary users
        boolean commandIsHidden       = false;
        boolean commandGloballyHidden = false;
        boolean commandLocallyHidden = false;
        boolean senderIsPlayer        = sender instanceof Player;
        boolean playerIsAdmin         = AA_API.checkPerms(sender, "aa.checkplayercommands.admin", false);
        String lowerClearCommandName  = clearCommandName.toLowerCase();

        // check if this command is hidden globally
        if (disabledHelpCommandsMap.containsKey("global") && disabledHelpCommandsMap.get("global").contains(lowerClearCommandName)) {
            // this command is globally hidden from everyone
            commandIsHidden = true;
            commandGloballyHidden = true;
        }

        // get player's primary group and check it against disabled commands
        // ... if it's a player running this command
        if (senderIsPlayer) {
            if (
                disabledHelpCommandsMap.containsKey(AA_API.getPlayerPrimaryPermGroup((Player) sender).toLowerCase()) &&
                    disabledHelpCommandsMap.get(AA_API.getPlayerPrimaryPermGroup((Player) sender).toLowerCase()).contains(lowerClearCommandName)
            ) {
                // this command is hidden from this permission group
                commandIsHidden = true;
                commandLocallyHidden = true;
            }
        }

        // continue with the next command, if we don't have an override permission
        if (commandIsHidden && senderIsPlayer && !playerIsAdmin) {
            return null;
        }

        // message output variables
        final String commandName = "/" + clearCommandName + ChatColor.WHITE;
        FancyMessage out         = new FancyMessage("");

        // add management links to chat, if we have an admin player as sender
        if (senderIsPlayer && playerIsAdmin) {
            // this is a toggle to show/hide this command globally
            out.then("[G]");

            if (!commandGloballyHidden) {
                out
                    .color(ChatColor.GREEN)
                    .tooltip(AA_API
                        .__("commands.disablehc-listing-click-to-hide-globally", ChatColor.AQUA + clearCommandName + ChatColor.RESET))
                    .command("/aa_disablehelpcommand " + lowerClearCommandName);
            } else {
                out
                    .color(ChatColor.RED)
                    .tooltip(AA_API
                        .__("commands.disablehc-listing-click-to-restore", ChatColor.AQUA + clearCommandName + ChatColor.RESET))
                    .command("/aa_enablehelpcommand global." + lowerClearCommandName);
            }

            // this is a toggle to show/hide this command based on player's primary perm group
            out.then("[P]");

            if (!commandLocallyHidden) {
                out
                    .color(ChatColor.GREEN)
                    .tooltip(AA_API
                        .__("commands.disablehc-listing-click-to-hide-permbased", ChatColor.AQUA + clearCommandName + ChatColor.RESET))
                    .command("/aa_disablehelpcommand " + clearCommandName + " " + AA_API.getPlayerPrimaryPermGroup((Player) sender).toLowerCase());
            } else {
                out
                    .color(ChatColor.RED)
                    .tooltip(AA_API
                        .__("commands.disablehc-listing-click-to-restore", ChatColor.AQUA + clearCommandName + ChatColor.RESET))
                    .command("/aa_enablehelpcommand " + AA_API.getPlayerPrimaryPermGroup((Player) sender).toLowerCase() + "." + lowerClearCommandName);
            }
        }

        // continue to display the actual available command
        out
            .then(ChatColor.GREEN + (showingOwnCommands && commandIsHidden ? "" + ChatColor.STRIKETHROUGH :
                                     "") + commandName)
            .color(ChatColor.GREEN);

        // command is not hidden for our player, create a common tooltip
        if (!commandIsHidden) {
            out
                .tooltip(AA_API.__("commands.click-to-run", ChatColor.AQUA + "/" + clearCommandName))
                .suggest("/" + rawCommandName);
        } else {
            // if a command is hidden, add tooltip telling the player how
            if (commandGloballyHidden) {
                out.tooltip(AA_API.__("commands.disablehc-listing-globally-hidden"));
            } else {
                out.tooltip(AA_API.__("commands.disablehc-listing-perm-based-hidden"));
            }
        }
        // show plugin name and permissions when requesting other player's commands
        if (!showingOwnCommands) {
            out
                .then(" [" + pluginName + ((null != pluginCorePrefix) ? " - " + pluginCorePrefix + ':' + clearCommandName : "") + ']')
                .color(ChatColor.GRAY);
        } else {
            out
                .then((commandIsHidden ? "" + ChatColor.STRIKETHROUGH : "") + " " + rawCommandDescription)
                .color(ChatColor.WHITE);

            // if a command is hidden, add tooltip telling the player how
            if (commandIsHidden) {
                if (commandGloballyHidden) {
                    out.tooltip(AA_API.__("commands.disablehc-listing-globally-hidden"));
                } else {
                    out.tooltip(AA_API.__("commands.disablehc-listing-perm-based-hidden"));
                }
            }
        }

        return out;
    }

    /**
     * Loads permission information from all plugins
     * on the server and check which ones are connected
     * to our player.
     *
     * @param messages A reference to the resulting list of messages to show in output.
     */
    @SuppressWarnings("ConstantConditions")
    private boolean loadMessages(final Map<String, FancyMessage> messages) {
        // de-duplication variable
        final Map<Integer, Boolean> doneCommandIDs = new HashMap<Integer, Boolean>();
        List<String> disabledCommands = AA_API.getCommandsList("removals");
        final boolean showingOwnCommands = (
            (AA_API.checkPerms(p, "aa.checkplayercommands.own", false) && !AA_API.checkPerms(p, "aa.checkplayercommands.admin", false) && 1 > args.length) ||
            (AA_API.checkPerms(p, "aa.checkplayercommands.own", false) && !AA_API.checkPerms(p, "aa.checkplayercommands.admin", false) && 1 == args.length && args[0].matches(Constants.INT_REGEX.toString()))
        );

        // load custom permission descriptions for permissions of those plugins
        // which do not include their description in their plugin.yml
        //noinspection HardCodedStringLiteral
        FileConfiguration permsFromConfig = AA_API.getManualPermDescriptionsConfig();

        // a simple map of all permission attachments
        // for the given player - used when Vault is not present
        Map<String, Boolean> playerPermsCache = null;

        // first of all, cache all available permission names for this player, if we don't have Vault enabled
        if (!AA_API.isVaultEnabled()) {
            for (final PermissionAttachmentInfo perm : p.getEffectivePermissions()) {
                if (null == playerPermsCache) {
                    playerPermsCache = new HashMap<String, Boolean>();
                }

                playerPermsCache.put(perm.getPermission(), true);
            }
        }

        boolean senderIsPlayer = sender instanceof Player;

        try {
            // iterate over all loaded commands from the commandMap
            // and load their names, plugins and aliases
            for (final Entry<String, Command> pair : AA_API.getAugmentedCommandMap().entrySet()) {
                String key              = pair.getKey();
                String pluginName       = null;
                String pluginCorePrefix = null;

                // strip out the initial colon from commands that start on one (like :ping)
                if (key.startsWith(":")) {
                    key = key.substring(1);
                }

                try {
                    // if the command is not a standard one, it won't be castable to PluginCommand
                    // and the catch statements will take over
                    final PluginCommand pc = ((PluginCommand) pair.getValue());
                    pluginName = pc.getPlugin().getName();
                } catch (final ClassCastException e) {
                    // try the usual route
                    final PluginCommand pc = Bukkit.getPluginCommand(key);

                    // check if prefixed and try getting plugin name from the prefix
                    if ((null == pc) && key.contains(":")) {
                        final Plugin p = AA_API.getPluginIgnoreCase(key.substring(0, key.indexOf(':')));
                        if (null != p) {
                            pluginName = p.getName();
                        }
                    }

                    // non-prefixed, non-standard command
                    // ... we can only guess by its classname location here
                    if (null == pluginName) {
                        try {
                            pluginName = AA_API.guessPluginFromClass(pair.getValue().getClass());
                        } catch (final InvalidClassException e1) {
                            sender.sendMessage(ChatColor.RED
                                + AA_API.__("error.general-for-chat"));
                            e.printStackTrace();
                            return false;
                        }

                        // store core prefix if this is a core command
                        if (AA_API.__("general.core").equals(pluginName)) {
                            pluginCorePrefix = (key.contains(":") ? key.substring(0, key.indexOf(':')) : null);
                        }
                    }
                }

                // name for a plugin not found
                if (null == pluginName) {
                    sender.sendMessage(ChatColor.RED + AA_API.__("error.general-for-chat"));
                    Bukkit.getLogger().severe('[' + AA_API.getAaName()
                        + "] " + AA_API.__("plugin.error-plugin-for-command-not-found") + ": " + pair.getKey());
                    return false;
                }

                // store clear command name (without colons) for futher processing
                final String clearCommandName = (pair.getKey().contains(":")
                                                 ? pair.getKey().substring(pair.getKey().indexOf(':') + 1)
                                                 : pair.getKey());
                final String lowerClearCommandName = clearCommandName.toLowerCase();
                final List<String> aliases = pair.getValue().getAliases();

                // don't show commands that are disabled via AA
                if (disabledCommands.contains(lowerClearCommandName)) {
                    continue;
                }

                FancyMessage out = this.addManagementPrefixes(sender, clearCommandName, showingOwnCommands, pair.getKey(), pair.getValue().getDescription(), pluginName, pluginCorePrefix);

                // if we got null as a result, the command is hidden from this listing
                // and we don't have override admin permission to show it (i.e. we're not displaying the listing management GUI)
                if (null == out) {
                    continue;
                }

                // ignore the following commands when we're administering this listing
                // and our user has the actual permission assigned to allow this instead of
                // the group:
                // - /aa_disablehelpcommand
                // - /aa_enablehelpcommand
                if (!showingOwnCommands && senderIsPlayer && !AA_API.checkGroupPerm(sender, "aa.checkplayercommands.admin")) {
                    // we are a player managing the listing and have the actual managing permission as player (not as group)
                    // ... check if this is one of our management commands
                    if (
                        clearCommandName.equals("aa_disablehelpcommand") ||
                        clearCommandName.equals("disablehelpcommand") ||
                        clearCommandName.equals("disablehc") ||
                        clearCommandName.equals("adhc") ||
                        clearCommandName.equals("aa_enablehelpcommand") ||
                        clearCommandName.equals("enablehelpcommand") ||
                        clearCommandName.equals("aehc") ||
                        clearCommandName.equals("enablehc")
                    ) {
                        continue;
                    }
                }

                // check that this is indeed a command and not an alias and that it's not been added to the listing yet
                if (
                    // hide duplicit commands (with and without a prefix) - but only if we're not managing the self-perms listing
                    (showingOwnCommands && doneCommandIDs.containsKey(pair.getValue().hashCode())) ||
                    // hide this command only if it's prefixed, so we don't duplicate it in listing,
                    // as we don't show prefixed commands in /aa_playercommands
                    (!showingOwnCommands && pair.getKey().contains(":") && doneCommandIDs.containsKey(pair.getValue().hashCode())) ||
                    // allow aliases if we're managing the actual showing of commands for this listing
                    (showingOwnCommands && (null != aliases) && aliases.contains(clearCommandName))
                ) {
                    // alias found or command already added, let's bail out here or we'd duplicate this command's listing
                    continue;
                } else if (!doneCommandIDs.containsKey(pair.getValue().hashCode()) && !showingOwnCommands) {
                    // mark this command as done, so we don't duplicate it
                    // ... this is because the commandMap contains both versions of the command,
                    //     one without the plugin prefix and one with it (i.e. /essentials:repair AND /repair)
                    doneCommandIDs.put(pair.getValue().hashCode(), true);
                }

                // let's see if we can get permissions for this command
                final Collection<String> tmpPerms = new ArrayList<String>();

                if (null != pair.getValue().getPermission() && !pair.getValue().getPermission().isEmpty()) {
                    // permission is present in the description file
                    tmpPerms.add(pair.getValue().getPermission());
                } else {
                    // permission not present in the description file, try our internal YML descriptions file
                    //noinspection HardCodedStringLiteral
                    final List<String> descriptionedPerms = permsFromConfig
                            .getStringList("manualPermissions." + pluginName.toLowerCase() + '.' + clearCommandName);

                    // strip perms of their descriptions
                    if (!descriptionedPerms.isEmpty()) {
                        for (final String descPerm : descriptionedPerms) {
                            // make sure it's not a custom command description
                            if (!descPerm.startsWith("$")) {
                                tmpPerms.add(descPerm.substring(0, descPerm.indexOf('=')));
                            }
                        }
                    }
                }

                // if we can link one of the player's permission to current command, add this permission to our output
                final Collection<String> viaPerms = new ArrayList<String>();
                for (final String perm : tmpPerms) {
                    if (((null != playerPermsCache) && playerPermsCache
                            .containsKey(perm)) || ((null == playerPermsCache)
                            && AA_API.isVaultEnabled() && AA_API.checkPerms(p, perm, false))) {
                        viaPerms.add(perm);
                    }
                }

                // found permissions via which this command is accessible
                if (!viaPerms.isEmpty()) {
                    //noinspection HardCodedStringLiteral
                    out
                        .then((!showingOwnCommands ? ' ' + AA_API.__("general.via") + ' ' + String.join(", ", viaPerms) : ""))
                        .color(ChatColor.WHITE);

                    messages.put(clearCommandName, out);
                }
            }
        } catch (final IllegalArgumentException | IllegalAccessException | NoSuchMethodException | SecurityException
                | InvocationTargetException | AccessException e) {
            sender.sendMessage(
                ChatColor.RED + AA_API.__("error.general-for-chat"));
            e.printStackTrace();
            return false;
        }

        return true;
    } // end method

    /**
     * The actual logic to list player's available commands.
     */
    @Override
    public void run() {
        final double maxPerPage = ((sender instanceof ConsoleCommandSender) ? 100.0 : AA_API.getMaxRecordsPerPage());

        // messages are stored temporarily for sorting purposes
        final Map<String, FancyMessage> messages = new HashMap<String, FancyMessage>();

        // pagination
        int requestedPage = 1;

        // update the page number if requested via first parameter
        if ((0 < args.length) && args[0].matches(Constants.INT_REGEX.toString())) {
            requestedPage = Integer.parseInt(args[0]);
        }

        // update the page number if requested via second parameter
        if ((1 < args.length) && args[1].matches(Constants.INT_REGEX.toString())) {
            requestedPage = Integer.parseInt(args[1]);
        }

        // this one is used to generate next/previous links
        // by using the same output as the input we've been given
        int requestedPageOriginal = requestedPage;

        // no nullpointers for you :P
        if (0 >= requestedPage) {
            requestedPage = 1;
            requestedPageOriginal = requestedPage;
        }

        // load all messages to output
        if (!loadMessages(messages)) {
            // something didn't work, just exit here
            // and let the loadMessages() method handle the error message
            return;
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
        int       fromIndex = (int) Math.max(0, requestedPage * maxPerPage);
        final int toIndex   = (int) Math.min(messages.size(), (requestedPage + 1) * maxPerPage);

        // don't go beyond the maximum messages we have
        if (fromIndex >= toIndex) {
            fromIndex = toIndex - 1;
        }

        sender.sendMessage("");

        final FancyMessage topMessage = new FancyMessage("== ").color(ChatColor.WHITE);
        final String intRegex = Constants.INT_REGEX.toString();

        // left arrow navigation
        if ((sender instanceof Player) && (0 < fromIndex)) {
            topMessage.then(AA_API.__("general.previous-character") + " ").color(ChatColor.AQUA);

            final String             newCmd  = '/' + cmd.getName() + ' ';
            final Collection<String> newArgs = new ArrayList<String>();

            for (final String arg : args) {
                //noinspection HardCodedStringLiteral
                if (arg.matches(intRegex) || arg.contains(AA_API.__("general.page") + ':') || arg.contains("pg:")) {
                    continue;
                } else {
                    newArgs.add(arg);
                }
            }
            newArgs.add(String.valueOf(requestedPageOriginal - 1));

            topMessage
                .command(newCmd + String.join(" ", newArgs))
                .tooltip(
                    AA_API.__("chat.navigation-show-next-prev-page",
                        AA_API.__("chat.navigation-previous"),
                        AA_API.__("general.page")
                    )
                );
        }

        // header
        topMessage.then(AA_API.__("commands.playercommands-commands-for", ChatColor.WHITE + p.getDisplayName()))
                  .color(ChatColor.YELLOW);

        //noinspection HardCodedStringLiteral
        topMessage.then(" (" + (requestedPage + 1) + ' ' + AA_API.__("general.of") + ' ' + pages + ')')
                  .color(ChatColor.YELLOW);

        // right arrow navigation
        if ((sender instanceof Player) && (toIndex < messages.size())) {
            topMessage.then(" " + AA_API.__("general.next-character")).color(ChatColor.AQUA);

            final String             newCmd  = '/' + cmd.getName() + ' ';
            final Collection<String> newArgs = new ArrayList<String>();

            for (final String arg : args) {
                //noinspection HardCodedStringLiteral
                if (arg.matches(intRegex) || arg.contains(AA_API.__("general.page") + ':') || arg.contains("pg:")) {
                    continue;
                } else {
                    newArgs.add(arg);
                }
            }
            newArgs.add(String.valueOf(requestedPageOriginal + 1));

            topMessage
                .command(newCmd + String.join(" ", newArgs))
                .tooltip(
                    AA_API.__("chat.navigation-show-next-prev-page",
                        AA_API.__("chat.navigation-next"),
                        AA_API.__("general.page")
                    )
                );
        }

        topMessage.then(" ==").send(sender);

        sender.sendMessage("");

        if (!messages.isEmpty()) {
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
        } else {
            sender.sendMessage(ChatColor.GREEN + AA_API.__("commands.playercommands-no-commands"));
        }

        sender.sendMessage("");
    } // end method

} // end class