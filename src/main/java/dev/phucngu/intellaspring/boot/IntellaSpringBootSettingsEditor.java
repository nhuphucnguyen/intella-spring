package dev.phucngu.intellaspring.boot;

import com.intellij.execution.ui.CommonJavaFragments;
import com.intellij.execution.ui.CommonParameterFragments;
import com.intellij.execution.ui.DefaultJreSelector;
import com.intellij.execution.ui.RunConfigurationFragmentedEditor;
import com.intellij.execution.ui.SettingsEditorFragment;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.ui.CheckBoxList;
import com.intellij.ui.ScrollPaneFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

    private final class MainClassEditor extends SettingsEditor<IntellaSpringBootRunConfiguration> {
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
            javax.swing.JPanel row = new javax.swing.JPanel(new java.awt.BorderLayout(4, 0));
            JButton browse = new JButton("…");
            browse.setToolTipText("Choose a Spring Boot application class");
            browse.addActionListener(e -> chooseMainClass());
            row.add(field, java.awt.BorderLayout.CENTER);
            row.add(browse, java.awt.BorderLayout.EAST);
            return LabeledComponent.create(row, "Main class");
        }

        /** Speed-searchable chooser over every @SpringBootApplication class with a main method. */
        private void chooseMainClass() {
            record Entry(@NotNull String qualifiedName, @NotNull com.intellij.pom.Navigatable target) {
            }
            java.util.List<Entry> entries = com.intellij.openapi.application.ReadAction.compute(() ->
                    dev.phucngu.intellaspring.boot.BootApplicationClasses
                            .findBootApplicationClasses(configuration.getProject()).stream()
                            .map(c -> new Entry(String.valueOf(c.getQualifiedName()),
                                    (com.intellij.pom.Navigatable) c))
                            .toList());
            if (entries.isEmpty()) {
                new com.intellij.notification.Notification("IntellaSpring", "No Spring Boot applications",
                        "No @SpringBootApplication class with a main method was found in this project.",
                        com.intellij.notification.NotificationType.INFORMATION).notify(configuration.getProject());
                return;
            }
            com.intellij.openapi.ui.popup.JBPopupFactory.getInstance()
                    .createPopupChooserBuilder(entries)
                    .setTitle("Spring Boot Application Class")
                    .setRenderer(new javax.swing.DefaultListCellRenderer() {
                        @Override
                        public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list,
                                                                                Object value, int index,
                                                                                boolean selected, boolean focus) {
                            java.awt.Component c = super.getListCellRendererComponent(list, value, index, selected, focus);
                            if (value instanceof Entry entry && c instanceof javax.swing.JLabel label) {
                                label.setText(entry.qualifiedName());
                            }
                            return c;
                        }
                    })
                    .setNamerForFiltering(Entry::qualifiedName)
                    .setFilterAlwaysVisible(true)
                    .setItemChosenCallback(entry -> {
                        if (entry != null) {
                            field.setText(entry.qualifiedName());
                        }
                    })
                    .createPopup()
                    .showInFocusCenter();
        }
    }

    private final class ActiveProfilesEditor extends SettingsEditor<IntellaSpringBootRunConfiguration> {
        private final JTextField field = new JTextField();
        private final JButton browse = new JButton("…");

        ActiveProfilesEditor() {
            browse.addActionListener(event -> chooseProfiles());
        }

        /** Checkboxes over profiles discovered from application-<profile>.* files. */
        private void chooseProfiles() {
            Project project = configuration.getProject();
            List<String> discovered = ReadAction.compute(() -> BootProfiles.discover(project));
            if (discovered.isEmpty()) {
                NotificationGroupManager.getInstance()
                        .getNotificationGroup("IntellaSpring")
                        .createNotification("No Spring profiles found",
                                "Add application-<profile>.properties files to the project first.",
                                NotificationType.INFORMATION)
                        .notify(project);
                return;
            }
            Set<String> current = Arrays.stream(field.getText().split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toCollection(LinkedHashSet::new));
            CheckBoxList<String> list = new CheckBoxList<>();
            for (String profile : discovered) {
                list.addItem(profile, profile, current.contains(profile));
            }
            DialogWrapper dialog = new DialogWrapper(project) {
                {
                    init();
                    setTitle("Active Spring Profiles");
                }

                @Override
                protected @NotNull JComponent createCenterPanel() {
                    return ScrollPaneFactory.createScrollPane(list, true);
                }
            };
            if (dialog.showAndGet()) {
                field.setText(String.join(", ", list.getCheckedItems()));
            }
        }

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
            JPanel row = new JPanel(new BorderLayout(4, 0));
            row.add(field, BorderLayout.CENTER);
            row.add(browse, BorderLayout.EAST);
            return LabeledComponent.create(row, "Active profiles");
        }
    }
}
