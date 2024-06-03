package com.martinambrus.adminAnything.commands;

import com.martinambrus.adminAnything.AA_API;
import com.martinambrus.adminAnything.Constants;
import com.martinambrus.adminAnything.Utils;
import mkremins.fanciful.FancyMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Command which lists all permissions
 * given to the player requested.
 *
 * @author Martin Ambrus
 */
public class Aa_playerperms extends AbstractCommand {

    /***
     * /aa_playerperms - displays list of permissions given to the player requested
     *
     * @param sender The player who is calling this command.
     * @param cmd The actual command that is being executed.
     * @param unused Name of the command which is being executed.
     * @param args Any arguments passed to this command.
     *
     * @return Returns true if we could list player's permissions, false otherwise.
     */
    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String unused, final String[] args) {
        if (!super.onCommand(sender, cmd, unused, args)) {
            return true;
        }

        //noinspection HardCodedStringLiteral
        if (!AA_API.isFeatureEnabled("playerperms")) {
            sender.sendMessage(ChatColor.RED + AA_API.__("general.feature-disabled"));
            return true;
        }

        // check for at least one parameter
        if (1 > args.length) {
            sender.sendMessage(
                ChatColor.RED + AA_API.__("commands.playerperms-no-player-name"));
            return false;
        }

        // check that we chose an online player
        @SuppressWarnings("deprecation") final Player p = Bukkit.getPlayer(args[0]);
        if ((null == p) || !p.isOnline()) {
            sender.sendMessage(ChatColor.RED + AA_API.__("commands.online-players-only"));
            return false;
        } else {
            // check if the player is not OP
            if (p.isOp()) {
                sender.sendMessage(
                    AA_API.__(
                        "commands.playerperms-player-is-operator",
                        p.getDisplayName() + ChatColor.GREEN
                    )
                );
                return true;
            }

            // prepare the search string against which to compare player's permissions
            final List<String> search = new ArrayList<String>(Arrays.asList(args));

            // remove the first parameter (player's name)
            search.remove(0);

            // remove pagination, if found
            final String intRegex = Constants.INT_REGEX.toString();
            if (args[args.length - 1].matches(intRegex)) {
                search.remove(search.size() - 1);
            }

            // create a text representation of the search string
            final String searchString = String.join(" ", search);

            // pagination
            final double maxPerPage = ((sender instanceof ConsoleCommandSender) ? 100.0 : AA_API.getMaxRecordsPerPage());
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

            // messages are stored temporarily for sorting purposes
            final List<String> messages = new ArrayList<String>();

            // prepare list of all permissions for the player
            final List<String> perms = new ArrayList<String>();
            for (final PermissionAttachmentInfo perm : p.getEffectivePermissions()) {
                if (!searchString.isEmpty() && !perm.getPermission().contains(searchString)) {
                    continue;
                }

                // add Vault support for double-checking on PEX and other perm systems (negative perms will still be assigned as perm attachments otherwise)
                if (AA_API.isVaultEnabled() && !AA_API.checkPerms(p, perm.getPermission(), false)) {
                    continue;
                }

                perms.add(perm.getPermission());
            }

            // sort them alphabetically
            Collections.sort(perms, String.CASE_INSENSITIVE_ORDER);
            String currentGroup = "";
            StringBuilder buffer = new StringBuilder();

            // build the output according to each perm start
            // and send them individually (so for example aa.* perms are together, factions.* perms are together etc.)

            // limit player chat output to 800 characters, as that's about as close to chat history limit without
            // extra pollution and loosing top pagination as we can get when players have extrabig permissions list
            final int maxDataPerMessage = 800;
            for (final String perm : perms) {
                // prepare permission group name
                final String permGroup = (perm.contains(".") ? perm.substring(0, perm.indexOf('.')) : perm);

                // check whether we should be starting a buffer for a new group
                // if current permissions group has changed from the last one
                if ((currentGroup.isEmpty() || permGroup
                    .equalsIgnoreCase(currentGroup)) && (!(sender instanceof ConsoleCommandSender) && maxDataPerMessage > (buffer
                    .length() << 1) + ((perm + ", ").length() << 1) || sender instanceof ConsoleCommandSender)) {
                    buffer.append(perm).append(", ");

                    if (currentGroup.isEmpty()) {
                        currentGroup = permGroup;
                    }
                } else {
                    // start a buffer for a new permissions group
                    currentGroup = permGroup;
                    String out = buffer.toString();

                    if (!out.isEmpty()) {
                        out = out.substring(0, out.length() - 2);
                        messages.add(ChatColor.WHITE + "- " + out);
                    }

                    buffer = new StringBuilder();
                    buffer.append(perm).append(", ");
                }
            }

            // add last perms line
            String out = buffer.toString();
            if (!out.isEmpty()) {
                out = out.substring(0, out.length() - 2);
                messages.add(ChatColor.WHITE + "- " + out);
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

            sender.sendMessage("");

            final FancyMessage topMessage = new FancyMessage("== ").color(ChatColor.WHITE);

            // left arrow navigation
            Utils.addChatTopNavigation(sender, fromIndex, topMessage, cmd, args, intRegex, requestedPageOriginal, true);

            // header
            topMessage.then(AA_API.__("commands.playerperms-perms-of", ChatColor.WHITE + p.getDisplayName()))
                      .color(ChatColor.YELLOW);

            topMessage.then(" (" + (requestedPage + 1) + ' ' + AA_API.__("general.of") + ' ' + pages + ')')
                      .color(ChatColor.YELLOW);

            // right arrow navigation
            Utils.addChatTopNavigation(sender, fromIndex, topMessage, cmd, args, intRegex, requestedPageOriginal, false);

            topMessage.then(" ==").send(sender);

            sender.sendMessage("");

            if (!messages.isEmpty()) {
                for (final String msg : messages.subList(fromIndex, toIndex)) {
                    sender.sendMessage(msg);
                }
            } else {
                sender.sendMessage(ChatColor.GREEN + AA_API.__("commands.playerperms-no-perms"));
            }
        }

        return true;
    } // end method

} // end class