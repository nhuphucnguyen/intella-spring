package dev.phucngu.intellaspring.jpa;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser and property validator for Spring Data derived queries ("findByAddress_CityAndAgeBetweenOrderByAgeDesc").
 * A simplified PartTree: subject split on And/Or boundaries (only before an uppercase word,
 * so {@code findByOwnerName} never splits on "Or"), greedy camelCase/underscore property
 * resolution against the entity, order-by handling — enough to catch the typos Spring only
 * reports at startup.
 */
public final class DerivedQueries {

    /** strip order matters: longest keyword first. */
    private static final List<String> TRAILING_KEYWORDS = List.of(
            "AllIgnoringCase", "AllIgnoreCase", "IgnoringCase", "IgnoreCase",
            "NotContainingIgnoreCase", "NotContaining", "ContainingIgnoreCase", "Containing",
            "StartingWithIgnoreCase", "StartingWith", "EndingWithIgnoreCase", "EndingWith",
            "GreaterThanEqualIgnoreCase", "GreaterThanEqual", "GreaterThan",
            "LessThanEqualIgnoreCase", "LessThanEqual", "LessThan",
            "IsNotNull", "IsNull", "NotInIgnoreCase", "NotIn", "NotLikeIgnoreCase", "NotLike",
            "InIgnoreCase", "LikeIgnoreCase", "BetweenIgnoreCase", "After", "Before",
            "Is", "Equals", "Not", "In", "Like", "Between", "True", "False",
            "Within", "Near", "Empty", "NotEmpty", "Regex", "Exists");

    private static final Pattern PREFIX = Pattern.compile(
            "^(find|read|get|query|stream|count|exists|delete|remove|search)By(.+)$");
    private static final Pattern CLAUSE_SPLIT = Pattern.compile("And(?=[A-Z_])|Or(?=[A-Z_])");

    private DerivedQueries() {
    }

    /** A derived query this parser recognizes (method name starts with a known …By prefix). */
    public static boolean isDerivedQueryName(@NotNull String methodName) {
        return PREFIX.matcher(methodName).matches();
    }

    /** The property path conditions (keywords stripped), in order. */
    public static @NotNull List<String> conditionPaths(@NotNull String methodName) {
        Matcher matcher = PREFIX.matcher(methodName);
        if (!matcher.matches()) {
            return List.of();
        }
        String subject = matcher.group(2);
        int orderIndex = subject.indexOf("OrderBy");
        if (orderIndex >= 0) {
            subject = subject.substring(0, orderIndex);
        }
        subject = stripTrailing(subject, "AllIgnoringCase");
        subject = stripTrailing(subject, "AllIgnoreCase");
        List<String> paths = new ArrayList<>();
        for (String clause : CLAUSE_SPLIT.split(subject)) {
            String path = clause;
            for (String keyword : TRAILING_KEYWORDS) {
                path = stripTrailing(path, keyword);
            }
            if (!path.isBlank()) {
                paths.add(path);
            }
        }
        return paths;
    }

    /** The order-by property path(s) (Asc/Desc stripped), in order. */
    public static @NotNull List<String> orderPaths(@NotNull String methodName) {
        Matcher matcher = PREFIX.matcher(methodName);
        if (!matcher.matches()) {
            return List.of();
        }
        String subject = matcher.group(2);
        int orderIndex = subject.indexOf("OrderBy");
        if (orderIndex < 0) {
            return List.of();
        }
        List<String> paths = new ArrayList<>();
        // "AgeDescNameAsc" → ["AgeDesc", "NameAsc"]: a direction suffix ends a segment only
        // when another property follows it; the trailing suffix is stripped below
        String orderPart = subject.substring(orderIndex + "OrderBy".length());
        for (String segment : orderPart.split("(Asc|Desc)(?=[A-Z])")) {
            String path = stripTrailing(segment, "IgnoreCase");
            path = stripTrailing(path, "Desc");
            path = stripTrailing(path, "Asc");
            if (!path.isBlank()) {
                paths.add(path);
            }
        }
        return paths;
    }

    /**
     * Resolves a property path ("Address_City", "addressCity") greedily against the entity;
     * returns the first property that cannot be resolved (lowercased first word form), or
     * null when the whole path resolves.
     */
    public static @Nullable String firstUnresolvableProperty(@NotNull PsiClass domain, @NotNull String path) {
        return walk(domain, words(path), 0);
    }

    private static @Nullable String walk(@NotNull PsiClass domain, @NotNull List<String> words, int from) {
        if (from >= words.size()) {
            return null; // everything resolved
        }
        for (int length = words.size() - from; length >= 1; length--) {
            StringBuilder property = new StringBuilder(lowerFirst(words.get(from)));
            for (int i = 1; i < length; i++) {
                property.append(words.get(from + i));
            }
            PsiType type = resolvePropertyType(domain, property.toString());
            if (type == null) {
                continue;
            }
            if (from + length == words.size()) {
                return null; // resolved to the very end
            }
            if (type instanceof PsiClassType classType && classType.resolve() != null) {
                String nested = walk(classType.resolve(), words, from + length);
                if (nested != RUN_FAILED) {
                    return nested; // success (null) or a definitive nested failure
                }
            }
        }
        // nothing matched at this position: the top level reports the full property run
        if (from == 0) {
            StringBuilder whole = new StringBuilder(lowerFirst(words.get(0)));
            for (int i = 1; i < words.size(); i++) {
                whole.append(words.get(i));
            }
            return whole.toString();
        }
        return RUN_FAILED;
    }

    /** sentinel: this run failed entirely; the caller should try a shorter property name */
    private static final String RUN_FAILED = new String();

    private static @Nullable PsiType resolvePropertyType(@NotNull PsiClass owner, @NotNull String property) {
        PsiField field = owner.findFieldByName(property, true);
        if (field != null) {
            return field.getType();
        }
        String suffix = Character.toUpperCase(property.charAt(0)) + property.substring(1);
        for (String prefix : new String[]{"get", "is"}) {
            for (PsiMethod method : owner.findMethodsByName(prefix + suffix, true)) {
                if (method.getParameterList().getParametersCount() == 0) {
                    return method.getReturnType();
                }
            }
        }
        return null;
    }

    private static @NotNull List<String> words(@NotNull String path) {
        List<String> words = new ArrayList<>();
        for (String token : path.split("_+")) {
            for (String word : token.split("(?=\\p{Upper})")) {
                if (!word.isBlank()) {
                    words.add(word);
                }
            }
        }
        return words;
    }

    private static @NotNull String lowerFirst(@NotNull String word) {
        return Character.toLowerCase(word.charAt(0)) + word.substring(1);
    }

    private static @NotNull String stripTrailing(@NotNull String value, @NotNull String keyword) {
        return value.endsWith(keyword) && value.length() > keyword.length()
                ? value.substring(0, value.length() - keyword.length())
                : value;
    }
}
