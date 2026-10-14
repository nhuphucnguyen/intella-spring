package dev.phucngu.intellaspring.config;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Collects configuration keys for the project: spring-configuration-metadata.json from module
 * resources and from every library jar on the module classpaths (the Boot starters). Cached
 * against the PSI modification counter; the root listener clears it when classpaths change —
 * the same invalidation strategy the bean model uses.
 */
public final class SpringConfigMetadataService {
    private static final String METADATA = "spring-configuration-metadata.json";
    private static final String ADDITIONAL = "additional-spring-configuration-metadata.json";

    private final Project project;
    private final Map<String, Snapshot> cache = new ConcurrentHashMap<>();

    private record Snapshot(long modificationCount, List<ConfigKey> keys) {
    }

    public SpringConfigMetadataService(@NotNull Project project) {
        this.project = project;
    }

    public static @NotNull SpringConfigMetadataService getInstance(@NotNull Project project) {
        return project.getService(SpringConfigMetadataService.class);
    }

    public @NotNull List<ConfigKey> keys() {
        long modificationCount = PsiModificationTracker.getInstance(project).getModificationCount();
        Snapshot cached = cache.get("project");
        if (cached != null && cached.modificationCount() == modificationCount) {
            return cached.keys();
        }
        Snapshot fresh = new Snapshot(modificationCount,
                com.intellij.openapi.application.ReadAction.compute(this::collect));
        cache.put("project", fresh);
        return fresh.keys();
    }

    public void invalidate() {
        cache.clear();
    }

    private @NotNull List<ConfigKey> collect() {
        List<ConfigKey> keys = new ArrayList<>();
        for (Module module : ModuleManager.getInstance(project).getModules()) {
            if (module.isDisposed()) {
                continue;
            }
            ModuleRootManager roots = ModuleRootManager.getInstance(module);
            for (VirtualFile contentRoot : roots.getContentRoots()) {
                readMetadata(contentRoot.findFileByRelativePath("META-INF/" + METADATA), keys);
                readMetadata(contentRoot.findFileByRelativePath("src/main/resources/META-INF/" + METADATA), keys);
                readMetadata(contentRoot.findFileByRelativePath("src/main/resources/META-INF/" + ADDITIONAL), keys);
            }
            for (OrderEntry entry : roots.getOrderEntries()) {
                if (!(entry instanceof LibraryOrderEntry library)) {
                    continue;
                }
                for (VirtualFile root : library.getFiles(OrderRootType.CLASSES)) {
                    readMetadata(root.findFileByRelativePath("META-INF/" + METADATA), keys);
                }
            }
        }
        return keys;
    }

    private static void readMetadata(@Nullable VirtualFile file, @NotNull List<ConfigKey> keys) {
        if (file == null || !file.isValid()) {
            return;
        }
        try {
            String json = new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
            keys.addAll(SpringConfigMetadata.parse(json));
        } catch (Exception ignored) {
            // unreadable metadata is not worth failing completion over
        }
    }
}
