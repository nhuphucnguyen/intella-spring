package dev.phucngu.intellaspring.boot;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import dev.phucngu.intellaspring.spring.SpringAnnotations;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Every {@code @SpringBootApplication} class in the project (through the meta chain) that
 * actually has a main method — the candidates for the run configuration's main class.
 */
public final class BootApplicationClasses {

    private BootApplicationClasses() {
    }

    /** Must run inside a read action. */
    public static @NotNull Set<PsiClass> findBootApplicationClasses(@NotNull Project project) {
        Set<PsiClass> found = new LinkedHashSet<>();
        for (Module module : ModuleManager.getInstance(project).getModules()) {
            if (module.isDisposed()) {
                continue;
            }
            GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false);
            PsiClass annotation = JavaPsiFacade.getInstance(project)
                    .findClass(SpringAnnotations.SPRING_BOOT_APPLICATION, scope);
            if (annotation == null) {
                continue;
            }
            for (PsiClass candidate : AnnotatedElementsSearch.searchPsiClasses(annotation, scope)) {
                if (candidate.isValid() && SpringBootApplicationDetector.hasMainMethod(candidate)) {
                    found.add(candidate);
                }
            }
        }
        return found;
    }
}
