package com.martinambrus.adminAnything.commands;

import com.martinambrus.adminAnything.AA_API;
import mkremins.fanciful.FancyMessage;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.io.InvalidClassException;
import java.lang.reflect.InvocationTargetException;
import java.rmi.AccessException;
import java.util.ArrayList;
import java.util.List;

/**
 * Displays information about all fixed, muted, disabled... commands
 * on the server, including warnings about invalid values from removed plugins.
 *
 * @author Martin Ambrus
 */
public class Aa_info extends AbstractCommand {

    /**
     * Instance of {@link com.martinambrus.adminAnything.AdminAnything}.
     */
    private final Plugin plugin;

    /**
     * Constructor, stores the instance of {@link com.martinambrus.adminAnything.AdminAnything}
     * for futher use.
     *
     * @param aa Instance of {@link com.martinambrus.adminAnything.AdminAnything}.
     */
    public Aa_info(final Plugin aa) {
        this.plugin = aa;
    } // end method

    /***
     * /aa_info - displays info about all fixed, muted, disabled... commands on the server
     *
     * @param sender The player who is calling this command.
     * @param cmd The actual command that is being executed.
     * @param unused Name of the command which is being executed.
     * @param args Any arguments passed to this command.
     *
     * @return Always returns true.
     */
    @SuppressWarnings("deprecation")
    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String unused, final String[] args) {
        if (!super.onCommand(sender, cmd, unused, args)) {
            return true;
        }

        //noinspection HardCodedStringLiteral
        if (!AA_API.isFeatureEnabled("info")) {
            sender.sendMessage(ChatColor.RED + AA_API.__("general.feature-disabled"));
            return true;
        }

        // will be set to false if at least a single feature of AA is used on the server
        final List<FancyMessage> messages = new ArrayList<FancyMessage>();

        // load fixed commands info
        checkStringList(
            "overrides",
            "commands.info-overrides-title",
            "commands.info-overrides-original-invalid",
            "commands.info-overrides-invalid",
            "commands.info-overrides-fixed-to",
            "aa_unfixcommand",
            messages,
            sender);

        // load redirected commands info
        checkStringList(
            "redirects",
            "commands.info-redirects-title",
            "commands.info-redirects-original-invalid",
            "commands.info-redirects-invalid",
            "commands.info-redirects-fixed-to",
            "aa_delredirect",
            messages,
            sender);

        // load virtual permissions info
        checkVirtualPermissions(messages, sender);

        // load mutes commands info
        checkStringList(
            "mutes",
            "commands.info-mutes-title",
            "commands.info-mutes-original-invalid",
            null,
            null,
            "aa_unmutecommand",
            messages,
            sender);

        // load removals commands info
        checkStringList(
            "removals",
            "commands.info-removals-title",
            "commands.info-removals-original-invalid",
            null,
            null,
            "aa_enablecommand",
            messages,
            sender);

        // load ignored commands info
        checkStringList(
            "ignores",
            "commands.info-ignores-title",
            "commands.info-ignores-original-invalid",
            null,
            null,
            "aa_unignorecommand",
            messages,
            sender);

        sender.sendMessage("");

        if (messages.isEmpty()) {
            sender.sendMessage(ChatColor.GREEN + AA_API.__("commands.info-nothing-aa-found", AA_API.getAaName()));
        } else {
            for (final FancyMessage msg : messages) {
                msg.send(sender);
            }
        }

        return true;
    } // end method

    /**
     * Checks string list backed configuration for commands and their existence on server
     * and adds them into messages to be sent out to the command sender.
     *
     * @param listName Name of the configuration list in which to check all the commands.
     * @param title Language key title name for this listing.
     * @param invalidOriginMessage Language key name for message that tells user about invalid origin command in the config file.
     * @param invalidTargetMessage Language key name for message that tells user about invalid target command in the config file.
     *                             Can be null, in which case we'll only return origin commands listing.
     * @param connectingWord Language key name for a word that connects these 2 commands, such as "fixed to" or "redirected to".
     *                       Can be null, in which case we'll only return origin commands listing.
     * @param correctionCommandName The name of AA command to use in order to fix this error. It will be used in chat click action
     *                              and its singular attribute will be the origin command from this config list.
     * @param messages List of messages to append informational messages for the command sender to.
     * @param sender The actual command sender for the /aa_info command.
     */
    private void checkStringList(String listName, String title, String invalidOriginMessage, String invalidTargetMessage, String connectingWord, String correctionCommandName, List<FancyMessage> messages, CommandSender sender) {
        boolean titleAppended = false;
        boolean checkOriginOnly = (invalidTargetMessage == null);
        for (String commandName : (checkOriginOnly ? AA_API.getCommandsList(listName) : AA_API.getCommandsConfigurationKeys(listName, false))) {
            String fixedCommandPluginName = null;
            List<String> fixedCommandPlugins = new ArrayList<String>();
            List<String> originCommandPlugins = new ArrayList<String>();
            try {
                if (!checkOriginOnly) {
                    fixedCommandPluginName = AA_API
                        .getPluginForCommand(AA_API.getCommandsConfigurationValue(listName, commandName), null);
                    fixedCommandPlugins = AA_API
                        .getCommandContainingPlugins(AA_API.getCommandsConfigurationValue(listName, commandName), true);
                }

                originCommandPlugins = AA_API.getCommandContainingPlugins(commandName, true);

                // append title/heading line for this config section
                if (!titleAppended) {
                    messages.add(
                        new FancyMessage(AA_API.__(title))
                            .color(ChatColor.AQUA)
                    );

                    titleAppended = true;
                }
            } catch (InvalidClassException | SecurityException | AccessException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e1) {
                sender.sendMessage(
                    ChatColor.RED + AA_API.__("error.general-for-chat"));
                e1.printStackTrace();
            }

            // start with listing the name of this original command
            FancyMessage msg = new FancyMessage("- ")
                .color(ChatColor.GOLD);

            // is the original command still valid and present on server?
            if (!originCommandPlugins.isEmpty()) {
                msg
                    .then(commandName)
                    .color(ChatColor.GOLD);
            } else {
                // original command is no longer present on server
                msg
                    .then(commandName + " (" + AA_API.__("general.invalid") + ')')
                    .color(ChatColor.RED)
                    .tooltip(AA_API.__(invalidOriginMessage))
                    .command("/" + correctionCommandName + " " + commandName);
            }

            // check if we're going for single command or multiple commands output
            if (!checkOriginOnly) {
                // continue by listing the actual override
                msg
                    .then(' ' + AA_API.__(connectingWord) + ' ')
                    .color(ChatColor.WHITE);

                // check if the override is still present on the server
                if (
                    (fixedCommandPluginName != null && !"".equals(fixedCommandPluginName))
                        ||
                        !fixedCommandPlugins.isEmpty()
                ) {
                    msg
                        .then(AA_API.getCommandsConfigurationValue(listName, commandName))
                        .color(ChatColor.GREEN);
                } else {
                    // command not found on this server anymore
                    msg
                        .then(AA_API.getCommandsConfigurationValue(listName, commandName) + " (" + AA_API
                            .__("general.invalid") + ')')
                        .color(ChatColor.RED);

                    if (!originCommandPlugins.isEmpty()) {
                        msg
                            .tooltip(AA_API.__(invalidTargetMessage, ChatColor.AQUA + "/" + commandName))
                            .command("/" + correctionCommandName + " " + commandName);
                    }

                    msg
                        .then("")
                        .color(ChatColor.GOLD);
                }
            }

            messages.add(msg);
        }

        // add empty line for the next possible section
        if (titleAppended) {
            messages.add(new FancyMessage("").color(ChatColor.WHITE));
        }
    } // end method

    /**
     * Checks string list backed configuration for virtual permissions and existence of their running commands on server
     * and adds them into messages to be sent out to the command sender.
     *
     * @param messages List of messages to append informational messages for the command sender to.
     * @param sender The actual command sender for the /aa_info command.
     */
    private void checkVirtualPermissions(List<FancyMessage> messages, CommandSender sender) {
        boolean titleAppended = false;
        for (String permName : AA_API.getCommandsConfigurationKeys("virtualperms", false)) {
            String fixedCommandPluginName = null;
            List<String> fixedCommandPlugins = new ArrayList<String>();
            try {
                // prepare the actual command name, as it can have parameters as well
                String cmdName = AA_API.getCommandsConfigurationValue("virtualperms", permName);
                if (cmdName.contains(" ")) {
                    cmdName = cmdName.split(" ")[0];
                }

                fixedCommandPluginName = AA_API.getPluginForCommand(cmdName, null);
                fixedCommandPlugins = AA_API.getCommandContainingPlugins(cmdName, true);

                // append title/heading line for this config section
                if (!titleAppended) {
                    messages.add(
                        new FancyMessage(AA_API.__("commands.info-virtualperms-title"))
                            .color(ChatColor.AQUA)
                    );

                    titleAppended = true;
                }
            } catch (InvalidClassException | SecurityException | AccessException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e1) {
                sender.sendMessage(
                    ChatColor.RED + AA_API.__("error.general-for-chat"));
                e1.printStackTrace();
            }

            // start with listing the name of this original command
            FancyMessage msg = new FancyMessage("- " + permName)
                .color(ChatColor.GOLD)
                .then(' ' + AA_API.__("commands.info-virtualperms-used-for") + ' ')
                .color(ChatColor.WHITE);

            // check if the command is still present on the server
            if (
                (fixedCommandPluginName != null && !"".equals(fixedCommandPluginName))
                    ||
                    !fixedCommandPlugins.isEmpty()
            ) {
                msg
                    .then(AA_API.getCommandsConfigurationValue("virtualperms", permName))
                    .color(ChatColor.GREEN);
            } else {
                // command not found on this server anymore
                msg
                    .then(AA_API.getCommandsConfigurationValue("virtualperms", permName) + " (" + AA_API
                        .__("general.invalid") + ')')
                    .color(ChatColor.RED)
                    .tooltip(AA_API.__("commands.info-virtualperms-invalid", ChatColor.AQUA + "/" + permName))
                    .command("/aa_delperm " + permName);

                msg
                    .then("")
                    .color(ChatColor.GOLD);
            }

            messages.add(msg);
        }

        // add empty line for the next possible section
        if (titleAppended) {
            messages.add(new FancyMessage("").color(ChatColor.WHITE));
        }
    } // end method

} // end class