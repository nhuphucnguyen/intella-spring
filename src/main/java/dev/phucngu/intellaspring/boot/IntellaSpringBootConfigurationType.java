package dev.phucngu.intellaspring.boot;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import dev.phucngu.intellaspring.ui.IntellaSpringIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

/** The "Intella Spring Boot" run configuration entry — IDEA's own Spring Boot type, ungated. */
public class IntellaSpringBootConfigurationType implements ConfigurationType {

    public static final String ID = "IntellaSpringBootApplication";

    @Override
    public @NotNull @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "Intella Spring Boot";
    }

    @Override
    public @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String getConfigurationTypeDescription() {
        return "Spring Boot application";
    }

    @Override
    public @NotNull Icon getIcon() {
        return IntellaSpringIcons.BEAN;
    }

    @Override
    public @NotNull String getId() {
        return ID;
    }

    @Override
    public ConfigurationFactory @NotNull [] getConfigurationFactories() {
        return new ConfigurationFactory[]{new Factory(this)};
    }

    private static final class Factory extends ConfigurationFactory {
        private Factory(@NotNull IntellaSpringBootConfigurationType type) {
            super(type);
        }

        @Override
        public @NotNull String getId() {
            // required override on 2026.2: the default delegates to the localizable name,
            // and factory ids must never depend on localization
            return IntellaSpringBootConfigurationType.ID;
        }

        @Override
        public @NotNull RunConfiguration createTemplateConfiguration(@NotNull Project project) {
            return new IntellaSpringBootRunConfiguration(project, this, "App");
        }
    }
}
