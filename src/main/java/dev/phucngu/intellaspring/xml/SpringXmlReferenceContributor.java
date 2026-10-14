package dev.phucngu.intellaspring.xml;

import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.PsiPolyVariantReferenceBase;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import dev.phucngu.intellaspring.spring.SpringContextService;
import dev.phucngu.intellaspring.spring.model.SpringBeanDef;
import dev.phucngu.intellaspring.ui.IntellaSpringIcons;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * References inside Spring XML configs: {@code ref="beanName"} on property/constructor-arg,
 * {@code <ref bean="…"/>}, {@code p:x-ref="…"} attributes, and a soft (never red)
 * {@code class="com.example.Foo"} link on {@code <bean>} tags. Bean names resolve against the
 * model, so annotations and XML declarations are interchangeable targets — the way IDEA's
 * CommonSpringBean makes them one namespace.
 */
public class SpringXmlReferenceContributor extends PsiReferenceContributor {

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(XmlAttributeValue.class),
                new PsiReferenceProvider() {
                    @Override
                    public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                                                                           @NotNull ProcessingContext context) {
                        if (!(element instanceof XmlAttributeValue value)
                                || !(value.getParent() instanceof XmlAttribute attribute)
                                || !(attribute.getParent() instanceof XmlTag tag)) {
                            return PsiReference.EMPTY_ARRAY;
                        }
                        String attributeName = attribute.getLocalName();
                        String tagName = tag.getLocalName();
                        String text = value.getValue();
                        if (text.isBlank()) {
                            return PsiReference.EMPTY_ARRAY;
                        }
                        if ("ref".equals(attributeName) && ("property".equals(tagName) || "constructor-arg".equals(tagName))
                                || "bean".equals(attributeName) && "ref".equals(tagName)
                                || attributeName.endsWith("-ref") && "bean".equals(tagName)) {
                            return new PsiReference[]{new SpringBeanReference(value)};
                        }
                        if ("class".equals(attributeName) && "bean".equals(tagName)) {
                            return new PsiReference[]{new SpringClassReference(value)};
                        }
                        return PsiReference.EMPTY_ARRAY;
                    }
                });
    }

    /** Resolves a bean by name/alias across the project's Spring contexts. */
    static java.util.List<SpringBeanDef> beansNamed(@NotNull PsiElement host, @NotNull String name) {
        return SpringContextService.getInstance(host.getProject()).allBeans().stream()
                .filter(bean -> bean.matchesName(name))
                .toList();
    }

    /** A hard reference: resolves to the bean's declaration, red when the name is unknown. */
    static final class SpringBeanReference extends PsiPolyVariantReferenceBase<XmlAttributeValue> {
        SpringBeanReference(@NotNull XmlAttributeValue host) {
            super(host, false);
        }

        @Override
        public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
            return beansNamed(getElement(), getElement().getValue()).stream()
                    .map(bean -> new PsiElementResolveResult(bean.sourceElement()))
                    .toArray(ResolveResult[]::new);
        }

        @Override
        public Object @NotNull [] getVariants() {
            return SpringContextService.getInstance(getElement().getProject()).allBeans().stream()
                    .map(bean -> LookupElementBuilder.create(bean.displayName())
                            .withIcon(IntellaSpringIcons.BEAN)
                            .withTailText("  " + bean.origin().name().toLowerCase(), true)
                            .withTypeText(bean.beanTypeName() != null ? bean.beanTypeName() : "spring bean", true))
                    .toArray();
        }

        @Override
        public @Nullable PsiElement resolve() {
            ResolveResult[] results = multiResolve(false);
            return results.length == 1 ? results[0].getElement() : null;
        }
    }

    /** A soft class link: navigates when the class resolves, never marks errors. */
    static final class SpringClassReference extends PsiPolyVariantReferenceBase<XmlAttributeValue> {
        SpringClassReference(@NotNull XmlAttributeValue host) {
            super(host, true);
        }

        @Override
        public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
            com.intellij.psi.PsiClass resolved = com.intellij.psi.JavaPsiFacade
                    .getInstance(getElement().getProject())
                    .findClass(getElement().getValue(), com.intellij.psi.search.GlobalSearchScope.allScope(getElement().getProject()));
            return resolved != null ? new ResolveResult[]{new PsiElementResolveResult(resolved)} : ResolveResult.EMPTY_ARRAY;
        }

        @Override
        public Object @NotNull [] getVariants() {
            return ArrayUtil.EMPTY_OBJECT_ARRAY;
        }
    }
}
