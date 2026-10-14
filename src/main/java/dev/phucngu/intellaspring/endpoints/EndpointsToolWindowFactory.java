package dev.phucngu.intellaspring.endpoints;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

/** Registers the "Spring Endpoints" tool window: every request mapping in the project. */
public final class EndpointsToolWindowFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        EndpointsPanel panel = new EndpointsPanel(project, toolWindow);
        Content content = ContentFactory.getInstance().createContent(panel, null, false);
        content.setDisposer(() -> EndpointsPanel.disposeFor(project));
        toolWindow.getContentManager().addContent(content);
    }
}
