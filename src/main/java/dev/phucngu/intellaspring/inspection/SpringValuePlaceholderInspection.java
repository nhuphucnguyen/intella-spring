package dev.phucngu.intellaspring.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElementVisitor;
import dev.phucngu.intellaspring.config.SpringConfigMetadataService;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Unresolved {@code ${key}} placeholders in {@code @Value}: the key must exist in the
 * configuration metadata (spring-configuration-metadata.json on the classpath) or carry an
 * in-line default ({@code ${key:fallback}}). Adaptive like the unknown-key inspection:
 * with no metadata on the classpath at all, nothing is flagged.
 */
public class SpringValuePlaceholderInspection extends LocalInspectionTool {

    private static final String VALUE_ANNOTATION = "org.springframework.beans.factory.annotation.Value";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new JavaElementVisitor() {
            @Override
            public void visitAnnotation(@NotNull PsiAnnotation annotation) {
                if (!VALUE_ANNOTATION.equals(annotation.getQualifiedName())) {
                    return;
                }
                var keys = SpringConfigMetadataService.getInstance(holder.getProject()).keys();
                if (keys.isEmpty()) {
                    return; // no metadata on the classpath — nothing to check against
                }
                String text = annotation.findDeclaredAttributeValue("value") != null
                        ? annotation.findDeclaredAttributeValue("value").getText() : "";
                Matcher matcher = PLACEHOLDER.matcher(text);
                while (matcher.find()) {
                    String placeholder = matcher.group(1);
                    int separator = placeholder.indexOf(':');
                    if (separator >= 0) {
                        continue; // in-line default — always resolvable at runtime
                    }
                    String key = placeholder.trim();
                    if (key.isEmpty() || key.contains("*")) {
                        continue;
                    }
                    boolean known = keys.stream().anyMatch(candidate -> candidate.name().equals(key));
                    if (!known) {
                        holder.registerProblem(annotation,
                                "Unresolved configuration key '" + key + "'");
                    }
                }
            }
        };
    }
}
