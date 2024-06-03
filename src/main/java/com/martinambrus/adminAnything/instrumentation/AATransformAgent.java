package com.martinambrus.adminAnything.instrumentation;

import com.martinambrus.adminAnything.AA_API;
import com.martinambrus.adminAnything.Utils;
import javassist.*;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

/**
 * A low-level, in-memory class file transformer
 * responsible for transforming Minecraft's server
 * classes, so their sendMessage() calls can be intercepted
 * and cancelled out for commands muted via AdminAnything.
 *
 * @author Martin Ambrus
 */
@SuppressWarnings("UseOfSystemOutOrSystemErr")
final class AATransformAgent implements ClassFileTransformer {

    /**
     * Instance of the class responsible for the initialization
     * procedure which includes attaching this agent and a couple
     * of other classes to the JVM and loading the correct JAR files
     * with all the tools necessary for this task.
     */
    private static Instrumentation instrumentation = null;

    /**
     * A singleton instance of this transformation agent.
     */
    private static AATransformAgent transformer;

    /**
     * A list of all transformations that need to be done
     * on Minecraft's server classes. This is basically a key-value
     * map consisting of "class-name-to-be-transformed" <> "instructions-what-to-transform"
     * information.
     */
    private static final Map<String, Map<String, String>> retransformations = new HashMap<String, Map<String, String>>();

    // TODO: can we really remove this one?
    //private static List<String> completedRetransformations = new ArrayList<>();

    /**
     * public static void main() but for this agent
     *
     * @param string Unused.
     * @param instrument The actual class responsible for transformation init routine.
     */
    @SuppressWarnings({"unchecked", "HardCodedStringLiteral"})
    public static void agentmain(final String string, final Instrumentation instrument) {
        // prepare ourselves :)
        // ... iterate over all command classes and check if any of them have
        //     a static method that would fill our transformations map
        final JavaPlugin aa = (JavaPlugin) Bukkit.getPluginManager().getPlugin("AdminAnything");
        final PluginDescriptionFile yml = aa.getDescription();
        if (null != yml.getCommands()) {
            for (final String cmd : AA_API.getCommandsKeySet()) {
                final CommandExecutor ce = aa.getCommand(cmd).getExecutor();
                final Class<?> commandClass = ce.getClass();
                Method transformations;
                try {
                    if (commandClass.getDeclaredField("readyToRetransform").getBoolean(null)) {
                        // load all transformations into our map
                        transformations = commandClass.getDeclaredMethod("transformations");
                        final Map<String, Map<String, String>> result = (Map<String, Map<String, String>>) transformations.invoke(ce);
                        retransformations.putAll(result);
                        commandClass.getDeclaredField("readyToRetransform").setBoolean(null, false);
                    }
                } catch (InvocationTargetException | NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | NoSuchFieldException e) {
                    // no transformations, no worries :)
                }
            }
        }

        //Bukkit.getLogger().info("[AdminAnything] Loaded transformer agent.");
        instrumentation = instrument;
        transformer = new AATransformAgent();
        instrumentation.addTransformer(transformer, true);
        try {
            // redefine classes
            final String serverVersion = Utils.getMinecraftVersion();
            for (final Entry<String, Map<String, String>> pair : retransformations.entrySet()) {
                //System.out.println("going " + pair.getKey());
                try {
                    instrumentation.redefineClasses(
                            new ClassDefinition(
                                    Class.forName(pair.getKey().replace("/", ".")),
                                    Tools.getBytesFromResource(AATransformAgent.class.getClassLoader(), pair.getKey() + ".class")
                                    )
                            );
                } catch (final ClassNotFoundException e) {
                    // check if this class is not marked as optional
                    boolean versionSkip = false;
                    final Map<String, String> value = pair.getValue();
                    for (final Entry<String, String> pair2 : value.entrySet()) {
                        if (pair2.getKey().startsWith("methodOptional")) {
                            versionSkip = true;
                            break;
                        }
                    }

                    // check if this class is not exluded for our server version
                    // ... that's if we've not already decided that it's optional
                    if (!versionSkip) {
                        for (final Entry<String, String> pair2 : value.entrySet()) {
                            if (pair2.getKey().startsWith("methodExcludeVersions")) {
                                // parse versions
                                final String[] versions = pair2.getValue().split(Pattern.quote("|"));
                                for (final String version : versions) {
                                    if (serverVersion.startsWith(version)) {
                                        // all okay, this class does not exist in our current version
                                        versionSkip = true;
                                        break;
                                    }
                                }
                            }
                        }
                    }

                    if (!versionSkip) {
                        e.printStackTrace(System.out);
                        Bukkit.getLogger().warning(
                            '[' +
                                aa.getName() +
                                "] " + //NON-NLS
                                AA_API.__("error.instrumentation-agent-cannot-instrument.1")
                        );
                        Bukkit.getLogger().warning(
                            '[' +
                                aa.getName() +
                                "] " + //NON-NLS
                                AA_API.__("error.instrumentation-agent-cannot-instrument.2")
                        );
                    }
                }
            }
        } catch (IOException | UnmodifiableClassException | SecurityException | IllegalArgumentException | AssertionError t) {
            t.printStackTrace(System.out);
            Bukkit.getLogger().warning(
                '[' +
                    aa.getName() +
                    "] " + //NON-NLS
                    AA_API.__("error.instrumentation-agent-cannot-instrument.1")
            );
            Bukkit.getLogger().warning(
                '[' +
                    aa.getName() +
                    "] " + //NON-NLS
                    AA_API.__("error.instrumentation-agent-cannot-instrument.2")
            );
        }

        // all done, the agent is no longer needed
        killAgent();
    } // end method

    /**
     * The method used to actually perform transformations
     * on Minecraft server's classes.
     */
    @Override
    public byte[] transform(final ClassLoader loader, final String className, final Class<?> classBeingRedefined,
                            final ProtectionDomain protectionDomain, final byte[] classfileBuffer) {
        // if this class doesn't use Bukkit's class loader, just return it untransformed
        if (loader != ClassLoader.getSystemClassLoader()) {
            //System.err.println(className + " is not using Bukkit's loader, and so cannot be loaded!");
            return classfileBuffer;
        }

        // don't attempt to transform classes that actually perform the transformation itself
        //noinspection HardCodedStringLiteral
        if (className.startsWith("com/martinambrus/AdminAnything/instrumentation")) {
            //System.err.println(className + " is part of profiling classes. No StackOverflow for you!");
            return classfileBuffer;
        }

        // the class has been found among those which we need to transform
        if (retransformations.containsKey(className)) {
            final Plugin aa = Bukkit.getPluginManager().getPlugin("AdminAnything"); //NON-NLS
            //System.out.println("going " + className);
            final Map<String, String> retransInfo = retransformations.get(className);
            final String              binName     = className.replace('/', '.');
            try {
                // load the class into a new ClassPool
                final ClassPool cPool = new ClassPool(true);
                //cPool.insertClassPath(new LoaderClassPath(aa.getClass().getClassLoader()));
                cPool.insertClassPath(new LoaderClassPath(loader));
                cPool.insertClassPath(new ByteArrayClassPath(binName, classfileBuffer));
                final CtClass ctClazz = cPool.get(binName);

                // iterate over all its methods and try to find the ones we need to transform
                for (final CtMethod method: ctClazz.getDeclaredMethods()) {
                    final String methodName = method.getName();

                    // check all definitions for this class name and transform as necessary
                    for (final Entry<String, String> pair : retransInfo.entrySet()){
                        //noinspection HardCodedStringLiteral
                        if (!ctClazz.isFrozen() && pair.getValue().equals(methodName) && pair.getKey().startsWith("methodName")) {
                            // match for this method found, let's do whatever was requested here
                            ctClazz.removeMethod(method);
                            //noinspection HardCodedStringLiteral
                            final String newCode = retransInfo.get("methodCode" + pair.getKey().replace("methodName", ""));
                            //noinspection HardCodedStringLiteral
                            final String where = retransInfo.get("methodPosition" + pair.getKey().replace("methodName", ""));

                            //noinspection HardCodedStringLiteral
                            if ("after".equals(where)) {
                                method.insertAfter(newCode);
                            } else {
                                // default to "insertBefore", so nothing gets half-transformed
                                // but still show an error message if the value is unknown
                                method.insertBefore(newCode);

                                //noinspection HardCodedStringLiteral
                                if (!"before".equals(where)) {
                                    System.err.println("[ " + AA_API.getAaName() + " ] " + AA_API
                                        .__("error.instrumentation-incorrect-code-position", methodName, where));
                                }
                            }
                            ctClazz.addMethod(method);
                        }
                    }
                }

                //System.out.println("[AdminAnything] Successfully instrumented " + className);
                // we've got our new class body, let's return it
                return ctClazz.toBytecode();
            } catch (final Exception ex) {
                ex.printStackTrace(System.err);
                Bukkit.getLogger().warning('[' + aa.getName() + "] " + AA_API
                    .__("error.instrumentation-failed-class") + ": " + binName);
                Bukkit.getLogger().warning(
                    '[' +
                        aa.getName() +
                        "] " + //NON-NLS
                        AA_API.__("error.instrumentation-agent-cannot-instrument.1")
                );
                Bukkit.getLogger().warning(
                    '[' +
                        aa.getName() +
                        "] " + //NON-NLS
                        AA_API.__("error.instrumentation-agent-cannot-instrument.2")
                );
                return classfileBuffer;
            }
        }
        return null;
    } // end method

    /**
     * Unlinks the transforming agent from JVM when no longer needed.
     */
    private static void killAgent() {
        instrumentation.removeTransformer(transformer);
    } // end method

} // end class