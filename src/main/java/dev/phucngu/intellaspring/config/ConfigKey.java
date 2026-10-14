package dev.phucngu.intellaspring.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One entry of Spring Boot's configuration metadata (the public
 * spring-configuration-metadata.json format every Boot starter ships).
 */
public record ConfigKey(
        @NotNull String name,
        @Nullable String type,
        @Nullable String defaultValue,
        @Nullable String description,
        boolean deprecated) {

    public @NotNull String firstSegment() {
        int dot = name.indexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
