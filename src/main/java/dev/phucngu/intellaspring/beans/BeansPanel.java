package dev.phucngu.intellaspring.beans;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Alarm;
import com.intellij.util.ui.tree.TreeUtil;
import dev.phucngu.intellaspring.beans.BeanDependencies.Dependency;
import dev.phucngu.intellaspring.beans.BeansTreeModel.BeanRow;
import dev.phucngu.intellaspring.beans.BeansTreeModel.ModuleRow;
import dev.phucngu.intellaspring.spring.SpringContextService;
import dev.phucngu.intellaspring.spring.model.BeanOrigin;
import dev.phucngu.intellaspring.spring.model.SpringBeanDef;
import dev.phucngu.intellaspring.ui.IntellaSpringIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.ListSelectionModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.BorderLayout;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The Beans tool window body: a module → bean tree on the left, and on the right the selected
 * bean's details with its dependency edges (what it injects / what injects it), both
 * double-clickable to the bean's declaration. Like the Endpoints panel, all PSI reads happen
 * inside read actions and rows carry precomputed strings only.
 */
final class BeansPanel extends SimpleToolWindowPanel {

    private final @NotNull Project project;
    private final @NotNull Tree tree = new Tree();
    private final @NotNull DefaultMutableTreeNode root = new DefaultMutableTreeNode("Spring Beans");
    private final @NotNull DefaultTreeModel model = new DefaultTreeModel(root);
    private final @NotNull Alarm psiAlarm;
    private final @NotNull JBLabel beanTitle = new JBLabel("Select a bean");
    private final @NotNull JBLabel beanInfo = new JBLabel();
    private final @NotNull JBList<Dependency> dependsOnList = new JBList<>();
    private final @NotNull JBList<Dependency> injectedIntoList = new JBList<>();
    private final @NotNull AtomicInteger selectionTicket = new AtomicInteger();
    private final @Nullable ToolWindow toolWindow;
    private volatile @NotNull List<SpringBeanDef> pool = List.of();

    BeansPanel(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        super(true, true);
        this.project = project;
        this.toolWindow = toolWindow;
        this.psiAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, toolWindow.getDisposable());

        tree.setModel(model);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.getEmptyText().setText("No Spring beans found");
        tree.setCellRenderer(new BeanRenderer());
        TreeSpeedSearch.installOn(tree);
        new DoubleClickListener() {
            @Override
            protected boolean onDoubleClick(@NotNull MouseEvent event) {
                return goToSelectedBean();
            }
        }.installOn(tree);
        new AnAction() {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                goToSelectedBean();
            }
        }.registerCustomShortcutSet(CustomShortcutSet.fromString("ENTER"), tree);
        tree.addTreeSelectionListener(e -> showDetails(selectedBean()));

        JComponent left = com.intellij.ui.ScrollPaneFactory.createScrollPane(tree, true);
        setToolbar(buildToolbar());
        OnePixelSplitter splitter = new OnePixelSplitter(false, 0.45f);
        splitter.setFirstComponent(left);
        splitter.setSecondComponent(buildDetails());
        setContent(splitter);

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
        refresh();
    }

    private @NotNull JComponent buildDetails() {
        javax.swing.JPanel details = new javax.swing.JPanel(new BorderLayout(0, JBUI_GAP));
        javax.swing.JPanel header = new javax.swing.JPanel(new BorderLayout());
        beanTitle.setFont(beanTitle.getFont().deriveFont(java.awt.Font.BOLD));
        header.add(beanTitle, BorderLayout.CENTER);
        header.add(beanInfo, BorderLayout.SOUTH);
        details.add(header, BorderLayout.NORTH);
        javax.swing.JPanel lists = new javax.swing.JPanel(new java.awt.GridLayout(2, 1, 0, JBUI_GAP));
        lists.add(section("Depends on", dependsOnList));
        lists.add(section("Injected into", injectedIntoList));
        details.add(lists, BorderLayout.CENTER);
        return details;
    }

    private static final int JBUI_GAP = com.intellij.util.ui.JBUI.scale(6);

    private @NotNull JComponent section(@NotNull String title, @NotNull JBList<Dependency> list) {
        list.setEmptyText("—");
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> l, Object value,
                                                                   int index, boolean selected, boolean focus) {
                java.awt.Component c = super.getListCellRendererComponent(l, value, index, selected, focus);
                if (value instanceof Dependency dep && c instanceof javax.swing.JLabel label) {
                    label.setText("<html><b>" + dep.bean().displayName() + "</b> <font color=gray>via "
                            + dep.siteLabel() + "</font></html>");
                    label.setIcon(IntellaSpringIcons.BEAN);
                }
                return c;
            }
        });
        new DoubleClickListener() {
            @Override
            protected boolean onDoubleClick(@NotNull MouseEvent event) {
                Dependency dep = list.getSelectedValue();
                if (dep != null && dep.bean().sourceElement().isValid()) {
                    ((com.intellij.pom.Navigatable) dep.bean().sourceElement()).navigate(true);
                    return true;
                }
                return false;
            }
        }.installOn(list);
        javax.swing.JPanel panel = new javax.swing.JPanel(new BorderLayout());
        JBLabel label = new JBLabel(title);
        label.setForeground(com.intellij.util.ui.UIUtil.getContextHelpForeground());
        panel.add(label, BorderLayout.NORTH);
        panel.add(com.intellij.ui.ScrollPaneFactory.createScrollPane(list, true), BorderLayout.CENTER);
        return panel;
    }

    private @NotNull JComponent buildToolbar() {
        DefaultActionGroup group = new DefaultActionGroup();
        group.add(new AnAction("Refresh", "Re-scan the bean model", AllIcons.Actions.Refresh) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                refresh();
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
                .createActionToolbar("IntellaSpringBeans", group, true);
        toolbar.setTargetComponent(this);
        return toolbar.getComponent();
    }

    private boolean goToSelectedBean() {
        SpringBeanDef bean = selectedBean();
        if (bean == null) {
            return false;
        }
        if (!bean.sourceElement().isValid()) {
            refresh();
            return true;
        }
        ((com.intellij.pom.Navigatable) bean.sourceElement()).navigate(true);
        return true;
    }

    private @Nullable SpringBeanDef selectedBean() {
        Object last = tree.getLastSelectedPathComponent();
        return last instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof BeanRow row
                ? row.def() : null;
    }

    private void scheduleRefresh() {
        if (toolWindow == null || !toolWindow.isVisible()) {
            return;
        }
        psiAlarm.cancelAllRequests();
        psiAlarm.addRequest(this::refresh, 700);
    }

    void refresh() {
        if (DumbService.isDumb(project)) {
            DumbService.getInstance(project).runWhenSmart(this::refresh);
            return;
        }
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            List<BeansTreeModel.ModuleRow> scanned = ReadAction.compute(() -> BeansTreeModel.build(project));
            List<SpringBeanDef> scannedPool = ReadAction.compute(() ->
                    SpringContextService.getInstance(project).allBeans());
            ApplicationManager.getApplication().invokeLater(() -> {
                if (project.isDisposed()) {
                    return;
                }
                pool = scannedPool;
                applyRows(scanned);
            }, com.intellij.openapi.application.ModalityState.any(), project.getDisposed());
        });
    }

    private void applyRows(@NotNull List<ModuleRow> rows) {
        root.removeAllChildren();
        for (ModuleRow module : rows) {
            DefaultMutableTreeNode moduleNode = new DefaultMutableTreeNode(module);
            for (BeanRow bean : module.beans()) {
                moduleNode.add(new DefaultMutableTreeNode(bean));
            }
            root.add(moduleNode);
        }
        model.reload();
        TreeUtil.expand(tree, 1);
        tree.getEmptyText().setText(rows.isEmpty()
                ? "No Spring beans found — add a @Component/@Service or an XML <bean>"
                : "No Spring beans found");
        repaint();
    }

    /** Details + dependency edges for the selected bean, computed off the EDT. */
    private void showDetails(@Nullable SpringBeanDef bean) {
        int ticket = selectionTicket.incrementAndGet();
        if (bean == null) {
            beanTitle.setText("Select a bean");
            beanInfo.setText("");
            dependsOnList.setModel(listModel(List.of()));
            injectedIntoList.setModel(listModel(List.of()));
            return;
        }
        beanTitle.setText(bean.displayName());
        beanInfo.setText("Computing…");
        List<SpringBeanDef> currentPool = pool;
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            record Details(String info, List<Dependency> out, List<Dependency> in) {
            }
            Details details = ReadAction.compute(() -> {
                String info = bean.origin() + (bean.stereotype() != null ? " (" + shortName(bean.stereotype()) + ")" : "")
                        + (bean.beanTypeName() != null ? " · " + bean.beanTypeName() : "")
                        + (bean.primary() ? " · primary" : "")
                        + (bean.lazy() ? " · lazy" : "")
                        + (!bean.profiles().isEmpty() ? " · profiles " + String.join(",", bean.profiles()) : "");
                return new Details(info, BeanDependencies.dependsOn(bean, currentPool),
                        BeanDependencies.injectedInto(bean, currentPool));
            });
            ApplicationManager.getApplication().invokeLater(() -> {
                if (project.isDisposed() || selectionTicket.get() != ticket) {
                    return;
                }
                beanInfo.setText(details.info());
                dependsOnList.setModel(listModel(details.out()));
                injectedIntoList.setModel(listModel(details.in()));
            }, com.intellij.openapi.application.ModalityState.any(), project.getDisposed());
        });
    }

    private static @NotNull javax.swing.ListModel<Dependency> listModel(@NotNull List<Dependency> deps) {
        javax.swing.DefaultListModel<Dependency> m = new javax.swing.DefaultListModel<>();
        deps.forEach(m::addElement);
        return m;
    }

    private static @NotNull String shortName(@NotNull String fqn) {
        return fqn.substring(fqn.lastIndexOf('.') + 1);
    }

    private final class BeanRenderer extends ColoredTreeCellRenderer {
        @Override
        public void customizeCellRenderer(@NotNull javax.swing.JTree treeComponent, @Nullable Object value,
                                          boolean selected, boolean expanded, boolean leaf,
                                          int row, boolean hasFocus) {
            if (!(value instanceof DefaultMutableTreeNode node)) {
                return;
            }
            if (node.getUserObject() instanceof BeanRow bean) {
                setIcon(bean.origin() == BeanOrigin.XML ? AllIcons.FileTypes.Xml
                        : bean.origin() == BeanOrigin.SPRING_DATA_REPOSITORY ? AllIcons.Nodes.Interface
                        : IntellaSpringIcons.BEAN);
                append(bean.name(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
                if (bean.typeShort() != null) {
                    append("  " + bean.typeShort(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
                }
            } else if (node.getUserObject() instanceof ModuleRow module) {
                setIcon(AllIcons.Nodes.Module);
                append(module.moduleName(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
            }
        }
    }
}
