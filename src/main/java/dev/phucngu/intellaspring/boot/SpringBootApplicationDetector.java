package dev.phucngu.intellaspring.boot;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.search.GlobalSearchScope;
import dev.phucngu.intellaspring.spring.SpringAnnotations;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Recognizes Spring Boot entry points: a class carrying {@code @SpringBootApplication} — or
 * any annotation transitively meta-annotated with it — and a main method. The meta-chain walk
 * mirrors how the bean model discovers custom stereotypes.
 */
public final class SpringBootApplicationDetector {

    private SpringBootApplicationDetector() {
    }

    public static boolean isSpringBootApplication(@NotNull PsiClass candidate,
                                                  @NotNull GlobalSearchScope scope) {
        Set<String> visited = new HashSet<>();
        Deque<PsiAnnotation> queue = new ArrayDeque<>();
        for (PsiAnnotation annotation : candidate.getModifierList() != null
                ? candidate.getModifierList().getAnnotations() : new PsiAnnotation[0]) {
            queue.add(annotation);
        }
        while (!queue.isEmpty()) {
            PsiAnnotation annotation = queue.poll();
            String fqn = annotation.getQualifiedName();
            if (fqn == null || !visited.add(fqn)) {
                continue;
            }
            if (SpringAnnotations.SPRING_BOOT_APPLICATION.equals(fqn)) {
                return true;
            }
            PsiClass annotationType = JavaPsiFacade.getInstance(candidate.getProject())
                    .findClass(fqn, scope);
            if (annotationType != null) {
                for (PsiAnnotation meta : annotationType.getModifierList() != null
                        ? annotationType.getModifierList().getAnnotations() : new PsiAnnotation[0]) {
                    queue.add(meta);
                }
            }
        }
        return false;
    }

    public static boolean hasMainMethod(@Nullable PsiClass candidate) {
        if (candidate == null) {
            return false;
        }
        for (PsiMethod method : candidate.findMethodsByName("main", false)) {
            if (method.hasModifierProperty(PsiModifier.STATIC)
                    && method.getParameterList().getParametersCount() == 1) {
                PsiParameter parameter = method.getParameterList().getParameters()[0];
                if (parameter.getType() instanceof com.intellij.psi.PsiArrayType array) {
                    String component = array.getComponentType().getCanonicalText();
                    return "java.lang.String".equals(component) || "String".equals(component);
                }
                return false;
            }
        }
        return false;
    }
}
