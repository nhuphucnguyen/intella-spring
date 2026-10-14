package dev.phucngu.intellaspring.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiNameValuePair;
import dev.phucngu.intellaspring.schedule.CronExpressions;
import org.jetbrains.annotations.NotNull;

/**
 * Validates the {@code cron} attribute of {@code @Scheduled}: six-field Spring cron with
 * lists/ranges/steps/names, {@code @}-macros, and "-" (disabled). Typos here only surface
 * at the first scheduled run.
 */
public class SpringScheduledCronInspection extends LocalInspectionTool {

    private static final String SCHEDULED = "org.springframework.scheduling.annotation.Scheduled";

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new JavaElementVisitor() {
            @Override
            public void visitNameValuePair(@NotNull PsiNameValuePair pair) {
                PsiAnnotation annotation = com.intellij.psi.util.PsiTreeUtil
                        .getParentOfType(pair, PsiAnnotation.class);
                if (annotation == null || !SCHEDULED.equals(annotation.getQualifiedName())) {
                    return;
                }
                String attribute = pair.getName() == null ? "value" : pair.getName();
                if (!attribute.equals("cron") || !(pair.getValue() instanceof PsiLiteralExpression literal)) {
                    return;
                }
                Object value = literal.getValue();
                if (!(value instanceof String cron)) {
                    return;
                }
                String problem = CronExpressions.describeProblem(cron);
                if (problem != null) {
                    holder.registerProblem(pair.getValue(),
                            "Invalid cron expression '" + cron + "': " + problem);
                }
            }
        };
    }
}
