package com.martinambrus.adminAnything.instrumentation;

import com.martinambrus.adminAnything.*;
import com.martinambrus.adminAnything.instrumentation.Tools.Platform;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.spi.AttachProvider;
import org.bukkit.Bukkit;
import sun.tools.attach.BsdAttachProvider;
import sun.tools.attach.LinuxAttachProvider;
import sun.tools.attach.SolarisAttachProvider;
import sun.tools.attach.WindowsAttachProvider;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.Objects;

/**
 * Class responsible for instrumenting the whole
 * transformation, including attaching agent to the JVM,
 * copying relevant library files where they are supposed
 * to be and downloading Javassist library if not found.
 *
 * @author Martin Ambrus
 */
@SuppressWarnings("UseOfSunClasses")
public class Instrumentator {

    /**
     * The library folder where to look for additional
     * static library files needed for all transformations.
     */
    private final String attachLibFolder;

    /**
     * The actual library path where to save
     * and from where to load the Javassist library.
     */
    private static final String javassistLibPath = System
        .getProperty("user.dir") + File.separator + "lib" + File.separator + "javassist.jar"; //NON-NLS

    /**
     * Download URL from which to download the Javassist
     * library if not found on the server.
     */
    private final String javassistDownloadURL = "https://github.com/jboss-javassist/javassist/releases/download/rel_3_20_0_ga/javassist.jar"; //NON-NLS

    /**
     * Constructor, stores library folder to which to save
     * and from which to load all the static libraries
     * required for the various class transformations.
     *
     * @param attachLibFolder Library folder to which to save and from which
     *                        to load all the static libraries required for
     *                        the various class transformations.
     */
    public Instrumentator(final String attachLibFolder) {
        this.attachLibFolder = attachLibFolder;
    } // end method

    /**
     * Getter method for Javassist library path.
     *
     * @return Returns the location where the Javassist library should reside.
     */
    public static String getJavassistLibPath() {
        return javassistLibPath;
    } // end method

    /**
     * Downloads the Javassist library from GitHub.
     * Only used if not found on the server already.
     *
     * @throws IOException When we couldn't download the library because of an IO error
     *                     (possibly a timeout or other network problems).
     */
    private void downloadJavassist() throws IOException {
        final URL           u             = new URL(javassistDownloadURL);
        final URLConnection uc            = u.openConnection();
        final String        contentType   = uc.getContentType();
        final int           contentLength = uc.getContentLength();

        // check that we can access the download URL and that it yields the correct data
        //noinspection HardCodedStringLiteral
        if ((null == contentType) || contentType.startsWith("text/") || (-1 == contentLength)) {
            //noinspection HardCodedStringLiterals
            throw new IOException(AA_API
                .__("error.instrumentation-cannot-download-javaassist") + ": " + javassistDownloadURL);
        }

        // download and store the library, piece by piece
        final InputStream raw = uc.getInputStream();
        final InputStream in = new BufferedInputStream(raw);
        final byte[]      data = new byte[contentLength];
        int               bytesRead;
        int               offset = 0;
        while (offset < contentLength) {
            bytesRead = in.read(data, offset, data.length - offset);
            if (-1 == bytesRead) {
                break;
            }
            offset += bytesRead;
        }
        in.close();

        // did we read all of the file?
        if (offset != contentLength) {
            throw new IOException("Unable to download the javassist library. Only read " + offset + " bytes; Expected " + contentLength + " bytes. You may restart your server to try again and/or check that you can access the following address via your browser: " + javassistDownloadURL);
        }

        // write it to disk
        final FileOutputStream out = new FileOutputStream(javassistLibPath);
        out.write(data);
        out.flush();
        out.close();
    } // end method

    /**
     * Starts the complicated instrumentation process.
     *
     * @throws NoSuchFieldException If we could not transform a class because of a missing defined field.
     * @throws SecurityException If we could not transform a class because JVM security did not allow us to.
     * @throws IllegalArgumentException If we could not transform a class because of our stupidity.
     * @throws IllegalAccessException If we could not transform a class because we were prohibited from accessing a class.
     * @throws IOException If we could not transform a class because there was a input / output error trying to read/write something.
     * @throws AttachNotSupportedException If we could not transform a class because attaching an agent to the JVM is not supported.
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void instrumentate() throws NoSuchFieldException, SecurityException, IllegalArgumentException,
        IllegalAccessException, IOException, AttachNotSupportedException, UnsupportedOperationException {
        // load the javassist JAR
        new File(System.getProperty("user.dir") + File.separator + "lib").mkdirs();
        if (!new File(javassistLibPath).exists()) {
            // download the javassist JAR
            Bukkit.getLogger()
                  .info('[' + AA_API.getAaName() + "] " + AA_API.__("error.instrumentation-downloading-javassist"));
            downloadJavassist();
        }

        // make javassist available
        try {
            ClasspathHacker.addFile( javassistLibPath );

            // add an appropriate library into statically linked libraries paths
            Tools.addToLibPath(getLibraryPath(attachLibFolder));

            AttachProvider.setAttachProvider( getAttachProvider() );

            // attach the actual agent
            AgentLoader.attachAgentToJVM(Tools.getCurrentPID(), AATransformAgent.class, Tools.class, Utils.class, MySecurityManager.class, LogFilter.class, AdminAnything.class, AA_API.class);
        } catch (ClassCastException | NoSuchMethodError | IOException ex) {
            // Java 11 has closed the holes which allowed us to attach
            // to the main JVM and transform classes of Bukkit to mute output
            // from all sendMessage() methods
            throw new UnsupportedOperationException("JAVA 11 detected - classes retransforming is not possible");
        }
    } // end method

    /**
     * Determines the correct path to the statically linked
     * libraries required to do our transformations according
     * to the system we use and its variation.
     *
     * @param parentDir The actual parent folder where to look for the path.
     *
     * @return Returns correct path to the required libraries for the operating system we use.
     */
    @SuppressWarnings("HardCodedStringLiteral")
    private static String getLibraryPath(final String parentDir) {
        String path = "64/";
        switch (Objects.requireNonNull(Platform.getPlatform())) {
            case LINUX:
                path += "linux/";
                break;
            case WINDOWS:
                path += "windows/";
                break;
            case MAC:
                path += "mac/";
                break;
            case SOLARIS:
                path += "solaris/";
                break;
            default:
                throw new UnsupportedOperationException("Unsupported platform detected.");
        }
        return new File(parentDir, path).getAbsolutePath();
    } // end method

    /**
     * Determines the correct attach provider
     * and returns it.
     *
     * @return Returns the correct attach provider given the OS we currently use.
     */
    private static AttachProvider getAttachProvider() {
        switch (Objects.requireNonNull(Platform.getPlatform())) {
            case LINUX:
                return new LinuxAttachProvider();
            case WINDOWS:
                return new WindowsAttachProvider();
            case MAC:
                return new BsdAttachProvider();
            case SOLARIS:
                return new SolarisAttachProvider();
            default:
                throw new UnsupportedOperationException("Unsupported platform detected.");
        }
    } // end method

} // end class