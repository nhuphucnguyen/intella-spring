package dev.phucngu.intellaspring.initializr;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

/** File → New → "Spring Boot Project (Initializr)…" — generate a project from start.spring.io. */
public class SpringInitializrAction extends AnAction implements DumbAware {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        new SpringInitializrDialog(e.getProject()).show();
    }
}
