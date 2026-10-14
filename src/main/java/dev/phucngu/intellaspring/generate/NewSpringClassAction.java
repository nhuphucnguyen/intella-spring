package dev.phucngu.intellaspring.generate;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * Project view ▸ New ▸ "Spring @Configuration Class" / "Spring @Component Class": asks for
 * a name and creates the class in the selected source directory, stereotype first,
 * package computed from the directory.
 */
public abstract class NewSpringClassAction extends AnAction {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");

    private final String stereotypeFqn;

    protected NewSpringClassAction(@NotNull String stereotypeFqn) {
        super();
        this.stereotypeFqn = stereotypeFqn;
        getTemplatePresentation().setIcon(AllIcons.Nodes.Class);
    }

    public static final class Configuration extends NewSpringClassAction {
        public Configuration() {
            super("org.springframework.context.annotation.Configuration");
        }
    }

    public static final class Component extends NewSpringClassAction {
        public Component() {
            super("org.springframework.stereotype.Component");
        }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        PsiDirectory directory = event.getData(CommonDataKeys.PSI_ELEMENT) instanceof PsiDirectory dir ? dir : null;
        boolean applicable = project != null && directory != null
                && JavaDirectoryService.getInstance().getPackageInSources(directory) != null;
        event.getPresentation().setEnabledAndVisible(applicable);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            return;
        }
        PsiDirectory directory = event.getData(CommonDataKeys.PSI_ELEMENT) instanceof PsiDirectory dir ? dir : null;
        if (directory == null) {
            return;
        }
        String stereotype = stereotypeFqn.substring(stereotypeFqn.lastIndexOf('.') + 1);
        String name = Messages.showInputDialog(project, "Class name:", "New Spring @" + stereotype + " Class",
                Messages.getQuestionIcon(), "", null);
        if (name == null || name.isBlank()) {
            return;
        }
        if (!IDENTIFIER.matcher(name).matches()) {
            Messages.showErrorDialog(project, "'" + name + "' is not a valid Java class name.", "Invalid Name");
            return;
        }
        WriteAction.run(() -> {
            PsiFile created = createSpringClass(directory, name, stereotypeFqn);
            if (created != null) {
                new com.intellij.openapi.fileEditor.OpenFileDescriptor(project, created.getVirtualFile()).navigate(true);
            }
        });
    }

    /** Creates <code>name.java</code> in {@code directory} with the stereotype on the class. */
    public static @Nullable PsiFile createSpringClass(@NotNull PsiDirectory directory, @NotNull String name,
                                                      @NotNull String stereotypeFqn) {
        if (directory.findFile(name + ".java") != null) {
            return null;
        }
        PsiPackage pkg = JavaDirectoryService.getInstance().getPackage(directory);
        String packageLine = pkg == null || pkg.getQualifiedName().isEmpty() ? "" : "package " + pkg.getQualifiedName() + ";\n\n";
        String text = packageLine + "@" + stereotypeFqn + "\npublic class " + name + " {\n}\n";
        PsiFile file = PsiFileFactory.getInstance(directory.getProject())
                .createFileFromText(name + ".java",
                        FileTypeManager.getInstance().getFileTypeByExtension("java"), text);
        PsiFile added = (PsiFile) directory.add(file);
        JavaCodeStyleManager.getInstance(directory.getProject()).shortenClassReferences(added);
        return added;
    }
}
