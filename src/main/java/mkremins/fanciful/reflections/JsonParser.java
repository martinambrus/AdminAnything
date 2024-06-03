package mkremins.fanciful.reflections;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.martinambrus.adminAnything.Reflections;

public class JsonParser {

    Object jsonParserInstance;

    Method parse;
    Method getAsJsonObject;

    public JsonParser() {
        if (this.jsonParserInstance == null) {
            this.jsonParserInstance = Reflections.getSimpleClass(new String[] {
                    // mc 1.8+
                    "com.google.gson.JsonParser",
                    // mc 1.7
                    "net.minecraft.util.com.google.gson.JsonParser",
                    // mc 1.6
                    "org.bukkit.craftbukkit.libs.com.google.gson.JsonParser" }, null);
        }
    }

    public JsonParser parse(final String value) throws IOException {
        this.parse = Reflections.getSimpleMethodFromObjectInstance(this.jsonParserInstance, this.parse, "parse", value);
        try {
            this.parse.invoke(this.jsonParserInstance, value);

        } catch (SecurityException | IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
            Reflections.handleReflectionException(e);
        }

        return this;
    }

    public JsonObject getAsJsonObject() throws IOException {
        JsonObject o = null;

        this.getAsJsonObject = Reflections.getSimpleMethodFromObjectInstance(this.jsonParserInstance,
                this.getAsJsonObject, "getAsJsonObject", null);
        try {
            o = (JsonObject) this.getAsJsonObject.invoke(this.jsonParserInstance);

        } catch (SecurityException | IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
            Reflections.handleReflectionException(e);
        }

        return o;
    }

}
