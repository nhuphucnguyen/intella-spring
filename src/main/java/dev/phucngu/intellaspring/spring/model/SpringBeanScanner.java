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
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import dev.phucngu.intellaspring.jpa.JpaRepositories;
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
 * {@code <bean>} declarations, plus Spring Data repository interfaces. Stereotypes are found through the meta-annotation chain, so
 * custom annotations meta-annotated with {@code @Component} work, and
 * {@code @SpringBootApplication} (→ {@code @SpringBootConfiguration} → {@code @Configuration}
 * → {@code @Component}) comes out for free. Pure read-side scanner; caching and invalidation
 * live in {@code SpringContextService}.
 */
public final class SpringBeanScanner {
    private SpringBeanScanner() {
    }

    /**
     * The meta-annotation closure of the stereotypes, as classes (to search with) and FQNs
     * (to match a class's direct annotations). Computed once per rebuild by the caller and
     * handed to the scan methods, so the BFS never repeats per module.
     */
    public record Stereotypes(@NotNull Set<PsiClass> annotations, @NotNull Set<String> fqns) {
        public static final Stereotypes EMPTY = new Stereotypes(Set.of(), Set.of());

        public boolean isEmpty() {
            return annotations.isEmpty();
        }

        static @NotNull Stereotypes of(@NotNull Set<PsiClass> annotations) {
            Set<String> fqns = new HashSet<>();
            for (PsiClass annotation : annotations) {
                String fqn = annotation.getQualifiedName();
                if (fqn != null) {
                    fqns.add(fqn);
                }
            }
            return new Stereotypes(annotations, fqns);
        }
    }

    /** True when any base stereotype resolves in the module's scope — i.e. Spring is on the classpath. */
    public static boolean hasSpringOnClasspath(@NotNull Module module) {
        JavaPsiFacade psi = JavaPsiFacade.getInstance(module.getProject());
        GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false);
        for (String fqn : SpringAnnotations.BASE_STEREOTYPES) {
            if (psi.findClass(fqn, scope) != null) {
                return true;
            }
        }
        return false;
    }

    /** Everything outside project content: the jars and library sources, scanned once per project. */
    public static @NotNull GlobalSearchScope libraryScope(@NotNull Project project) {
        return GlobalSearchScope.allScope(project)
                .intersectWith(GlobalSearchScope.notScope(GlobalSearchScope.projectScope(project)));
    }

    /** Stereotype closure over libraries only — changes with the classpath, not with edits. */
    public static @NotNull Stereotypes libraryStereotypes(@NotNull Project project) {
        GlobalSearchScope scope = libraryScope(project);
        return Stereotypes.of(closure(project, baseStereotypes(project, scope), scope));
    }

    /** Library closure extended with annotations the project itself meta-annotates. */
    public static @NotNull Stereotypes projectStereotypes(@NotNull Project project, @NotNull Stereotypes library) {
        Set<PsiClass> seeds = new LinkedHashSet<>(library.annotations());
        seeds.addAll(baseStereotypes(project, GlobalSearchScope.allScope(project)));
        return Stereotypes.of(closure(project, seeds, GlobalSearchScope.projectScope(project)));
    }

    /** Beans declared in the module's own sources only; dependencies compose on top of this. */
    public static @NotNull List<SpringBeanDef> declaredBeans(@NotNull Module module, @NotNull Stereotypes stereotypes) {
        Project project = module.getProject();
        GlobalSearchScope scope = GlobalSearchScope.moduleScope(module);
        List<SpringBeanDef> beans = new ArrayList<>();
        beans.addAll(stereotypeBeans(scope, stereotypes));
        beans.addAll(beanMethodBeans(project, scope));
        beans.addAll(repositoryBeans(module, scope));
        beans.addAll(XmlBeanScanner.scan(module));
        return beans;
    }

    /** Beans declared by classes in libraries (jars / library sources). */
    public static @NotNull List<SpringBeanDef> libraryBeans(@NotNull Project project, @NotNull Stereotypes library) {
        GlobalSearchScope scope = libraryScope(project);
        List<SpringBeanDef> beans = new ArrayList<>(stereotypeBeans(scope, library));
        beans.addAll(beanMethodBeans(project, scope));
        return beans;
    }

    private static @NotNull Set<PsiClass> baseStereotypes(@NotNull Project project, @NotNull GlobalSearchScope scope) {
        JavaPsiFacade psi = JavaPsiFacade.getInstance(project);
        Set<PsiClass> found = new LinkedHashSet<>();
        for (String fqn : SpringAnnotations.BASE_STEREOTYPES) {
            PsiClass annotation = psi.findClass(fqn, scope);
            if (annotation != null) {
                found.add(annotation);
            }
        }
        return found;
    }

    /** Seeds plus every annotation in {@code scope} transitively meta-annotated with one. */
    private static @NotNull Set<PsiClass> closure(@NotNull Project project, @NotNull Set<PsiClass> seeds,
                                                  @NotNull GlobalSearchScope scope) {
        Set<PsiClass> found = new LinkedHashSet<>(seeds);
        Deque<PsiClass> queue = new ArrayDeque<>(seeds);
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

    private static @NotNull List<SpringBeanDef> stereotypeBeans(@NotNull GlobalSearchScope scope,
                                                                @NotNull Stereotypes stereotypes) {
        Set<PsiClass> beanClasses = new LinkedHashSet<>();
        for (PsiClass annotation : stereotypes.annotations()) {
            for (PsiClass beanClass : AnnotatedElementsSearch.searchPsiClasses(annotation, scope)) {
                if (beanClass.isValid() && !beanClass.isAnnotationType()) {
                    beanClasses.add(beanClass);
                }
            }
        }
        List<SpringBeanDef> beans = new ArrayList<>();
        for (PsiClass beanClass : beanClasses) {
            beans.add(classBean(beanClass, stereotypes.fqns()));
        }
        return beans;
    }

    /** Spring Data repository interfaces in {@code scope}: proxied by Spring Data, injectable like any bean. */
    private static @NotNull List<SpringBeanDef> repositoryBeans(@NotNull Module module, @NotNull GlobalSearchScope scope) {
        Project project = module.getProject();
        PsiClass repository = JavaPsiFacade.getInstance(project).findClass(JpaRepositories.REPOSITORY_FQN,
                GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false));
        if (repository == null) {
            return List.of();
        }
        List<SpringBeanDef> beans = new ArrayList<>();
        for (PsiClass candidate : ClassInheritorsSearch.search(repository, scope, true).findAll()) {
            if (!candidate.isValid() || !JpaRepositories.isRepositoryBean(candidate)) {
                continue;
            }
            String simpleName = candidate.getName();
            if (simpleName == null) {
                continue;
            }
            beans.add(new SpringBeanDef(
                    SpringNames.defaultBeanName(simpleName),
                    List.of(),
                    JavaPsiFacade.getElementFactory(project).createType(candidate),
                    candidate.getQualifiedName(),
                    BeanOrigin.SPRING_DATA_REPOSITORY,
                    null,
                    qualifierValues(candidate.getAnnotation(SpringAnnotations.QUALIFIER)),
                    SpringBeanDef.SINGLETON,
                    List.of(),
                    candidate.getAnnotation(SpringAnnotations.PRIMARY) != null,
                    candidate.getAnnotation(SpringAnnotations.LAZY) != null,
                    sourceElement(candidate)));
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
        PsiClass beanAnnotation = psi.findClass(SpringAnnotations.BEAN, GlobalSearchScope.allScope(project));
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
