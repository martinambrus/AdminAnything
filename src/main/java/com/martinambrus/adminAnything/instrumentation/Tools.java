package com.martinambrus.adminAnything.instrumentation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;

/**
 * Various IO tools used for class transformations.
 *
 * @author Tudor
 */
public enum Tools {
    ;

    /**
     * Gets the current JVM PID.
     *
     * @return Returns the PID.
     */
    public static String getCurrentPID() {
        final String jvm = ManagementFactory.getRuntimeMXBean().getName();
        return jvm.substring(0, jvm.indexOf('@'));
    }

    /**
     * Gets class bytes from a stream.
     *
     * @param stream The stream to get class bytes from.
     *
     * @return Returns a byte[] representation of given class.
     *
     * @throws IOException When an IO error occurs preventing us from retrieving class bytes.
     */
    public static byte[] getBytesFromStream(final InputStream stream) throws IOException {
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        final byte[] data = new byte[65536];
        final int dataLength = data.length;
        while (-1 != (nRead = stream.read(data, 0, dataLength))) {
            buffer.write(data, 0, nRead);
        }
        buffer.flush();
        return buffer.toByteArray();

    } // end method

    /**
     * Gets bytes from a class.
     *
     * @param clazz The class to retrieve bytes from.
     *
     * @return Returns a byte[] representation of given class.
     *
     * @throws IOException When an IO error occurs preventing us from retrieving class bytes.
     */
    public static byte[] getBytesFromClass(final Class<?> clazz) throws IOException {
        return getBytesFromStream(clazz.getClassLoader()
                                       .getResourceAsStream(clazz.getName().replace('.', '/') + ".class"));
    } // end method

    /**
     * Gets bytes from a resource.
     *
     * @param resource The resource string.
     *
     * @return Returns a byte[] representation of given resource.
     *
     * @throws IOException When an IO error occurs preventing us from retrieving class bytes.
     */
    public static byte[] getBytesFromResource(final ClassLoader clazzLoader, final String resource) throws IOException {
        return getBytesFromStream(clazzLoader.getResourceAsStream(resource));
    }

    /**
     * Adds a a path to the current java.library.path.
     *
     * @param path The path to the library.
     *
     * @throws SecurityException When we couldn't add a path due to the JVM security restrictions.
     * @throws NoSuchFieldException When we couldn't find the sys_paths field in the loaded library.
     * @throws IllegalAccessException When we could not access the actual sys_paths field in the loaded library.
     * @throws IllegalArgumentException When we're dumbasses and can't remember what field we wanted.
     */
    public static void addToLibPath(final String path) throws NoSuchFieldException, SecurityException,
    IllegalArgumentException, IllegalAccessException {
        if (null != System.getProperty("java.library.path")) {
            // If java.library.path is not empty, we will prepend our path
            // Note that path.separator is ; on Windows and : on *nix,
            // so we can't hard code it.
            System.setProperty("java.library.path", path + System.getProperty("path.separator") + System.getProperty("java.library.path"));
        } else {
            System.setProperty("java.library.path", path);
        }

        // Important: java.library.path is cached
        // We will be using reflection to clear the cache
        final Field fieldSysPath = ClassLoader.class.getDeclaredField("sys_paths");
        fieldSysPath.setAccessible(true);
        fieldSysPath.set(null, null);
    } // end method

    /**
     * An ENUM of platform constants.
     */
    @SuppressWarnings("HardCodedStringLiteral")
    public enum Platform {

        LINUX, WINDOWS, MAC, SOLARIS;

        /**
         * Gets currently used platform.
         *
         * @return Returns currently used platform.
         */
        public static Platform getPlatform() {
            final String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win") && !os.contains("darwin")) {
                return WINDOWS;
            }
            if ((os.contains("nix")) || (os.contains("nux")) || (0 < os.indexOf("aix"))) {
                return LINUX;
            }
            if (os.contains("mac")) {
                return MAC;
            }
            if (os.contains("sunos")) {
                return SOLARIS;
            }
            return null;
        } // end method

    } // end class

} // end class