package dev.phucngu.intellaspring.initializr;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.ide.wizard.GeneratorNewProjectWizard;
import com.intellij.ide.wizard.NewProjectWizardBaseStep;
import com.intellij.ide.wizard.NewProjectWizardChainStep;
import com.intellij.ide.wizard.NewProjectWizardStep;
import com.intellij.ide.wizard.RootNewProjectWizardStep;
import dev.phucngu.intellaspring.ui.IntellaSpringIcons;
import org.jetbrains.annotations.NotNull;

/**
 * The native New Project dialog entry (left rail, under "Generators"): a "Spring Boot"
 * generator whose right pane is the platform's own Name/Location rows followed by the
 * Initializr settings — the same UX as IDEA Ultimate's gated Spring wizard, working on
 * the free tier.
 */
public final class SpringBootInitializrWizard implements GeneratorNewProjectWizard {

    @Override
    public @NotNull String getId() {
        return "intellaspring.boot.initializr";
    }

    @Override
    public @NotNull String getName() {
        return "Spring Boot";
    }

    @Override
    public @NotNull javax.swing.Icon getIcon() {
        return IntellaSpringIcons.BEAN;
    }

    @Override
    public @NotNull NewProjectWizardStep createStep(@NotNull WizardContext context) {
        NewProjectWizardChainStep<RootNewProjectWizardStep> chain =
                new NewProjectWizardChainStep<>(new RootNewProjectWizardStep(context));
        NewProjectWizardChainStep<NewProjectWizardBaseStep> withBase =
                chain.nextStep(root -> new NewProjectWizardBaseStep(root));
        return withBase.nextStep(base -> new SpringBootInitializrStep(base));
    }
}
