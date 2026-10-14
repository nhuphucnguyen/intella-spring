package dev.phucngu.intellaspring.endpoints;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import dev.phucngu.intellaspring.endpoints.EndpointsTreeModel.ControllerRow;
import dev.phucngu.intellaspring.endpoints.EndpointsTreeModel.EndpointRow;
import dev.phucngu.intellaspring.endpoints.EndpointsTreeModel.ModuleRow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.Color;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Endpoints tool window body: a module → controller → endpoint tree over
 * {@link dev.phucngu.intellaspring.mvc.RequestMappingModel}. Rows are rebuilt on PSI changes (debounced, only while
 * visible) so the renderer only ever reads precomputed strings.
 */
final class EndpointsPanel extends SimpleToolWindowPanel {
    static final String TOOL_WINDOW_ID = "Spring Endpoints";

    private static final Map<Project, EndpointsPanel> ACTIVE = new HashMap<>();

    private final @NotNull Project project;
    private final @NotNull Tree tree = new Tree();
    private final @NotNull DefaultMutableTreeNode root = new DefaultMutableTreeNode("Spring Endpoints");
    private final @NotNull DefaultTreeModel model = new DefaultTreeModel(root);
    private final @Nullable ToolWindow toolWindow;
    private final com.intellij.util.Alarm psiAlarm;
    private boolean groupByController = true;
    private volatile @NotNull List<ModuleRow> rows = List.of();

    EndpointsPanel(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        super(true, true);
        this.project = project;
        this.toolWindow = toolWindow;
        this.psiAlarm = new com.intellij.util.Alarm(com.intellij.util.Alarm.ThreadToUse.SWING_THREAD, toolWindow.getDisposable());

        tree.setModel(model);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.getEmptyText().setText("No Spring request mappings found");
        tree.setCellRenderer(new EndpointRenderer());
        TreeSpeedSearch.installOn(tree);
        new DoubleClickListener() {
            @Override
            protected boolean onDoubleClick(@NotNull MouseEvent event) {
                return goToEndpoint();
            }
        }.installOn(tree);
        new AnAction() {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                goToEndpoint();
            }
        }.registerCustomShortcutSet(CustomShortcutSet.fromString("ENTER"), tree);
        tree.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(@NotNull MouseEvent event) {
                maybeShowPopup(event);
            }

            @Override
            public void mouseReleased(@NotNull MouseEvent event) {
                maybeShowPopup(event);
            }
        });

        setToolbar(buildToolbar());
        setContent(com.intellij.ui.ScrollPaneFactory.createScrollPane(tree, true));

        // rebuild after source edits, debounced; only while the tool window is on screen
        PsiManager.getInstance(project).addPsiTreeChangeListener(new PsiTreeChangeAdapter() {
            @Override
            public void childAdded(@NotNull PsiTreeChangeEvent event) { scheduleRefresh(); }
            @Override
            public void childRemoved(@NotNull PsiTreeChangeEvent event) { scheduleRefresh(); }
            @Override
            public void childReplaced(@NotNull PsiTreeChangeEvent event) { scheduleRefresh(); }
            @Override
            public void childMoved(@NotNull PsiTreeChangeEvent event) { scheduleRefresh(); }
            @Override
            public void childrenChanged(@NotNull PsiTreeChangeEvent event) { scheduleRefresh(); }
            @Override
            public void propertyChanged(@NotNull PsiTreeChangeEvent event) { scheduleRefresh(); }
        }, toolWindow.getDisposable());
        project.getMessageBus().connect(toolWindow.getDisposable()).subscribe(
                ToolWindowManagerListener.TOPIC, new ToolWindowManagerListener() {
                    @Override
                    public void toolWindowShown(@NotNull ToolWindow shown) {
                        if (shown == toolWindow) {
                            refresh();
                        }
                    }
                });

        synchronized (ACTIVE) {
            ACTIVE.put(project, this);
        }
        refresh();
    }

    static void disposeFor(@NotNull Project project) {
        synchronized (ACTIVE) {
            ACTIVE.remove(project);
        }
    }

    /** Activates the tool window and selects the tree row for the given handler (gutter entry point). */
    static void revealEndpoint(@NotNull Project project, @NotNull PsiMethod handler) {
        EndpointsPanel panel;
        synchronized (ACTIVE) {
            panel = ACTIVE.get(project);
        }
        if (panel == null) {
            return;
        }
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID);
        if (toolWindow != null) {
            toolWindow.activate(null);
        }
        panel.selectHandler(handler);
    }

    private void selectHandler(@NotNull PsiMethod handler) {
        DefaultMutableTreeNode found = findNode(root, handler);
        if (found != null) {
            TreePath path = new TreePath(found.getPath());
            tree.setSelectionPath(path);
            tree.scrollPathToVisible(path);
        }
    }

    private static @Nullable DefaultMutableTreeNode findNode(@NotNull DefaultMutableTreeNode node, @NotNull PsiMethod handler) {
        if (node.getUserObject() instanceof EndpointRow row && row.handler() == handler) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode hit = findNode((DefaultMutableTreeNode) node.getChildAt(i), handler);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private @NotNull JComponent buildToolbar() {
        DefaultActionGroup group = new DefaultActionGroup();
        group.add(new AnAction("Refresh", "Re-scan request mappings", AllIcons.Actions.Refresh) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                refresh();
            }
        });
        group.addSeparator();
        group.add(new CopyAction("Copy as cURL", AllIcons.Actions.Copy) {
            @Override
            protected @NotNull String textOf(@NotNull EndpointRow row) {
                return ReadAction.compute(() ->
                        EndpointsTreeModel.curlCommand(row, EndpointsTreeModel.serverPort(project)));
            }
        });
        group.add(new CopyAction("Copy Path", AllIcons.Actions.Copy) {
            @Override
            protected @NotNull String textOf(@NotNull EndpointRow row) {
                return row.path();
            }
        });
        group.addSeparator();
        group.add(new ToggleAction("Group by Controller", null, AllIcons.Actions.GroupByPackage) {
            @Override
            public boolean isSelected(@NotNull AnActionEvent e) {
                return groupByController;
            }

            @Override
            public void setSelected(@NotNull AnActionEvent e, boolean state) {
                groupByController = state;
                applyRows();
            }
        });
        group.add(new AnAction("Collapse All", null, AllIcons.Actions.Collapseall) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                for (int i = tree.getRowCount() - 1; i >= 0; i--) {
                    tree.collapseRow(i);
                }
            }
        });
        ActionToolbar toolbar = ActionManager.getInstance()
                .createActionToolbar("IntellaSpringEndpoints", group, true);
        toolbar.setTargetComponent(this);
        return toolbar.getComponent();
    }

    private abstract class CopyAction extends AnAction {
        CopyAction(@NotNull String text, @NotNull javax.swing.Icon icon) {
            super(text, null, icon);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            EndpointRow row = selectedEndpoint();
            if (row != null) {
                CopyPasteManager.getInstance().setContents(new StringSelection(textOf(row)));
            }
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setEnabledAndVisible(selectedEndpoint() != null);
        }

        protected abstract @NotNull String textOf(@NotNull EndpointRow row);
    }

    private @Nullable EndpointRow selectedEndpoint() {
        Object last = tree.getLastSelectedPathComponent();
        return last instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof EndpointRow row
                ? row : null;
    }

    private boolean goToEndpoint() {
        EndpointRow row = selectedEndpoint();
        if (row == null) {
            return false;
        }
        if (!row.handler().isValid()) {
            refresh();
            return true;
        }
        row.handler().navigate(true);
        return true;
    }

    private void maybeShowPopup(@NotNull MouseEvent event) {
        if (!event.isPopupTrigger()) {
            return;
        }
        TreePath path = tree.getClosestPathForLocation(event.getX(), event.getY());
        if (path == null) {
            return;
        }
        tree.setSelectionPath(path);
        if (!(((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject() instanceof EndpointRow)) {
            return;
        }
        JPopupMenu menu = new JPopupMenu();
        JMenuItem goTo = new JMenuItem("Go to Handler", AllIcons.Actions.EditSource);
        goTo.addActionListener(e -> goToEndpoint());
        JMenuItem copyCurl = new JMenuItem("Copy as cURL", AllIcons.Actions.Copy);
        copyCurl.addActionListener(e -> {
            EndpointRow row = selectedEndpoint();
            if (row != null) {
                CopyPasteManager.getInstance().setContents(new StringSelection(ReadAction.compute(() ->
                        EndpointsTreeModel.curlCommand(row, EndpointsTreeModel.serverPort(project)))));
            }
        });
        JMenuItem copyPath = new JMenuItem("Copy Path", AllIcons.Actions.Copy);
        copyPath.addActionListener(e -> {
            EndpointRow row = selectedEndpoint();
            if (row != null) {
                CopyPasteManager.getInstance().setContents(new StringSelection(row.path()));
            }
        });
        menu.add(goTo);
        menu.addSeparator();
        menu.add(copyCurl);
        menu.add(copyPath);
        menu.show(tree, event.getX(), event.getY());
    }

    private void scheduleRefresh() {
        if (toolWindow == null || !toolWindow.isVisible()) {
            return;
        }
        psiAlarm.cancelAllRequests();
        psiAlarm.addRequest(this::refresh, 700);
    }

    /** Rescans in the background and repopulates the tree on the EDT. */
    void refresh() {
        if (DumbService.isDumb(project)) {
            DumbService.getInstance(project).runWhenSmart(this::refresh);
            return;
        }
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            List<ModuleRow> scanned = ReadAction.compute(() -> EndpointsTreeModel.build(project));
            ApplicationManager.getApplication().invokeLater(() -> {
                if (project.isDisposed()) {
                    return;
                }
                rows = scanned;
                applyRows();
            }, com.intellij.openapi.application.ModalityState.any(), project.getDisposed());
        });
    }

    private void applyRows() {
        root.removeAllChildren();
        for (ModuleRow module : rows) {
            DefaultMutableTreeNode moduleNode = new DefaultMutableTreeNode(module);
            if (groupByController) {
                for (ControllerRow controller : module.controllers()) {
                    DefaultMutableTreeNode controllerNode = new DefaultMutableTreeNode(controller);
                    for (EndpointRow endpoint : controller.endpoints()) {
                        controllerNode.add(new DefaultMutableTreeNode(endpoint));
                    }
                    moduleNode.add(controllerNode);
                }
            } else {
                for (EndpointRow endpoint : module.flatEndpoints()) {
                    moduleNode.add(new DefaultMutableTreeNode(endpoint));
                }
            }
            root.add(moduleNode);
        }
        model.reload();
        TreeUtil.expand(tree, 2);
        tree.getEmptyText().setText(rows.isEmpty()
                ? "No Spring request mappings found — add a @RestController with a mapping"
                : "No Spring request mappings found");
        repaint();
    }

    private final class EndpointRenderer extends ColoredTreeCellRenderer {
        @Override
        public void customizeCellRenderer(@NotNull javax.swing.JTree tree, @Nullable Object value,
                                          boolean selected, boolean expanded, boolean leaf,
                                          int row, boolean hasFocus) {
            if (!(value instanceof DefaultMutableTreeNode node)
                    || !(node.getUserObject() instanceof EndpointRow endpoint)) {
                if (value instanceof DefaultMutableTreeNode node2
                        && node2.getUserObject() instanceof ControllerRow controller) {
                    setIcon(AllIcons.Nodes.Class);
                    append(controller.controllerName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
                } else if (value instanceof DefaultMutableTreeNode node3
                        && node3.getUserObject() instanceof ModuleRow module) {
                    setIcon(AllIcons.Nodes.Module);
                    append(module.moduleName(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
                }
                return;
            }
            setIcon(AllIcons.Nodes.Method);
            append(endpoint.verbsLabel() + "  ", verbAttributes(endpoint.verbsLabel()));
            append(endpoint.path(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
    }

    private static @NotNull SimpleTextAttributes verbAttributes(@NotNull String verbsLabel) {
        Color color = switch (verbsLabel) {
            case "GET" -> new JBColor(0x1E7B34, 0x57A85F);
            case "POST" -> new JBColor(0xB3621B, 0xD08B4A);
            case "PUT" -> new JBColor(0x2062C4, 0x6D9FEB);
            case "DELETE" -> new JBColor(0xB3271E, 0xE06C60);
            case "PATCH" -> new JBColor(0x7A3FC4, 0xA98AE8);
            default -> UIUtil.getContextHelpForeground(); // ANY / multi-verb
        };
        return new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, color);
    }
}
