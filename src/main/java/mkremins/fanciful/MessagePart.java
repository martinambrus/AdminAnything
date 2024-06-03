package mkremins.fanciful;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import mkremins.fanciful.reflections.JsonWriter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Internal class: Represents a component of a JSON-serializable {@link FancyMessage}.
 */
final public class MessagePart implements JsonRepresentedObject, ConfigurationSerializable, Cloneable {

    ChatColor                        color                   = ChatColor.WHITE;
    ArrayList<ChatColor>             styles                  = new ArrayList<ChatColor>();
    String                           clickActionName         = null, clickActionData = null, hoverActionName = null;
    JsonRepresentedObject            hoverActionData         = null;
    TextualComponent                 text                    = null;
    String                           insertionData           = null;
    ArrayList<JsonRepresentedObject> translationReplacements = new ArrayList<JsonRepresentedObject>();

    public MessagePart(final TextualComponent text) {
        this.text = text;
    }

    MessagePart() {
        this.text = null;
    }

    boolean hasText() {
        return this.text != null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public MessagePart clone() throws CloneNotSupportedException {
        final MessagePart obj = (MessagePart) super.clone();
        obj.styles = (ArrayList<ChatColor>) this.styles.clone();
        if (this.hoverActionData instanceof JsonString) {
            obj.hoverActionData = new JsonString(((JsonString) this.hoverActionData).getValue());
        } else if (this.hoverActionData instanceof FancyMessage) {
            obj.hoverActionData = ((FancyMessage) this.hoverActionData).clone();
        }
        obj.translationReplacements = (ArrayList<JsonRepresentedObject>) this.translationReplacements.clone();
        return obj;

    }

    static final BiMap<ChatColor, String> stylesToNames;

    static {
        final ImmutableBiMap.Builder<ChatColor, String> builder = ImmutableBiMap.builder();
        for (final ChatColor style : ChatColor.values()) {
            if (!style.isFormat()) {
                continue;
            }

            String styleName;
            switch (style) {
                case MAGIC:
                    styleName = "obfuscated";
                    break;
                case UNDERLINE:
                    styleName = "underlined";
                    break;
                default:
                    styleName = style.name().toLowerCase();
                    break;
            }

            builder.put(style, styleName);
        }
        stylesToNames = builder.build();
    }

    @Override
    public void writeJson(final JsonWriter json) {
        try {
            json.beginObject();
            this.text.writeJson(json);
            json.name("color").value(this.color.name().toLowerCase());
            for (final ChatColor style : this.styles) {
                json.name(stylesToNames.get(style)).value(true);
            }
            if (this.clickActionName != null && this.clickActionData != null) {
                json.name("clickEvent").beginObject().name("action").value(this.clickActionName).name("value")
                        .value(this.clickActionData).endObject();
            }
            if (this.hoverActionName != null && this.hoverActionData != null) {
                json.name("hoverEvent").beginObject().name("action").value(this.hoverActionName).name("value");
                this.hoverActionData.writeJson(json);
                json.endObject();
            }
            if (this.insertionData != null) {
                json.name("insertion").value(this.insertionData);
            }
            if (this.translationReplacements.size() > 0 && this.text != null
                    && TextualComponent.isTranslatableText(this.text)) {
                json.name("with").beginArray();
                for (final JsonRepresentedObject obj : this.translationReplacements) {
                    obj.writeJson(json);
                }
                json.endArray();
            }
            json.endObject();
        } catch (final IOException e) {
            Bukkit.getLogger().log(Level.WARNING, "A problem occured during writing of JSON string", e);
        }
    }

    @Override
    public Map<String, Object> serialize() {
        final HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("text", this.text);
        map.put("styles", this.styles);
        map.put("color", this.color.getChar());
        map.put("hoverActionName", this.hoverActionName);
        map.put("hoverActionData", this.hoverActionData);
        map.put("clickActionName", this.clickActionName);
        map.put("clickActionData", this.clickActionData);
        map.put("insertion", this.insertionData);
        map.put("translationReplacements", this.translationReplacements);
        return map;
    }

    @SuppressWarnings("unchecked")
    public static MessagePart deserialize(final Map<String, Object> serialized) {
        final MessagePart part = new MessagePart((TextualComponent) serialized.get("text"));
        part.styles = (ArrayList<ChatColor>) serialized.get("styles");
        part.color = ChatColor.getByChar(serialized.get("color").toString());
        part.hoverActionName = (String) serialized.get("hoverActionName");
        part.hoverActionData = (JsonRepresentedObject) serialized.get("hoverActionData");
        part.clickActionName = (String) serialized.get("clickActionName");
        part.clickActionData = (String) serialized.get("clickActionData");
        part.insertionData = (String) serialized.get("insertion");
        part.translationReplacements = (ArrayList<JsonRepresentedObject>) serialized.get("translationReplacements");
        return part;
    }

    static {
        ConfigurationSerialization.registerClass(MessagePart.class);
    }

}
