package dev.phucngu.intellaspring.ui;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.pom.Navigatable;
import dev.phucngu.intellaspring.spring.SpringContextService;
import dev.phucngu.intellaspring.spring.model.SpringBeanDef;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * "Spring Bean…" in the Navigate menu: a chooser over every bean the model knows in the
 * project's Spring modules, with speed-search typing. The first user-visible consumer of the
 * S1 model — it doubles as the quickest way to sanity-check what the plugin understands.
 */
public class GoToSpringBeanAction extends AnAction {

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        e.getPresentation().setEnabledAndVisible(project != null
                && !DumbService.isDumb(project)
                && !beans(project).isEmpty());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }
        List<SpringBeanDef> beans = new ArrayList<>(beans(project));
        beans.sort(Comparator.comparing(SpringBeanDef::displayName, String.CASE_INSENSITIVE_ORDER));
        JBPopupFactory.getInstance()
                .createListPopup(new BaseListPopupStep<>("Spring Beans", beans) {
                    @Override
                    public @NotNull String getTextFor(SpringBeanDef bean) {
                        String location = bean.beanTypeName() != null
                                ? bean.beanTypeName() : bean.sourceElement().getContainingFile().getName();
                        return bean.displayName() + "  —  " + location;
                    }

                    @Override
                    public Icon getIconFor(SpringBeanDef bean) {
                        return IntellaSpringIcons.BEAN;
                    }

                    @Override
                    public PopupStep<?> onChosen(SpringBeanDef bean, boolean finalChoice) {
                        return doFinalStep(() -> ((Navigatable) bean.sourceElement()).navigate(true));
                    }
                })
                .showInFocusCenter();
    }

    private static @NotNull List<SpringBeanDef> beans(@NotNull Project project) {
        SpringContextService spring = SpringContextService.getInstance(project);
        List<SpringBeanDef> all = new ArrayList<>();
        for (Module module : ModuleManager.getInstance(project).getModules()) {
            if (spring.isSpringModule(module)) {
                all.addAll(spring.beans(module));
            }
        }
        return all;
    }
}
