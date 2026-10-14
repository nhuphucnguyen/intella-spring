package dev.phucngu.intellaspring.xml;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import dev.phucngu.intellaspring.spring.SpringContextService;
import dev.phucngu.intellaspring.spring.SpringNames;
import dev.phucngu.intellaspring.ui.IntellaSpringIcons;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Completes {@code <property name="…">}/{@code <constructor-arg name="…">?} from the setters
 * of the enclosing {@code <bean>} class. Bean-name completion comes free through the
 * references in {@link SpringXmlReferenceContributor}.
 */
public class SpringXmlCompletionContributor extends CompletionContributor {

    public SpringXmlCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new CompletionProvider<>() {
            @Override
            protected void addCompletions(@NotNull CompletionParameters parameters,
                                          @NotNull com.intellij.util.ProcessingContext context,
                                          @NotNull CompletionResultSet result) {
                PsiElement position = parameters.getPosition();
                XmlAttributeValue value = owningAttributeValue(position);
                if (value == null) {
                    return;
                }
                if (!(value.getParent() instanceof XmlAttribute attribute)
                        || !(attribute.getParent() instanceof XmlTag tag)
                        || !"property".equals(tag.getLocalName())
                        || !"name".equals(attribute.getLocalName())) {
                    return;
                }
                PsiClass beanClass = enclosingBeanClass(tag);
                if (beanClass == null) {
                    return;
                }
                for (String property : writableProperties(beanClass)) {
                    result.addElement(LookupElementBuilder.create(property)
                            .withIcon(IntellaSpringIcons.BEAN)
                            .withTypeText(beanClass.getName() != null ? beanClass.getName() : "bean", true));
                }
            }
        });
    }

    private static XmlAttributeValue owningAttributeValue(@NotNull PsiElement position) {
        if (position instanceof XmlAttributeValue value) {
            return value;
        }
        if (position.getParent() instanceof XmlAttributeValue value) {
            return value;
        }
        return null;
    }

    /** Walks up from a nested tag to the owning <bean> and resolves its class attribute. */
    static PsiClass enclosingBeanClass(@NotNull XmlTag nested) {
        XmlTag tag = nested;
        while (tag != null) {
            if ("bean".equals(tag.getLocalName())) {
                String className = tag.getAttributeValue("class");
                if (className == null) {
                    return null;
                }
                var scope = com.intellij.psi.search.GlobalSearchScope.allScope(tag.getProject());
                Module module = ModuleUtilCore.findModuleForPsiElement(tag);
                if (module != null) {
                    scope = com.intellij.psi.search.GlobalSearchScope
                            .moduleWithDependenciesAndLibrariesScope(module, false);
                }
                return com.intellij.psi.JavaPsiFacade.getInstance(tag.getProject()).findClass(className, scope);
            }
            tag = tag.getParentTag();
        }
        return null;
    }

    /** Writable properties: single-parameter non-static setters, Intellabean-decapitalized. */
    public static java.util.List<String> writableProperties(@NotNull PsiClass beanClass) {
        Set<String> names = new LinkedHashSet<>();
        for (PsiMethod method : beanClass.getAllMethods()) {
            String name = method.getName();
            if (name.startsWith("set") && name.length() > 3
                    && method.getParameterList().getParametersCount() == 1
                    && !method.hasModifierProperty(com.intellij.psi.PsiModifier.STATIC)) {
                names.add(SpringNames.defaultBeanName(name.substring(3)));
            }
        }
        return java.util.List.copyOf(names);
    }
}
