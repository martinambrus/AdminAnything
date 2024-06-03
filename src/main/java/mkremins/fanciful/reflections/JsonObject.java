package mkremins.fanciful.reflections;

import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

import com.martinambrus.adminAnything.Reflections;

public class JsonObject extends JsonElement {

    Object jsonObjectInstance;

    Method getAsJsonArray;
    Method get;

    private LinkedTreeMap<String, JsonElement> members = null;

    public JsonObject(final StringWriter string) {
        super();

        if (this.jsonObjectInstance == null) {
            this.jsonObjectInstance = Reflections.getSimpleClass(new String[] {
                    // mc 1.8+
                    "com.google.gson.JsonObject",
                    // mc 1.7
                    "net.minecraft.util.com.google.gson.JsonObject",
                    // mc 1.6
                    "org.bukkit.craftbukkit.libs.com.google.gson.JsonObject" }, string);
        }
    }

    public JsonArray getAsJsonArray(final String string) {
        JsonArray a = null;

        this.getAsJsonArray = Reflections.getSimpleMethodFromObjectInstance(this.jsonObjectInstance,
                this.getAsJsonArray, "getAsJsonArray", string);
        try {
            a = (JsonArray) this.getAsJsonArray.invoke(this.jsonObjectInstance, string);

        } catch (SecurityException | IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
            Reflections.handleReflectionException(e);
        }

        return a;
    }

    @SuppressWarnings("unchecked")
    public Set<Map.Entry<String, JsonElement>> entrySet() {
        if (this.members == null) {
            Field field;
            try {
                field = this.jsonObjectInstance.getClass().getDeclaredField("members");
                field.setAccessible(true);
                this.members = (LinkedTreeMap<String, JsonElement>) field.get(this.jsonObjectInstance);
            } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
                Reflections.handleReflectionException(e);
            }
        }

        return this.members.entrySet();
    }

    public JsonElement get(final String string) {
        JsonElement e = null;

        this.get = Reflections.getSimpleMethodFromObjectInstance(this.jsonObjectInstance, this.get, "get", string);
        try {
            e = (JsonElement) this.get.invoke(this.jsonObjectInstance, string);

        } catch (SecurityException | IllegalArgumentException | IllegalAccessException | InvocationTargetException ex) {
            Reflections.handleReflectionException(ex);
        }

        return e;
    }

}
