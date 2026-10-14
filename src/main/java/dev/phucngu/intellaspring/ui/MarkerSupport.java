package dev.phucngu.intellaspring.ui;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.awt.RelativePoint;
import dev.phucngu.intellaspring.spring.SpringContextService;
import dev.phucngu.intellaspring.spring.model.SpringBeanDef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Function;

/** Shared guards, lookups and chooser navigation for the line-marker providers. */
public final class MarkerSupport {
    private MarkerSupport() {
    }

    /** The Spring module the element lives in, or null when it is outside any Spring module. */
    public static @Nullable Module springModuleOf(@NotNull PsiElement element) {
        Module module = ModuleUtilCore.findModuleForPsiElement(element);
        if (module == null || module.isDisposed()) {
            return null;
        }
        return SpringContextService.getInstance(element.getProject()).isSpringModule(module) ? module : null;
    }

    /** Beans whose declared type is the given class — the candidates an injection of it resolves to. */
    public static @NotNull List<SpringBeanDef> beansOfType(@NotNull Module module, @Nullable PsiClass type) {
        if (type == null || type.getQualifiedName() == null) {
            return List.of();
        }
        String fqn = type.getQualifiedName();
        return SpringContextService.getInstance(module.getProject()).beans(module).stream()
                .filter(bean -> fqn.equals(bean.beanTypeName()))
                .toList();
    }

    /** Navigate to a single target directly; several open a titled chooser with bean icons. */
    public static void navigate(@NotNull Project project, @NotNull List<? extends Navigatable> targets,
                                @NotNull MouseEvent event, @NotNull String title,
                                @NotNull Function<Navigatable, String> label) {
        if (targets.isEmpty()) {
            return;
        }
        if (targets.size() == 1) {
            targets.get(0).navigate(true);
            return;
        }
        JBPopupFactory.getInstance()
                .createListPopup(new BaseListPopupStep<Navigatable>(title, targets) {
                    @Override
                    public @NotNull String getTextFor(Navigatable target) {
                        return label.apply(target);
                    }

                    @Override
                    public Icon getIconFor(Navigatable target) {
                        return IntellaSpringIcons.BEAN;
                    }

                    @Override
                    public PopupStep<?> onChosen(Navigatable target, boolean finalChoice) {
                        return doFinalStep(() -> target.navigate(true));
                    }
                })
                .show(new RelativePoint(event));
    }

    /** "Owner.member" label for a field, method or parameter. */
    public static @NotNull String memberLabel(@NotNull PsiElement member) {
        String name = member instanceof PsiNamedElement named && named.getName() != null ? named.getName() : member.getText();
        PsiClass containing = PsiTreeUtil.getParentOfType(member, PsiClass.class, false);
        return containing != null ? containing.getName() + "." + name : name;
    }
}
