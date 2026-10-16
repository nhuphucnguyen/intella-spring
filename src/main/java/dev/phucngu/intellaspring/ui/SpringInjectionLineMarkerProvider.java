package dev.phucngu.intellaspring.ui;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.ui.awt.RelativePoint;
import dev.phucngu.intellaspring.spring.SpringAnnotations;
import dev.phucngu.intellaspring.spring.SpringBeanMatching;
import dev.phucngu.intellaspring.spring.SpringContextService;
import dev.phucngu.intellaspring.spring.SpringInjections;
import dev.phucngu.intellaspring.spring.model.SpringBeanDef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The reverse direction of the bean marker: an up-arrow on {@code @Autowired} members and
 * {@code @Bean} factory methods, jumping to the bean declaration(s) that satisfy the injected
 * type. Single candidate navigates directly; several open a chooser.
 */
public class SpringInjectionLineMarkerProvider implements LineMarkerProvider {

    @Override
    public @Nullable LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        if (!(element instanceof PsiIdentifier identifier)) {
            return null;
        }
        PsiElement owner = identifier.getParent();
        List<SpringBeanMatching.InjectionSite> sites = null;
        if (owner instanceof PsiField field && field.getNameIdentifier() == identifier
                && (SpringInjections.isAutowiredField(field) || SpringInjections.isLombokInjectedField(field))) {
            sites = List.of(new SpringBeanMatching.InjectionSite(field, field.getType()));
        } else if (owner instanceof PsiMethod method && method.getNameIdentifier() == identifier
                && (SpringInjections.isAutowiredMethod(method) || SpringInjections.isBeanFactoryMethod(method)
                || (method.isConstructor() && SpringInjections.isImplicitInjectionConstructor(method)))) {
            sites = new ArrayList<>();
            for (PsiParameter parameter : method.getParameterList().getParameters()) {
                if (parameter.getAnnotation(SpringAnnotations.VALUE) == null) {
                    sites.add(new SpringBeanMatching.InjectionSite(parameter, parameter.getType()));
                }
            }
        }
        if (sites == null || sites.isEmpty()) {
            return null;
        }
        Module module = MarkerSupport.springModuleOf(element);
        if (module == null) {
            return null;
        }
        // same pool and rules as the autowiring inspection
        List<SpringBeanDef> pool = SpringContextService.getInstance(module.getProject()).allBeans();
        Set<SpringBeanDef> candidates = new LinkedHashSet<>();
        for (SpringBeanMatching.InjectionSite site : sites) {
            candidates.addAll(SpringBeanMatching.narrow(SpringBeanMatching.candidates(site, pool)));
        }
        if (candidates.isEmpty()) {
            return null;
        }
        String names = candidates.stream().map(SpringBeanDef::displayName)
                .reduce((a, b) -> a + ", " + b).orElse("");
        return new LineMarkerInfo<>(
                identifier,
                identifier.getTextRange(),
                IntellaSpringIcons.BEAN,
                ignored -> "Injected by Spring: " + names + " (navigate to bean)",
                (event, elt) -> navigateTo(module.getProject(), List.copyOf(candidates), event),
                GutterIconRenderer.Alignment.LEFT,
                () -> "intellaspring.injection." + names);
    }

    private static void navigateTo(@NotNull Project project, @NotNull List<SpringBeanDef> candidates,
                                   @NotNull MouseEvent event) {
        MarkerSupport.navigate(project,
                candidates.stream().map(b -> (Navigatable) b.sourceElement()).toList(),
                event, "Injected Beans",
                n -> MarkerSupport.memberLabel((com.intellij.psi.PsiElement) n));
    }
}
