package dev.phucngu.intellaspring.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiElementVisitor;
import dev.phucngu.intellaspring.config.ConfigKeyIndex;
import dev.phucngu.intellaspring.config.ProjectConfigKeys;
import dev.phucngu.intellaspring.config.SpringConfigMetadataService;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

/**
 * Unresolved {@code ${key}} placeholders in {@code @Value}: the key must exist in the
 * configuration metadata (spring-configuration-metadata.json on the classpath, relaxed binding
 * and Map-typed prefixes included), in the project's own application*.properties/yml, carry an
 * in-line default ({@code ${key:fallback}}), or look like an environment variable ({@code ${HOME}}). Adaptive like the unknown-key inspection:
 * with no metadata on the classpath at all, nothing is flagged.
 */
public class SpringValuePlaceholderInspection extends LocalInspectionTool {

    private static final String VALUE_ANNOTATION = "org.springframework.beans.factory.annotation.Value";
    private static final Pattern ENVIRONMENT_STYLE = Pattern.compile("[A-Z][A-Z0-9_]*");

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new JavaElementVisitor() {
            @Override
            public void visitAnnotation(@NotNull PsiAnnotation annotation) {
                if (!VALUE_ANNOTATION.equals(annotation.getQualifiedName())) {
                    return;
                }
                Project project = holder.getProject();
                ConfigKeyIndex index = SpringConfigMetadataService.getInstance(project).index();
                if (index.isEmpty()) {
                    return; // no metadata on the classpath — nothing to check against
                }
                PsiAnnotationMemberValue value = annotation.findDeclaredAttributeValue("value");
                if (value == null) {
                    return;
                }
                checkPlaceholders(value.getText(), project, index, annotation, holder);
            }
        };
    }

    /** Walks {@code ${…}} groups with brace nesting: {@code ${a:${b}}} checks a's default too. */
    private static void checkPlaceholders(@NotNull String text, @NotNull Project project,
                                          @NotNull ConfigKeyIndex index, @NotNull PsiAnnotation annotation,
                                          @NotNull ProblemsHolder holder) {
        int from = 0;
        while (true) {
            int start = text.indexOf("${", from);
            if (start < 0) {
                return;
            }
            int end = matchingBrace(text, start + 1);
            if (end < 0) {
                return; // unbalanced — leave it to the compiler/runtime
            }
            checkPlaceholder(text.substring(start + 2, end), project, index, annotation, holder);
            from = end + 1;
        }
    }

    private static void checkPlaceholder(@NotNull String body, @NotNull Project project,
                                         @NotNull ConfigKeyIndex index, @NotNull PsiAnnotation annotation,
                                         @NotNull ProblemsHolder holder) {
        int separator = topLevelColon(body);
        String key = (separator >= 0 ? body.substring(0, separator) : body).trim();
        if (separator >= 0) {
            // the default is only evaluated when the key is missing, but its own placeholders must resolve
            checkPlaceholders(body.substring(separator + 1), project, index, annotation, holder);
            return;
        }
        if (key.contains("${")) {
            checkPlaceholders(key, project, index, annotation, holder); // dynamic key — check what we can
            return;
        }
        if (key.isEmpty() || key.contains("*") || key.contains("#{") || ENVIRONMENT_STYLE.matcher(key).matches()
                || System.getProperty(key) != null
                || index.isKnown(key) || ProjectConfigKeys.isDefined(project, key)) {
            return;
        }
        holder.registerProblem(annotation, "Unresolved configuration key '" + key + "'");
    }

    /** Index of the '}' closing the '{' at {@code open}, or -1. */
    private static int matchingBrace(@NotNull String text, int open) {
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    /** First ':' not inside a nested {@code ${…}}, or -1. */
    private static int topLevelColon(@NotNull String body) {
        int depth = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
            } else if (c == ':' && depth == 0) {
                return i;
            }
        }
        return -1;
    }
}
