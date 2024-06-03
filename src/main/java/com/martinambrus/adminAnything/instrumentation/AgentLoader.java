package com.martinambrus.adminAnything.instrumentation;

import com.martinambrus.adminAnything.Utils;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.jar.Attributes.Name;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * In-memory classes transformation agent loader,
 * responsible to attach a new transformation agent
 * to the JVM, so we can change Minecraft server's
 * classes on-the-fly.
 *
 * @author Martin Ambrus
 */
@SuppressWarnings({"unchecked", "HardCodedStringLiteral"})
enum AgentLoader {
    ;

    /**
     * Loads an agent into a JVM.
     * Those classes will be moved inside the "pretransformed" folder inside the agent jar
     *
     * @param agent The main agent class.
     * @param resources Array of classes to be included with agent.
     * @param pid The ID of the target JVM.
     *
     * @throws IOException If it was impossible to attach agent to JVM because of an input/output error.
     * @throws AttachNotSupportedException If it was impossible to attach agent to JVM because it's not supported.
     */
    static void attachAgentToJVM(final String pid, final Class<?> agent, final Class<?>... resources)
            throws IOException, AttachNotSupportedException {
        final VirtualMachine vm = VirtualMachine.attach(pid);
        try {
            vm.loadAgent(generateAgentJar(agent, resources).getAbsolutePath());
        } catch (Throwable e) {
            e.printStackTrace();
        }
        vm.detach();
    } // end method

    /**
     * Generates a temporary agent file to be loaded.
     *
     * @param agent The main agent class.
     * @param resources Array of classes to be included with agent.
     *
     * @return Returns a temporary jar file with the specified classes included.
     *
     * @throws IOException When it was impossible to create the agent JAR file due to an input/output error.
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    static File generateAgentJar(final Class<?> agent, final Class<?>... resources) throws IOException {
        // remove all leftover JAR files
        for (File f : Objects
            .requireNonNull((Bukkit.getPluginManager().getPlugin("AdminAnything")).getDataFolder().listFiles())) {
            String fname = f.getName();
            if (!f.isDirectory() && fname.contains("AdminAnythingTransformAgent") && fname.contains(".jar")) {
                f.delete();
            }
        }

        // initialize the agent JAR file
        final File jarFile = new File((Bukkit.getPluginManager().getPlugin("AdminAnything")).getDataFolder(), "AdminAnythingTransformAgent" + Utils
            .getUnixTimestamp() + ".jar");
        if (jarFile.exists()) {
            // get rid of any file that was created but not deleted due to an IO error before
            jarFile.delete();
        }

        // make sure this temporary JAR file is deleted when no longer needed
        jarFile.deleteOnExit();

        // Create manifest stating that agent is allowed to transform classes
        final Manifest manifest       = new Manifest();
        //noinspection rawtypes
        final Map      mainAttributes = manifest.getMainAttributes();
        mainAttributes.put(Name.MANIFEST_VERSION, "1.0");
        mainAttributes.put(new Name("Agent-Class"), agent.getName());
        mainAttributes.put(new Name("Can-Retransform-Classes"), "true");
        mainAttributes.put(new Name("Can-Redefine-Classes"), "true");

        // start an output stream
        final JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile), manifest);

        // unqualify agent class name
        final String unqualifiedAgent = unqualify(agent);
        jos.putNextEntry(new JarEntry(unqualifiedAgent));
        jos.write(Tools.getBytesFromStream(agent.getClassLoader().getResourceAsStream(unqualifiedAgent)));
        jos.closeEntry();

        // add all resources that will be needed by the transforming agent
        for (final Class<?> clazz : resources) {
            jos.putNextEntry(new JarEntry(unqualify(clazz)));
            jos.write(Tools.getBytesFromClass(clazz));
            jos.flush();
            jos.closeEntry();
        }

        // write the JAR file
        jos.flush();
        jos.close();
        return jarFile;
    } // end class

    /**
     * Returns an unqualified class name, so it can be used
     * in a JAR file.
     *
     * @param clazz The class to unqualify.
     *
     * @return Returns an unqualified class name.
     */
    private static String unqualify(final Class<?> clazz) {
        return clazz.getName().replace('.', '/') + ".class";
    } // end method

} // end class