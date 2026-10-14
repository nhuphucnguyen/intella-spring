package dev.phucngu.intellaspring.spring;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds where a bean type is injected, with the same rule set IDEA's SpringAutowireUtil uses:
 * {@code @Autowired}/{@code @Inject}/{@code @Resource} fields, setters and constructors
 * (javax and jakarta), parameters of {@code @Bean} factory methods, Spring's implicit
 * injection through the single constructor of a bean class, and Lombok stand-ins — a bean
 * class with {@code @RequiredArgsConstructor}/{@code @AllArgsConstructor} injects through its
 * final/all fields even though no constructor is written. {@code @Value} parameters configure,
 * not inject, and are excluded.
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
            if (field != null && (isAutowiredField(field) || isLombokInjectedField(field))) {
                out.add(field);
                continue;
            }
            PsiMethod method = PsiTreeUtil.getParentOfType(at, PsiMethod.class, false);
            if (method != null && isAutowiredMethod(method)) {
                out.add(method);
            }
        }
        return new ArrayList<>(out);
    }

    public static boolean isAutowiredField(@NotNull PsiField field) {
        return !field.hasModifierProperty(PsiModifier.STATIC) && hasAnyAutowiredAnnotation(field);
    }

    public static boolean isAutowiredMethod(@NotNull PsiMethod method) {
        return !method.hasModifierProperty(PsiModifier.STATIC) && hasAnyAutowiredAnnotation(method);
    }

    public static boolean isBeanFactoryMethod(@NotNull PsiMethod method) {
        return method.getAnnotation(SpringAnnotations.BEAN) != null;
    }

    /**
     * Spring wires the one constructor of a bean class with no annotation at all; a Lombok
     * constructor annotation is the same rule with the constructor generated.
     */
    public static boolean isImplicitInjectionConstructor(@NotNull PsiMethod constructor) {
        if (!constructor.isConstructor() || constructor.getParameterList().getParametersCount() == 0) {
            return false;
        }
        PsiClass containing = constructor.getContainingClass();
        if (containing == null) {
            return false;
        }
        PsiMethod[] constructors = containing.getConstructors();
        if (constructors.length != 1 || constructors[0] != constructor) {
            return false;
        }
        // With the Lombok plugin, generated constructors appear here and the single-constructor
        // rule already covers them; without it, the class-level annotation stands in.
        return hasLombokArgsConstructor(containing) || isBeanClass(containing);
    }

    /** A bean class's field injected through a Lombok-generated constructor. */
    public static boolean isLombokInjectedField(@NotNull PsiField field) {
        if (field.hasModifierProperty(PsiModifier.STATIC)) {
            return false;
        }
        PsiClass containing = field.getContainingClass();
        if (containing == null || !hasLombokArgsConstructor(containing) || !isBeanClass(containing)) {
            return false;
        }
        if (containing.getAnnotation(SpringAnnotations.LOMBOK_ALL_ARGS) != null) {
            return true;
        }
        return field.hasModifierProperty(PsiModifier.FINAL)
                || field.getAnnotation("lombok.NonNull") != null;
    }

    /** Parameters of injection-point methods inject; @Value parameters configure instead. */
    public static boolean injectedParameter(@NotNull PsiParameter parameter) {
        if (parameter.getAnnotation(SpringAnnotations.VALUE) != null) {
            return false;
        }
        if (hasAnyAutowiredAnnotation(parameter)) {
            return true;
        }
        if (parameter.getDeclarationScope() instanceof PsiMethod declaring) {
            return isAutowiredMethod(declaring) || isBeanFactoryMethod(declaring)
                    || isImplicitInjectionConstructor(declaring);
        }
        return false;
    }

    private static boolean hasAnyAutowiredAnnotation(@NotNull PsiModifierListOwner owner) {
        for (String fqn : SpringAnnotations.AUTOWIRED_ANNOTATIONS) {
            if (owner.getAnnotation(fqn) != null) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasLombokArgsConstructor(@NotNull PsiClass containing) {
        return containing.getAnnotation(SpringAnnotations.LOMBOK_REQUIRED_ARGS) != null
                || containing.getAnnotation(SpringAnnotations.LOMBOK_ALL_ARGS) != null;
    }

    private static boolean isBeanClass(@Nullable PsiClass candidate) {
        if (candidate == null || candidate.getQualifiedName() == null) {
            return false;
        }
        String fqn = candidate.getQualifiedName();
        return SpringContextService.getInstance(candidate.getProject()).allBeans().stream()
                .anyMatch(bean -> fqn.equals(bean.beanTypeName()));
    }
}
