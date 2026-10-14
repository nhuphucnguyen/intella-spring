package dev.phucngu.intellaspring.spring;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import dev.phucngu.intellaspring.spring.model.AnnotationValues;
import dev.phucngu.intellaspring.spring.model.SpringBeanDef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;


/**
 * Shared candidate resolution for bean injection: given an injection site (field, parameter
 * or annotated method) and the bean pool, which beans can Spring legally deliver? Used by
 * the autowiring inspection, the dependency view, and the choosers — one rule set, like
 * IDEA's SpringAutowireUtil.
 */
public final class SpringBeanMatching {

    /** Types Spring fills with many beans at once — never single-bean candidates. */
    public static final Set<String> MANY_BEAN_TYPES = Set.of(
            "java.util.Collection", "java.util.List", "java.util.Set", "java.util.Map", "java.util.Optional");

    /** One place Spring may inject a bean: an annotated field, or a (possibly implicit) parameter. */
    public record InjectionSite(@NotNull PsiModifierListOwner member, @NotNull PsiType type) {
    }

    private SpringBeanMatching() {
    }

    /**
     * The sites through which a class obtains its dependencies — the same rule set the
     * autowiring inspection visits: @Autowired/@Inject/@Resource fields and methods
     * (including inherited ones), implicit single constructors of bean classes,
     * Lombok-injected fields, and @Bean factory parameters. @Value sites configure rather
     * than inject and stay out.
     */
    public static @NotNull List<InjectionSite> injectionSites(@NotNull PsiClass beanClass) {
        Set<InjectionSite> sites = new LinkedHashSet<>();
        Set<PsiClass> seen = new HashSet<>();
        for (PsiClass c = beanClass; c != null && seen.add(c); c = c.getSuperClass()) {
            for (PsiField field : c.getFields()) {
                if (SpringInjections.isAutowiredField(field) || SpringInjections.isLombokInjectedField(field)) {
                    sites.add(new InjectionSite(field, field.getType()));
                }
            }
            for (PsiMethod method : c.getMethods()) {
                collectParameterSites(method, sites);
            }
            for (PsiMethod constructor : c.getConstructors()) {
                collectParameterSites(constructor, sites);
            }
        }
        return new ArrayList<>(sites);
    }

    private static void collectParameterSites(@NotNull PsiMethod method, @NotNull Set<InjectionSite> sites) {
        boolean wired = SpringInjections.isAutowiredMethod(method) || SpringInjections.isBeanFactoryMethod(method)
                || (method.isConstructor() && SpringInjections.isImplicitInjectionConstructor(method));
        if (!wired) {
            return;
        }
        for (PsiParameter parameter : method.getParameterList().getParameters()) {
            if (parameter.getAnnotation(SpringAnnotations.VALUE) == null) {
                sites.add(new InjectionSite(parameter, parameter.getType()));
            }
        }
    }

    /** The parameters a {@code @Bean} factory method injects — the produced bean's dependencies. */
    public static @NotNull List<InjectionSite> factoryMethodSites(@NotNull PsiMethod factoryMethod) {
        List<InjectionSite> sites = new ArrayList<>();
        for (PsiParameter parameter : factoryMethod.getParameterList().getParameters()) {
            if (parameter.getAnnotation(SpringAnnotations.VALUE) == null) {
                sites.add(new InjectionSite(parameter, parameter.getType()));
            }
        }
        return sites;
    }

    /** The @Qualifier at the site — on the member itself, or (for parameters) the declaring method. */
    public static @Nullable String qualifierOf(@NotNull PsiModifierListOwner member) {
        String onMember = AnnotationValues.stringValue(member.getAnnotation(SpringAnnotations.QUALIFIER), "value");
        if (onMember != null) {
            return onMember;
        }
        if (member instanceof PsiParameter parameter
                && parameter.getDeclarationScope() instanceof PsiMethod declaring) {
            return AnnotationValues.stringValue(declaring.getAnnotation(SpringAnnotations.QUALIFIER), "value");
        }
        return null;
    }

    /** Beans that can satisfy an injection of {@code type} narrowed by an optional qualifier. */
    public static @NotNull List<SpringBeanDef> candidates(@Nullable PsiType type, @Nullable String qualifier,
                                                          @NotNull List<SpringBeanDef> pool) {
        if (!(type instanceof PsiClassType classType)) {
            return List.of();
        }
        PsiClass injected = classType.resolve();
        if (injected == null || injected.getQualifiedName() == null
                || MANY_BEAN_TYPES.contains(injected.getQualifiedName())) {
            return List.of();
        }
        String fqn = injected.getQualifiedName();
        return pool.stream()
                .filter(bean -> fqn.equals(bean.beanTypeName()))
                .filter(bean -> matchesQualifier(bean, qualifier))
                .toList();
    }

    /** Spring's fallback: an explicit @Qualifier matches the bean's qualifiers or its name. */
    public static boolean matchesQualifier(@NotNull SpringBeanDef bean, @Nullable String qualifier) {
        return qualifier == null || bean.qualifiers().contains(qualifier)
                || qualifier.equals(bean.displayName());
    }
}
