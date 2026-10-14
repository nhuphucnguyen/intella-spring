package dev.phucngu.intellaspring.spring;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import org.jetbrains.annotations.NotNull;

/**
 * Library/classpath changes decide whether a module is a Spring module at all, so the bean
 * cache has to go when roots move — the PSI modification counter does not cover this.
 */
public class SpringModelRootListener implements ModuleRootListener {
    @Override
    public void rootsChanged(@NotNull ModuleRootEvent event) {
        Project project = event.getProject();
        if (!project.isDisposed()) {
            SpringContextService.getInstance(project).invalidate();
        }
    }
}
