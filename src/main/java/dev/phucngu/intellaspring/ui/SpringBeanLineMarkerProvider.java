package dev.phucngu.intellaspring.ui;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import dev.phucngu.intellaspring.spring.SpringAnnotations;
import dev.phucngu.intellaspring.spring.SpringContextService;
import dev.phucngu.intellaspring.spring.SpringInjections;
import dev.phucngu.intellaspring.spring.model.SpringBeanDef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;

/**
 * The bean leaf in the gutter of stereotype classes and {@code @Bean} methods. Clicking jumps
 * to the injection points of the bean (a single target navigates directly, several open a
 * chooser); S4's autowiring analysis will reuse the same injection locator for its inspections.
 */
public class SpringBeanLineMarkerProvider implements LineMarkerProvider {

    @Override
    public @Nullable LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        if (!(element instanceof PsiIdentifier identifier)) {
            return null;
        }
        PsiElement owner = identifier.getParent();
        boolean beanSite = (owner instanceof PsiClass beanClass && beanClass.getNameIdentifier() == identifier
                && !beanClass.isAnnotationType())
                || (owner instanceof PsiMethod method && method.getNameIdentifier() == identifier
                && method.getAnnotation(SpringAnnotations.BEAN) != null);
        if (!beanSite) {
            return null;
        }
        Module module = MarkerSupport.springModuleOf(element);
        if (module == null) {
            return null;
        }
        SpringBeanDef bean = SpringContextService.getInstance(element.getProject()).beans(module).stream()
                .filter(b -> element.equals(b.sourceElement()))
                .findFirst()
                .orElse(null);
        if (bean == null) {
            return null;
        }
        return new LineMarkerInfo<>(
                identifier,
                identifier.getTextRange(),
                IntellaSpringIcons.BEAN,
                ignored -> "Spring bean: " + bean.displayName(),
                (event, elt) -> navigateToInjections(element.getProject(), owner, event),
                GutterIconRenderer.Alignment.LEFT,
                () -> "intellaspring.bean." + bean.displayName());
    }

    private static void navigateToInjections(@NotNull Project project, @NotNull PsiElement owner,
                                             @NotNull MouseEvent event) {
        PsiClass beanType = beanTypeOf(owner);
        if (beanType == null) {
            return;
        }
        MarkerSupport.navigate(project,
                SpringInjections.injectionPoints(beanType).stream().map(t -> (Navigatable) t).toList(),
                event, "Injection Points", n -> MarkerSupport.memberLabel((com.intellij.psi.PsiElement) n));
    }

    private static @Nullable PsiClass beanTypeOf(@NotNull PsiElement owner) {
        if (owner instanceof PsiClass beanClass) {
            return beanClass;
        }
        if (owner instanceof PsiMethod method && method.getReturnType() instanceof PsiClassType type) {
            return type.resolve();
        }
        return null;
    }
}
