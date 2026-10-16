package dev.phucngu.intellaspring.spring.model;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.psi.impl.PsiTreeChangeEventImpl;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Counts Java PSI changes that can alter the bean model: anything in a Java file outside a
 * code block (so typing in a method body, a lambda or an initializer block is free), plus
 * Java files being added, removed or moved. The platform's own language trackers also move
 * on code-block edits, which is exactly the rescan this exists to avoid.
 */
public final class JavaStructureTracker extends PsiTreeChangeAdapter implements ModificationTracker {
    private final AtomicLong count = new AtomicLong();

    public JavaStructureTracker(@NotNull Project project, @NotNull Disposable parent) {
        PsiManager.getInstance(project).addPsiTreeChangeListener(this, parent);
    }

    @Override
    public long getModificationCount() {
        return count.get();
    }

    @Override
    public void childAdded(@NotNull PsiTreeChangeEvent event) {
        changed(event);
    }

    @Override
    public void childRemoved(@NotNull PsiTreeChangeEvent event) {
        changed(event);
    }

    @Override
    public void childReplaced(@NotNull PsiTreeChangeEvent event) {
        changed(event);
    }

    @Override
    public void childMoved(@NotNull PsiTreeChangeEvent event) {
        changed(event);
    }

    @Override
    public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
        // The platform follows every precise child event with a generic "something changed under
        // this file" one; it carries no information and would defeat the code-block filter.
        if (event instanceof PsiTreeChangeEventImpl impl && impl.isGenericChange()) {
            return;
        }
        changed(event);
    }

    @Override
    public void propertyChanged(@NotNull PsiTreeChangeEvent event) {
        changed(event);
    }

    private void changed(@NotNull PsiTreeChangeEvent event) {
        PsiFile file = event.getFile();
        if (file == null) {
            // Directory-level event: only Java files (or whole directories) coming and going matter.
            PsiElement child = event.getChild();
            if (child == null || child instanceof PsiJavaFile || !(child instanceof PsiFile)) {
                count.incrementAndGet();
            }
            return;
        }
        if (!(file instanceof PsiJavaFile)) {
            return;
        }
        PsiElement parent = event.getParent();
        if (parent != null && PsiTreeUtil.getParentOfType(parent, PsiCodeBlock.class, false) != null) {
            return;
        }
        count.incrementAndGet();
    }
}
