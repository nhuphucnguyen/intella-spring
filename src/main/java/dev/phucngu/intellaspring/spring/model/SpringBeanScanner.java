package dev.phucngu.intellaspring.spring.model;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import dev.phucngu.intellaspring.spring.SpringAnnotations;
import dev.phucngu.intellaspring.spring.SpringNames;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the bean list for one module: stereotype classes, {@code @Bean} methods, and
 * {@code <bean>} declarations. Stereotypes are found through the meta-annotation chain, so
 * custom annotations meta-annotated with {@code @Component} work, and
 * {@code @SpringBootApplication} (→ {@code @SpringBootConfiguration} → {@code @Configuration}
 * → {@code @Component}) comes out for free. Pure read-side scanner; caching and invalidation
 * live in {@code SpringContextService}.
 */
public final class SpringBeanScanner {
    private SpringBeanScanner() {
    }

    /** True when any base stereotype resolves in the module's scope — i.e. Spring is on the classpath. */
    public static boolean hasSpringOnClasspath(@NotNull Module module) {
        return !stereotypeAnnotations(module.getProject(), searchScope(module)).isEmpty();
    }

    public static @NotNull List<SpringBeanDef> scan(@NotNull Module module) {
        if (!hasSpringOnClasspath(module)) {
            return List.of();
        }
        Project project = module.getProject();
        GlobalSearchScope scope = searchScope(module);
        List<SpringBeanDef> beans = new ArrayList<>();
        beans.addAll(stereotypeBeans(project, scope));
        beans.addAll(beanMethodBeans(project, scope));
        beans.addAll(XmlBeanScanner.scan(module));
        return beans;
    }

    private static @NotNull GlobalSearchScope searchScope(@NotNull Module module) {
        // Module sources plus dependencies and libraries: sources cover fixture tests,
        // libraries are where real projects keep the Spring annotations.
        return GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false);
    }

    /** Base stereotype classes plus every annotation transitively meta-annotated with one. */
    private static @NotNull Set<PsiClass> stereotypeAnnotations(@NotNull Project project,
                                                                @NotNull GlobalSearchScope scope) {
        JavaPsiFacade psi = JavaPsiFacade.getInstance(project);
        Set<PsiClass> found = new LinkedHashSet<>();
        Deque<PsiClass> queue = new ArrayDeque<>();
        for (String fqn : SpringAnnotations.BASE_STEREOTYPES) {
            PsiClass annotation = psi.findClass(fqn, scope);
            if (annotation != null && found.add(annotation)) {
                queue.add(annotation);
            }
        }
        while (!queue.isEmpty()) {
            PsiClass annotation = queue.poll();
            for (PsiClass candidate : AnnotatedElementsSearch.searchPsiClasses(annotation, scope)) {
                if (candidate.isValid() && candidate.isAnnotationType() && found.add(candidate)) {
                    queue.add(candidate);
                }
            }
        }
        return found;
    }

    private static @NotNull List<SpringBeanDef> stereotypeBeans(@NotNull Project project,
                                                                @NotNull GlobalSearchScope scope) {
        Set<PsiClass> stereotypeAnnotations = stereotypeAnnotations(project, scope);
        Set<String> stereotypeFqns = new HashSet<>();
        for (PsiClass annotation : stereotypeAnnotations) {
            String fqn = annotation.getQualifiedName();
            if (fqn != null) {
                stereotypeFqns.add(fqn);
            }
        }
        Set<PsiClass> beanClasses = new LinkedHashSet<>();
        for (PsiClass annotation : stereotypeAnnotations) {
            for (PsiClass beanClass : AnnotatedElementsSearch.searchPsiClasses(annotation, scope)) {
                if (beanClass.isValid() && !beanClass.isAnnotationType()) {
                    beanClasses.add(beanClass);
                }
            }
        }
        List<SpringBeanDef> beans = new ArrayList<>();
        for (PsiClass beanClass : beanClasses) {
            beans.add(classBean(beanClass, stereotypeFqns));
        }
        return beans;
    }

    private static @NotNull SpringBeanDef classBean(@NotNull PsiClass beanClass, @NotNull Set<String> stereotypeFqns) {
        // The stereotype to report is the annotation sitting directly on the class — a base
        // one, or the user's custom annotation. Names prefer its value/name attribute.
        String directStereotype = null;
        PsiAnnotation stereotypeAnnotation = null;
        for (PsiAnnotation annotation : beanClass.getModifierList() != null
                ? beanClass.getModifierList().getAnnotations() : new PsiAnnotation[0]) {
            String fqn = annotation.getQualifiedName();
            if (fqn != null && stereotypeFqns.contains(fqn)) {
                directStereotype = fqn;
                stereotypeAnnotation = annotation;
                break;
            }
        }
        String declaredName = stereotypeAnnotation != null
                ? AnnotationValues.firstPresent(stereotypeAnnotation, "value", "name")
                : null;
        String name = declaredName != null ? declaredName
                : SpringNames.defaultBeanName(beanClass.getName() != null ? beanClass.getName() : "");
        return new SpringBeanDef(
                name,
                List.of(),
                JavaPsiFacade.getElementFactory(beanClass.getProject()).createType(beanClass),
                beanClass.getQualifiedName(),
                BeanOrigin.STEREOTYPE,
                directStereotype,
                qualifierValues(beanClass.getAnnotation(SpringAnnotations.QUALIFIER)),
                scopeValue(beanClass.getAnnotation(SpringAnnotations.SCOPE)),
                AnnotationValues.stringValues(beanClass.getAnnotation(SpringAnnotations.PROFILE), "value"),
                beanClass.getAnnotation(SpringAnnotations.PRIMARY) != null,
                beanClass.getAnnotation(SpringAnnotations.LAZY) != null,
                sourceElement(beanClass));
    }

    private static @NotNull List<SpringBeanDef> beanMethodBeans(@NotNull Project project,
                                                                @NotNull GlobalSearchScope scope) {
        JavaPsiFacade psi = JavaPsiFacade.getInstance(project);
        PsiClass beanAnnotation = psi.findClass(SpringAnnotations.BEAN, scope);
        if (beanAnnotation == null) {
            return List.of();
        }
        List<SpringBeanDef> beans = new ArrayList<>();
        for (PsiMethod method : AnnotatedElementsSearch.searchPsiMethods(beanAnnotation, scope)) {
            if (!method.isValid()) {
                continue;
            }
            PsiAnnotation bean = method.getAnnotation(SpringAnnotations.BEAN);
            List<String> names = new ArrayList<>(AnnotationValues.stringValues(bean, "value"));
            names.addAll(AnnotationValues.stringValues(bean, "name"));
            // @Bean(value={"a","b"}) — first is the bean name, the rest are aliases.
            String name = names.isEmpty() ? method.getName() : names.get(0);
            List<String> aliases = names.size() > 1 ? List.copyOf(names.subList(1, names.size())) : List.of();
            PsiType returnType = method.getReturnType();
            beans.add(new SpringBeanDef(
                    name,
                    aliases,
                    returnType,
                    returnType != null ? returnType.getCanonicalText() : null,
                    BeanOrigin.BEAN_METHOD,
                    SpringAnnotations.BEAN,
                    qualifierValues(method.getAnnotation(SpringAnnotations.QUALIFIER)),
                    scopeValue(method.getAnnotation(SpringAnnotations.SCOPE)),
                    AnnotationValues.stringValues(method.getAnnotation(SpringAnnotations.PROFILE), "value"),
                    method.getAnnotation(SpringAnnotations.PRIMARY) != null,
                    method.getAnnotation(SpringAnnotations.LAZY) != null,
                    sourceElement(method)));
        }
        return beans;
    }

    private static @NotNull List<String> qualifierValues(@Nullable PsiAnnotation qualifier) {
        return AnnotationValues.stringValues(qualifier, "value");
    }

    private static @NotNull String scopeValue(@Nullable PsiAnnotation scope) {
        String value = AnnotationValues.firstPresent(scope, "value", "scopeName");
        return value != null ? value : SpringBeanDef.SINGLETON;
    }

    /** The element navigation should land on: the identifier when there is one, else the owner. */
    private static @NotNull PsiElement sourceElement(@NotNull PsiClass beanClass) {
        PsiIdentifier identifier = beanClass.getNameIdentifier();
        return identifier != null ? identifier : beanClass;
    }

    private static @NotNull PsiElement sourceElement(@NotNull PsiMethod method) {
        PsiIdentifier identifier = method.getNameIdentifier();
        return identifier != null ? identifier : method;
    }
}
