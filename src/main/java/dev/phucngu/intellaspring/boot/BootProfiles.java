package dev.phucngu.intellaspring.boot;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Properties;
import java.util.TreeSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spring profile names discoverable in the project: {@code application-<profile>.properties}
 * and {@code application-<profile>.yml/.yaml} file names, plus the comma list in
 * {@code spring.profiles.active} inside any {@code application.properties} on the classpath.
 * Feeds the run editor's profile picker.
 */
public final class BootProfiles {

    private static final Pattern PROFILE_FILE = Pattern.compile("application-(.+)\\.(properties|ya?ml)");
    private static final String ACTIVE_KEY = "spring.profiles.active";

    private BootProfiles() {
    }

    public static @NotNull List<String> discover(@NotNull Project project) {
        Set<String> profiles = new TreeSet<>();
        for (String fileName : new String[]{"application.properties", "application.yaml", "application.yml"}) {
            for (VirtualFile file : FilenameIndex.getVirtualFilesByName(project, fileName,
                    GlobalSearchScope.projectScope(project))) {
                collectActiveProperty(file, profiles);
            }
        }
        for (VirtualFile file : FilenameIndex.getAllFilesByExt(project, "properties",
                GlobalSearchScope.projectScope(project))) {
            collectFromName(file.getName(), profiles);
        }
        for (String ext : new String[]{"yml", "yaml"}) {
            for (VirtualFile file : FilenameIndex.getAllFilesByExt(project, ext,
                    GlobalSearchScope.projectScope(project))) {
                collectFromName(file.getName(), profiles);
            }
        }
        return List.copyOf(profiles);
    }

    private static void collectFromName(@NotNull String fileName, @NotNull Set<String> into) {
        Matcher match = PROFILE_FILE.matcher(fileName);
        if (match.matches()) {
            addAll(match.group(1), into);
        }
    }

    /** Plain {@code application.properties}: parse and split {@code spring.profiles.active}. */
    private static void collectActiveProperty(@NotNull VirtualFile file, @NotNull Set<String> into) {
        if (!"application.properties".equals(file.getName())) {
            return;
        }
        Properties properties = new Properties();
        try (var stream = file.getInputStream()) {
            properties.load(stream);
        } catch (Exception ignored) {
            return;
        }
        addAll(properties.getProperty(ACTIVE_KEY), into);
    }

    private static void addAll(String commaList, @NotNull Set<String> into) {
        if (commaList == null || commaList.isBlank()) {
            return;
        }
        for (String part : commaList.split(",")) {
            String profile = part.trim();
            if (!profile.isEmpty()) {
                into.add(profile);
            }
        }
    }
}
