package com.martinambrus.adminAnything;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * Utility class for dynamically changing the classpath + adding classes during runtime.
 *
 * @author Jonathan Nadeau, https://stackoverflow.com/a/2593771/467164
 */
@SuppressWarnings("JavadocReference")
public enum ClasspathHacker {
    ;

    /**
     * Parameters for method which adds new JAR URL to System classes.
     */
    private static final Class<?>[] parameters = new Class[] { URL.class };

    /**
     * Adds a file to the classpath.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * ClasspathHacker.addFile("Plugins/MySuperPlugin.jar");
     * </pre>
     *
     * @param s A {@link java.lang.String String} pointing to the JAR file
     *          to be added to the system class path.
     * @throws IOException When the JAR file could not be found or read.
     */
    public static void addFile(final String s) throws IOException {
        final File f = new File(s);
        addFile(f);
    } //end method

    /**
     * Adds a file to the classpath
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * ClasspathHacker.addFile(new File("Plugins/MyAwesomePlugin.jar"));
     * </pre>
     *
     * @param f The {@link java.io.File File} to be added to the system class path.
     * @throws IOException When the JAR file could not be found or read.
     */
    public static void addFile(final File f) throws IOException {
        addURL(f.toURI().toURL());
    } //end method

    /**
     * Adds the content pointed by the URL to the classpath.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * ClasspathHacker.addURL("");
     * </pre>
     *
     * @param u The {@link java.net.URL URL} pointing to the content
     *          to be added to the system class path.
     * @throws IOException When the JAR URL could not be read.
     */
    public static void addURL(final URL u) throws IOException {
        final URLClassLoader sysloader = (URLClassLoader) ClassLoader.getSystemClassLoader();
        final Class<?> sysclass = URLClassLoader.class;
        try {
            final Method method = sysclass.getDeclaredMethod("addURL", parameters);
            method.setAccessible(true);
            method.invoke(sysloader, u);
        } catch (final Throwable t) {
            t.printStackTrace();
            throw new IOException("Error, could not add URL to system classloader");
        } //end try catch
    } //end method

} // end class