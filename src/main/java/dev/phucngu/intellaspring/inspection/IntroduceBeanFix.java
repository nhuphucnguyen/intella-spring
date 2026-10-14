package dev.phucngu.intellaspring.inspection;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import dev.phucngu.intellaspring.spring.SpringAnnotations;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Quick-fix on "No qualifying bean of type 'X' found" when X is a project class without a
 * stereotype: registers it as a bean by adding {@code @Component} (or the name-appropriate
 * {@code @Service}/{@code @Repository}) to the class declaration.
 */
final class IntroduceBeanFix implements LocalQuickFix {

    private final SmartPsiElementPointer<PsiClass> beanClass;
    private final String stereotypeFqn;

    IntroduceBeanFix(@NotNull PsiClass beanClass) {
        this.beanClass = SmartPointerManager.getInstance(beanClass.getProject())
                .createSmartPsiElementPointer(beanClass);
        this.stereotypeFqn = stereotypeFor(beanClass);
    }

    /** A concrete, writable (project-source) class that carries no stereotype yet. */
    static boolean eligible(@Nullable PsiClass cls) {
        if (cls == null || !cls.isWritable() || cls.isInterface() || cls.isAnnotationType()
                || cls.isEnum() || cls.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return false;
        }
        for (String stereotype : SpringAnnotations.BASE_STEREOTYPES) {
            if (cls.getAnnotation(stereotype) != null) {
                return false;
            }
        }
        return true;
    }

    /** IDEA-style naming heuristic: *Service → @Service, *Repository/*Repo → @Repository, else @Component. */
    static @NotNull String stereotypeFor(@NotNull PsiClass cls) {
        String name = cls.getName() == null ? "" : cls.getName();
        if (name.endsWith("Service")) {
            return SpringAnnotations.SERVICE;
        }
        if (name.endsWith("Repository") || name.endsWith("Repo")) {
            return SpringAnnotations.REPOSITORY;
        }
        return SpringAnnotations.COMPONENT;
    }

    @Override
    public @NotNull @IntentionFamilyName String getFamilyName() {
        String fqn = qualifiedNameOf(beanClass.getElement());
        String stereotype = stereotypeFqn.substring(stereotypeFqn.lastIndexOf('.') + 1);
        return fqn == null ? "Register class as a Spring bean (@" + stereotype + ")"
                : "Register '" + fqn + "' as a Spring bean (@" + stereotype + ")";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiClass target = beanClass.getElement();
        if (!eligible(target)) {
            return;
        }
        PsiModifierList modifiers = target.getModifierList();
        if (modifiers == null) {
            return;
        }
        PsiAnnotation annotation = JavaPsiFacade.getElementFactory(project)
                .createAnnotationFromText("@" + stereotypeFqn, target);
        WriteCommandAction.runWriteCommandAction(project, getFamilyName(), null, () -> {
            modifiers.addBefore(annotation, modifiers.getFirstChild());
            JavaCodeStyleManager.getInstance(project).shortenClassReferences(modifiers);
        });
    }

    private static @Nullable String qualifiedNameOf(@Nullable PsiClass cls) {
        if (cls == null) {
            return null;
        }
        String fqn = cls.getQualifiedName();
        if (fqn != null) {
            return fqn;
        }
        // local/anonymous classes have no FQN; the fix is not offered for them anyway
        return cls.getName();
    }
}
