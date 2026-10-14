package dev.phucngu.intellaspring.spring;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds where a bean type is injected. v1 recognizes the explicit forms: {@code @Autowired}
 * fields, setters and constructors, parameters of {@code @Autowired} methods, and the implicit
 * ones Spring wires without an annotation — parameters of {@code @Bean} factory methods.
 */
public final class SpringInjections {
    private SpringInjections() {
    }

    public static @NotNull List<PsiElement> injectionPoints(@NotNull PsiClass beanClass) {
        Set<PsiElement> out = new LinkedHashSet<>();
        for (PsiReference reference : ReferencesSearch.search(beanClass)) {
            PsiElement at = reference.getElement();
            PsiParameter parameter = PsiTreeUtil.getParentOfType(at, PsiParameter.class, false);
            if (parameter != null && injectedParameter(parameter)) {
                out.add(parameter);
                continue;
            }
            PsiField field = PsiTreeUtil.getParentOfType(at, PsiField.class, false);
            if (field != null && field.getAnnotation(SpringAnnotations.AUTOWIRED) != null) {
                out.add(field);
                continue;
            }
            PsiMethod method = PsiTreeUtil.getParentOfType(at, PsiMethod.class, false);
            if (method != null && method.getAnnotation(SpringAnnotations.AUTOWIRED) != null) {
                out.add(method);
            }
        }
        return new ArrayList<>(out);
    }

    private static boolean injectedParameter(@NotNull PsiParameter parameter) {
        if (parameter.getAnnotation(SpringAnnotations.AUTOWIRED) != null) {
            return true;
        }
        // @Bean method parameters and @Autowired constructors/setters inject without their
        // own annotation — Spring wires every parameter of such methods.
        if (parameter.getDeclarationScope() instanceof PsiMethod declaring) {
            return declaring.getAnnotation(SpringAnnotations.AUTOWIRED) != null
                    || declaring.getAnnotation(SpringAnnotations.BEAN) != null;
        }
        return false;
    }
}
