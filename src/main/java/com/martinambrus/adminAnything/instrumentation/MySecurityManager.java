package com.martinambrus.adminAnything.instrumentation;

/**
 * Security manager implementation used to
 * retrieval of class names from call stacks.
 *
 * @author Martin Ambrus
 */
public class MySecurityManager extends SecurityManager {

    //public static final MySecurityManager INSTANCE = new MySecurityManager();

    /**
     * Retrieves a calling class name
     * from current stack according to the given
     * depth parameter.
     *
     * @param callStackDepth The depth in which to look for the class name.
     *                       This is handled by the calling class, as they
     *                       would know how deep they are and which class
     *                       name they need to retrieve.
     *
     * @return Returns a calling class name from current stack.
     */
    public String getCallerClassName(final int callStackDepth) {
        final Class<?>[] c = this.getClassContext();
        //noinspection ReturnOfNull
        return c.length >= (callStackDepth - 1) ? this.getClassContext()[callStackDepth].getName() : null;
    } // end method

} // end class