package dev.phucngu.intellaspring.boot;

import com.intellij.execution.ui.CommonJavaFragments;
import com.intellij.execution.ui.CommonParameterFragments;
import com.intellij.execution.ui.DefaultJreSelector;
import com.intellij.execution.ui.RunConfigurationFragmentedEditor;
import com.intellij.execution.ui.SettingsEditorFragment;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import javax.swing.JTextField;
import java.util.ArrayList;
import java.util.List;

/**
 * The run-configuration editor, composed the way IDEA's Spring Boot plugin composes
 * {@code SpringBootApplicationRunConfigurationFragmentedEditor}: the standard Java fragments
 * (module/classpath, JRE, VM options, program arguments, working directory, environment)
 * plus the Boot panel — active profiles, hide banner, launch optimization — surfaced through
 * the same "Modify options" fragment grammar.
 */
public final class IntellaSpringBootSettingsEditor
        extends RunConfigurationFragmentedEditor<IntellaSpringBootRunConfiguration> {

    private final @NotNull IntellaSpringBootRunConfiguration configuration;

    public IntellaSpringBootSettingsEditor(@NotNull IntellaSpringBootRunConfiguration configuration) {
        super(configuration);
        this.configuration = configuration;
    }

    @Override
    protected List<SettingsEditorFragment<IntellaSpringBootRunConfiguration, ?>> createRunFragments() {
        Project project = configuration.getProject();
        List<SettingsEditorFragment<IntellaSpringBootRunConfiguration, ?>> fragments = new ArrayList<>();
        fragments.add(CommonJavaFragments.moduleClasspath());
        fragments.add(SettingsEditorFragment.createWrapper("isb.mainClass", "Main Class",
                "Main class", new MainClassEditor(), c -> true));
        fragments.add(CommonJavaFragments.createJrePath(DefaultJreSelector.projectSdk(project)));
        fragments.add(CommonJavaFragments.vmOptions(() -> true));
        CommonParameterFragments<IntellaSpringBootRunConfiguration> common =
                new CommonParameterFragments<>(project, this::currentModule);
        fragments.add(common.programArguments());
        fragments.add(CommonParameterFragments.createWorkingDirectory(project, this::currentModule));
        fragments.add(CommonParameterFragments.createEnvParameters());
        fragments.add(SettingsEditorFragment.createWrapper("isb.activeProfiles", "Active Profiles",
                "Active Spring profiles (comma-separated)", new ActiveProfilesEditor(), c -> true));
        fragments.add(SettingsEditorFragment.createTag("isb.hideBanner", "Hide Banner",
                "Do not show the Spring Boot startup banner",
                IntellaSpringBootRunConfiguration::isHideBanner,
                IntellaSpringBootRunConfiguration::setHideBanner));
        fragments.add(SettingsEditorFragment.createTag("isb.launchOptimization", "Enable Launch Optimization",
                "Start faster with -XX:TieredStopAtLevel=1",
                IntellaSpringBootRunConfiguration::isLaunchOptimization,
                IntellaSpringBootRunConfiguration::setLaunchOptimization));
        return fragments;
    }

    /** The configuration's module; null until one is chosen in the classpath combo. */
    private @org.jetbrains.annotations.Nullable Module currentModule() {
        return configuration.getConfigurationModule().getModule();
    }

    private static final class MainClassEditor extends SettingsEditor<IntellaSpringBootRunConfiguration> {
        private final JTextField field = new JTextField();

        @Override
        protected void resetEditorFrom(@NotNull IntellaSpringBootRunConfiguration configuration) {
            field.setText(configuration.getMainClassName() != null ? configuration.getMainClassName() : "");
        }

        @Override
        protected void applyEditorTo(@NotNull IntellaSpringBootRunConfiguration configuration)
                throws ConfigurationException {
            configuration.setMainClassName(field.getText().trim());
        }

        @Override
        protected @NotNull JComponent createEditor() {
            return LabeledComponent.create(field, "Main class");
        }
    }

    private static final class ActiveProfilesEditor extends SettingsEditor<IntellaSpringBootRunConfiguration> {
        private final JTextField field = new JTextField();

        @Override
        protected void resetEditorFrom(@NotNull IntellaSpringBootRunConfiguration configuration) {
            field.setText(configuration.getActiveProfiles());
        }

        @Override
        protected void applyEditorTo(@NotNull IntellaSpringBootRunConfiguration configuration)
                throws ConfigurationException {
            configuration.setActiveProfiles(field.getText().trim());
        }

        @Override
        protected @NotNull JComponent createEditor() {
            return LabeledComponent.create(field, "Active profiles");
        }
    }
}
