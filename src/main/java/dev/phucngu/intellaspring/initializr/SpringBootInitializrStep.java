package dev.phucngu.intellaspring.initializr;

import com.intellij.ide.wizard.AbstractNewProjectWizardStep;
import com.intellij.ide.wizard.NewProjectWizardBaseStep;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.CheckBoxList;
import com.intellij.ui.dsl.builder.Panel;
import com.intellij.ui.dsl.builder.RowLayout;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import kotlin.Unit;

/**
 * The Initializr settings shown below the platform's Name/Location rows in the native
 * New Project dialog: type, Boot version, group/artifact/package/description, Java,
 * packaging and a filterable dependency checklist from start.spring.io (built-in
 * fallback offline). Create downloads the starter zip into the new project directory.
 */
final class SpringBootInitializrStep extends AbstractNewProjectWizardStep {

    private final @NotNull NewProjectWizardBaseStep base;

    private final JComboBox<String> typeCombo = new JComboBox<>();
    private final JComboBox<String> bootCombo = new JComboBox<>();
    private final JComboBox<String> javaCombo = new JComboBox<>();
    private final JComboBox<String> packagingCombo = new JComboBox<>();
    private final JTextField groupField = new JTextField("com.example");
    private final JTextField artifactField = new JTextField("demo");
    private final JTextField packageField = new JTextField("com.example.demo");
    private final JTextField descriptionField = new JTextField();
    private final JTextField filterField = new JTextField();
    private final CheckBoxList<String> dependenciesList = new CheckBoxList<>();
    private final JLabel metadataStatus = new JLabel("Loading metadata from start.spring.io…");

    private InitializrModels.Metadata metadata = InitializrModels.fallback();
    private final Map<String, String> typeNameToId = new HashMap<>();

    SpringBootInitializrStep(@NotNull NewProjectWizardBaseStep base) {
        super(base);
        this.base = base;
        fillFromMetadata();
        loadMetadataInBackground();
        filterField.getDocument().addDocumentListener(new FilterListener());
    }

    @Override
    public void setupUI(@NotNull Panel panel) {
        panel.row("Type:", row -> { row.cell(typeCombo); return Unit.INSTANCE; });
        panel.row("Boot version:", row -> { row.cell(bootCombo); return Unit.INSTANCE; });
        panel.row("Group:", row -> { row.cell(groupField); return Unit.INSTANCE; });
        panel.row("Artifact:", row -> { row.cell(artifactField); return Unit.INSTANCE; });
        panel.row("Package name:", row -> { row.cell(packageField); return Unit.INSTANCE; });
        panel.row("Description:", row -> { row.cell(descriptionField); return Unit.INSTANCE; });
        panel.row("Java:", row -> { row.cell(javaCombo); return Unit.INSTANCE; });
        panel.row("Packaging:", row -> { row.cell(packagingCombo); return Unit.INSTANCE; });
        panel.row("Filter dependencies:", row -> {
            row.layout(RowLayout.PARENT_GRID);
            row.cell(filterField);
            return Unit.INSTANCE;
        });
        panel.row("", row -> {
            row.cell(new com.intellij.ui.components.JBScrollPane(dependenciesList));
            return Unit.INSTANCE;
        });
        panel.row("", row -> { row.cell(metadataStatus); return Unit.INSTANCE; });
    }

    @Override
    public void setupProject(@NotNull Project project) {
        String name = base.getName();
        String artifact = sanitize(name);
        Path target = Path.of(base.getPath(), artifact);
        Logger.getInstance(SpringBootInitializrStep.class)
                .info("Initializr setupProject: target=" + target);
        InitializrClient.GenerateRequest request;
        byte[] zip;
        try {
            ProgressManager.checkCanceled();
            request = buildRequest(artifact);
            zip = new InitializrClient().generate(request);
        } catch (IOException | InterruptedException e) {
            warn(project, "start.spring.io could not be reached: " + e.getMessage()
                    + ". An empty project was created at " + target + ".");
            return;
        }
        try {
            Files.createDirectories(target);
            Unzips.unzip(zip, target);
        } catch (IOException e) {
            warn(project, "The starter zip could not be written to " + target + ": " + e.getMessage());
            return;
        } catch (RuntimeException e) {
            // surface framework-level failures instead of letting commit swallow them silently
            Logger.getInstance(SpringBootInitializrStep.class).error("Initializr generation failed", e);
            warn(project, "Generation failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return;
        }
        Logger.getInstance(SpringBootInitializrStep.class)
                .info("Initializr generated project files into " + target);
        // no VFS refresh here: setupProject can run inside the commit write action,
        // where refresh is forbidden — the platform refreshes the project root on open
    }

    private static void warn(@NotNull Project project, @NotNull String message) {
        Logger.getInstance(SpringBootInitializrStep.class).warn(message);
        NotificationGroupManager.getInstance().getNotificationGroup("IntellaSpring")
                .createNotification("Spring Boot project not generated", message, NotificationType.WARNING)
                .notify(project);
    }

    /** Pure mapping from the form to the Initializr request — unit-tested. */
    @NotNull InitializrClient.GenerateRequest buildRequest(@NotNull String artifact) {
        String typeName = (String) typeCombo.getSelectedItem();
        String typeId = typeNameToId.getOrDefault(typeName, typeName);
        String packaging = String.valueOf(packagingCombo.getSelectedItem()).toLowerCase(Locale.ROOT);
        return new InitializrClient.GenerateRequest(
                typeId,
                (String) bootCombo.getSelectedItem(),
                groupField.getText().trim(),
                artifact,
                artifact,
                descriptionField.getText().trim(),
                packageField.getText().trim(),
                packaging,
                (String) javaCombo.getSelectedItem(),
                dependenciesList.getCheckedItems());
    }

    static @NotNull String sanitize(@NotNull String raw) {
        return raw.replaceAll("[^A-Za-z0-9._-]", "-");
    }

    static @NotNull String defaultPackage(@NotNull String group, @NotNull String name) {
        String artifact = sanitize(name).toLowerCase(Locale.ROOT);
        return (group.isBlank() ? artifact : group + "." + artifact).toLowerCase(Locale.ROOT);
    }

    private void loadMetadataInBackground() {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            InitializrModels.Metadata loaded;
            String status;
            try {
                loaded = new InitializrClient().fetchMetadata();
                status = "Dependencies and versions from start.spring.io";
            } catch (Exception e) {
                loaded = null;
                status = "start.spring.io unreachable — built-in list (" + e.getMessage() + ")";
            }
            InitializrModels.Metadata result = loaded;
            String finalStatus = status;
            SwingUtilities.invokeLater(() -> {
                if (result != null) {
                    metadata = result;
                    fillFromMetadata();
                }
                metadataStatus.setText(finalStatus);
            });
        });
    }

    private void fillFromMetadata() {
        typeNameToId.clear();
        typeCombo.removeAllItems();
        for (InitializrModels.BuildType type : metadata.buildTypes()) {
            typeNameToId.put(type.name(), type.id());
            typeCombo.addItem(type.name());
        }
        fillCombo(bootCombo, metadata.bootVersions());
        fillCombo(javaCombo, metadata.javaVersions());
        packagingCombo.removeAllItems();
        packagingCombo.addItem("Jar");
        packagingCombo.addItem("War");
        applyDependencyFilter();
    }

    private static void fillCombo(@NotNull JComboBox<String> combo, @NotNull List<String> values) {
        Object selected = combo.getSelectedItem();
        combo.removeAllItems();
        values.forEach(combo::addItem);
        if (selected != null && values.contains(selected)) {
            combo.setSelectedItem(selected);
        }
    }

    /** Rebuilds the checklist keeping checked state; the filter matches group labels and ids. */
    private void applyDependencyFilter() {
        Set<String> checked = new HashSet<>(dependenciesList.getCheckedItems());
        dependenciesList.clear();
        String query = filterField.getText().trim().toLowerCase(Locale.ROOT);
        for (InitializrModels.DependencyGroup group : metadata.groups()) {
            for (InitializrModels.Dependency dep : group.dependencies()) {
                String label = dep.name() + " (" + group.name() + ")";
                if (query.isEmpty() || label.toLowerCase(Locale.ROOT).contains(query)
                        || dep.id().toLowerCase(Locale.ROOT).contains(query)) {
                    dependenciesList.addItem(dep.id(), label, checked.contains(dep.id()));
                }
            }
        }
    }

    private final class FilterListener implements javax.swing.event.DocumentListener {
        @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { applyDependencyFilter(); }
        @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { applyDependencyFilter(); }
        @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { applyDependencyFilter(); }
    }
}
