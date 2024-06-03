package com.martinambrus.adminAnything.commands;

import com.martinambrus.adminAnything.AA_API;
import mkremins.fanciful.FancyMessage;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command which lists all available actions that can be used
 * on the given command.
 *
 * @author Martin Ambrus
 */
public class Aa_actions extends AbstractCommand {

    /**
     * Constructor, starts the commandPreprocessor listener
     * if it was not started yet, so our fixed, muted, disabled...
     * commands can get pre-processed by AdminAnything.
     */
    public Aa_actions() {
        //noinspection HardCodedStringLiteral
        AA_API.startRequiredListener("commandPreprocessor");
    } // end method

    /***
     * /aa_actions - lists available actions for the given command or alias
     *
     * @param sender The player who is calling this command.
     * @param cmd The actual command that is being executed.
     * @param unused Name of the command which is being executed.
     * @param args Any arguments passed to this command.
     *
     * @return Returns true if it was possible to display actions
     *         for the requested command, false otherwise.
     */
    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String unused, final String[] args) {
        if (!super.onCommand(sender, cmd, unused, args)) {
            return true;
        }

        //noinspection HardCodedStringLiteral
        if (!AA_API.isFeatureEnabled("actions")) {
            sender.sendMessage(ChatColor.RED + AA_API.__("general.feature-disabled"));
            return true;
        }

        // there is no use from console for this one
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.YELLOW + AA_API.__("commands.actions-players-only"));
            return true;
        }

        // no command to list actions for
        if (0 == args.length) {
            sender.sendMessage(ChatColor.RED + AA_API.__("commands.actions-enter-command"));
            return false;
        }

        // begin
        sender.sendMessage("");
        sender
            .sendMessage(
                ChatColor.YELLOW +
                    AA_API.__("commands.actions-for",
                        ChatColor.WHITE + "/" + args[0] + ChatColor.YELLOW
                    )
            );
        sender.sendMessage("====================");

        final FancyMessage msg = new FancyMessage("");
        final String lowercaseCommand = args[0].toLowerCase();

        // Full Info link
        msg
            .then(AA_API.__("commands.actions-full-info"))
            .color(ChatColor.AQUA)
            .command(
                "/aa_listcommands al:yes perm:yes desc:yes permdesc:yes usg:yes multiline:yes " + //NON-NLS
                    AA_API.__("general.search") + ':' + args[0]
            )
            //noinspection HardCodedStringLiteral
            .tooltip(AA_API.__("commands.actions-show-full-info") + " /" + args[0]);

        // if it's not fixed, add a Fix link
        //noinspection HardCodedStringLiteral
        if (AA_API.isFeatureEnabled("fixcommand") && AA_API.checkPerms(sender, "aa.fixcommand", false) && !AA_API.getCommandsConfigurationValues("overrides").containsKey(lowercaseCommand)) {
            msg
                .then(' ' + AA_API.__("commands.actions-fix")) //NON-NLS
                .color(ChatColor.GREEN)
                .suggest("/aa_fixcommand " + args[0] + ' ') //NON-NLS
                .tooltip(AA_API.__("commands.actions-fix-duplication"));
        }

        // if it's fixed, add an UnFix link
        //noinspection HardCodedStringLiteral
        if (AA_API.isFeatureEnabled("fixcommand") && AA_API.checkPerms(sender, "aa.unfixcommand", false) && AA_API.getCommandsConfigurationValues("overrides").containsKey(lowercaseCommand)) {
            msg
                .then(' ' + AA_API.__("commands.actions-unfix")) //NON-NLS
                .color(ChatColor.YELLOW)
                .command("/aa_unfixcommand " + args[0]) //NON-NLS
                //noinspection HardCodedStringLiteral
                .tooltip(AA_API.__("commands.actions-restore-functionality", ChatColor.AQUA + "/" + args[0]));
        }

        // if it's not disabled, add a Disable link
        //noinspection HardCodedStringLiteral
        if (AA_API.isFeatureEnabled("disablecommand") && AA_API.checkPerms(sender, "aa.disablecommand", false) && !AA_API.getCommandsList("removals").contains(lowercaseCommand)) {
            msg
                .then(' ' + AA_API.__("commands.actions-disable")) //NON-NLS
                .color(ChatColor.RED)
                .command("/aa_disablecommand " + args[0]) //NON-NLS
                //noinspection HardCodedStringLiteral
                .tooltip(AA_API
                    .__("commands.actions-disable-command", ChatColor.AQUA + "/" + args[0] + ChatColor.RESET));
        }

        // if it's disabled, add a Re-Enable link
        //noinspection HardCodedStringLiteral
        if (AA_API.isFeatureEnabled("disablecommand") && AA_API.checkPerms(sender, "aa.enablecommand", false) && AA_API.getCommandsList("removals").contains(lowercaseCommand)) {
            msg
                .then(' ' + AA_API.__("commands.actions-reenable")) //NON-NLS
                .color(ChatColor.YELLOW)
                .command("/aa_enablecommand " + args[0]) //NON-NLS
                .tooltip(AA_API.__("commands.actions-reenable-command"));
        }

        // if it's not muted, add a Mute link
        //noinspection HardCodedStringLiteral
        if (AA_API.isFeatureEnabled("mutecommand") && AA_API.checkPerms(sender, "aa.mutecommand", false) && !AA_API.getCommandsList("mutes").contains(lowercaseCommand)) {
            msg
                .then(' ' + AA_API.__("commands.actions-mute")) //NON-NLS
                .color(ChatColor.GRAY)
                .command("/aa_mutecommand " + args[0]) //NON-NLS
                //noinspection HardCodedStringLiteral
                .tooltip(AA_API
                    .__("commands.actions-mute-output-of", ChatColor.AQUA + "/" + args[0] + ChatColor.RESET));
        }

        // if it's muted, add an UnMute link
        //noinspection HardCodedStringLiteral
        if (AA_API.isFeatureEnabled("mutecommand") && AA_API.checkPerms(sender, "aa.unmutecommand", false) && AA_API.getCommandsList("mutes").contains(lowercaseCommand)) {
            msg
                .then(' ' + AA_API.__("commands.actions-unmute")) //NON-NLS
                .color(ChatColor.YELLOW)
                .command("/aa_unmutecommand " + args[0]) //NON-NLS
                .tooltip(AA_API.__("commands.actions-unmute-output"));
        }

        // if it's not ignored, add an Ignore link
        //noinspection HardCodedStringLiteral
        if (AA_API.isFeatureEnabled("ignorecommand") && AA_API.checkPerms(sender, "aa.ignorecommand", false) && !AA_API.getCommandsList("ignores").contains(lowercaseCommand)) {
            msg
                .then(' ' + AA_API.__("commands.actions-ignore")) //NON-NLS
                .color(ChatColor.DARK_PURPLE)
                .command("/aa_ignorecommand " + args[0]) //NON-NLS
                //noinspection HardCodedStringLiteral
                .tooltip(AA_API
                    .__("commands.actions-remove-from-checked-commands", ChatColor.AQUA + "/" + args[0] + ChatColor.RESET));
        }

        // if it's ignored, add an UnIgnore link
        //noinspection HardCodedStringLiteral
        if (AA_API.isFeatureEnabled("ignorecommand") && AA_API.checkPerms(sender, "aa.unignorecommand", false) && AA_API.getCommandsList("ignores").contains(lowercaseCommand)) {
            msg
                .then(' ' + AA_API.__("commands.actions-unignore")) //NON-NLS
                .color(ChatColor.YELLOW)
                .command("/aa_unignorecommand " + args[0]) //NON-NLS
                //noinspection HardCodedStringLiteral
                .tooltip(AA_API
                    .__("commands.actions-readd-to-checked-commands", ChatColor.AQUA + "/" + args[0] + ChatColor.RESET));
        }

        // all actions listed, show them to player
        msg.send(sender);
        return true;
    } // end method

} // end class