package mkremins.fanciful.reflections;

import com.martinambrus.adminAnything.Reflections;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class JsonWriter {

    Object jsonWriterInstance;

    Method name;
    Method valueString;
    Method valueBool;
    Method beginObject;
    Method beginArray;
    Method endArray;
    Method endObject;
    Method close;

    public JsonWriter(final StringWriter string) {
        if (this.jsonWriterInstance == null) {
            this.jsonWriterInstance = Reflections.getSimpleClass(new String[] {
                    // mc 1.8+
                    "com.google.gson.stream.JsonWriter",
                    // mc 1.7
                    "net.minecraft.util.com.google.gson.stream.JsonWriter",
                    // mc 1.6
                    "org.bukkit.craftbukkit.libs.com.google.gson.stream.JsonWriter" }, string);
        }
    }

    public JsonWriter name(final String value) throws IOException {
        this.name = Reflections.getSimpleMethodFromObjectInstance(this.jsonWriterInstance, this.name, "name", value);

        try {
            this.name.invoke(this.jsonWriterInstance, value);

        } catch (SecurityException | IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
            Reflections.handleReflectionException(e);
        }

        return this;
    }

    public JsonWriter value(final String value) {
        this.valueString = Reflections.getSimpleMethodFromObjectInstance(this.jsonWriterInstance, this.valueString,
                "value", value);
        try {
            this.valueString.invoke(this.jsonWriterInstance, value);

        } catch (SecurityException | IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
            Reflections.handleReflectionException(e);
        }

        return this;
    }

    public JsonWriter value(final Boolean b) {
        this.valueBool = Reflections.getSimpleMethodFromObjectInstance(this.jsonWriterInstance, this.valueBool, "value",
                b);
        try {
            this.valueBool.invoke(this.jsonWriterInstance, b);

        } catch (SecurityException | IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
            Reflections.handleReflectionException(e);
        }

        return this;
    }

    public JsonWriter beginObject() {
        this.beginObject = Reflections.getSimpleMethodFromObjectInstance(this.jsonWriterInstance, this.beginObject,
                "beginObject", null);
        try {
            this.beginObject.invoke(this.jsonWriterInstance);

        } catch (SecurityException | IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
            Reflections.handleReflectionException(e);
        }

        return this;
    }

    public JsonWriter beginArray() {
        this.beginArray = Reflections.getSimpleMethodFromObjectInstance(this.jsonWriterInstance, this.beginArray,
                "beginArray", null);
        try {
            this.beginArray.invoke(this.jsonWriterInstance);

        } catch (SecurityException | IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
            Reflections.handleReflectionException(e);
        }

        return this;
    }

    public JsonWriter endArray() {
        this.endArray = Reflections.getSimpleMethodFromObjectInstance(this.jsonWriterInstance, this.endArray,
                "endArray", null);
        try {
            this.endArray.invoke(this.jsonWriterInstance);

        } catch (SecurityException | IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
            Reflections.handleReflectionException(e);
        }

        return this;
    }

    public JsonWriter endObject() {
        this.endObject = Reflections.getSimpleMethodFromObjectInstance(this.jsonWriterInstance, this.endObject,
                "endObject", null);
        try {
            this.endObject.invoke(this.jsonWriterInstance);

        } catch (SecurityException | IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
            Reflections.handleReflectionException(e);
        }

        return this;
    }

    public JsonWriter close() {
        this.close = Reflections.getSimpleMethodFromObjectInstance(this.jsonWriterInstance, this.close, "close", null);
        try {
            this.close.invoke(this.jsonWriterInstance);

        } catch (SecurityException | IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
            Reflections.handleReflectionException(e);
        }

        return this;
    }

}
