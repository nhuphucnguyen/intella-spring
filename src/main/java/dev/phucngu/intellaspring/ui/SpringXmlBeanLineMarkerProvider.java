package dev.phucngu.intellaspring.ui;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.module.Module;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import dev.phucngu.intellaspring.spring.SpringContextService;
import dev.phucngu.intellaspring.spring.SpringInjections;
import dev.phucngu.intellaspring.spring.model.SpringBeanDef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The bean leaf on {@code <bean>} tags in Spring XML configs — the XML twin of the Java class
 * marker. Navigation follows the same rule as the Java side: to the injection points of the
 * bean's class.
 */
public class SpringXmlBeanLineMarkerProvider implements LineMarkerProvider {

    @Override
    public @Nullable LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        if (!(element instanceof XmlToken token) || token.getTokenType() != XmlTokenType.XML_NAME) {
            return null;
        }
        if (!(token.getParent() instanceof XmlTag tag) || !"bean".equals(tag.getLocalName())) {
            return null;
        }
        Module module = MarkerSupport.springModuleOf(element);
        if (module == null) {
            return null;
        }
        SpringBeanDef bean = beansOfTag(module, tag);
        if (bean == null) {
            return null;
        }
        return new LineMarkerInfo<>(
                token,
                token.getTextRange(),
                IntellaSpringIcons.BEAN,
                ignored -> "Spring bean: " + bean.displayName(),
                (event, elt) -> {
                    if (bean.beanType() instanceof PsiClassType classType && classType.resolve() != null) {
                        MarkerSupport.navigate(module.getProject(),
                                SpringInjections.injectionPoints(classType.resolve()).stream()
                                        .map(t -> (Navigatable) t).toList(),
                                event, "Injection Points", n -> MarkerSupport.memberLabel((com.intellij.psi.PsiElement) n));
                    }
                },
                GutterIconRenderer.Alignment.LEFT,
                () -> "intellaspring.xmlbean." + bean.displayName());
    }

    private static @Nullable SpringBeanDef beansOfTag(@NotNull Module module, @NotNull XmlTag tag) {
        List<SpringBeanDef> beans = SpringContextService.getInstance(module.getProject()).beans(module);
        for (SpringBeanDef bean : beans) {
            if (bean.sourceElement() == tag) {
                return bean;
            }
        }
        return null;
    }
}
