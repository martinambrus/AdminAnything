package mkremins.fanciful.reflections;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.AbstractMap;
import java.util.Set;

import com.martinambrus.adminAnything.Reflections;

public final class LinkedTreeMap<K, V> extends AbstractMap<K, V> implements Serializable {

    private static final long serialVersionUID = -2991408984304877223L;

    Object treeMapInstance;

    Method entrySet;

    public LinkedTreeMap() {
        if (this.treeMapInstance == null) {
            this.treeMapInstance = Reflections.getSimpleClass(new String[] {
                    // mc 1.8+
                    "com.google.gson.internal.LinkedTreeMap",
                    // mc 1.7
                    "net.minecraft.util.com.google.gson.internal.LinkedTreeMap",
                    // mc 1.6
                    "org.bukkit.craftbukkit.libs.com.google.gson.internal.LinkedTreeMap" }, null);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Set<Entry<K, V>> entrySet() {
        Set<Entry<K, V>> s = null;

        this.entrySet = Reflections.getSimpleMethodFromObjectInstance(this.treeMapInstance, this.entrySet, "entrySet",
                null);
        try {
            s = (Set<java.util.Map.Entry<K, V>>) this.entrySet.invoke(this.treeMapInstance);

        } catch (SecurityException | IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
            Reflections.handleReflectionException(e);
        }

        return s;
    }

}
