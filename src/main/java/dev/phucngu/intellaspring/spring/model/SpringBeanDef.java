package dev.phucngu.intellaspring.spring.model;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One bean in a module's Spring context, unified across origins the way IDEA's
 * {@code CommonSpringBean} unifies XML, stereotypes and {@code @Bean} methods. Names follow
 * Spring rules: explicit annotation/XML value first, else the decapitalized class name or
 * the {@code @Bean} method name.
 *
 * @param beanName   primary name; null when the definition is anonymous (an XML bean
 *                   without id/name) — such beans are referenced by type only
 * @param beanType   resolved type when possible; an XML class attribute may point at a
 *                   class not on the classpath, in which case only {@link #beanTypeName} is set
 * @param stereotype the annotation that made a STEREOTYPE bean a bean — a base stereotype
 *                   FQN or the custom annotation's own FQN
 */
public record SpringBeanDef(
        @Nullable String beanName,
        @NotNull List<String> aliases,
        @Nullable PsiType beanType,
        @Nullable String beanTypeName,
        @NotNull BeanOrigin origin,
        @Nullable String stereotype,
        @NotNull List<String> qualifiers,
        @NotNull String scope,
        @NotNull List<String> profiles,
        boolean primary,
        boolean lazy,
        @NotNull PsiElement sourceElement) {

    public static final String SINGLETON = "singleton";

    public boolean isSingleton() {
        return scope.isBlank() || SINGLETON.equals(scope);
    }

    /** Name for choosers and (later) the beans tree. */
    public @NotNull String displayName() {
        if (beanName != null) {
            return beanName;
        }
        return beanTypeName != null ? beanTypeName : "<unnamed>";
    }

    public boolean matchesName(@NotNull String candidate) {
        return candidate.equals(beanName) || aliases.contains(candidate);
    }

    /**
     * PSI-free by design: this record reaches Swing renderers (the beans details lists),
     * which paint outside read actions — the generated record toString would touch
     * {@code beanType}/{@code sourceElement} and crash the paint.
     */
    @Override
    public String toString() {
        return "SpringBeanDef[" + displayName() + ", type=" + beanTypeName + ", origin=" + origin + "]";
    }
}
