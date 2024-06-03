package com.martinambrus.adminAnything.listeners;

import com.martinambrus.adminAnything.AA_API;
import com.martinambrus.adminAnything.Utils;
import com.martinambrus.adminAnything.commands.Aa_mutecommand;
import com.martinambrus.adminAnything.events.AAAdjustListenerPrioritiesEvent;
import com.martinambrus.adminAnything.events.AAReloadEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandException;
import org.bukkit.command.CommandSender;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.permissions.Permissible;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InvalidClassException;
import java.lang.reflect.InvocationTargetException;
import java.rmi.AccessException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

/**
 * Listens to player and console commands
 * and handles their fixing, muting, redirecting...
 *
 * @author Martin Ambrus
 */
@SuppressWarnings("OverlyComplexClass")
public class commandPreprocessor implements Listener {

    /**
     * Name of this feature, used when reload event is called
     * to react to the correct one.
     */
    @SuppressWarnings("HardCodedStringLiteral")
    private final String featureName = "commandPreprocessor";

    /**
     * Instance of {@link com.martinambrus.adminAnything.AdminAnything}.
     */
    private static Plugin plugin;

    /**
     * List of command overrides.
     */
    @SuppressWarnings("HardCodedStringLiteral")
    private List<String> commandOverridesList = Utils.makeListMutable(AA_API.getCommandsList("overrides"));

    /**
     * List of virtual permissions.
     */
    @SuppressWarnings("HardCodedStringLiteral")
    private List<String> virtualPermsList = Utils.makeListMutable(AA_API.getCommandsList("virtualperms"));

    /**
     * List of command overrides.
     */
    @SuppressWarnings("HardCodedStringLiteral")
    private List<String> commandRemovalsList = Utils.makeListMutable(AA_API.getCommandsList("removals"));

    /**
     * List of command mutes.
     */
    @SuppressWarnings("HardCodedStringLiteral")
    private List<String> commandMutesList = Utils.makeListMutable(AA_API.getCommandsList("mutes"));

    /**
     * List of command redirects.
     */
    @SuppressWarnings("HardCodedStringLiteral")
    private List<String> commandRedirectsList = Utils.makeListMutable(AA_API.getCommandsList("redirects"));

    /**
     * A map of command mutes, so we can perform quick lookups.
     */
    private Map<String, Boolean> commandMutesMap = AA_API.getMutesMap();

    /**
     * Constructor, stores instance of AdminAnything for further use.
     *
     * @param aa Instance of {@link com.martinambrus.adminAnything.AdminAnything}.
     */
    public commandPreprocessor(final Plugin aa) {
        plugin = aa;
        // load commands and adjust listener priorities for all of our lists
        // but do this only after the server finishes loading and then after
        // a couple of more seconds to make sure we catch all loaded plugins
        Bukkit.getScheduler().scheduleSyncDelayedTask(aa, new Runnable() {

            @Override
            public void run() {
                Bukkit.getScheduler().scheduleSyncDelayedTask(aa, new Runnable() {

                    @Override
                    public void run() {
                        for (final List<String> listReference : Arrays.asList(
                            commandOverridesList, virtualPermsList, commandRemovalsList,
                            commandMutesList, commandRedirectsList)) {
                            loadCommandsList(listReference);
                        }
                    }

                }, 20 * 5); // wait a couple of seconds before we load...
            }

        }, 0); // 0 = will be run as soon as the server finished loading
    } // end method

    /**
     * Reloads lists of commands once our config changes.
     */
    @SuppressWarnings("HardCodedStringLiteral")
    private void onReload() {
        commandOverridesList = Utils.makeListMutable(AA_API.getCommandsList("overrides"));
        virtualPermsList = Utils.makeListMutable(AA_API.getCommandsList("virtualperms"));
        commandRemovalsList = Utils.makeListMutable(AA_API.getCommandsList("removals"));
        commandMutesList = Utils.makeListMutable(AA_API.getCommandsList("mutes"));
        commandRedirectsList = Utils.makeListMutable(AA_API.getCommandsList("redirects"));
        commandMutesMap = AA_API.getMutesMap();
    } // end method

    /**
     * Loads the list of commands / permissions / etc., while
     * adjusting listener priorities for them in one go.
     *
     * @param theList The actual list to load and adjust
     *                listener priorities for.
     */
    @SuppressWarnings("HardCodedStringLiteral")
    private void loadCommandsList(final List<String> theList) {
        // first remove all duplicates from the given list
        for (final List<String> listReference : Arrays.asList(
                commandOverridesList, virtualPermsList, commandRemovalsList,
                commandMutesList, commandRedirectsList)) {
            // don't remove commands from the list we've requested
            // to load data for
            if (theList != listReference) {
                // don't change priorities of commands that have their priorities already changed
                theList.removeAll(listReference);
            }
        }

        // empty lists need no adjustments of listener priorities
        String listName;

        if (!theList.isEmpty()) {
            if (theList == commandOverridesList) {
                listName = "overrides";
            } else if (theList == virtualPermsList) {
                listName = "virtualperms";
            } else if (theList == commandRemovalsList) {
                listName = "removals";
            } else if (theList == commandMutesList) {
                listName = "mutes";
            } else if (theList == commandRedirectsList) {
                listName = "redirects";
            } else {
                listName = "";
            }

            // we need to do this synchronously here, since otherwise the listName variable
            // would get passed into even a syncTask incorrectly
            Bukkit.getPluginManager().callEvent( new AAAdjustListenerPrioritiesEvent(
                    null,
                    null,
                    theList.toArray(new String[theList.size()]),
                    listName,
                    true,
                    null,
                    null
                    )
                    );
        }
    } // end method

    /**
     * Retrieves a new instance of the Virtual Command Sender
     * class from the actual command sender that we know sent
     * the real command. This is so we can call a new command
     * with the credentials and name of the original player / console.
     *
     * @param csender Original command sender.
     *
     * @return Returns the instance of Virtual Command Sender class
     *         with names and permissions cloned from the original
     *         command sender.
     */
    private CommandSender getVirtualSenderFor(final Permissible csender) {
        final VirtualCommandSender sender = new VirtualCommandSender();
        sender.setEffectivePermissions(csender.getEffectivePermissions());
        sender.setOp(csender.isOp());

        return sender;
    } // end method

    /**
     * Checks whether the event is a console event.
     *
     * @param e The event to check.
     *
     * @return Returns true if the event comes from console,
     *         false otherwise.
     */
    private boolean isConsoleEvent(final Event e) {
        return e instanceof ServerCommandEvent;
    } // end method

    /**
     * Returns the actual command sender from either
     * a console or player event.
     *
     * @param e The event from which we return command sender.
     *
     * @return The CommandSender who initiated this command event.
     */
    private CommandSender getCommandSender(final Event e) {
        boolean isConsole = isConsoleEvent(e);
        return isConsole ? ((ServerCommandEvent) e).getSender() : ((PlayerEvent) e).getPlayer();
    } // end method

    /**
     * Checks whether the given command is not disabled
     * and cancels it out if it is.
     *
     * @param cmd The actual command to check.
     * @param e Either console or player command event.
     *
     * @return Returns true if this command is disabled, false otherwise.
     */
    private boolean checkDisabledCommand(String cmd, final Event e) {
        // check if we come from the console
        final boolean       isConsole = isConsoleEvent(e);
        final CommandSender csender   = getCommandSender(e);

        if (
            AA_API.isFeatureEnabled("disablecommand") && //NON-NLS
            commandRemovalsList.contains(cmd.toLowerCase()) &&
            !AA_API.checkPerms(csender, "aa.bypassdeletecommand OR aa.bypassdeletecommand.all OR aa.bypassdeletecommand." + cmd.toLowerCase(), false) //NON-NLS
        ) {
            // console command sender
            if (isConsole) {
                cancelConsoleEvent((ServerCommandEvent) e);
                csender.sendMessage(AA_API.__("listeners.preprocessor-command-disabled", AA_API.getAaName()));
            } else {
                // player command sender
                ((Cancellable) e).setCancelled(true);
                Bukkit.getLogger().info(
                    '[' + plugin.getDescription().getName() + "] " +
                        AA_API.__("listeners.preprocessor-cancelling-disabled-command", cmd)
                );
            }

            return true;
        } else {
            return false;
        }
    } // end method

    /**
     * Checks whether a custom permission is not set for this command
     * and whether the command sender needed it to execute.
     *
     * @param cmd The command line to check custom permission for.
     * @param e Command event used for various checks.
     *
     * @return Returns true if the command sender needs a custom permision
     *         but does not have it, false otherwise.
     */
    private boolean needsCustomPermission(final String cmd, final Event e) {
        // check if we come from the console
        final boolean       isConsole = isConsoleEvent(e);
        final CommandSender csender   = getCommandSender(e);

        //noinspection HardCodedStringLiteral
        if (AA_API.isFeatureEnabled("addperm")) {
            //noinspection HardCodedStringLiteral
            for (final Entry<String, Object> permCmdLine : AA_API.getCommandsConfigurationValues("virtualperms").entrySet()) {
                if (permCmdLine.getValue().equals(cmd)) {
                    // check the permission
                    if (!AA_API.checkPerms(csender, permCmdLine.getKey(), false)) {
                        if (isConsole) {
                            //noinspection HardCodedStringLiteral
                            cancelConsoleEvent((ServerCommandEvent) e, "list");
                        } else {
                            ((Cancellable) e).setCancelled(true);
                        }

                        // show the message
                        csender.sendMessage(ChatColor.RED + AA_API.__("listeners.preprocessor-no-permisison"));
                        return true;
                    }
                }
            }
        }

        // no custom permission needed
        return false;
    } // end method

    /**
     * Checks whether the requested command was not muted.
     * If it was, the original CommandSender will be replaced
     * by a Virtual Command Sender instance, so any messages
     * from this muted command are sent to it instead.
     *
     * @param cmdOriginal The original command to check against.
     * @param csender The original CommandSender for this command.
     * @param updateSender The mode for this method. True means we update
     *                     the CommandSender and that's done when a first
     *                     check for muted command is performend in an event.
     *                     False means we'll actually mute the command itself
     *                     by instrumenting server classes and dispatching it
     *                     as a Virtual CommandSender.
     */
    @SuppressWarnings({"NonConstantStringShouldBeStringBuffer", "ConstantConditions", "HardCodedStringLiteral"})
    private void checkMutedCommand(final String cmdOriginal, CommandSender csender, final Event e, final boolean updateSender) {
        if (AA_API.isFeatureEnabled("mutecommand")) {
            final String[]      cmdParams       = cmdOriginal.split(Pattern.quote(" "));
            final int           cmdParamsLength = cmdParams.length;

            for (int i = 0; i < cmdParamsLength; i++) {
                StringBuilder cmdLineBuilder = new StringBuilder();
                for (int x = 0; x <= i; x++) {
                    cmdLineBuilder.append(' ').append(cmdParams[x]);
                }
                String cmdLine = cmdLineBuilder.toString();
                cmdLine = cmdLine.trim();

                if (commandMutesMap.containsKey(cmdLine)) {
                    // update sender only
                    if (updateSender) {
                        Aa_mutecommand.captureNextCommandSender = true;
                        if (!Aa_mutecommand.retransformed) {
                            // replace sender with VirtualCommandSender if we couldn't transform
                            csender = getVirtualSenderFor(csender);

                            // this one is used for a Callable routine below
                            final CommandSender finalCsender = csender;
                        }
                    } else{
                        // this one is used for a Callable routine below
                        final CommandSender finalCsender = csender;

                        // instrument classes, mute the command and send it out
                        // as a Virtual Command Sender
                        if (!Aa_mutecommand.retransformed) {
                            // dispatch this command via VirtualCommandSender if we couldn't transform
                            Aa_mutecommand.lastMuteTimestamp = Utils.getUnixTimestamp(0L);
                            if (isConsoleEvent(e)) {
                                cancelConsoleEvent((ServerCommandEvent) e, "list");

                                Bukkit.getScheduler().callSyncMethod(plugin, new Callable<Boolean>() {

                                    @Override
                                    public Boolean call() {
                                        return Bukkit.dispatchCommand(finalCsender, cmdOriginal);
                                    }

                                });
                            } else {
                                ((Cancellable) e).setCancelled(true);

                                Bukkit.getScheduler().callSyncMethod(plugin, new Callable<Boolean>() {

                                    @Override
                                    public Boolean call() {
                                        return Bukkit.dispatchCommand(finalCsender, ((PlayerCommandPreprocessEvent) e).getMessage().substring(1));
                                    }

                                });
                            }
                        } else {
                            // transformed class will capture command sender of this one for us
                            Aa_mutecommand.lastMuteTimestamp = Utils.getUnixTimestamp(0L);
                        }
                    }
                }
            }
        }
    } // end method

    /**
     * Cancels and overrides the given command event,
     * so we can fix this command and run it from the plugin
     * which the Admin has set.
     *
     * @param csender The originator for this command. Could be VirtualCommandSender or console.
     * @param e The actual command event.
     * @param originalOverride The command we want to run instead of the intercepted one.
     */
    @SuppressWarnings("HardCodedStringLiteral")
    private void cancelAndOverride(final CommandSender csender, Event e, final String originalOverride) {
        try {
            if (csender instanceof VirtualCommandSender) {
                cancelConsoleEvent((ServerCommandEvent) e, "list");
                Aa_mutecommand.lastMuteTimestamp = Utils.getUnixTimestamp(0L);

                Bukkit.getScheduler().callSyncMethod(plugin, new Callable<Boolean>() {

                    @Override
                    public Boolean call() {
                        return Bukkit.dispatchCommand(csender, originalOverride);
                    }

                });
            } else {
                ((ServerCommandEvent) e).setCommand(originalOverride);
            }
        } catch (IllegalAccessError ex) {
            // MC 1.7
            ((ServerCommandEvent) e).setCommand(originalOverride);
        }
    } // end method

    /**
     * Checks for command overrides for the given command.
     *
     * @param cmd The command to check for overrides for.
     * @param e The original command event.
     *
     * @return Returns true if a command was overridden and thus
     *         cancelled and ran again from the plugin set up in
     *         the config file. Otherwise returns false.
     *
     * @throws AccessException When we don't have access to instrumenting classes inside the JVM.
     * @throws IllegalAccessException When we don't have access to instrumenting classes inside the JVM.
     * @throws NoSuchMethodException When we couldn't find the correct method in a server class while trying to transform it.
     * @throws SecurityException When we were not allowed to instrument classes inside the JVM.
     * @throws InvocationTargetException When we're stupid and don't remember how to do what we wanted to.
     * @throws InvalidClassException When we couldn't find a plugin for a command override.
     * @throws InvalidParameterException When we're dumb and can't remember what parameters to pass to classes via Reflection.
     * @throws ClassNotFoundException When we couldn't find the plugin or command for one of the command overrides.
     */
    @SuppressWarnings("HardCodedStringLiteral")
    private boolean checkCommandOverride(String cmd, final Event e) throws AccessException, IllegalAccessException,
            NoSuchMethodException, SecurityException, InvocationTargetException, InvalidClassException, InvalidParameterException,
            ClassNotFoundException {
        // check if we come from the console
        final boolean       isConsole = isConsoleEvent(e);
        final CommandSender csender   = getCommandSender(e);
        String clearCommandName;
        String[] commandSplitted = null;

        if (cmd.contains(" ")) {
            commandSplitted = cmd.split(Pattern.quote(" "));
            clearCommandName = commandSplitted[0];
        } else {
            clearCommandName = cmd;
        }

        if (AA_API.isFeatureEnabled("fixcommand") && AA_API.getCommandsList("overrides").contains(clearCommandName)) {
            // command arguments array
            String[] args = null;

            // cancel the event here
            if (!isConsole) {
                ((Cancellable) e).setCancelled(true);

                // turn parameters into array
                if (commandSplitted != null) {
                    final int commandSplittedLength = commandSplitted.length;

                    if (null != commandSplitted[0] && commandSplitted[0].isEmpty()) {
                        // this is not a real command, bail out
                        // note: by returning true, we don't allow any more processing,
                        //       as this is the last check in the event handler, so we'll
                        //       basically end the event method here
                        return true;
                    }

                    cmd = commandSplitted[0];
                    final List<String> params = new ArrayList<String>(Arrays.asList(commandSplitted).subList(1, commandSplittedLength));
                    args = params.toArray(new String[params.size()]);
                } else {
                    args = new String[0];
                }
            }

            // prepare the override
            final String originalOverride = AA_API.getCommandsConfigurationValue("overrides", cmd);
            final String[] spl = originalOverride.split(Pattern.quote(":"));
            Plugin p = null;
            boolean overrideGoesToCoreCommand = false;

            // no plugin for core commands
            if ("minecraft".equalsIgnoreCase(spl[0]) || "spigot".equalsIgnoreCase(spl[0])
                || "bukkit".equalsIgnoreCase(spl[0])) {
                overrideGoesToCoreCommand = true;
            } else {
                // validate
                p = AA_API.getPluginIgnoreCase(spl[0]);
            }

            // did we find the override specified?
            if (null != p || overrideGoesToCoreCommand) {
                if (overrideGoesToCoreCommand || (null != ((JavaPlugin) p).getCommand(spl[1]))) {

                    // player command event
                    if (!isConsole) {
                        if (overrideGoesToCoreCommand) {
                            Aa_mutecommand.lastMuteTimestamp = Utils.getUnixTimestamp(0L);
                            AA_API.getCommandMapKey(spl[0].toLowerCase() + ':' + spl[1].toLowerCase()).execute(csender,
                                    originalOverride.toLowerCase(), args);
                        } else {
                            Aa_mutecommand.lastMuteTimestamp = Utils.getUnixTimestamp(0L);
                            ((JavaPlugin) p).getCommand(spl[1].toLowerCase()).execute(csender,
                                    originalOverride.toLowerCase(), args);
                        }
                    } else {
                        cancelAndOverride(csender, e, originalOverride);
                    }

                } else {
                    // getCommand() was not able to find the command,
                    // we'll need to do a lookup in the CommandMap ourselves
                    final String pluginName;
                    final String lowerCasedCommand = originalOverride.toLowerCase();
                    String commandLocationParsed;

                    if (AA_API.commandMapContainsKey(lowerCasedCommand)) {
                        // exact match found (for example essentials:ban)
                        // we can bail out here, since it's natural that this is the exact command to execute
                        if (!isConsole) {
                            // player command event
                            Aa_mutecommand.lastMuteTimestamp = Utils.getUnixTimestamp(0L);
                            AA_API.getCommandMapKey(spl[0].toLowerCase() + ':' + spl[1].toLowerCase()).execute(csender,
                                    originalOverride.toLowerCase(), args);
                            return true;
                        } else {
                            cancelAndOverride(csender, e, lowerCasedCommand);
                            return true;
                        }
                    } else if (AA_API.commandMapContainsKey(spl[1])) {
                        // prefix-less match found (for example ban)
                        // we need to check that this is the plugin we need to be executed here
                        commandLocationParsed = AA_API.parsePluginJARLocation(AA_API.getCommandMapKey(spl[1]).getClass());
                    } else {
                        // nothing found
                        throw new ClassNotFoundException('[' + plugin.getDescription().getName()
                                + "] No reference can be found in CommandMap for " + AA_API.getCommandsConfigurationValue("overrides", cmd));
                    }

                    // check if this command belongs to any of the loaded plugins
                    pluginName = AA_API.classMapContainsKey(commandLocationParsed) ?
                                 AA_API.getClassMapKey(commandLocationParsed) : lowerCasedCommand;

                    // is the plugin for this command really the one we're looking for?
                    if ((null != pluginName) && pluginName.equals(spl[0])) {
                        if (!isConsole) {
                            // player command sender
                            Aa_mutecommand.lastMuteTimestamp = Utils.getUnixTimestamp(0L);
                            AA_API.getCommandMapKey(spl[1].toLowerCase()).execute(csender, originalOverride.toLowerCase(), args);
                        } else {
                            // console command sender
                            try {
                                if (csender instanceof VirtualCommandSender) {
                                    cancelConsoleEvent((ServerCommandEvent) e, "list");
                                    Aa_mutecommand.lastMuteTimestamp = Utils.getUnixTimestamp(0L);

                                    Bukkit.getScheduler().callSyncMethod(plugin, new Callable<Boolean>() {

                                        @Override
                                        public Boolean call() {
                                            return Bukkit.dispatchCommand(csender, spl[1].toLowerCase());
                                        }

                                    });
                                } else {
                                    ((ServerCommandEvent) e).setCommand(spl[1].toLowerCase());
                                }
                            } catch (IllegalAccessError ex) {
                                // MC 1.7
                                ((ServerCommandEvent) e).setCommand(spl[1].toLowerCase());
                            }
                        }
                    } else {
                        Bukkit.getLogger().warning(
                            '[' + plugin.getDescription().getName() + "] " +
                                AA_API.__(
                                    "config.error-command-not-found",
                                    originalOverride,
                                    AA_API.__("config.name-command-overrides")
                                )
                        );
                    }
                }
            } else {
                Bukkit.getLogger().warning(
                    '[' + plugin.getDescription().getName() + "] " +
                        AA_API.__(
                            "config.error-plugin-not-found",
                            spl[0],
                            AA_API.__("config.name-command-overrides")
                        )
                );
            }
        }

        return false;
    } // end method

    /***
     * Redirects command from the old one (via fixCommand) to a new one that should replace it.
     *
     * If a command is in the list of removed commands instead, this will prevent it from running.
     *
     * This is for player commands only.
     *
     * @param e The player pre-process event to work with.
     *
     * @throws InvalidClassException When we couldn't determine this command's plugin.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void redirectPlayerCommand(final PlayerCommandPreprocessEvent e) throws InvalidClassException {
        final String cmd = e.getMessage().substring(1);
        final String clearCommandName;
        final String[] commandParameters;

        // if the command has parameters, get the actual command
        // and its parameters separately for some of the checks below
        if (cmd.contains(" ")) {
            String[] spl = cmd.split(Pattern.quote(" "));
            clearCommandName = spl[0];
            List<String> list = new ArrayList<String>(Arrays.asList(spl));
            list.remove(0);
            commandParameters = list.toArray(new String[0]);
        } else {
            clearCommandName = cmd;
            commandParameters = new String[]{ cmd };
        }

        // check for fake commands, like "/" or "/ "
        if (cmd.isEmpty() || cmd.matches("^[ ]+$")) {
            return;
        }

        // this command should not be allowed to run and this player does not have a permission to override
        final CommandSender csender = e.getPlayer();
        if (checkDisabledCommand(clearCommandName, e)) {
            return;
        }

        // verify that we don't have a virtual permission for this command line to check
        if (needsCustomPermission(cmd, e)) {
            return;
        }

        // check if this command is not redirected
        //noinspection HardCodedStringLiteral
        if (AA_API.isFeatureEnabled("redirectcommand") && commandRedirectsList.contains(clearCommandName)) {
            e.setCancelled(true);
            //noinspection HardCodedStringLiteral
            Bukkit.getScheduler().callSyncMethod(plugin, new Callable<Boolean>() {

                @Override
                public Boolean call() {
                    return Bukkit.dispatchCommand(csender, AA_API.getCommandsConfigurationValue("redirects", clearCommandName) + " " + String.join(" ", commandParameters));
                }

            });
            return;
        }

        // if this command is muted, it needs to be sent out via a virtual command sender
        // in order to prevent chat messages be sent back to this player
        checkMutedCommand(cmd, csender, e, true);

        try {
            if (!checkCommandOverride(cmd, e)) {
                // actually mute the muted command
                checkMutedCommand(cmd, csender, e, false);
            }
        } catch (AccessException | InvalidParameterException | IllegalAccessException | NoSuchMethodException | SecurityException |
                InvocationTargetException | CommandException | ClassNotFoundException e1) {
            e.getPlayer().sendMessage(
                ChatColor.RED + AA_API.__("error.general-for-chat"));
            Bukkit.getLogger().severe(e1.getMessage());
            e1.printStackTrace();
        }
    } // end method

    /**
     * Since we cannot cancel console command events, we replace the command
     * by one that doesn't exist, so we can still "cancel" it this way.
     *
     * @param e The actual server command event.
     * @param replacementCommand A replacement command to execute instead.
     */
    private void cancelConsoleEvent(final ServerCommandEvent e, final String... replacementCommand) {
        try {
            e.getClass().getDeclaredMethod("setCancelled", boolean.class).invoke(e, true);
        } catch (NoSuchMethodException | IllegalAccessException | IllegalArgumentException
                | InvocationTargetException ex) {
            // Bukkit < 1.8 does not support cancelling of console commands
            if (0 < replacementCommand.length) {
                e.setCommand(replacementCommand[0]);
            } else {
                e.setCommand("IShallNotExist"); //NON-NLS
            }
        }
    } // end method

    /***
     * Redirects command from the old one (via fixCommand) to a new one that should replace it.
     *
     * If a command is in the list of removed commands instead, this will prevent it from running.
     *
     * This is for console commands only.
     *
     * @param e The server command event to work with.

     * @throws InvalidClassException When we couldn't determine this command's plugin.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void redirectConsoleCommand(final ServerCommandEvent e) throws InvalidClassException {
        final String cmd;
        final String cmdOriginal = e.getCommand();

        if (cmdOriginal.contains(" ")) {
            final String[] spl = cmdOriginal.split(Pattern.quote(" "));

            if (null != spl[0] && spl[0].isEmpty()) {
                // this is not a real command, bail out
                return;
            }

            cmd = spl[0];
        } else {
            cmd = cmdOriginal;
        }

        // this command should not be allowed to run
        final CommandSender csender = e.getSender();
        if (null != cmd && checkDisabledCommand(cmd, e)) {
            return;
        }

        // verify that we don't have a virtual permission for this command line to check
        if (needsCustomPermission(cmdOriginal, e)) {
            return;
        }

        // check if this command is not redirected
        //noinspection HardCodedStringLiteral
        if (AA_API.isFeatureEnabled("redirectcommand") && commandRedirectsList.contains(cmd)) {
            //noinspection HardCodedStringLiteral
            Bukkit.getScheduler().callSyncMethod(plugin, new Callable<Boolean>() {

                    @Override
                    public Boolean call() {
                        return Bukkit.dispatchCommand(csender, AA_API.getCommandsConfigurationValue("redirects", cmd));
                    }

            });

            //noinspection HardCodedStringLiteral
            cancelConsoleEvent(e, "list");
            return;
        }

        // if this command is muted, it needs to be sent out via a virtual command sender
        // in order to prevent chat messages be sent back to this player
        checkMutedCommand(cmdOriginal, csender, e, true);

        // check whether we don't need to override this command
        // and call it from a specific plugin
        try {
            if (!checkCommandOverride(cmd, e)) {
                // actually mute the muted command
                checkMutedCommand(cmdOriginal, csender, e, false);
            }
        } catch (AccessException | InvalidParameterException | IllegalAccessException | NoSuchMethodException | SecurityException |
                InvocationTargetException | CommandException | ClassNotFoundException e1) {
            Bukkit.getLogger().severe(e1.getMessage());
            e1.printStackTrace();
        }
    } // end method

    /***
     * React to the custom ReloadEvent which will reload
     * all command lists internal to this event listener
     * whenevet our configuration changes.
     *
     * @param e The actual reload event with message that says who is this reload for.
     */
    @SuppressWarnings("HardCodedStringLiteral")
    @EventHandler(priority = EventPriority.LOWEST)
    public void reload(final AAReloadEvent e) {
        final String msg = e.getMessage();
        if ("commandPreprocessor".equals(msg)) {
            onReload();
        }
    } // end method

} // end class