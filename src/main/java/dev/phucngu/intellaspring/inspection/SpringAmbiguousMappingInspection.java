package dev.phucngu.intellaspring.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiMethod;
import dev.phucngu.intellaspring.mvc.RequestMappingModel;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Two handlers serving the same path with overlapping HTTP methods is a startup failure in
 * Spring — IDEA's gated plugin flags it, and so do we, pointing at the earlier mapping.
 */
public class SpringAmbiguousMappingInspection extends LocalInspectionTool {

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new JavaElementVisitor() {
            @Override
            public void visitMethod(@NotNull PsiMethod method) {
                Module module = ModuleUtilCore.findModuleForPsiElement(method);
                if (module == null) {
                    return;
                }
                List<RequestMappingModel.Mapping> mappings = RequestMappingModel.collect(module);
                for (RequestMappingModel.Mapping mapping : mappings) {
                    if (!mapping.handler().equals(method)) {
                        continue;
                    }
                    for (RequestMappingModel.Mapping other : mappings) {
                        if (other.handler().equals(mapping.handler())
                                || !other.path().equals(mapping.path())
                                || !verbsOverlap(mapping.httpMethods(), other.httpMethods())) {
                            continue;
                        }
                        String owner = other.handler().getContainingClass() != null
                                ? other.handler().getContainingClass().getName() + "." : "";
                        holder.registerProblem(method.getNameIdentifier() != null
                                        ? method.getNameIdentifier() : method,
                                "Ambiguous mapping: " + describe(mapping) + " is also mapped by "
                                        + owner + other.handler().getName() + "()");
                    }
                }
            }
        };
    }

    private static boolean verbsOverlap(@NotNull List<String> a, @NotNull List<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return true; // empty set means "any verb"
        }
        return a.stream().anyMatch(b::contains);
    }

    private static @NotNull String describe(@NotNull RequestMappingModel.Mapping mapping) {
        String verbs = mapping.httpMethods().isEmpty() ? "ANY" : String.join("/", mapping.httpMethods());
        return verbs + " " + mapping.path();
    }
}
