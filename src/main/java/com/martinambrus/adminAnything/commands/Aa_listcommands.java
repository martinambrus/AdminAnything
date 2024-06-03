package com.martinambrus.adminAnything.commands;

import com.martinambrus.adminAnything.AA_API;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Lists commands on the server, optionally filtered
 * by the given input variables.
 *
 * @author Martin Ambrus
 */
public class Aa_listcommands extends AbstractCommand {

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
    public Aa_listcommands(final Plugin aa) {
        plugin = aa;
    } // end method

    /**
     * Shows help for /aa_listcommands.
     *
     * @param sender The command sender to show help to.
     */
    @SuppressWarnings("HardCodedStringLiteral")
    private void showFilters(final CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + AA_API.__("commands.listcommands-help-for"));
        sender.sendMessage(AA_API.__("commands.listcommands-help-flags") + ':');
        sender.sendMessage("");
        sender
            .sendMessage("- " + ChatColor.GREEN + "pg, " + AA_API.__("general.page") + ChatColor.RESET + " = " + AA_API
                .__("commands.listcommands-help-go-to-page"));
        sender.sendMessage("-- " + ChatColor.AQUA + AA_API
            .__("general.example") + ChatColor.RESET + ": /aa_listcommands pg:2");
        sender.sendMessage("");
        sender.sendMessage("- " + ChatColor.GREEN + "pl, plug, " + AA_API.__("general.plugin") + ChatColor.RESET
            + " = " + AA_API.__("commands.listcommands-help-include-exclude-plugin"));
        sender.sendMessage("-- " + ChatColor.AQUA + AA_API.__("general.example") + '1' + ChatColor.RESET + " (" + AA_API
            .__("commands.listcommands-help-show-essentials-commands") + "):");
        sender.sendMessage("---   /aa_listcommands pl:Essentials");
        sender.sendMessage(
            "-- " + ChatColor.AQUA + AA_API.__("general.example") + '2' + ChatColor.RESET + " (" + AA_API
                .__("commands.listcommands-help-show-essentials-and-mcmmo-commands") + "):");
        sender.sendMessage("---   /aa_listcommands pl:Essentials,mcMMO");
        sender.sendMessage("-- " + ChatColor.AQUA + AA_API.__("general.example") + '3' + ChatColor.RESET
            + " (" + AA_API.__("commands.listcommands-help-show-no-essentials-commands") + "):");
        sender.sendMessage("---   /aa_listcommands pl:-Essentials");
        sender.sendMessage("-- " + ChatColor.AQUA + AA_API.__("general.example") + '4' + ChatColor.RESET
            + " (" + AA_API.__("commands.listcommands-help-show-no-essentials-or-mcmmo-commands") + "):");
        sender.sendMessage("---   /aa_listcommands pl:-Essentials,-mcMMO");
        sender.sendMessage("");
        sender.sendMessage("- " + ChatColor.GREEN + "desc, " + AA_API.__("general.description") + ", " +
            AA_API.__("commands.listcommands-showdescription") + ", " + AA_API
            .__("commands.listcommands-showdescriptions")
            + ChatColor.RESET + " = " + AA_API.__("commands.listcommands-help-show-or-hide-descriptions"));
        sender.sendMessage("-- " + ChatColor.AQUA + AA_API
            .__("general.example") + '1' + ChatColor.RESET + ": /aa_listcommands desc:" + AA_API.__("general.yes"));
        sender.sendMessage("-- " + ChatColor.AQUA + AA_API
            .__("general.example") + '2' + ChatColor.RESET + ": /aa_listcommands desc:" + AA_API.__("general.no"));
        sender.sendMessage("");
        sender.sendMessage("- " + ChatColor.GREEN + "al, " + AA_API.__("general.aliases") + ", " + AA_API
            .__("commands.listcommands-showaliases") + ChatColor.RESET
            + " = " + AA_API.__("commands.listcommands-help-show-or-hide-aliases"));
        sender.sendMessage("-- " + ChatColor.AQUA + AA_API
            .__("general.example") + '1' + ChatColor.RESET + ": /aa_listcommands al:" + AA_API.__("general.yes"));
        sender.sendMessage("-- " + ChatColor.AQUA + AA_API
            .__("general.example") + '2' + ChatColor.RESET + ": /aa_listcommands al:" + AA_API.__("general.no"));
        sender.sendMessage("");
        sender.sendMessage("- " + ChatColor.GREEN + "perm, perms, " + AA_API.__("general.permission") + ", " + AA_API
            .__("general.permissions") + ChatColor.RESET
            + " = " + AA_API.__("commands.listcommands-help-show-or-hide-permissions"));
        sender.sendMessage(
            "-- " + ChatColor.AQUA + AA_API
                .__("general.example") + '1' + ChatColor.RESET + ": /aa_listcommands perm:" + AA_API.__("general.yes"));
        sender.sendMessage("-- " + ChatColor.AQUA + AA_API
            .__("general.example") + '2' + ChatColor.RESET + ": /aa_listcommands perm:" + AA_API.__("general.no"));
        sender.sendMessage("");
        sender.sendMessage("- " + ChatColor.GREEN + "permdesc, " + AA_API
            .__("commands.listcommands-permissiondescriptions") + ", " + AA_API
            .__("commands.listcommands-permissionsdescriptions")
            + ChatColor.RESET + " = " + AA_API.__("commands.listcommands-help-show-or-hide-permission-descriptions"));
        sender.sendMessage("-- " + ChatColor.AQUA + AA_API.__("general.example") + '1' + ChatColor.RESET
            + ": /aa_listcommands permdesc:" + AA_API.__("general.yes"));
        sender.sendMessage(
            "-- " + ChatColor.AQUA + AA_API
                .__("general.example") + '2' + ChatColor.RESET + ": /aa_listcommands permdesc:" + AA_API
                .__("general.no"));
        sender.sendMessage("");
        sender.sendMessage("- " + ChatColor.GREEN + "usg, " + AA_API.__("general.usage") + ", " + AA_API
            .__("commands.listcommands-showusage") + ChatColor.RESET
            + " = " + AA_API.__("commands.listcommands-help-show-or-hide-usage"));
        sender.sendMessage(
            "-- " + ChatColor.AQUA + AA_API
                .__("general.example") + '1' + ChatColor.RESET + ": /aa_listcommands usg:" + AA_API.__("general.yes"));
        sender.sendMessage("-- " + ChatColor.AQUA + AA_API
            .__("general.example") + '2' + ChatColor.RESET + ": /aa_listcommands usg:" + AA_API.__("general.no"));
        sender.sendMessage("");
        sender.sendMessage("- " + ChatColor.GREEN + AA_API.__("general.search") + ChatColor.RESET
            + " = " + AA_API.__("commands.listcommands-search-description.1"));
        sender.sendMessage("-- " + ChatColor.AQUA + AA_API.__("general.example") + '1' + ChatColor.RESET + '(' + AA_API
            .__("commands.listcommands-search-description.2") + "):");
        sender.sendMessage("---   /aa_listcommands " + AA_API.__("general.search") + "://cut");
        sender.sendMessage("-- " + ChatColor.AQUA + AA_API.__("general.example") + '2' + ChatColor.RESET
            + " (" + AA_API.__("commands.listcommands-search-description.3") + "):");
        sender.sendMessage("---   /aa_listcommands " + AA_API.__("general.search") + ":\"" + AA_API
            .__("commands.listcommands-search-description.3a-higest-block") + '"');
        sender.sendMessage("");
        sender.sendMessage("- " + ChatColor.GREEN + AA_API.__("commands.listcommands-multiline") + ChatColor.RESET
            + " = " + AA_API.__("commands.listcommands-enable-disable-multiline"));
        sender.sendMessage("-- " + ChatColor.AQUA + AA_API
            .__("general.example") + '1' + ChatColor.RESET + ": /aa_listcommands " + AA_API
            .__("commands.listcommands-multiline") + ':' + AA_API.__("general.yes"));
        sender.sendMessage("-- " + ChatColor.AQUA + AA_API
            .__("general.example") + '2' + ChatColor.RESET + ": /aa_listcommands " + AA_API
            .__("commands.listcommands-multiline") + ':' + AA_API.__("general.no"));
        sender.sendMessage("");
        sender.sendMessage(AA_API.__("commands.listcommands-flags-can-be-combined"));
        sender.sendMessage(
            ChatColor.AQUA + AA_API
                .__("general.example") + ChatColor.RESET + ": /aa_listcommands pl:-Essentials pg:2 desc:" + AA_API
                .__("general.no"));
        sender.sendMessage(ChatColor.AQUA + AA_API.__("general.example") + "2 (" + AA_API
            .__("commands.listcommands-show-full-info-for-command") + ')' + ChatColor.RESET
            + ": /aa_listcommands al:yes perm:yes desc:yes permdesc:yes usg:yes " + AA_API
            .__("commands.listcommands-multiline") + ':' + AA_API.__("general.yes") + ' ' + AA_API
            .__("general.search") + ':' + AA_API.__("general.yes"));
    } // end method

    /***
     * /aa_listCommands - lists all commands of all currently loaded plug-ins
     *
     * @param sender The player who is calling this command.
     * @param cmd The actual command that is being executed.
     * @param unused Name of the command which is being executed.
     * @param args Any arguments passed to this command.
     *
     * @return Returns true if we could list commands, false otherwise.
     */
    @SuppressWarnings("deprecation")
    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String unused, final String[] args) {
        if (!super.onCommand(sender, cmd, unused, args)) {
            return true;
        }

        //noinspection HardCodedStringLiteral
        if (!AA_API.isFeatureEnabled("listcommands")) {
            sender.sendMessage(ChatColor.RED + AA_API.__("general.feature-disabled"));
            return true;
        }

        // check if we're not requesting help for this command
        if ((1 == args.length) && ("?".equals(args[0]) || AA_API.__("general.help").equals(args[0]))) {
            showFilters(sender);
            return true;
        }

        try {
            if (!(sender instanceof Player)) {
                Bukkit.getScheduler().scheduleAsyncDelayedTask(plugin,
                    new Aa_listcommands_runnable(sender, args, cmd));
            } else {
                Bukkit.getScheduler().scheduleSyncDelayedTask(plugin,
                    new Aa_listcommands_runnable(sender, args, cmd));
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + AA_API.__("error.general-for-chat"));
            Bukkit.getLogger().severe('[' + AA_API.getAaName()
                + "] " + AA_API.__("error.config-cannot-load-perms-descriptions")); //NON-NLS
            e.printStackTrace();
            return false;
        }
        return true;
    } // end method

} // end class