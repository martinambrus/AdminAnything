package mkremins.fanciful.reflections;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.martinambrus.adminAnything.Reflections;

public abstract class JsonElement {

    Object jsonElementInstance;

    Method getAsJsonObject;
    Method isJsonPrimitive;
    Method getAsString;
    Method getAsBoolean;
    Method getAsJsonArray;

    public JsonElement() {
        if (this.jsonElementInstance == null) {
            this.jsonElementInstance = Reflections.getSimpleClass(new String[] {
                    // mc 1.8+
                    "com.google.gson.JsonElement",
                    // mc 1.7
                    "net.minecraft.util.com.google.gson.JsonElement",
                    // mc 1.6
                    "org.bukkit.craftbukkit.libs.com.google.gson.JsonElement" }, null);
        }
    }

    public JsonElement(final int capacity) {
        if (this.jsonElementInstance == null) {
            this.jsonElementInstance = Reflections.getSimpleClass(new String[] {
                    // mc 1.8+
                    "com.google.gson.JsonElement",
                    // mc 1.7
                    "net.minecraft.util.com.google.gson.JsonElement",
                    // mc 1.6
                    "org.bukkit.craftbukkit.libs.com.google.gson.JsonElement" }, capacity);
        }
    }

    public JsonObject getAsJsonObject() {
        JsonObject o = null;

        this.getAsJsonObject = Reflections.getSimpleMethodFromObjectInstance(this.jsonElementInstance,
                this.getAsJsonObject, "getAsJsonObject", null);
        try {
            o = (JsonObject) this.getAsJsonObject.invoke(this.jsonElementInstance);
        } catch (SecurityException | IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
            Reflections.handleReflectionException(e);
        }

        return o;
    }

    public boolean isJsonPrimitive() {
        boolean isPrimitive = false;

        this.isJsonPrimitive = Reflections.getSimpleMethodFromObjectInstance(this.jsonElementInstance,
                this.isJsonPrimitive, "isJsonPrimitive", null);
        try {
            isPrimitive = (boolean) this.isJsonPrimitive.invoke(this.jsonElementInstance);

        } catch (SecurityException | IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
            Reflections.handleReflectionException(e);
        }

        return isPrimitive;
    }

    public String getAsString() {
        String s = null;

        this.getAsString = Reflections.getSimpleMethodFromObjectInstance(this.jsonElementInstance, this.getAsString,
                "getAsString", null);
        try {
            s = (String) this.getAsString.invoke(this.jsonElementInstance);

        } catch (SecurityException | IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
            Reflections.handleReflectionException(e);
        }

        return s;
    }

    public boolean getAsBoolean() {
        boolean b = false;

        this.getAsBoolean = Reflections.getSimpleMethodFromObjectInstance(this.jsonElementInstance, this.getAsBoolean,
                "getAsBoolean", null);
        try {
            b = (boolean) this.getAsBoolean.invoke(this.jsonElementInstance);

        } catch (SecurityException | IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
            Reflections.handleReflectionException(e);
        }

        return b;
    }

    public JsonArray getAsJsonArray() {
        JsonArray a = null;

        this.getAsJsonArray = Reflections.getSimpleMethodFromObjectInstance(this.jsonElementInstance,
                this.getAsJsonArray, "getAsJsonArray", null);
        try {
            a = (JsonArray) this.getAsJsonArray.invoke(this.jsonElementInstance);

        } catch (SecurityException | IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
            Reflections.handleReflectionException(e);
        }

        return a;
    }

}
