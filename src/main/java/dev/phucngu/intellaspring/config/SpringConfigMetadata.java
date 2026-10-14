package dev.phucngu.intellaspring.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Parser for spring-configuration-metadata.json — the format's spec is public Spring Boot API. */
public final class SpringConfigMetadata {

    private SpringConfigMetadata() {
    }

    public static @NotNull List<ConfigKey> parse(@NotNull String json) {
        List<ConfigKey> keys = new ArrayList<>();
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) {
            return keys;
        }
        JsonElement properties = root.getAsJsonObject().get("properties");
        if (properties == null || !properties.isJsonArray()) {
            return keys;
        }
        JsonArray array = properties.getAsJsonArray();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject property = element.getAsJsonObject();
            String name = string(property, "name");
            if (name == null || name.isBlank()) {
                continue;
            }
            JsonElement deprecation = property.get("deprecation");
            keys.add(new ConfigKey(
                    name,
                    string(property, "type"),
                    string(property, "defaultValue"),
                    string(property, "description"),
                    deprecation != null && deprecation.isJsonObject()));
        }
        return keys;
    }

    private static String string(@NotNull JsonObject object, @NotNull String member) {
        JsonElement element = object.get(member);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }
}
