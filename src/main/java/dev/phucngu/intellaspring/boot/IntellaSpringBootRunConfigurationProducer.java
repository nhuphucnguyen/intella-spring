package dev.phucngu.intellaspring.boot;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Creates "Intella Spring Boot" run configurations from context: click a main class or its
 * {@code @SpringBootApplication} annotation (gutter Run icon, Run with… menus) and the
 * configuration appears pre-filled — the same entry flow as the gated plugin.
 */
public class IntellaSpringBootRunConfigurationProducer
        extends RunConfigurationProducer<IntellaSpringBootRunConfiguration> {

    public IntellaSpringBootRunConfigurationProducer() {
        super(ConfigurationTypeUtil.findConfigurationType(IntellaSpringBootConfigurationType.class));
    }

    @Override
    protected boolean setupConfigurationFromContext(@NotNull IntellaSpringBootRunConfiguration configuration,
                                                    @NotNull ConfigurationContext context,
                                                    @NotNull Ref<PsiElement> sourceElement) {
        PsiClass bootClass = bootClassAt(context.getPsiLocation());
        if (bootClass == null || bootClass.getQualifiedName() == null) {
            return false;
        }
        configuration.setMainClassName(bootClass.getQualifiedName());
        if (context.getModule() != null) {
            configuration.setModule(context.getModule());
        }
        String name = configuration.suggestedName();
        if (name != null) {
            configuration.setName(name);
        }
        sourceElement.set(bootClass);
        return true;
    }

    @Override
    public boolean isConfigurationFromContext(@NotNull IntellaSpringBootRunConfiguration configuration,
                                              @NotNull ConfigurationContext context) {
        PsiClass bootClass = bootClassAt(context.getPsiLocation());
        return bootClass != null && bootClass.getQualifiedName() != null
                && bootClass.getQualifiedName().equals(configuration.getMainClassName());
    }

    /** The nearest enclosing class that is a Spring Boot application with a main method. */
    static PsiClass bootClassAt(PsiElement location) {
        for (PsiClass candidate = PsiTreeUtil.getParentOfType(location, PsiClass.class, false);
             candidate != null;
             candidate = PsiTreeUtil.getParentOfType(candidate, PsiClass.class)) {
            if (SpringBootApplicationDetector.hasMainMethod(candidate)
                    && SpringBootApplicationDetector.isSpringBootApplication(candidate,
                            GlobalSearchScope.allScope(candidate.getProject()))) {
                return candidate;
            }
        }
        return null;
    }
}
