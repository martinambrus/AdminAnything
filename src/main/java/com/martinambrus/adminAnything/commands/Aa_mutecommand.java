package com.martinambrus.adminAnything.commands;

import com.martinambrus.adminAnything.AA_API;
import com.martinambrus.adminAnything.Constants;
import com.martinambrus.adminAnything.LogFilter;
import com.martinambrus.adminAnything.Utils;
import com.martinambrus.adminAnything.events.AAAdjustListenerPrioritiesEvent;
import com.martinambrus.adminAnything.events.AAReloadEvent;
import com.martinambrus.adminAnything.events.AASaveMutedCommandsEvent;
import com.martinambrus.adminAnything.instrumentation.Instrumentator;
import mkremins.fanciful.FancyMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.*;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Command which mutes any possible output from
 * the given executed command.
 *
 * The methodology here is a little complicated,
 * so let's get into explanations here.
 *
 * First, all methods containing a call to the sendMessage()
 * method are instrumented (i.e. changed in-memory),
 * so we first check whether a muted command was run beforehand.
 *
 * If a muted command was run before just before sendMessage()
 * was called, we simply cancel it. There is no way for us to know
 * exactly which command has requested this because of the events system,
 * so we have to mute everything until a certain time has passed.
 *
 * This timeout time is set to 1.5 seconds by default, which is by far
 * enough to mute anything that should be trying to call sendMessage().
 *
 * After this timeout is reached, we reset our internal status and will
 * no longer be muting any commands up until another command that should
 * be muted is run - at which point this whole circus starts over :-P
 *
 * @author Martin Ambrus
 */
public class Aa_mutecommand extends AbstractCommand {

    /**
     * Determines how long ago has the last command
     * that should be muted ran.
     */
    public static int lastMuteTimestamp = 0;

    /**
     * Stores a name for the class which called
     * a muted command.
     */
    public static String newMutedClass;

    /**
     * Determines whether the last class that called sendMessage()
     * was muted.
     */
    public static boolean lastClassMuted = false;

    /**
     * Determined whether we should capture the name
     * of the current class that calls sendMessage().
     */
    public static boolean captureNextCommandSender = false;

    /**
     * Determines whether we've re-transformed any classes yet.
     * This will be set to true after the first instrumentation,
     * so we don't do this unnecessarily again and again.
     */
    public static boolean retransformed = false;

    /**
     * Used to check whether we need to try to download the javassist
     * library, if it's not present.
     */
    public static boolean retransformationTried = false;

    /**
     * Determines whether we're ready to start the instrumentation.
     */
    public static boolean readyToRetransform = false;

    /**
     * This will hold a LogFilter that's used when we cannot run
     * instrumentation for any reason. It will not mute much but still
     * can be used to mute at least some output.
     */
    public static LogFilter logFilter;

    /**
     * Maximum time to cancel all sendMessage() calls for.
     */
    private static final double maxMuteCheckTimeout = 1.5;

    /**
     * Cache.
     * A map with all classes that should be muted.
     */
    private static final Map<String, Integer> mutedClasses = new HashMap<String, Integer>();

    /**
     * Instance of {@link com.martinambrus.adminAnything.instrumentation.Instrumentator}.
     */
    private static Instrumentator instrumentator;

    /**
     * Instance of {@link com.martinambrus.adminAnything.AdminAnything}.
     */
    private final Plugin plugin;

    /**
     * Constructor, takes AdminAnything as a parameter,
     * since we'll be needing it later to set up a delayed
     * task (as not to overload server by all the action :P).
     *
     * Also starts the commandPreprocessor listener
     * if it was not started yet, so our fixed, muted, disabled...
     * commands can get pre-processed by AdminAnything.
     *
     * @param aa Instance of {@link com.martinambrus.adminAnything.AdminAnything}.
     */
    public Aa_mutecommand(final Plugin aa) {
        plugin = aa;

        // support for 32-bit architectures has been dropped
        if (!Utils.is64Bit()) {
            return;
        }

        //noinspection HardCodedStringLiteral
        AA_API.startRequiredListener("commandPreprocessor");

        // apply server core classes transformations if we have any commands muted already
        // note: we have to do this once the server is fully loaded, otherwise we wouldn't be able to get
        //       this command's command executor, i.e. this very class and we'd get an instance of AA instead
        //       in AATransformAgent
        //noinspection HardCodedStringLiteral
        if (!AA_API.getCommandsList("mutes").isEmpty()) {
            // check for native overrides system being enabled
            // and delay showing to user at the end of server load,
            // so it's well visible
            Bukkit.getScheduler().scheduleSyncDelayedTask(aa, new Runnable() {

                @Override
                public void run() {
                    readyToRetransform = true;
                    retransformCBMC();
                    retransformationTried = true;
                }

            }, 0); // 0 = will be run as soon as the server finished loading
        }
    } // end method

    /**
     * Prepares a map of transformable classes that we need to check for
     * on the server.
     *
     * @return Returns a map of all transformable classes that we need to check for.
     */
    @SuppressWarnings("HardCodedStringLiteral")
    public static Map<String, Map<String, String>> transformations() {
        // we'll need to provide state fields to the main AdminAnything class here
        final Map<String, Map<String, String>> retransformations = new HashMap<String, Map<String, String>>();

        if (!AA_API.isFeatureEnabled("mutecommand")) {
            return retransformations;
        }

        final String mapUUID = "aa_mutecommand";
        String serverVersion = '/' + Utils.getMinecraftVersion() + '/';

        // prepare code to inject into existing server methods calling sendMessage()
        final String muteCheckCode = "" + "try {" + "  String commandCaller;"
                // get the calling class
                + "  try {" + "    commandCaller = sun.reflect.Reflection.getCallerClass(3).getName();"
                // adjust if we got Bukkit native class - that means our plugin is 1 position to the front
                + "    if (commandCaller.startsWith(\"org.bukkit\") || commandCaller.startsWith(\"org.bukkit\")) {"
                + "      commandCaller = sun.reflect.Reflection.getCallerClass(2).getName();" + "    }"
                + "  } catch (java.lang.Exception e) {"
                + "    com.martinambrus.adminAnything.instrumentation.MySecurityManager mm = new com.martinambrus.adminAnything.instrumentation.MySecurityManager();"
                + "    commandCaller = mm.getCallerClassName(3);"
                // adjust if we got Bukkit native class - that means our plugin is 1 position to the front
                + "    if (commandCaller.startsWith(\"org.bukkit\") || commandCaller.startsWith(\"org.bukkit\")) {"
                + "      commandCaller = mm.getCallerClassName(2);" + "    }" + "  }" + ""
                // don't mute players talking to other players and don't continue if we couldn't get
                // the caller class name
                + "  if (commandCaller != null && !commandCaller.endsWith(\".PlayerConnection\")) {"
                // get command executor for Aa_mutecommand, so we can work with it
                + "    org.bukkit.command.CommandExecutor executor = org.bukkit.Bukkit.getPluginCommand(\"aa_mutecommand\").getExecutor();"
                + "    boolean isClassMuted = false;" + "  "
                // always set newMutedClass to our current caller, so the command executor method
                // will be able to do a lookup if our class is one of the muted ones and thus
                // needs to be muted :P (we can't work with maps and lists here /VerifyError/)
                + "    executor.getClass().getDeclaredField(\"newMutedClass\").set(null, commandCaller);" + "  "
                // should we be muting the current class? (since it is the first one to message player / broadcast
                // after a muted command was ran)
                + "    boolean captureNextCommandSender = executor.getClass().getDeclaredField(\"captureNextCommandSender\").getBoolean(null);"
                + "    if (captureNextCommandSender) {"
                // check that we're not after 1.5s threshold, in which case we're no longer muting anyone
                + "      executor.getClass().getDeclaredMethod(\"verifyMuteCheckTimeout\", null).invoke(null, null);"
                // update captureNextCommandSender which will be changed now if we're beyond the timout
                + "      captureNextCommandSender = executor.getClass().getDeclaredField(\"captureNextCommandSender\").getBoolean(null);"
                // we're still within allowed time, let's mute this one
                + "      if (captureNextCommandSender) {" + "        isClassMuted = true;" + "      }" + "    } else {"
                + "      executor.getClass().getDeclaredMethod(\"checkIsClassMuted\", null).invoke(null, null);"
                + "      isClassMuted = executor.getClass().getDeclaredField(\"lastClassMuted\").getBoolean(null);"
                + "    }" + "  "
                // let the command executor add this class to the map of muted classes
                + "    if (captureNextCommandSender) {"
                + "      executor.getClass().getDeclaredMethod(\"setCurrentClassMuted\", null).invoke(null, null);"
                + "    }" + "  " + "    if (isClassMuted) {" + "      return;" + "    }" + "  }"
                + "} catch (java.lang.Exception exc) {}";

        // adjust CraftServer.broadcastMessage(), so it will return when ran from a muted command
        final Map<String, String> broadcastMessagePatch = new HashMap<String, String>();
        final String broadcastMessageClass = "org/bukkit/craftbukkit" + serverVersion + "CraftServer";
        broadcastMessagePatch.put("methodName" + mapUUID, "broadcastMessage");
        broadcastMessagePatch.put("methodCode" + mapUUID, muteCheckCode.replace("return;", "return true;"));
        broadcastMessagePatch.put("methodPosition" + mapUUID, "before");
        if (retransformations.containsKey(broadcastMessageClass)) {
            broadcastMessagePatch.putAll(retransformations.get(broadcastMessageClass));
        }
        retransformations.put(broadcastMessageClass, broadcastMessagePatch);

        // adjust CraftPlayer.sendMessage(), so it will return when ran from a muted command
        final Map<String, String> playerSendMessagePatch = new HashMap<String, String>();
        final String playerSendMessageClass = "org/bukkit/craftbukkit" + serverVersion + "entity/CraftPlayer";
        playerSendMessagePatch.put("methodName" + mapUUID, "sendMessage");
        playerSendMessagePatch.put("methodCode" + mapUUID, muteCheckCode);
        playerSendMessagePatch.put("methodPosition" + mapUUID, "before");
        if (retransformations.containsKey(playerSendMessageClass)) {
            playerSendMessagePatch.putAll(retransformations.get(playerSendMessageClass));
        }
        retransformations.put(playerSendMessageClass, playerSendMessagePatch);

        // adjust CraftMinecartCommand.sendMessage(), so it will return when ran from a muted command
        final Map<String, String> minecartCommandSendMessagePatch = new HashMap<String, String>();
        final String minecartCommandSendMessageClass = "org/bukkit/craftbukkit" + serverVersion
                + "entity/CraftMinecartCommand";
        minecartCommandSendMessagePatch.put("methodName" + mapUUID, "sendMessage");
        minecartCommandSendMessagePatch.put("methodCode" + mapUUID, muteCheckCode);
        minecartCommandSendMessagePatch.put("methodPosition" + mapUUID, "before");
        if (retransformations.containsKey(minecartCommandSendMessageClass)) {
            minecartCommandSendMessagePatch.putAll(retransformations.get(minecartCommandSendMessageClass));
        }
        retransformations.put(minecartCommandSendMessageClass, minecartCommandSendMessagePatch);

        // adjust CraftEntity.sendMessage(), so it will return when ran from a muted command
        final Map<String, String> entitySendMessagePatch = new HashMap<String, String>();
        final String entitySendMessageClass = "org/bukkit/craftbukkit" + serverVersion + "entity/CraftEntity";
        entitySendMessagePatch.put("methodName" + mapUUID, "sendMessage");
        entitySendMessagePatch.put("methodCode" + mapUUID, muteCheckCode);
        entitySendMessagePatch.put("methodPosition" + mapUUID, "before");
        if (retransformations.containsKey(entitySendMessageClass)) {
            entitySendMessagePatch.putAll(retransformations.get(entitySendMessageClass));
        }
        retransformations.put(entitySendMessageClass, entitySendMessagePatch);

        // adjust ProxiedNativeCommand.sendMessage(), so it will return when ran from a muted command
        final Map<String, String> proxyCommandSendMessagePatch = new HashMap<String, String>();
        final String proxyCommandSendMessageClass = "org/bukkit/craftbukkit" + serverVersion
                + "command/ProxiedNativeCommandSender";
        proxyCommandSendMessagePatch.put("methodName" + mapUUID, "sendMessage");
        proxyCommandSendMessagePatch.put("methodCode" + mapUUID, muteCheckCode);
        proxyCommandSendMessagePatch.put("methodPosition" + mapUUID, "before");
        proxyCommandSendMessagePatch.put("methodExcludeVersions" + mapUUID, "v1_7");
        if (retransformations.containsKey(proxyCommandSendMessageClass)) {
            proxyCommandSendMessagePatch.putAll(retransformations.get(proxyCommandSendMessageClass));
        }
        retransformations.put(proxyCommandSendMessageClass, proxyCommandSendMessagePatch);

        // adjust CraftRemoteConsoleCommandSender.sendMessage(), so it will return when ran from a muted command
        final Map<String, String> remoteCommandSendMessagePatch = new HashMap<String, String>();
        final String remoteCommandSendMessageClass = "org/bukkit/craftbukkit" + serverVersion
                + "command/CraftRemoteConsoleCommandSender";
        remoteCommandSendMessagePatch.put("methodName" + mapUUID, "sendMessage");
        remoteCommandSendMessagePatch.put("methodCode" + mapUUID, muteCheckCode);
        remoteCommandSendMessagePatch.put("methodPosition" + mapUUID, "before");
        if (retransformations.containsKey(remoteCommandSendMessageClass)) {
            remoteCommandSendMessagePatch.putAll(retransformations.get(remoteCommandSendMessageClass));
        }
        retransformations.put(remoteCommandSendMessageClass, remoteCommandSendMessagePatch);

        // adjust CraftConsoleCommandSender.sendMessage(), so it will return when ran from a muted command
        final Map<String, String> consoleCommandSendMessagePatch = new HashMap<String, String>();
        final String consoleCommandSendMessageClass = "org/bukkit/craftbukkit" + serverVersion
                + "command/CraftConsoleCommandSender";
        consoleCommandSendMessagePatch.put("methodName" + mapUUID, "sendMessage");
        consoleCommandSendMessagePatch.put("methodCode" + mapUUID, muteCheckCode);
        consoleCommandSendMessagePatch.put("methodPosition" + mapUUID, "before");
        consoleCommandSendMessagePatch.put("methodName" + mapUUID + '2', "sendRawMessage");
        consoleCommandSendMessagePatch.put("methodCode" + mapUUID + '2', muteCheckCode);
        consoleCommandSendMessagePatch.put("methodPosition" + mapUUID + '2', "before");
        if (retransformations.containsKey(consoleCommandSendMessageClass)) {
            consoleCommandSendMessagePatch.putAll(retransformations.get(consoleCommandSendMessageClass));
        }
        retransformations.put(consoleCommandSendMessageClass, consoleCommandSendMessagePatch);

        // adjust CraftFunctionCommandSender.sendMessage(), so it will return when ran from a muted command
        final Map<String, String> functionCommandSendMessagePatch = new HashMap<String, String>();
        final String functionCommandSendMessageClass = "org/bukkit/craftbukkit" + serverVersion
                + "command/CraftFunctionCommandSender";
        functionCommandSendMessagePatch.put("methodName" + mapUUID, "sendMessage");
        functionCommandSendMessagePatch.put("methodCode" + mapUUID, muteCheckCode);
        functionCommandSendMessagePatch.put("methodPosition" + mapUUID, "before");
        functionCommandSendMessagePatch.put("methodOptional" + mapUUID, "1");
        if (retransformations.containsKey(functionCommandSendMessageClass)) {
            functionCommandSendMessagePatch.putAll(retransformations.get(functionCommandSendMessageClass));
        }
        retransformations.put(functionCommandSendMessageClass, functionCommandSendMessagePatch);

        // adjust EntityPlayer.sendMessage(), so it will return when ran from a muted command
        final Map<String, String> entityPlayerCommandSendMessagePatch = new HashMap<String, String>();
        final String entityPlayerCommandSendMessageClass = "net/minecraft/server" + serverVersion + "EntityPlayer";
        entityPlayerCommandSendMessagePatch.put("methodName" + mapUUID, "sendMessage");
        entityPlayerCommandSendMessagePatch.put("methodCode" + mapUUID, muteCheckCode);
        entityPlayerCommandSendMessagePatch.put("methodPosition" + mapUUID, "before");
        if (retransformations.containsKey(entityPlayerCommandSendMessageClass)) {
            entityPlayerCommandSendMessagePatch.putAll(retransformations.get(entityPlayerCommandSendMessageClass));
        }
        retransformations.put(entityPlayerCommandSendMessageClass, entityPlayerCommandSendMessagePatch);

        // adjust CommandBlockListenerAbstract.sendMessage(), so it will return when ran from a muted command
        final Map<String, String> commandBlockListenerCommandSendMessagePatch = new HashMap<String, String>();
        final String commandBlockListenerCommandSendMessageClass = "net/minecraft/server" + serverVersion
                + "CommandBlockListenerAbstract";
        commandBlockListenerCommandSendMessagePatch.put("methodName" + mapUUID, "sendMessage");
        commandBlockListenerCommandSendMessagePatch.put("methodCode" + mapUUID, muteCheckCode);
        commandBlockListenerCommandSendMessagePatch.put("methodPosition" + mapUUID, "before");
        if (retransformations.containsKey(commandBlockListenerCommandSendMessageClass)) {
            commandBlockListenerCommandSendMessagePatch
            .putAll(retransformations.get(commandBlockListenerCommandSendMessageClass));
        }
        retransformations.put(commandBlockListenerCommandSendMessageClass, commandBlockListenerCommandSendMessagePatch);

        // adjust MinecraftServer.sendMessage(), so it will return when ran from a muted command
        final Map<String, String> mcServerCommandSendMessagePatch = new HashMap<String, String>();
        final String mcServerCommandSendMessageClass = "net/minecraft/server" + serverVersion + "MinecraftServer";
        mcServerCommandSendMessagePatch.put("methodName" + mapUUID, "sendMessage");
        mcServerCommandSendMessagePatch.put("methodCode" + mapUUID, muteCheckCode);
        mcServerCommandSendMessagePatch.put("methodPosition" + mapUUID, "before");
        if (retransformations.containsKey(mcServerCommandSendMessageClass)) {
            mcServerCommandSendMessagePatch.putAll(retransformations.get(mcServerCommandSendMessageClass));
        }
        retransformations.put(mcServerCommandSendMessageClass, mcServerCommandSendMessagePatch);

        // adjust PlayerList.sendMessage(), so it will return when ran from a muted command
        final Map<String, String> playerListCommandSendMessagePatch = new HashMap<String, String>();
        final String playerListCommandSendMessageClass = "net/minecraft/server" + serverVersion + "PlayerList";
        playerListCommandSendMessagePatch.put("methodName" + mapUUID, "sendMessage");
        playerListCommandSendMessagePatch.put("methodCode" + mapUUID, muteCheckCode);
        playerListCommandSendMessagePatch.put("methodPosition" + mapUUID, "before");
        if (retransformations.containsKey(playerListCommandSendMessageClass)) {
            playerListCommandSendMessagePatch.putAll(retransformations.get(playerListCommandSendMessageClass));
        }
        retransformations.put(playerListCommandSendMessageClass, playerListCommandSendMessagePatch);

        // adjust RemoteControlCommandListener.sendMessage(), so it will return when ran from a muted command
        final Map<String, String> remoteCommandListenerCommandSendMessagePatch = new HashMap<String, String>();
        final String remoteCommandListenerCommandSendMessageClass = "net/minecraft/server" + serverVersion
                + "RemoteControlCommandListener";
        remoteCommandListenerCommandSendMessagePatch.put("methodName" + mapUUID, "sendMessage");
        remoteCommandListenerCommandSendMessagePatch.put("methodCode" + mapUUID, muteCheckCode);
        remoteCommandListenerCommandSendMessagePatch.put("methodPosition" + mapUUID, "before");
        if (retransformations.containsKey(remoteCommandListenerCommandSendMessageClass)) {
            remoteCommandListenerCommandSendMessagePatch
            .putAll(retransformations.get(remoteCommandListenerCommandSendMessageClass));
        }
        retransformations.put(remoteCommandListenerCommandSendMessageClass,
                remoteCommandListenerCommandSendMessagePatch);

        // adjust CraftBlockCommandSender.sendMessage(), so it will return when ran from a muted command
        final Map<String, String> blockCommandSendMessagePatch = new HashMap<String, String>();
        final String blockCommandSendMessageClass = "org/bukkit/craftbukkit" + serverVersion
                + "command/CraftBlockCommandSender";
        blockCommandSendMessagePatch.put("methodName" + mapUUID, "sendMessage");
        blockCommandSendMessagePatch.put("methodCode" + mapUUID, muteCheckCode);
        blockCommandSendMessagePatch.put("methodPosition" + mapUUID, "before");
        if (retransformations.containsKey(blockCommandSendMessageClass)) {
            blockCommandSendMessagePatch.putAll(retransformations.get(blockCommandSendMessageClass));
        }
        retransformations.put(blockCommandSendMessageClass, blockCommandSendMessagePatch);

        // adjust ColouredConsoleSender.sendMessage(), so it will return when ran from a muted command
        final Map<String, String> colorConsoleCommandSendMessagePatch = new HashMap<String, String>();
        final String colorConsoleCommandSendMessageClass = "org/bukkit/craftbukkit" + serverVersion
                + "command/ColouredConsoleSender";
        colorConsoleCommandSendMessagePatch.put("methodName" + mapUUID, "sendMessage");
        colorConsoleCommandSendMessagePatch.put("methodCode" + mapUUID, muteCheckCode);
        colorConsoleCommandSendMessagePatch.put("methodPosition" + mapUUID, "before");
        colorConsoleCommandSendMessagePatch.put("methodOptional" + mapUUID, "1");
        if (retransformations.containsKey(colorConsoleCommandSendMessageClass)) {
            colorConsoleCommandSendMessagePatch.putAll(retransformations.get(colorConsoleCommandSendMessageClass));
        }
        retransformations.put(colorConsoleCommandSendMessageClass, colorConsoleCommandSendMessagePatch);

        retransformed = true;

        return retransformations;
    } // end method

    /**
     * Checks whether the previously saved class name
     * should be muted and sets the internal variables
     * accordingly.
     */
    public static void checkIsClassMuted() {
        // only consider main classes, as their subclasses are part of them
        if (newMutedClass.contains("$")) {
            newMutedClass = newMutedClass.substring(0, newMutedClass.indexOf('$'));
        }

        if (mutedClasses.containsKey(newMutedClass)) {
            // check if this class should still be muted
            if ((mutedClasses.get(newMutedClass) + maxMuteCheckTimeout) > Utils.getUnixTimestamp()) {
                lastClassMuted = true;
            } else {
                // class should be muted no more
                mutedClasses.remove(newMutedClass);
                lastClassMuted = false;
            }
        } else {
            lastClassMuted = false;
        }
    } // end method

    /**
     * Checks whether we're still within a timeout to mute
     * all commands that call sendMessage() and sets the internal
     * variables accordingly.
     */
    public static void verifyMuteCheckTimeout() {
        if ((lastMuteTimestamp + maxMuteCheckTimeout) < Utils.getUnixTimestamp()) {
            // we're beyond the check timeout, nothing to do here
            captureNextCommandSender = false;
        }
    } // end method

    /**
     * Sets previously saved class as one that should be muted.
     */
    public static void setCurrentClassMuted() {
        // only consider main classes, as their subclasses are part of them
        if (newMutedClass.contains("$")) {
            newMutedClass = newMutedClass.substring(0, newMutedClass.indexOf('$'));
        }

        mutedClasses.put(newMutedClass, Utils.getUnixTimestamp());
        captureNextCommandSender = false;
    } // end method

    /**
     * Sets chat filters.
     * This method is used if we cannot instrument server classes
     * for any reason, so we can mute at least some things in the chat.
     */
    private static void setFilters() {
        logFilter = new LogFilter();
        final Enumeration<String> en = LogManager.getLogManager().getLoggerNames();
        while (en.hasMoreElements()) {
            final String el = en.nextElement();
            Logger.getLogger(el).setFilter(logFilter);
        }
    } // end method

    /**
     * Instruments CraftBukkit and Minecraft classes
     * to enable us cancelling out the sendMessage()
     * method calls from them as needed.
     */
    public static void retransformCBMC() {
        //noinspection HardCodedStringLiteral
        final Plugin aa = Bukkit.getPluginManager().getPlugin("AdminAnything");

        // save (lib)attach.dll|so files for all OSes
        // ... used to inject custom code into loaded classes for purposes like
        //     cancelling broadcasts and player messages from muted commands etc.
        if (!new File(AA_API.getAaDataDir() + "/libraries/natives/64/linux/libattach.so").exists()) {
            aa.saveResource("libraries/natives/64/linux/libattach.so", true); //NON-NLS
            aa.saveResource("libraries/natives/64/mac/libattach.dylib", true); //NON-NLS
            aa.saveResource("libraries/natives/64/solaris/libattach.so", true); //NON-NLS
            aa.saveResource("libraries/natives/64/windows/attach.dll", true); //NON-NLS
        }

        // instrument server classes
        try {
            instrumentator = new Instrumentator(new File(AA_API.getAaDataDir(), "libraries/natives/").getPath());
            instrumentator.instrumentate();
        } catch (final Throwable e) {
            if ( AA_API.getDebug() ) {
                e.printStackTrace();
            }

            //noinspection HardCodedStringLiteral
            Bukkit.getLogger().warning('[' + aa.getDescription().getName()
                + "] " + AA_API.__("commands.mute-cannot-instrument.1"));
            //noinspection HardCodedStringLiteral
            Bukkit.getLogger().warning('[' + aa.getDescription().getName()
                + "] " + AA_API.__("commands.mute-cannot-instrument.2"));
        }

        // set chat filters in any case
        setFilters();
    } // end method

    /***
     * /aa_muteCommand - mutes a command, so it won't broadcast any messaged to console or player chat
     *
     * @param sender The player who is calling this command.
     * @param cmd The actual command that is being executed.
     * @param unused Name of the command which is being executed.
     * @param args Any arguments passed to this command.
     *
     * @return Returns true if we can instrument server classes and use this command, false otherwise.
     */
    @SuppressWarnings("OverlyComplexAnonymousInnerClass")
    @Override
    public boolean onCommand(final CommandSender sender, final Command cmd, final String unused, final String[] args) {
        if (!super.onCommand(sender, cmd, unused, args)) {
            return true;
        }

        if (!Utils.is64Bit()) {
            sender.sendMessage(ChatColor.RED + AA_API.__("general.32bit-not-supported"));
            return true;
        }

        //noinspection HardCodedStringLiteral
        if (!AA_API.isFeatureEnabled("mutecommand")) {
            sender.sendMessage(ChatColor.RED + AA_API.__("general.feature-disabled"));
            return true;
        }

        // check for at least a single argument
        if (1 > args.length) {
            sender.sendMessage(ChatColor.RED + AA_API.__("commands.mute-enter-command"));
            return false;
        }

        // never allow muting AdminAnything's core commands
        if (AA_API.isAaCoreCommand(Utils.compactQuotedArgs(args))) {
            sender.sendMessage(ChatColor.RED + AA_API.__("commands.no-core-manipulation"));
            return false;
        }

        // if the command is already muted, do nothing
        //noinspection HardCodedStringLiteral
        if (AA_API.getCommandsList("mutes").contains(args[0])) {
            sender.sendMessage(ChatColor.RED + AA_API.__("commands.mute-already-muted"));
            return true;
        }

        // try to instrument the server
        if (!retransformationTried) {
            readyToRetransform = true;

            if (!new File(Instrumentator.getJavassistLibPath()).exists()) {
                sender.sendMessage(ChatColor.YELLOW + AA_API.__("commands.mute-downloading-javaassist"));
            }
            retransformCBMC();
            retransformationTried = true;
        }

        // add muted commands in a separate thread
        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {

            @Override
            public void run() {
                // compact any commands with parameters
                final String[] altArgs = Utils.compactQuotedArgs(args);

                // list of commands that were muted
                final List<String> done = new ArrayList<String>();

                // list of commands that could NOT have been muted
                final List<String> ko = new ArrayList<String>();

                // say no if we've not instrumented and we are trying to mute a vanilla command
                // ... this is because custom command senders do not support vanilla commands
                //     and our methodology would fail when trying to send the vanilla command to such
                //     a sender in order to mute its output
                if (!retransformed) {
                    final Collection<String> vanillaMutes = new ArrayList<String>();
                    final List<String> builtins = Constants.BUILTIN_COMMANDS.getValues();
                    // iterate over all commands requested
                    for (String command : altArgs) {
                        if (command.contains(" ")) {
                            command = command.substring(0, command.indexOf(' '));
                        }

                        // if this is a vanilla command mute we're trying to do,
                        // record it
                        if (builtins.contains(command.toLowerCase())
                                && !vanillaMutes.contains(command)) {
                            vanillaMutes.add(command);
                        }
                    }

                    // do we have vanilla commands we tried to mute?
                    if (!vanillaMutes.isEmpty()) {
                        sender.sendMessage(ChatColor.YELLOW + AA_API
                            .__("commands.mute-unable-to-mute.1", AA_API.getAaName()));
                        sender.sendMessage(ChatColor.YELLOW + AA_API.__("commands.mute-unable-to-mute.2"));
                        return;
                    }
                }

                // add slash to all commands we requested to mute to make them real commands
                final String[] newArgs       = new String[altArgs.length];
                final int      altArgsLength = altArgs.length;
                for (int i = 0; i < altArgsLength; i++) {
                    newArgs[i] = '/' + altArgs[i];
                }

                // adjust listener priorities to make sure our muted commands gets pre-processed by AdminAnything
                Bukkit.getPluginManager().callEvent( new AAAdjustListenerPrioritiesEvent(
                        null,
                        null,
                        newArgs,
                        "mutes", //NON-NLS
                        false,
                        done,
                        ko
                        )
                        );

                // tell player about commands we could mute
                if (!done.isEmpty()) {
                    sender.sendMessage(ChatColor.GREEN + AA_API.__("commands.mute-done") + ": " //NON-NLS
                            + ChatColor.WHITE + String.join(", ", done));

                    if (sender instanceof Player) {
                        //noinspection HardCodedStringLiteral
                        new FancyMessage('[' + AA_API.__("general.undo") + ']').color(ChatColor.AQUA)
                                                                               .tooltip("unmute commands '" + String
                                                                               .join(", ", done) + '\'')
                                                                               .command("/aa_unmutecommand " + String.join(" ", done)).send(sender);
                    }
                }

                // tell player about commands we could NOT mute
                if (!ko.isEmpty()) {
                    //noinspection HardCodedStringLiteral
                    sender.sendMessage(ChatColor.RED + AA_API.__("commands.mute-not-muted") + ": " + ChatColor.WHITE
                            + String.join(", ", ko));
                }

                if (done.isEmpty() && ko.isEmpty()) {
                    sender.sendMessage(ChatColor.RED + AA_API.__("commands.mute-not-found"));
                    return;
                }

                // reload and save mutes
                Bukkit.getPluginManager().callEvent( new AASaveMutedCommandsEvent(sender) );

                // reload commandPreprocessor internal variables
                //noinspection HardCodedStringLiteral
                Bukkit.getPluginManager().callEvent( new AAReloadEvent("commandPreprocessor") );
            }
        }, 2);

        return true;
    } // end method

} // end class