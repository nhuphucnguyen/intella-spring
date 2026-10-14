package dev.phucngu.intellaspring.ui;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import dev.phucngu.intellaspring.spring.SpringInjections;
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

    /**
     * Navigate to a single target directly; several open a titled chooser with bean icons.
     * Labels are computed eagerly, under the caller's read action — the popup renders rows
     * lazily during paint and must never touch PSI from there.
     */
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
        List<LabeledTarget> entries = com.intellij.openapi.application.ReadAction.compute(() ->
                targets.stream().map(t -> new LabeledTarget(t, label.apply(t))).toList());
        JBPopupFactory.getInstance()
                .createListPopup(new BaseListPopupStep<>(title, entries) {
                    @Override
                    public @NotNull String getTextFor(LabeledTarget target) {
                        return target.label();
                    }

                    @Override
                    public Icon getIconFor(LabeledTarget target) {
                        return IntellaSpringIcons.BEAN;
                    }

                    @Override
                    public PopupStep<?> onChosen(LabeledTarget target, boolean finalChoice) {
                        return doFinalStep(() -> target.target().navigate(true));
                    }
                })
                .show(new RelativePoint(event));
    }

    /** A chooser entry with its label already computed — safe to read during paint. */
    public record LabeledTarget(@NotNull Navigatable target, @NotNull String label) {
    }

    /**
     * The leaf's click behavior: jump to injection points; a bean nobody wires yet falls back
     * to its plain usages; a bean with no references at all gets a balloon instead of silence.
     */
    public static void navigateToInjections(@NotNull Project project, @Nullable PsiClass beanType,
                                            @NotNull MouseEvent event, @NotNull String beanName) {
        if (beanType == null) {
            return;
        }
        // the gutter handler may run outside a read action; PSI search requires one
        java.util.List<PsiElement> targets = com.intellij.openapi.application.ReadAction.compute(() -> {
            java.util.List<PsiElement> found = new java.util.ArrayList<>(SpringInjections.injectionPoints(beanType));
            return found;
        });
        String title = "Injection Points";
        if (targets.isEmpty()) {
            title = "Usages";
            targets = com.intellij.openapi.application.ReadAction.compute(() -> {
                java.util.List<PsiElement> usages = new java.util.ArrayList<>();
                for (PsiReference reference : ReferencesSearch.search(beanType)) {
                    PsiMember member = PsiTreeUtil.getParentOfType(reference.getElement(), PsiMember.class, false);
                    if (member != null) {
                        usages.add(member);
                    }
                }
                return usages;
            });
        }
        if (targets.isEmpty()) {
            new Notification("IntellaSpring", "Spring bean '" + beanName + "'",
                    "No injection points or usages — nothing references this bean yet.",
                    NotificationType.INFORMATION).notify(project);
            return;
        }
        navigate(project, targets.stream().map(t -> (Navigatable) t).toList(),
                event, title, n -> MarkerSupport.memberLabel((com.intellij.psi.PsiElement) n));
    }

    /** "Owner.member" label for a field, method or parameter. */
    public static @NotNull String memberLabel(@NotNull PsiElement member) {
        String name = member instanceof PsiNamedElement named && named.getName() != null ? named.getName() : member.getText();
        PsiClass containing = PsiTreeUtil.getParentOfType(member, PsiClass.class, false);
        return containing != null ? containing.getName() + "." + name : name;
    }
}
