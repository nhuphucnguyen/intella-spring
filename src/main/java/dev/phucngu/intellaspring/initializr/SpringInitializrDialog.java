package dev.phucngu.intellaspring.initializr;

import com.intellij.icons.AllIcons;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CheckBoxList;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import dev.phucngu.intellaspring.initializr.InitializrModels.BuildType;
import dev.phucngu.intellaspring.initializr.InitializrModels.Dependency;
import dev.phucngu.intellaspring.initializr.InitializrModels.DependencyGroup;
import dev.phucngu.intellaspring.initializr.InitializrModels.Metadata;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The Spring Initializr wizard: project coordinates, build type, Boot/Java versions and a
 * searchable starter list (live from start.spring.io, built-in fallback offline). Create
 * downloads the generated zip, extracts it next to the chosen location and offers to open
 * it as a project — the bundled Maven/Gradle importers pick it up from there.
 */
public class SpringInitializrDialog extends DialogWrapper {
    private final @Nullable Project project;

    private final JBTextField nameField = new JBTextField("demo");
    private final JBTextField locationField = new JBTextField();
    private final JBTextField groupField = new JBTextField("com.example");
    private final JBTextField artifactField = new JBTextField("demo");
    private final JBTextField packageField = new JBTextField("com.example.demo");
    private final JBTextField descriptionField = new JBTextField("Demo project for Spring Boot");
    private final JComboBox<BuildType> typeCombo = new JComboBox<>();
    private final JComboBox<String> bootCombo = new JComboBox<>();
    private final JComboBox<String> javaCombo = new JComboBox<>();
    private final JComboBox<String> packagingCombo = new JComboBox<>(new String[]{"Jar", "War"});
    private final JBTextField dependencyFilter = new JBTextField();
    private final CheckBoxList<Dependency> dependenciesList = new CheckBoxList<>();
    private final JBLabel metadataStatus = new JBLabel();

    private Metadata metadata = InitializrModels.fallback();
    private final List<Dependency> selected = new ArrayList<>();

    public SpringInitializrDialog(@Nullable Project project) {
        super(project);
        this.project = project;
        setTitle("New Spring Boot Project (Initializr)");
        String base = project != null && project.getBasePath() != null
                ? project.getBasePath() : System.getProperty("user.home");
        locationField.setText(base);
        setOKButtonText("Create");
        init();
        loadMetadata();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JButton browse = new JButton("…");
        browse.setToolTipText("Choose the parent directory");
        browse.addActionListener(e -> {
            VirtualFile current = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                    .findFileByIoFile(java.nio.file.Path.of(locationField.getText()).toFile());
            VirtualFile dir = FileChooser.chooseFile(
                    FileChooserDescriptorFactory.createSingleFolderDescriptor(), project, current);
            if (dir != null && dir.isWritable()) {
                locationField.setText(dir.getPath());
            }
        });
        JPanel locationRow = new JPanel(new java.awt.BorderLayout(4, 0));
        locationRow.add(locationField, java.awt.BorderLayout.CENTER);
        locationRow.add(browse, java.awt.BorderLayout.EAST);

        dependenciesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        dependenciesList.getEmptyText().setText("No matching dependencies");
        rebuildDependencyList("");
        dependencyFilter.getEmptyText().setText("Filter dependencies");
        dependencyFilter.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { rebuildDependencyList(dependencyFilter.getText()); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { rebuildDependencyList(dependencyFilter.getText()); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { rebuildDependencyList(dependencyFilter.getText()); }
        });

        metadataStatus.setForeground(com.intellij.util.ui.UIUtil.getContextHelpForeground());
        metadataStatus.setText("Loading metadata from start.spring.io…");

        JComponent panel = FormBuilder.createFormBuilder()
                .addLabeledComponent("Name:", nameField)
                .addLabeledComponent("Location:", locationRow)
                .addLabeledComponent("Group:", groupField)
                .addLabeledComponent("Artifact:", artifactField)
                .addLabeledComponent("Package name:", packageField)
                .addLabeledComponent("Description:", descriptionField)
                .addLabeledComponent("Type:", typeCombo)
                .addLabeledComponent("Boot version:", bootCombo)
                .addLabeledComponent("Packaging:", packagingCombo)
                .addLabeledComponent("Java version:", javaCombo)
                .addLabeledComponent("Filter dependencies:", dependencyFilter)
                .addLabeledComponent("Dependencies:", dependenciesList, true)
                .addComponent(metadataStatus)
                .getPanel();
        fillFromMetadata(metadata);
        return panel;
    }

    private void fillFromMetadata(@NotNull Metadata loaded) {
        this.metadata = loaded;
        typeCombo.removeAllItems();
        for (BuildType type : loaded.buildTypes()) {
            typeCombo.addItem(type);
        }
        bootCombo.removeAllItems();
        for (String version : loaded.bootVersions()) {
            bootCombo.addItem(version);
        }
        bootCombo.setSelectedItem(loaded.defaultBootVersion());
        javaCombo.removeAllItems();
        for (String version : loaded.javaVersions()) {
            javaCombo.addItem(version);
        }
        if (javaCombo.getItemCount() > 0 && javaCombo.getSelectedItem() == null) {
            javaCombo.setSelectedIndex(0);
        }
        rebuildDependencyList(dependencyFilter.getText());
    }

    private void rebuildDependencyList(@NotNull String filter) {
        dependenciesList.clear();
        String needle = filter.trim().toLowerCase();
        for (DependencyGroup group : metadata.groups()) {
            for (Dependency dependency : group.dependencies()) {
                String label = group.name() + " · " + dependency.name()
                        + (dependency.description().isEmpty() ? "" : " — " + dependency.description());
                boolean matches = needle.isEmpty()
                        || label.toLowerCase().contains(needle)
                        || dependency.id().toLowerCase().contains(needle);
                if (matches) {
                    dependenciesList.addItem(dependency, label, selected.contains(dependency));
                }
            }
        }
    }

    private record Loaded(@NotNull Metadata metadata, @NotNull String status) {
    }

    private void loadMetadata() {
        InitializrClient client = new InitializrClient();
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            Loaded result = loadSafely(client);
            ApplicationManager.getApplication().invokeLater(() -> {
                if (!isDisposed()) {
                    fillFromMetadata(result.metadata());
                    metadataStatus.setText(result.status());
                }
            });
        });
    }

    private static @NotNull Loaded loadSafely(@NotNull InitializrClient client) {
        try {
            return new Loaded(client.fetchMetadata(), "Dependencies and versions from start.spring.io");
        } catch (IOException | InterruptedException e) {
            return new Loaded(InitializrModels.fallback(),
                    "start.spring.io unreachable — built-in list (" + e.getMessage() + ")");
        }
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (nameField.getText().isBlank()) {
            return new ValidationInfo("A project name is required", nameField);
        }
        if (!Files.isDirectory(Path.of(locationField.getText()))) {
            return new ValidationInfo("Location must be an existing directory", locationField);
        }
        if (Files.exists(Path.of(locationField.getText()).resolve(sanitize(nameField.getText())))) {
            return new ValidationInfo("'" + sanitize(nameField.getText()) + "' already exists there", locationField);
        }
        return null;
    }

    private static @NotNull String sanitize(@NotNull String name) {
        return name.trim().replaceAll("[^A-Za-z0-9._-]", "-");
    }

    @Override
    protected void doOKAction() {
        selected.clear();
        selected.addAll(dependenciesList.getCheckedItems());
        String name = sanitize(nameField.getText());
        Path target = Path.of(locationField.getText()).resolve(name);
        InitializrClient.GenerateRequest request = new InitializrClient.GenerateRequest(
                typeCombo.getSelectedItem() instanceof BuildType type ? type.id() : "maven-project",
                String.valueOf(bootCombo.getSelectedItem()),
                groupField.getText().isBlank() ? "com.example" : groupField.getText().trim(),
                artifactField.getText().isBlank() ? name : artifactField.getText().trim(),
                name,
                descriptionField.getText(),
                packageField.getText().isBlank() ? "com.example." + name.toLowerCase() : packageField.getText().trim(),
                packagingCombo.getSelectedIndex() == 1 ? "war" : "jar",
                String.valueOf(javaCombo.getSelectedItem()),
                selected.stream().map(Dependency::id).toList());
        close(OK_EXIT_CODE);
        generateInBackground(request, target);
    }

    private void generateInBackground(@NotNull InitializrClient.GenerateRequest request, @NotNull Path target) {
        InitializrClient client = new InitializrClient();
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                byte[] zip = client.generate(request);
                Unzips.unzip(zip, target);
                ApplicationManager.getApplication().invokeLater(() -> reportSuccess(target));
            } catch (IOException | InterruptedException e) {
                ApplicationManager.getApplication().invokeLater(
                        () -> reportFailure(e.getMessage() == null ? e.toString() : e.getMessage()));
            }
        });
    }

    private void reportSuccess(@NotNull Path target) {
        Notification notification = new Notification("IntellaSpring", "Spring Boot project generated",
                target.toString(), NotificationType.INFORMATION);
        notification.addAction(new AnAction("Open Project") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                ProjectManagerEx.getInstanceEx().openProject(target,
                        com.intellij.ide.impl.OpenProjectTask.build());
            }
        });
        notification.notify(project);
    }

    private void reportFailure(@NotNull String message) {
        new Notification("IntellaSpring", "Spring Boot project generation failed",
                message, NotificationType.ERROR).notify(project);
    }
}
