package dev.phucngu.intellaspring.ui;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.awt.RelativePoint;
import dev.phucngu.intellaspring.spring.SpringAnnotations;
import dev.phucngu.intellaspring.spring.SpringContextService;
import dev.phucngu.intellaspring.spring.SpringInjections;
import dev.phucngu.intellaspring.spring.model.SpringBeanDef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * The bean leaf in the gutter of stereotype classes and {@code @Bean} methods. Clicking jumps
 * to the injection points of the bean (a single target navigates directly, several open a
 * chooser); S4's autowiring analysis will reuse the same injection locator for the reverse
 * arrows.
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
        Module module = ModuleUtilCore.findModuleForPsiElement(element);
        if (module == null) {
            return null;
        }
        SpringContextService spring = SpringContextService.getInstance(element.getProject());
        if (!spring.isSpringModule(module)) {
            return null;
        }
        SpringBeanDef bean = spring.beans(module).stream()
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
        List<PsiElement> targets = SpringInjections.injectionPoints(beanType);
        if (targets.isEmpty()) {
            // Nothing injects this bean yet; the tooltip already names it. Improves with S4.
            return;
        }
        if (targets.size() == 1) {
            ((Navigatable) targets.get(0)).navigate(true);
            return;
        }
        JBPopupFactory.getInstance()
                .createListPopup(new BaseListPopupStep<InjectionTarget>("Injection Points",
                        targets.stream().map(InjectionTarget::new).toList()) {
                    @Override
                    public @NotNull String getTextFor(InjectionTarget target) {
                        return target.label();
                    }

                    @Override
                    public Icon getIconFor(InjectionTarget target) {
                        return IntellaSpringIcons.BEAN;
                    }

                    @Override
                    public PopupStep<?> onChosen(InjectionTarget target, boolean finalChoice) {
                        return doFinalStep(() -> ((Navigatable) target.element()).navigate(true));
                    }
                })
                .show(new RelativePoint(event));
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

    /** A chooser entry: the injected member, labeled "Owner.member (kind)". */
    private record InjectionTarget(@NotNull PsiElement element) implements Navigatable {
        private @NotNull String label() {
            if (element instanceof PsiMethod method) {
                String owner = method.getContainingClass() != null ? method.getContainingClass().getName() + "." : "";
                return owner + method.getName() + "()";
            }
            String owner = containingClassName(element);
            String name = element instanceof PsiParameter parameter ? parameter.getName() : element.getText();
            return owner != null ? owner + "." + name : name;
        }

        private @Nullable String containingClassName(@NotNull PsiElement element) {
            PsiClass containing = PsiTreeUtil.getParentOfType(element, PsiClass.class, false);
            return containing != null ? containing.getName() : null;
        }

        @Override
        public void navigate(boolean requestFocus) {
            ((Navigatable) element).navigate(requestFocus);
        }

        @Override
        public boolean canNavigate() {
            return ((Navigatable) element).canNavigate();
        }

        @Override
        public boolean canNavigateToSource() {
            return ((Navigatable) element).canNavigateToSource();
        }
    }
}
