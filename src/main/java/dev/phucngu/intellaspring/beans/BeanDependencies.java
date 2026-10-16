package dev.phucngu.intellaspring.beans;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import dev.phucngu.intellaspring.spring.SpringBeanMatching;
import dev.phucngu.intellaspring.spring.model.BeanOrigin;
import dev.phucngu.intellaspring.spring.model.SpringBeanDef;
import dev.phucngu.intellaspring.ui.MarkerSupport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Bean dependency edges for the Beans view: what a bean injects, and who injects it.
 * Java-side only for now (annotation sites, implicit constructors, Lombok, @Bean factory
 * parameters) — XML {@code <property ref>} edges are deferred.
 */
public final class BeanDependencies {

    /** One edge: the bean on the other end, and the site that creates the dependency. */
    public record Dependency(@NotNull SpringBeanDef bean, @NotNull String siteLabel) {
    }

    private BeanDependencies() {
    }

    /** Beans this bean injects into itself. */
    public static @NotNull List<Dependency> dependsOn(@NotNull SpringBeanDef bean,
                                                      @NotNull List<SpringBeanDef> pool) {
        Set<Dependency> edges = new LinkedHashSet<>();
        for (SpringBeanMatching.InjectionSite site : sitesOf(bean)) {
            for (SpringBeanDef candidate : SpringBeanMatching.narrow(SpringBeanMatching.candidates(site, pool))) {
                if (!candidate.equals(bean)) {
                    edges.add(new Dependency(candidate, MarkerSupport.memberLabel(site.member())));
                }
            }
        }
        return sorted(edges);
    }

    /**
     * A {@code @Bean}-produced bean depends on what its factory method needs; every other
     * bean depends on what its class injects.
     */
    private static @NotNull List<SpringBeanMatching.InjectionSite> sitesOf(@NotNull SpringBeanDef bean) {
        if (bean.origin() == BeanOrigin.BEAN_METHOD) {
            com.intellij.psi.PsiMethod factory = com.intellij.psi.util.PsiTreeUtil.getParentOfType(
                    bean.sourceElement(), com.intellij.psi.PsiMethod.class);
            if (factory != null && factory.getAnnotation(dev.phucngu.intellaspring.spring.SpringAnnotations.BEAN) != null) {
                return SpringBeanMatching.factoryMethodSites(factory);
            }
        }
        PsiClass beanClass = resolveBeanClass(bean);
        return beanClass != null ? SpringBeanMatching.injectionSites(beanClass) : List.of();
    }

    /** Beans that inject this bean into themselves. */
    public static @NotNull List<Dependency> injectedInto(@NotNull SpringBeanDef bean,
                                                         @NotNull List<SpringBeanDef> pool) {
        List<Dependency> edges = new ArrayList<>();
        for (SpringBeanDef other : pool) {
            if (other.equals(bean)) {
                continue;
            }
            PsiClass otherClass = resolveBeanClass(other);
            if (otherClass == null) {
                continue;
            }
            for (SpringBeanMatching.InjectionSite site : SpringBeanMatching.injectionSites(otherClass)) {
                if (SpringBeanMatching.narrow(SpringBeanMatching.candidates(site, pool)).contains(bean)) {
                    edges.add(new Dependency(other, MarkerSupport.memberLabel(site.member())));
                }
            }
        }
        return sorted(edges);
    }

    private static @NotNull List<Dependency> sorted(@NotNull Iterable<Dependency> edges) {
        List<Dependency> list = new ArrayList<>();
        edges.forEach(list::add);
        list.sort(Comparator.comparing((Dependency d) -> d.bean().displayName(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Dependency::siteLabel));
        return list;
    }

    static @Nullable PsiClass resolveBeanClass(@NotNull SpringBeanDef bean) {
        return bean.beanType() instanceof PsiClassType classType ? classType.resolve() : null;
    }
}
