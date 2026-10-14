package dev.phucngu.intellaspring.spring;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring's naming rules, kept separate from scanning so they are testable in isolation.
 */
public final class SpringNames {
    private SpringNames() {
    }

    /**
     * Spring derives a default bean name with the JavaBeans decapitalize rule: lowercase the
     * first letter unless the first two letters are uppercase ("UserService" becomes
     * "userService", "URLFetcher" stays "URLFetcher").
     */
    public static @NotNull String defaultBeanName(@NotNull String className) {
        if (className.length() > 1 && Character.isUpperCase(className.charAt(0))
                && Character.isUpperCase(className.charAt(1))) {
            return className;
        }
        return Character.toLowerCase(className.charAt(0)) + className.substring(1);
    }

    /**
     * XML {@code name} attributes may carry several names separated by comma, semicolon or
     * whitespace — the first becomes the bean name, the rest are aliases.
     */
    public static @NotNull List<String> splitNames(@NotNull String names) {
        List<String> out = new ArrayList<>();
        for (String part : names.split("[,;\\s]+")) {
            if (!part.isBlank()) {
                out.add(part);
            }
        }
        return out;
    }
}
