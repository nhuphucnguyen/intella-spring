package dev.phucngu.intellaspring.spring;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import dev.phucngu.intellaspring.spring.model.AnnotationValues;
import dev.phucngu.intellaspring.spring.model.SpringBeanDef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.Introspector;
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

    /** One qualifier at an injection site: the annotation, and its string value when it has one. */
    public record SiteQualifier(@NotNull String annotation, @Nullable String value) {
    }

    /**
     * The qualifiers at the site — declared on the member itself, or (for parameters, when the
     * parameter has none) on the declaring method: {@code @Qualifier}, {@code @Named}, and custom
     * annotations meta-annotated with a qualifier annotation.
     */
    public static @NotNull List<SiteQualifier> qualifiersOf(@NotNull PsiModifierListOwner member) {
        List<SiteQualifier> declared = qualifiersDeclaredOn(member);
        if (declared.isEmpty() && member instanceof PsiParameter parameter
                && parameter.getDeclarationScope() instanceof PsiMethod declaring) {
            return qualifiersDeclaredOn(declaring);
        }
        return declared;
    }

    private static @NotNull List<SiteQualifier> qualifiersDeclaredOn(@NotNull PsiModifierListOwner owner) {
        List<SiteQualifier> out = new ArrayList<>();
        if (owner.getModifierList() == null) {
            return out;
        }
        for (PsiAnnotation annotation : owner.getModifierList().getAnnotations()) {
            String fqn = annotation.getQualifiedName();
            if (fqn == null || !isQualifierAnnotation(annotation)) {
                continue;
            }
            String value = AnnotationValues.stringValue(annotation, "value");
            if (isNamed(fqn) && (value == null || value.isBlank())) {
                continue; // @Named without a name qualifies nothing
            }
            out.add(new SiteQualifier(fqn, value));
        }
        return out;
    }

    private static boolean isNamed(@NotNull String fqn) {
        return SpringAnnotations.NAMED_JAKARTA.equals(fqn) || SpringAnnotations.NAMED_JAVAX.equals(fqn);
    }

    /** {@code @Qualifier}, {@code @Named}, or a custom annotation meta-annotated as a qualifier. */
    private static boolean isQualifierAnnotation(@NotNull PsiAnnotation annotation) {
        String fqn = annotation.getQualifiedName();
        if (SpringAnnotations.QUALIFIER.equals(fqn) || (fqn != null && isNamed(fqn))) {
            return true;
        }
        PsiJavaCodeReferenceElement reference = annotation.getNameReferenceElement();
        PsiElement resolved = reference != null ? reference.resolve() : null;
        return resolved instanceof PsiClass annotationClass
                && (annotationClass.hasAnnotation(SpringAnnotations.QUALIFIER)
                || annotationClass.hasAnnotation(SpringAnnotations.QUALIFIER_JAKARTA)
                || annotationClass.hasAnnotation(SpringAnnotations.QUALIFIER_JAVAX));
    }

    /** True for the container types Spring fills with many beans at once. */
    public static boolean isManyBeanType(@Nullable PsiType type) {
        return type instanceof PsiClassType classType && classType.resolve() != null
                && MANY_BEAN_TYPES.contains(classType.resolve().getQualifiedName());
    }

    /**
     * THE candidate lookup: beans in {@code pool} that can be injected at the site. A
     * {@code @Resource} site resolves by name first (explicit name, else field/property name),
     * then by type; every other site resolves by type narrowed by its qualifiers. Container
     * types (List, Map, ...) only match beans of the container type itself — Spring's fallback
     * when no element beans exist; element-wise matching is not modelled here.
     */
    public static @NotNull List<SpringBeanDef> candidates(@NotNull InjectionSite site,
                                                          @NotNull List<SpringBeanDef> pool) {
        PsiAnnotation resource = resourceOf(site.member());
        if (resource != null) {
            String explicit = AnnotationValues.stringValue(resource, "name");
            String name = explicit != null && !explicit.isBlank() ? explicit : resourceDefaultName(site.member());
            if (name != null) {
                List<SpringBeanDef> byName = pool.stream()
                        .filter(bean -> bean.matchesName(name))
                        .filter(bean -> typeMatches(site.type(), bean))
                        .toList();
                if (!byName.isEmpty() || (explicit != null && !explicit.isBlank())) {
                    return byName;
                }
            }
        }
        return candidates(site.type(), qualifiersOf(site.member()), pool);
    }

    /** Beans assignable to {@code type}, narrowed by the (all-must-match) qualifiers. */
    public static @NotNull List<SpringBeanDef> candidates(@Nullable PsiType type,
                                                          @NotNull List<SiteQualifier> qualifiers,
                                                          @NotNull List<SpringBeanDef> pool) {
        if (!(type instanceof PsiClassType classType)) {
            return List.of();
        }
        PsiClass injected = classType.resolve();
        if (injected == null || injected.getQualifiedName() == null) {
            return List.of();
        }
        List<SpringBeanDef> loose = pool.stream()
                .filter(bean -> typeMatches(type, bean))
                .filter(bean -> qualifiers.stream().allMatch(q -> matchesQualifier(bean, q)))
                .toList();
        if (loose.size() < 2 || classType.getParameterCount() == 0) {
            return loose;
        }
        // generics are matched leniently (erasure), but prefer exact generic matches when
        // some exist so Repo<User> and Repo<Order> beans don't read as ambiguous
        List<SpringBeanDef> strict = loose.stream().filter(bean -> strictlyAssignable(type, bean)).toList();
        return strict.isEmpty() ? loose : strict;
    }

    /**
     * Spring's single-winner narrowing among type/qualifier candidates: {@code @Fallback}
     * beans yield to any non-fallback candidate, then {@code @Primary} wins. More than one
     * survivor is a genuine ambiguity.
     */
    public static @NotNull List<SpringBeanDef> narrow(@NotNull List<SpringBeanDef> candidates) {
        List<SpringBeanDef> nonFallback = candidates.stream().filter(bean -> !isFallback(bean)).toList();
        List<SpringBeanDef> base = nonFallback.isEmpty() ? candidates : nonFallback;
        List<SpringBeanDef> primaries = base.stream().filter(SpringBeanDef::primary).toList();
        return primaries.isEmpty() ? base : primaries;
    }

    /** Whether the class is itself the declared type of some bean in the pool (exact, not a supertype). */
    public static boolean isBeanClass(@Nullable PsiClass candidate, @NotNull List<SpringBeanDef> pool) {
        if (candidate == null || candidate.getQualifiedName() == null) {
            return false;
        }
        String fqn = candidate.getQualifiedName();
        return pool.stream().anyMatch(bean -> fqn.equals(declaredClassName(bean)));
    }

    private static @Nullable String declaredClassName(@NotNull SpringBeanDef bean) {
        if (bean.beanType() instanceof PsiClassType classType && classType.resolve() != null) {
            return classType.resolve().getQualifiedName();
        }
        return rawName(bean.beanTypeName());
    }

    private static @Nullable String rawName(@Nullable String typeName) {
        if (typeName == null) {
            return null;
        }
        int generics = typeName.indexOf('<');
        return generics < 0 ? typeName : typeName.substring(0, generics);
    }

    /**
     * The injected type is assignable from the bean's type, on erasures. A bean whose type is
     * unresolved (an XML class not on the classpath) can only match by exact class name.
     */
    private static boolean typeMatches(@NotNull PsiType injected, @NotNull SpringBeanDef bean) {
        PsiType beanType = bean.beanType();
        boolean resolvable = beanType != null && beanType.isValid()
                && !(beanType instanceof PsiClassType classType && classType.resolve() == null);
        if (!resolvable) {
            String name = rawName(bean.beanTypeName());
            PsiClass injectedClass = injected instanceof PsiClassType t ? t.resolve() : null;
            return name != null && injectedClass != null && name.equals(injectedClass.getQualifiedName());
        }
        PsiType wanted = TypeConversionUtil.erasure(injected);
        PsiType actual = TypeConversionUtil.erasure(beanType);
        return wanted != null && actual != null && wanted.isAssignableFrom(actual);
    }

    private static boolean strictlyAssignable(@NotNull PsiType injected, @NotNull SpringBeanDef bean) {
        PsiType beanType = bean.beanType();
        return beanType != null && beanType.isValid() && injected.isAssignableFrom(beanType);
    }

    private static boolean matchesQualifier(@NotNull SpringBeanDef bean, @NotNull SiteQualifier qualifier) {
        PsiModifierListOwner owner = ownerOf(bean);
        String value = qualifier.value();
        if (value == null) {
            // marker-style custom qualifier: the bean must carry the same annotation
            return owner != null && owner.hasAnnotation(qualifier.annotation());
        }
        // Spring's fallback: a qualifier value also matches the bean name
        if (bean.qualifiers().contains(value) || bean.matchesName(value) || value.equals(bean.displayName())) {
            return true;
        }
        if (owner == null || owner.getModifierList() == null) {
            return false;
        }
        for (PsiAnnotation annotation : owner.getModifierList().getAnnotations()) {
            if (annotation.getQualifiedName() != null && isQualifierAnnotation(annotation)
                    && value.equals(AnnotationValues.stringValue(annotation, "value"))) {
                return true;
            }
        }
        return false;
    }

    /** The class or {@code @Bean} method carrying the bean's annotations; null for XML beans. */
    private static @Nullable PsiModifierListOwner ownerOf(@NotNull SpringBeanDef bean) {
        PsiElement source = bean.sourceElement();
        return source.isValid() ? PsiTreeUtil.getNonStrictParentOfType(source, PsiMethod.class, PsiClass.class) : null;
    }

    private static boolean isFallback(@NotNull SpringBeanDef bean) {
        PsiModifierListOwner owner = ownerOf(bean);
        return owner != null && owner.hasAnnotation(SpringAnnotations.FALLBACK);
    }

    /** {@code @Resource} on the member, or on the declaring method for a (setter) parameter. */
    private static @Nullable PsiAnnotation resourceOf(@NotNull PsiModifierListOwner member) {
        PsiModifierListOwner holder = member instanceof PsiParameter parameter
                && parameter.getDeclarationScope() instanceof PsiMethod declaring ? declaring : member;
        PsiAnnotation jakarta = holder.getAnnotation(SpringAnnotations.RESOURCE_JAKARTA);
        return jakarta != null ? jakarta : holder.getAnnotation(SpringAnnotations.RESOURCE_JAVAX);
    }

    /** Field name, or the property name of a {@code setFoo} method. */
    private static @Nullable String resourceDefaultName(@NotNull PsiModifierListOwner member) {
        if (member instanceof PsiField field) {
            return field.getName();
        }
        if (member instanceof PsiParameter parameter && parameter.getDeclarationScope() instanceof PsiMethod method
                && method.getName().length() > 3 && method.getName().startsWith("set")) {
            return Introspector.decapitalize(method.getName().substring(3));
        }
        return null;
    }
}
