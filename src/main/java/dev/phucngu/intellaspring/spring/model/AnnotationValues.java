package dev.phucngu.intellaspring.spring.model;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads annotation attributes as plain Java values. Uses declared attributes only (a missing
 * {@code value} means "default", not {@code ""}) and evaluates constants so
 * {@code @Profile(PROFILES)} behaves like a literal.
 */
public final class AnnotationValues {
    private AnnotationValues() {
    }

    /** The declared attribute as a string, or null when absent/unresolvable. */
    public static @Nullable String stringValue(@Nullable PsiAnnotation annotation, @NotNull String attribute) {
        List<String> values = stringValues(annotation, attribute);
        return values.isEmpty() ? null : values.get(0);
    }

    /** The declared attribute as a string list: a single value yields one element, arrays expand. */
    public static @NotNull List<String> stringValues(@Nullable PsiAnnotation annotation, @NotNull String attribute) {
        List<String> out = new ArrayList<>();
        if (annotation == null) {
            return out;
        }
        PsiAnnotationMemberValue value = annotation.findDeclaredAttributeValue(attribute);
        if (value instanceof PsiArrayInitializerMemberValue array) {
            for (PsiAnnotationMemberValue element : array.getInitializers()) {
                addEvaluated(out, element);
            }
        } else if (value != null) {
            addEvaluated(out, value);
        }
        return out;
    }

    /** First non-blank among the given attribute names — e.g. {@code @Bean}'s value/name pair. */
    public static @Nullable String firstPresent(@Nullable PsiAnnotation annotation, String... attributes) {
        if (annotation == null) {
            return null;
        }
        for (String attribute : attributes) {
            String value = stringValue(annotation, attribute);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static void addEvaluated(@NotNull List<String> out, @NotNull PsiAnnotationMemberValue value) {
        Object evaluated = JavaPsiFacade.getInstance(value.getProject())
                .getConstantEvaluationHelper()
                .computeConstantExpression(value);
        if (evaluated instanceof String s && !s.isBlank()) {
            out.add(s);
        }
    }
}
