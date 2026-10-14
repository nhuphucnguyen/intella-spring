package dev.phucngu.intellaspring.generate;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.searches.AllClassesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Query;
import dev.phucngu.intellaspring.spring.SpringAnnotations;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Generate (Alt+Insert) inside a {@code @Configuration}/{@code @Component} class:
 * "Spring @Bean Method…" picks any concrete project class and appends a
 * {@code @Bean} factory method for it — {@code return new X();} when X is
 * default-constructible, otherwise {@code return null;} as a construction TODO.
 */
public class GenerateBeanMethodAction extends AnAction {

    /** Chooser entry: precomputed under read access — PSI is not touched during popup paint. */
    private record BeanClassEntry(@NotNull String qualifiedName, @NotNull String displayName) {
    }

    private static final int MAX_ENTRIES = 500;

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        PsiFile file = event.getData(CommonDataKeys.PSI_FILE);
        var editor = event.getData(CommonDataKeys.EDITOR);
        boolean applicable = project != null && file != null && editor != null
                && configurationClassAt(file, editor.getCaretModel().getOffset()) != null;
        event.getPresentation().setEnabledAndVisible(applicable);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        PsiFile file = event.getData(CommonDataKeys.PSI_FILE);
        var editor = event.getData(CommonDataKeys.EDITOR);
        if (project == null || file == null || editor == null) {
            return;
        }
        PsiClass configClass = configurationClassAt(file, editor.getCaretModel().getOffset());
        if (configClass == null) {
            return;
        }
        List<BeanClassEntry> entries = com.intellij.openapi.application.ReadAction.compute(() -> chooserEntries(project, configClass));
        if (entries.isEmpty()) {
            return;
        }
        JBPopupFactory.getInstance().createPopupChooserBuilder(entries)
                .setTitle("Bean Type")
                .setRenderer(displayRenderer())
                .setNamerForFiltering(BeanClassEntry::displayName)
                .setFilterAlwaysVisible(true)
                .setItemChosenCallback(entry -> generateFor(project, configClass, entry))
                .createPopup()
                .showInFocusCenter();
    }

    /** The @Configuration/@Component class containing the offset, or null. */
    private static @Nullable PsiClass configurationClassAt(@NotNull PsiFile file, int offset) {
        return com.intellij.openapi.application.ReadAction.compute(() -> {
            PsiElement at = file.getViewProvider().findElementAt(offset);
            PsiClass cls = PsiTreeUtil.getParentOfType(at, PsiClass.class);
            while (cls != null) {
                if (isConfiguration(cls)) {
                    return cls;
                }
                cls = PsiTreeUtil.getParentOfType(cls.getParent(), PsiClass.class);
            }
            return null;
        });
    }

    private static boolean isConfiguration(@NotNull PsiClass cls) {
        return cls.getAnnotation(SpringAnnotations.CONFIGURATION) != null
                || cls.getAnnotation(SpringAnnotations.COMPONENT) != null;
    }

    private static @NotNull List<BeanClassEntry> chooserEntries(@NotNull Project project, @NotNull PsiClass configClass) {
        String selfFqn = configClass.getQualifiedName();
        List<BeanClassEntry> entries = new ArrayList<>();
        Query<PsiClass> query = AllClassesSearch.search(ProjectScope.getContentScope(project), project);
        for (PsiClass cls : query) {
            if (entries.size() >= MAX_ENTRIES) {
                break;
            }
            String fqn = cls.getQualifiedName();
            if (fqn == null || fqn.equals(selfFqn) || cls.isInterface() || cls.isEnum()
                    || cls.isAnnotationType() || cls.hasModifierProperty(PsiModifier.ABSTRACT)) {
                continue;
            }
            entries.add(new BeanClassEntry(fqn, cls.getName()));
        }
        entries.sort(Comparator.comparing(BeanClassEntry::displayName));
        return entries;
    }

    private static @NotNull ListCellRenderer<Object> displayRenderer() {
        return new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focus) {
                Component component = super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value instanceof BeanClassEntry entry) {
                    setText(entry.displayName() + "  (" + entry.qualifiedName() + ")");
                }
                return component;
            }
        };
    }

    private void generateFor(@NotNull Project project, @NotNull PsiClass configClass, @NotNull BeanClassEntry entry) {
        com.intellij.openapi.application.ReadAction.compute(() -> {
            PsiClass target = JavaPsiFacade.getInstance(project).findClass(
                    entry.qualifiedName(), GlobalSearchScope.projectScope(project));
            if (target == null) {
                return null;
            }
            WriteCommandAction.runWriteCommandAction(project, "Generate @Bean Method", null, () -> {
                PsiMethod method = insertBeanMethod(configClass, target);
                if (method != null) {
                    DaemonCodeAnalyzer.getInstance(project).restart(configClass.getContainingFile());
                }
            });
            return null;
        });
    }

    /** Appends a {@code @Bean} factory method for {@code beanClass} to {@code configClass}. */
    public static @Nullable PsiMethod insertBeanMethod(@NotNull PsiClass configClass, @NotNull PsiClass beanClass) {
        String fqn = beanClass.getQualifiedName();
        if (fqn == null) {
            return null;
        }
        PsiElementFactory factory = JavaPsiFacade.getInstance(configClass.getProject()).getElementFactory();
        String body = isDefaultConstructible(beanClass)
                ? "return new " + fqn + "();"
                : "return null; // TODO construct " + beanClass.getName();
        PsiMethod method = factory.createMethodFromText("""
                @org.springframework.context.annotation.Bean
                public %s %s() {
                    %s
                }""".formatted(fqn, methodNameFor(configClass, beanClass), body), configClass);
        PsiMethod added = (PsiMethod) configClass.addBefore(method, configClass.getRBrace());
        JavaCodeStyleManager.getInstance(configClass.getProject()).shortenClassReferences(added);
        com.intellij.psi.codeStyle.CodeStyleManager.getInstance(configClass.getProject()).reformat(added);
        return added;
    }

    private static boolean isDefaultConstructible(@NotNull PsiClass cls) {
        PsiMethod[] constructors = cls.getConstructors();
        if (constructors.length == 0) {
            return true;
        }
        for (PsiMethod constructor : constructors) {
            if (constructor.getParameterList().getParameters().length == 0) {
                return true;
            }
        }
        return false;
    }

    /** Decapitalized simple name, suffixed with 2, 3, … when a same-named method already exists. */
    private static @NotNull String methodNameFor(@NotNull PsiClass configClass, @NotNull PsiClass beanClass) {
        String name = beanClass.getName();
        if (name == null) {
            name = "bean";
        }
        String base = Character.toLowerCase(name.charAt(0)) + name.substring(1);
        String candidate = base;
        int suffix = 2;
        while (methodExists(configClass, candidate)) {
            candidate = base + suffix++;
        }
        return candidate;
    }

    private static boolean methodExists(@NotNull PsiClass cls, @NotNull String name) {
        for (PsiMethod method : cls.getMethods()) {
            if (name.equals(method.getName())) {
                return true;
            }
        }
        return false;
    }
}
