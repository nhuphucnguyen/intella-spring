package dev.phucngu.intellaspring.spring;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import org.jetbrains.annotations.NotNull;

/**
 * The bean caches follow roots through {@code ProjectRootModificationTracker}; on a roots
 * change this only drops the service's dumb-mode fallback and the config metadata cache.
 */
public class SpringModelRootListener implements ModuleRootListener {
    @Override
    public void rootsChanged(@NotNull ModuleRootEvent event) {
        Project project = event.getProject();
        if (!project.isDisposed()) {
            SpringContextService.getInstance(project).invalidate();
            dev.phucngu.intellaspring.config.SpringConfigMetadataService.getInstance(project).invalidate();
        }
    }
}
