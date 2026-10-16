package dev.phucngu.intellaspring.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Lookup structure over a metadata snapshot, built once and shared by completion,
 * documentation and both inspections. Keys are compared in a canonical relaxed form
 * (lower case, no '-' or '_', no {@code [n]} indices) so {@code driverClassName},
 * {@code driver_class_name} and {@code driver-class-name} are one key, and every key under a
 * container-typed property (Map, or a collection that takes indexed entries) is known too.
 */
public final class ConfigKeyIndex {
    private static final Pattern INDEX = Pattern.compile("\\[[^\\]]*]");
    private static final ConfigKeyIndex EMPTY = new ConfigKeyIndex(List.of());

    private final List<ConfigKey> keys;
    private final Map<String, ConfigKey> byCanonical = new HashMap<>();
    private final Set<String> containers = new HashSet<>();
    private final Set<String> roots = new HashSet<>();

    public ConfigKeyIndex(@NotNull List<ConfigKey> keys) {
        this.keys = keys;
        for (ConfigKey key : keys) {
            String canonical = canonical(key.name());
            byCanonical.putIfAbsent(canonical, key);
            if (isContainer(key.type())) {
                containers.add(canonical);
            }
            int dot = canonical.indexOf('.');
            roots.add(dot > 0 ? canonical.substring(0, dot) : canonical);
        }
    }

    public static @NotNull ConfigKeyIndex empty() {
        return EMPTY;
    }

    /** Lower case, '-' and '_' dropped, {@code [n]} indices dropped. */
    public static @NotNull String canonical(@NotNull String key) {
        String stripped = key.indexOf('[') >= 0 ? INDEX.matcher(key).replaceAll("") : key;
        StringBuilder out = new StringBuilder(stripped.length());
        for (int i = 0; i < stripped.length(); i++) {
            char c = stripped.charAt(i);
            if (c != '-' && c != '_') {
                out.append(Character.toLowerCase(c));
            }
        }
        return out.toString().trim();
    }

    private static boolean isContainer(@Nullable String type) {
        if (type == null) {
            return false;
        }
        return type.startsWith("java.util.Map") || type.startsWith("java.util.List")
                || type.startsWith("java.util.Set") || type.startsWith("java.util.Collection");
    }

    public @NotNull List<ConfigKey> keys() {
        return keys;
    }

    public boolean isEmpty() {
        return keys.isEmpty();
    }

    /** The metadata entry for this key in relaxed form, or null. */
    public @Nullable ConfigKey find(@NotNull String key) {
        return byCanonical.get(canonical(key));
    }

    /** True for a declared key (relaxed form) or any key nested under a Map/collection property. */
    public boolean isKnown(@NotNull String key) {
        String canonical = canonical(key);
        if (byCanonical.containsKey(canonical)) {
            return true;
        }
        if (containers.isEmpty()) {
            return false;
        }
        for (int dot = canonical.indexOf('.'); dot > 0; dot = canonical.indexOf('.', dot + 1)) {
            if (containers.contains(canonical.substring(0, dot))) {
                return true;
            }
        }
        return false;
    }

    /** True when the key's first segment is a first segment of some metadata key. */
    public boolean hasRoot(@NotNull String key) {
        String canonical = canonical(key);
        int dot = canonical.indexOf('.');
        return roots.contains(dot > 0 ? canonical.substring(0, dot) : canonical);
    }
}
