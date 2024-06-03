package com.martinambrus.adminAnything;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permissible;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Permissions-related utility functions, mostly to work with
 * the <a href="https://dev.bukkit.org/projects/vault">Vault</a> plugin
 * or standalone if Vault is not found.
 *
 * @author Martin Ambrus
 *
 */
final class Permissions {

    /**
     * Vault permissions provider.
     * @see <a href="https://dev.bukkit.org/projects/vault">Vault</a>
     */
    private net.milkbowl.vault.permission.Permission vault = null;

    /**
     * Regular expression to check for and resolve all nested AND + OR conditions.
     *
     * For example, from the following "query": (perm1 AND (perm2 OR (perm3 AND perm4))) OR (perm6 AND perm8) OR perm7
     * ... the first statement that contains no further brackets (i.e. the innermost one) will be selected: (perm3 AND perm4)
     */
    private final Pattern bracketsRegex = Pattern.compile("\\([^ (]+( (AND|OR) )?([^ )]+)?\\)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Regular expression to check for and resolve all AND conditions from the final bracket-less "query".
     *
     * For example, from the following "query": perm1 AND perm2 OR perm3
     * ... the first AND statement will be resolved: perm1 AND perm2
     */
    private final Pattern oneLinerAndRegex = Pattern.compile("\\(?[^ (]+ AND +([^ )]+)?\\)?", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Regular expression to check for and resolve all OR conditions from the final bracket-less "query".
     *
     * For example, from the following "query": perm1 AND perm2 OR perm3
     * ... the first OR statement will be resolved: perm2 OR perm3
     *
     * Of course, since this regex would be called only after all AND conditions are resolved, the actual
     * final "query" would be more similar to one such as: true OR perm3
     */
    private final Pattern oneLinerOrRegex = Pattern.compile("\\(?[^ (]+ OR +([^ )]+)?\\)?", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Constructor.
     * Hooks into the <a href="https://dev.bukkit.org/projects/vault">Vault</a> permissions manager.
     *
     * @param aa The instance of {@link com.martinambrus.adminAnything.AdminAnything AdminAnything} plugin.
     */
    Permissions(final Plugin aa) {
        if (Bukkit.getPluginManager().isPluginEnabled("Vault")) { //NON-NLS
            final RegisteredServiceProvider<net.milkbowl.vault.permission.Permission> rsp = aa.getServer()
                    .getServicesManager().getRegistration(net.milkbowl.vault.permission.Permission.class);
            vault = rsp.getProvider();
        }
    }

    /**
     * Checks the command sender for a permissions from an AND condition
     * that is given via the pattern parameter.
     *
     * @param sender  The {@link org.bukkit.command.CommandSender CommandSender} who we're checking the permission for.
     * @param pattern The permission query containing an AND "query" to resolve, for example: (perm1 AND perm2)
     *
     * @return Returns true if the AND "query" resolves into selecting an existing permission of the command sender, false otherwise.
     */
    @SuppressWarnings("HardCodedStringLiteral")
    private String resolveAndOneLiner(final Permissible sender, final String pattern) {
        final String[] andSplit = pattern.replaceAll("[()]", "").split(Pattern.quote(" AND ")); //NON-NLS
        String         result;

        // true/false and permission checks
        if (
            ("false".equals(andSplit[0]) && "false".equals(andSplit[1])) ||
                ("true".equals(andSplit[0]) && "true".equals(andSplit[1]))
                ) {
            // check that both values are not true or false, in which case the result will be true
            result = "true";
        } else if ("false".equals(andSplit[0]) || "false".equals(andSplit[1])) {
            // check if one of the values is not false - in which case, this whole check will be false
            result = "false";
        } else {
            result = (
                         ("true".equals(andSplit[0]) || checkPermSimple(sender, andSplit[0])) &&
                             ("true".equals(andSplit[1]) || checkPermSimple(sender, andSplit[1]))
                    )
                    ? "true"
                            : "false";
        }

        return result;
    } // end method

    /**
     * Checks the command sender for a permissions from an OR condition
     * that is given via the pattern parameter.
     *
     * @param sender  The {@link org.bukkit.command.CommandSender CommandSender} who we're checking the permission for.
     * @param pattern The permission query containing an OR "query" to resolve, for example: (perm1 OR perm2)
     *
     * @return Returns true if the OR "query" resolves into selecting an existing permission of the command sender, false otherwise.
     */
    @SuppressWarnings("HardCodedStringLiteral")
    private String resolveOrOneLiner(final Permissible sender, final String pattern) {
        final String[] orSplit = pattern.replaceAll("[()]", "").split(Pattern.quote(" OR "));
        String         result;

        // true/false and permission checks
        if ("true".equals(orSplit[0]) || "true".equals(orSplit[1])) {
            // check whether one of the values is not true, in which case this whole check will be true
            result = "true";
        } else if ("false".equals(orSplit[0]) && "false".equals(orSplit[1])) {
            // check if both values are false, in which case this whole check will be false
            result = "false";
        } else {
            // if one of the values actually evaluated to "false" before,
            // don't check perms for it or it may return TRUE (LuckPerms),
            // which is of course plain wrong
            if ("false".equals(orSplit[0])) {
                result = checkPermSimple(sender, orSplit[1]) ? "true" : "false";
            } else if ("false".equals(orSplit[1])) {
                result = checkPermSimple(sender, orSplit[0]) ? "true" : "false";
            } else
                result = (
                         checkPermSimple(sender, orSplit[0]) ||
                         checkPermSimple(sender, orSplit[1])
                ) ? "true" : "false";
        }

        return result;
    } // end method

    /***
     * Resets connection to Vault permissions manager.
     * Used when disabling this plugin.
     */
    void unregisterVaultServiceProvider() {
        if (null != vault) {
            vault = null;
        }
    } //end method

    /**
     * Checks whether a command sender has the given permission to execute it. Uses
     * <a href="https://dev.bukkit.org/projects/vault">Vault</a> permissions manager if found,
     * otherwise it utilizes {@link org.bukkit.command.CommandSender#hasPermission CommandSender.hasPermission()}.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * {@code
     * if (aa_plugin_instance.getPluginUtils().checkPermSimple(sender, "my_permission") {
     *  // do your stuff
     * }
     * </pre>
     *
     * @param sender The {@link org.bukkit.permissions.Permissible Permissible} command sender we're checking the permission for.
     * @param perm The actual text representation of a permission to check for.
     *
     * @return Returns true if the sender has the requested permission, false otherwise.
     */
    boolean checkPermSimple(final Permissible sender, final String perm) {
        return (null != vault) && (sender instanceof Player) ? vault.has((Player) sender, perm) : sender.hasPermission(perm);
    } //end method

    /**
     * Checks whether a command sender's group has the given permission. Uses
     * <a href="https://dev.bukkit.org/projects/vault">Vault</a> permissions manager if found,
     * otherwise it returns false.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * {@code
     * if (aa_plugin_instance.getPluginUtils().checkGroupPermSimple(sender, "my_permission") {
     *  // do your stuff
     * }
     * </pre>
     *
     * @param sender The {@link org.bukkit.permissions.Permissible Permissible} command sender who's group we're checking the permission.
     * @param perm The actual text representation of a permission to check for.
     *
     * @return Returns true if the sender has the requested permission, false otherwise.
     */
    boolean checkGroupPermSimple(final Permissible sender, final String perm) {
        boolean outcome = false;
        try {
            outcome = (null != vault) && vault.hasGroupSupport() && (sender instanceof Player) ? vault
                .groupHas(((Player) sender).getWorld(), this.getPlayerPrimaryPermGroup((Player) sender), perm) : false;
        } catch (Throwable e) {
            // UltimatePerms has bug that will throw a NullPointer exception in some cases,
            // so we'll just leave outcome as false
        }

        return outcome;
    } //end method

    /**
     * Checks whether a command sender's group has the given permission. Uses
     * <a href="https://dev.bukkit.org/projects/vault">Vault</a> permissions manager if found,
     * otherwise it returns false.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * {@code
     * if (aa_plugin_instance.getPluginUtils().checkGroupPermSimple(sender, "my_permission") {
     *  // do your stuff
     * }
     * </pre>
     *
     * @param sender The {@link org.bukkit.permissions.Permissible Permissible} command sender who's group we're checking the permission.
     * @param perm The actual text representation of a permission to check for.
     *
     * @return Returns true if the sender has the requested permission, false otherwise.
     */
    boolean checkGroupPermSimple(String worldName, String groupName, final String perm) {
        boolean outcome = false;
        try {
            outcome =
                (null != vault) && vault.hasGroupSupport() && vault.groupHas(worldName, groupName, perm) ? true : false;
        } catch (Throwable e) {
            // UltimatePerms has bug that will throw a NullPointer exception in some cases,
            // so we'll just leave outcome as false
        }

        return outcome;
    } //end method

    /***
     * Utilizes the {@link #checkPermSimple(Permissible, String) checkPermSimple()} method to check
     * for a single permission or uses a parsing algorithm to check for many permission nodes given
     * via the permsQuery parameter.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * {@code
     * if (aa_plugin_instance.getPluginUtils().checkPerms(sender, "my_permission", true) {
     *  // do your stuff
     *  // ... if the commandSender (sender) OR their permission groups do not have the relevant permission,
     *  //     an error message will be displayed to the sender
     * }
     * }
     * </pre>
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * {@code
     * if (aa_plugin_instance.getPluginUtils().checkPerms(sender, "(perm1 OR perm2) AND perm3", false) {
     *  // do your stuff
     *  // ... in this case, no error message is displayed to the command sender (last parameter is false)
     *  //     and you can handle this case with your own logic
     * }
     * }
     * </pre>
     *
     * @param sender     The {@link org.bukkit.command.CommandSender CommandSender} who we're checking the permission(s) for.
     * @param permsQuery An SQL-like query containing all the parameters that we need to check for.
     *                   The syntax is the same as used in SQL queries with AND / OR sections. Parenthesis are important, as is
     *                   remembering the general rule of operator priority, which says that AND has a higher priority over OR.
     *                   That means that any AND statements will be evaluated first.
     *                   Example: perm1 AND perm2 -> will check for both, perm1 and perm2 permissions
     *                   Example: perm1 OR perm2 -> will check for either, perm1 or perm2 permission
     *                   Example: perm1 AND perm2 OR perm3 -> will check for either perm1 + perm2, or the perm3 permission
     *                   Example: perm1 AND (perm2 OR perm3) -> the use of parenthesis now changes the previous example,
     *                            so perm1 + EITHER ONE of perm2 or perm3 permissions will be checked
     * @param showResultToSender If TRUE, a message will be sent out to the player / console for who we're checking these permissions.
     *
     * @return Returns true if the sender has the requested permission(s), false otherwise.
     */
    boolean checkPerms(final CommandSender sender, final String permsQuery, final boolean showResultToSender) {
        //noinspection UnusedAssignment
        boolean hasPerms = false;

        // parsed will keep getting updates until it's a one-word result of true, false or a permission name
        String parsed = permsQuery;

        // single node check
        if (!parsed.contains("(") && !parsed.contains(" AND ") && !parsed.contains(" OR ")) { //NON-NLS
            hasPerms = checkPermSimple(sender, parsed);
        } else {
            // multiple nodes check
            if (Utils.CheckParentesis(parsed)) {
                try {
                    // used to prevent infinite loops - should they occur at all
                    int i = 0;

                    // first step - handle all nested conditions
                    Matcher nestedConditionsMatcher = bracketsRegex.matcher(parsed);

                    while (nestedConditionsMatcher.find()) {
                        if (10000 < i++) {
                            //noinspection UseOfSystemOutOrSystemErr
                            System.out.print("AA - infinite loop at 01!"); //NON-NLS
                            return false;
                        }

                        if (nestedConditionsMatcher.group().contains(" AND ")) { //NON-NLS
                            // AND condition pair
                            parsed = parsed.replaceAll(Pattern.quote(nestedConditionsMatcher
                                    .group()), resolveAndOneLiner(sender, nestedConditionsMatcher.group()));
                        } else if (nestedConditionsMatcher.group().contains(" OR ")) { //NON-NLS
                            // OR condition pair
                            parsed = parsed.replaceAll(Pattern.quote(nestedConditionsMatcher
                                    .group()), resolveOrOneLiner(sender, nestedConditionsMatcher.group()));
                        } else {
                            // remove brackets, since we have a query like: (perm7)
                            parsed = parsed.replaceAll("[()]", "");
                        }

                        // update matcher to reflect latest text changes
                        nestedConditionsMatcher = bracketsRegex.matcher(parsed);
                    }

                    // now handle the remaining one-liner, AND conditions first
                    if (parsed.contains(" AND ")) { //NON-NLS
                        i = 0;
                        Matcher andOneLinerConditionsMatcher = oneLinerAndRegex.matcher(parsed);

                        while (andOneLinerConditionsMatcher.find()) {
                            if (10000 < i++) {
                                //noinspection UseOfSystemOutOrSystemErr
                                System.out.print("AA - infinite loop at 02!"); //NON-NLS
                                return false;
                            }

                            parsed = parsed.replaceAll(Pattern.quote(andOneLinerConditionsMatcher
                                    .group()), resolveAndOneLiner(sender, andOneLinerConditionsMatcher.group()));

                            // update matcher to reflect latest text changes
                            andOneLinerConditionsMatcher = oneLinerAndRegex.matcher(parsed);
                        }
                    }

                    // handle OR conditions in the remaining one-liner
                    if (parsed.contains(" OR ")) { //NON-NLS
                        //noinspection ReuseOfLocalVariable
                        i = 0;
                        Matcher orOneLinerConditionsMatcher = oneLinerOrRegex.matcher(parsed);

                        while (orOneLinerConditionsMatcher.find()) {
                            if (10000 < i++) {
                                //noinspection UseOfSystemOutOrSystemErr
                                System.out.print("AA - infinite loop at 03!"); //NON-NLS
                                return false;
                            }

                            parsed = parsed.replaceAll(Pattern.quote(orOneLinerConditionsMatcher
                                    .group()), resolveOrOneLiner(sender, orOneLinerConditionsMatcher.group()));

                            // update matcher to reflect latest text changes
                            orOneLinerConditionsMatcher = oneLinerOrRegex.matcher(parsed);
                        }
                    }

                    switch (parsed) {
                        case "true": //NON-NLS
                            hasPerms = true;
                            break;
                        default:
                            hasPerms = !"false".equals(parsed) && checkPermSimple(sender, parsed //NON-NLS
                                                                                                 .replaceAll("[()]", ""));
                            break;
                    }
                } catch (final PatternSyntaxException ex) {
                    // Syntax error in the regular expression
                    Bukkit.getLogger()
                          .severe('[' + AA_API.getAaName() + "] " + AA_API.__("perms.custom-check-failed")); //NON-NLS
                    return false;
                }
            } else {
                Bukkit.getLogger().severe('[' + AA_API.getAaName() + "] " + AA_API
                    .__("perms.invalid-permission", parsed)); //NON-NLS
                return false;
            }
        }

        if (!hasPerms && showResultToSender) {
            sender.sendMessage(ChatColor.RED + AA_API.__("perms.insufficient-permission"));
        }

        return hasPerms;
    } // end method

    /***
     * Gives permission to the player in question.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * aa_plugin_instance.getPluginUtils().addPerm(player, "my_permission");
     * </pre>
     *
     * @param p Player to give a permission to.
     * @param perm The actual permission node to assign to the player.
     *
     * @return Returns true if the permission was added correctly, false otherwise.
     */
    @SuppressWarnings("ConstantConditions")
    boolean addPerm(final Player p, final String perm) {
        if (null != vault && vault.isEnabled()) {
            vault.playerAdd(p, perm);
        }

        return checkPerms(p, perm, false);
    } // end method

    /**
     * Checks whether Vault is enabled on the server.
     *
     * @return Returns true if Vault is enabled, false otherwise.
     */
    boolean isVaultEnabled() {
        return (null != vault);
    } // end method

    /**
     * Checks whether a player is in the given permission group.
     * This check is done via Vault's API and if Vault is not present
     * or enabled, it will return TRUE in case the group name provided
     * was either "global" or "default", FALSE otherwise.
     *
     * @param p Player for which we want to check presence in the permision group.
     * @param group The actual permission group name to check if the player is in.
     *
     * @return Returns TRUE if the player is present in the group, or if Vault is disabled,
     *         then returns TRUE if the player is in either group named "global" or "default".
     *         Returns FALSE otherwise.
     */
    boolean isPlayerInPermGroup(Player p, String group) {
        if (isVaultEnabled() && vault.hasGroupSupport()) {
            return vault.playerInGroup(p, group);
        } else {
            // if Vault is disabled, check if we didn't provide a "global" or "default" group
            // which is the default name for everything and if that's the player's group,
            // let's say this player is in that group
            group = group.toLowerCase();
            if (group.equals("global") || group.equals("default")) {
                return true;
            } else {
                return false;
            }
        }
    } // end method

    /**
     * Returns all permission groups in which a player is present.
     * If Vault is disabled, an empty array is returned.
     *
     * @param p The player to check groups for.
     *
     * @return Returns all permission groups in which a player is present.
     *         If Vault is disabled, an empty array is returned.
     */
    String[] getPlayerPermGroups(Player p) {
        if (isVaultEnabled() && vault.hasGroupSupport()) {
            return vault.getPlayerGroups(p);
        } else {
            return new String[]{};
        }
    } // end method

    /**
     * Retrieves player's primary group name.
     *
     * @param p Player to retrieve primary group info for.
     *
     * @return Returns name of player's primary group or "" (empty string) if Vault is disabled.
     */
    String getPlayerPrimaryPermGroup(Player p) {
        if (isVaultEnabled() && vault.hasGroupSupport()) {
            return vault.getPrimaryGroup(p);
        } else {
            return "";
        }
    } // end method

    /**
     * Retrieves a list of all permission groups on the server.
     *
     * @return Returns a list of all permission groups on the server.
     */
    String[] getAllPermGroups() {
        if (isVaultEnabled() && vault.hasGroupSupport()) {
            return vault.getGroups();
        } else {
            return new String[]{};
        }
    } // end method

} // end class