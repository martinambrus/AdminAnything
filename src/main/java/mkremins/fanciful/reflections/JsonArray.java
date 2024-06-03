package mkremins.fanciful.reflections;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.List;

import com.martinambrus.adminAnything.Reflections;

public class JsonArray extends JsonElement implements Iterable<JsonElement> {

    Object jsonArrayInstance;

    List<JsonElement> elements;

    public JsonArray(final int capacity) {
        super(capacity);

        if (this.jsonArrayInstance == null) {
            this.jsonArrayInstance = Reflections.getSimpleClass(new String[] {
                    // mc 1.8+
                    "com.google.gson.JsonArray",
                    // mc 1.7
                    "net.minecraft.util.com.google.gson.JsonArray",
                    // mc 1.6
                    "org.bukkit.craftbukkit.libs.com.google.gson.JsonArray" }, capacity);
        }
    }

    public JsonArray() {
        super();

        if (this.jsonArrayInstance == null) {
            this.jsonArrayInstance = Reflections.getSimpleClass(new String[] {
                    // mc 1.8+
                    "com.google.gson.JsonArray",
                    // mc 1.7
                    "net.minecraft.util.com.google.gson.JsonArray",
                    // mc 1.6
                    "org.bukkit.craftbukkit.libs.com.google.gson.JsonArray" }, null);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Iterator<JsonElement> iterator() {
        if (this.elements == null) {
            Field field;
            try {
                field = this.jsonArrayInstance.getClass().getDeclaredField("elements");
                field.setAccessible(true);
                this.elements = (List<JsonElement>) field.get(this.jsonArrayInstance);
            } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
                Reflections.handleReflectionException(e);
            }
        }

        return this.elements.iterator();
    }

}
