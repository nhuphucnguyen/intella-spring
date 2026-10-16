package dev.phucngu.intellaspring.config;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLFile;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.util.HashSet;
import java.util.Set;

/**
 * Keys the project itself defines in application*.properties / application*.yml / .yaml (all
 * profiles). Per-file key sets are cached on the PSI file, so an edit re-reads only that file.
 */
public final class ProjectConfigKeys {

    private ProjectConfigKeys() {
    }

    /** True when any application config file defines the key (relaxed form). */
    public static boolean isDefined(@NotNull Project project, @NotNull String key) {
        String canonical = ConfigKeyIndex.canonical(key);
        PsiManager psiManager = PsiManager.getInstance(project);
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        for (String extension : new String[]{"properties", "yml", "yaml"}) {
            for (var file : FilenameIndex.getAllFilesByExt(project, extension, scope)) {
                if (!SpringPropertiesCompletionContributor.isApplicationFile(file.getName())) {
                    continue;
                }
                PsiFile psi = psiManager.findFile(file);
                if (psi != null && keysOf(psi).contains(canonical)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static @NotNull Set<String> keysOf(@NotNull PsiFile file) {
        return CachedValuesManager.getCachedValue(file, () ->
                CachedValueProvider.Result.create(collect(file), file));
    }

    private static @NotNull Set<String> collect(@NotNull PsiFile file) {
        Set<String> keys = new HashSet<>();
        if (file instanceof PropertiesFile properties) {
            for (var property : properties.getProperties()) {
                String key = property.getKey();
                if (key != null) {
                    keys.add(ConfigKeyIndex.canonical(key));
                }
            }
        } else if (file instanceof YAMLFile) {
            for (YAMLKeyValue entry : PsiTreeUtil.findChildrenOfType(file, YAMLKeyValue.class)) {
                StringBuilder path = new StringBuilder(entry.getKeyText());
                for (YAMLKeyValue parent = PsiTreeUtil.getParentOfType(entry, YAMLKeyValue.class);
                     parent != null; parent = PsiTreeUtil.getParentOfType(parent, YAMLKeyValue.class)) {
                    path.insert(0, parent.getKeyText() + ".");
                }
                keys.add(ConfigKeyIndex.canonical(path.toString()));
            }
        }
        return keys;
    }
}
