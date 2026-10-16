package dev.phucngu.intellaspring.config;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.properties.psi.Property;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;

/**
 * Flags unknown configuration keys in application*.properties: a key whose first segment is a
 * known metadata root (spring, server, …) but which matches no metadata entry. Free-form keys
 * (custom properties) are left alone.
 */
public class SpringUnknownConfigKeyInspection extends LocalInspectionTool {

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (element instanceof Property property) {
                    check(property, holder);
                }
            }
        };
    }

    private void check(@NotNull Property property, @NotNull ProblemsHolder holder) {
        String key = property.getKey();
        if (key == null || key.isBlank()
                || !SpringPropertiesCompletionContributor.isApplicationFile(
                        property.getContainingFile().getName())) {
            return;
        }
        ConfigKeyIndex index = SpringConfigMetadataService.getInstance(holder.getProject()).index();
        if (!index.isEmpty() && index.hasRoot(key) && !index.isKnown(key)) {
            holder.registerProblem(property,
                    "Unknown Spring Boot configuration key '" + key + "'");
        }
    }
}
