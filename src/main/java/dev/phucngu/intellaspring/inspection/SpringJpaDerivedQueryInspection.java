package dev.phucngu.intellaspring.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiMethod;
import dev.phucngu.intellaspring.jpa.DerivedQueries;
import dev.phucngu.intellaspring.jpa.JpaRepositories;
import org.jetbrains.annotations.NotNull;

/**
 * Validates Spring Data derived queries against the repository's entity: every property
 * path of {@code findBy…And…}, keywords stripped, must resolve (greedy camelCase/underscore
 * walk) — Spring only reports a typo at startup, this flags it in the editor. Methods with
 * a {@code @Query} declaration declare their own SQL and are skipped.
 */
public class SpringJpaDerivedQueryInspection extends LocalInspectionTool {

    private static final String QUERY_ANNOTATION = "org.springframework.data.repository.query.Query";

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new JavaElementVisitor() {
            @Override
            public void visitMethod(@NotNull PsiMethod method) {
                PsiClass containing = method.getContainingClass();
                if (containing == null || !JpaRepositories.isRepositoryInterface(containing)) {
                    return;
                }
                if (method.getAnnotation(QUERY_ANNOTATION) != null) {
                    return; // explicit query — the name is free-form
                }
                String name = method.getName();
                if (!DerivedQueries.isDerivedQueryName(name) || method.getNameIdentifier() == null) {
                    return;
                }
                PsiClass domain = JpaRepositories.domainTypeOf(containing);
                if (domain == null || domain.getName() == null) {
                    return;
                }
                for (String path : DerivedQueries.conditionPaths(name)) {
                    check(path, domain, method, holder);
                }
                for (String path : DerivedQueries.orderPaths(name)) {
                    check(path, domain, method, holder);
                }
            }
        };
    }

    private static void check(@NotNull String path, @NotNull PsiClass domain,
                              @NotNull PsiMethod method, @NotNull ProblemsHolder holder) {
        String failed = DerivedQueries.firstUnresolvableProperty(domain, path);
        if (failed != null) {
            holder.registerProblem(method.getNameIdentifier(),
                    "Cannot resolve property '" + failed + "' of '" + domain.getName() + "'");
        }
    }
}
