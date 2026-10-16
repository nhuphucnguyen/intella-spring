package dev.phucngu.intellaspring.spring;

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import dev.phucngu.intellaspring.spring.model.SpringBeanDef;
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

    /**
     * Where the bean class is injected: through references to the class itself, and through
     * references to its supertypes (an interface or abstract base) whenever the shared
     * matcher says the site would actually receive this class.
     */
    public static @NotNull List<PsiElement> injectionPoints(@NotNull PsiClass beanClass) {
        Set<PsiElement> out = new LinkedHashSet<>();
        collectInjectionPoints(beanClass, out, null);
        List<SpringBeanDef> pool = null;
        for (PsiClass superType : InheritanceUtil.getSuperClasses(beanClass)) {
            if (CommonClassNames.JAVA_LANG_OBJECT.equals(superType.getQualifiedName())) {
                continue;
            }
            if (pool == null) {
                pool = SpringContextService.getInstance(beanClass.getProject()).allBeans();
            }
            collectInjectionPoints(superType, out, new Receiver(beanClass, pool));
        }
        return new ArrayList<>(out);
    }

    /** The class whose injection a supertype reference must lead to, and the bean pool to judge by. */
    private record Receiver(@NotNull PsiClass beanClass, @NotNull List<SpringBeanDef> pool) {
        boolean receivedBy(@NotNull PsiElement point) {
            if (point instanceof PsiMethod method) {
                return java.util.Arrays.stream(method.getParameterList().getParameters()).anyMatch(this::receivedBy);
            }
            PsiType type = point instanceof PsiField field ? field.getType()
                    : point instanceof PsiParameter parameter ? parameter.getType() : null;
            if (type == null) {
                return false;
            }
            var site = new SpringBeanMatching.InjectionSite((PsiModifierListOwner) point, type);
            return SpringBeanMatching.narrow(SpringBeanMatching.candidates(site, pool)).stream()
                    .anyMatch(bean -> SpringBeanMatching.isBeanClass(beanClass, List.of(bean)));
        }
    }

    private static void collectInjectionPoints(@NotNull PsiClass target, @NotNull Set<PsiElement> out,
                                               @Nullable Receiver receiver) {
        for (PsiReference reference : ReferencesSearch.search(target)) {
            PsiElement at = reference.getElement();
            PsiElement point = null;
            PsiParameter parameter = PsiTreeUtil.getParentOfType(at, PsiParameter.class, false);
            PsiField field = PsiTreeUtil.getParentOfType(at, PsiField.class, false);
            PsiMethod method = PsiTreeUtil.getParentOfType(at, PsiMethod.class, false);
            if (parameter != null && injectedParameter(parameter)) {
                point = parameter;
            } else if (field != null && (isAutowiredField(field) || isLombokInjectedField(field))) {
                point = field;
            } else if (method != null && isAutowiredMethod(method)) {
                point = method;
            }
            if (point != null && (receiver == null || receiver.receivedBy(point))) {
                out.add(point);
            }
        }
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
        return candidate != null && SpringBeanMatching.isBeanClass(candidate,
                SpringContextService.getInstance(candidate.getProject()).allBeans());
    }
}
