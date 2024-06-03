package com.martinambrus.adminAnything;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * A utility class to work with Java's Reflections and allow for
 * ease of use of the GSon library, wherever it is currently located,
 * as it has relocated at least once during all Minecraft's development.
 *
 * @author Martin Ambrus
 */
public enum Reflections {
    ;

    /**
     * Receives a list of possible class locations for our target class
     * and an optional constructor parameter (only single parameter classes
     * are supported for now) and returns the actual class from one of the places
     * where it founds it.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * Object gson = Reflections.getSimpleClass(new String[] {
     *          // mc 1.8+
     *          "com.google.gson.Gson",
     *          // mc 1.7
     *          "net.minecraft.util.com.google.gson.Gson",
     *          // mc 1.6
     *          "org.bukkit.craftbukkit.libs.com.google.gson.Gson" }, null);
     * </pre>
     *
     * @param clazzez List of class locations where to look for a class we need.
     * @param constructorParameter Optional (can be null) constructor parameter. Only single parameter constructors are supported for now.
     *
     * @return Returns the actual class we wanted or null if it cannot be found in any of the provided locations.
     */
    public static Object getSimpleClass(final String[] clazzez, final Object constructorParameter) {
        Object ret = null;

        // iterate over all class locations and try to find a working one
        for (final String className : clazzez) {
            try {
                final Class<?> clazz = Class.forName(className);

                if (null == constructorParameter) {
                    ret = clazz.getConstructor().newInstance();
                } else {
                    try {
                        final Constructor<?> constructor = clazz.getConstructor(constructorParameter.getClass());
                        ret = constructor.newInstance(constructorParameter);
                    } catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException
                            | IllegalArgumentException | InvocationTargetException e) {
                        // special case for StringWriter, which extends the Writer class
                        // and at least GSon library only supports constructor with Writer.class
                        if ("StringWriter".equals(constructorParameter.getClass().getSimpleName())) { //NON-NLS
                            try {
                                final Constructor<?> constructor = clazz.getConstructor(Writer.class);
                                //noinspection JavaReflectionInvocation
                                ret = constructor.newInstance(constructorParameter);
                            } catch (IllegalArgumentException | InvocationTargetException | NoSuchMethodException
                                    | SecurityException e1) {
                                // ignore these errors, as we can have more classes to try yet
                            }
                        }
                    }
                }
                break;
            } catch (final ClassNotFoundException | InstantiationException | IllegalAccessException ignored) {
                // we continue with other classes and only handle ClassNotFound after the loop is done
            } catch (NoSuchMethodException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }

        if (null == ret) {
            handleReflectionException(
                    new Exception("Could not find any of the desired classes (contructor parameter = "
                        + constructorParameter + "): " + String.join(" ", clazzez)));
        }

        return ret;
    } // end method

    /**
     * Retrieves a simple method (with a single parameter)
     * from the instance of an object.
     *
     * <br><br><strong>Example:</strong>
     * <pre>
     * // this will be null at first
     * Method myMethod;
     *
     * // try to get a simple, parameter-less method definition
     * myMethod = Reflections.getSimpleMethodFromObjectInstance(someObjectInstance, myMethod, "myMethodName", null);
     *
     * // here, myMethod would already be defined, so getSimpleMethodFromObjectInstance() would simply
     * // return myMethod's value straight away
     * myMethod = Reflections.getSimpleMethodFromObjectInstance(someObjectInstance, myMethod, "myMethodName", null);
     * </pre>
     *
     * @param o               The object instance to retrieve the method from.
     * @param m               This parameter must be either null or any Method. If a Method is passed
     *                        as this parameter, then a name check if performed if it's not the same method
     *                        as we want to retrieve. If it is, we simply return it. Otherwise, Reflection
     *                        is used and the method is retrieved that way.
     * @param methodName      Name of the method we want to retrieve from the given object.
     * @param methodParameter An optional (can be null) parameter for the given method, if multiple
     *                        method definitions exist with different parameters.
     *
     * @return Returns the requested method or the same Method (m) as the input if we couldn't find it.
     */
    public static Method getSimpleMethodFromObjectInstance(final Object o, Method m, final String methodName,
            final Object methodParameter) {
        try {
            if ((null == m) || !m.getName().equals(methodName)) {
                if (null == methodParameter) {
                    m = o.getClass().getMethod(methodName);
                } else if (methodParameter instanceof String) {
                    m = o.getClass().getMethod(methodName, String.class);
                } else if (int.class.isAssignableFrom(methodParameter.getClass())) {
                    m = o.getClass().getMethod(methodName, int.class);
                } else if (boolean.class.isAssignableFrom(methodParameter.getClass())) {
                    m = o.getClass().getMethod(methodName, boolean.class);
                } else {
                    handleReflectionException(new Exception("Unsupported class type for method \"" + methodName
                            + "\" given. Object class passed was: " + methodParameter.getClass().getName()));
                }
            }
        } catch (NoSuchMethodException | SecurityException | IllegalArgumentException e) {
            handleReflectionException(e);
        }

        return m;
    } // end method

    /**
     * A very simple error handler for the Reflections utility class.
     * Will log an error with the severity of "severe" into the console
     * and print a stack trace.
     *
     * @param e The actual exception to log.
     */
    public static void handleReflectionException(final Exception e) {
        Bukkit.getLogger().severe(
            ChatColor.RED + AA_API.__("error.general-for-chat"));
        e.printStackTrace();
    } // end method

} // end class