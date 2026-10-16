package dev.phucngu.intellaspring.config;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Collects configuration keys for the project: spring-configuration-metadata.json from every
 * library jar on the module classpaths (the Boot starters) and, per module, from resources, the
 * hand-written additional-…json and the compiler output (where spring-boot-configuration-processor
 * writes the project's own @ConfigurationProperties keys).
 * <p>
 * Library metadata is parsed once per jar entry and reused across modules and snapshots (keyed by
 * the file and its modification stamp). The merged snapshot is reused until the roots change
 * ({@link #invalidate()}), a module-local metadata file appears, disappears or is edited — never on
 * ordinary PSI edits.
 */
public final class SpringConfigMetadataService {
    private static final String METADATA = "spring-configuration-metadata.json";
    private static final String ADDITIONAL = "additional-spring-configuration-metadata.json";
    private static final AtomicInteger PARSE_COUNT = new AtomicInteger();

    private final Project project;
    private final Map<VirtualFile, LibraryEntry> libraries = new ConcurrentHashMap<>();
    private volatile Snapshot snapshot;
    /** Structure stamp at which the snapshot's module-local file list was last confirmed. */
    private volatile long verifiedStructure;
    private final AtomicInteger generation = new AtomicInteger();

    private record LibraryEntry(long stamp, List<ConfigKey> keys) {
    }

    private record Watched(VirtualFile file, long stamp) {
    }

    private record Snapshot(int generation, List<Watched> watched,
                            List<ConfigKey> keys, ConfigKeyIndex index) {
    }

    public SpringConfigMetadataService(@NotNull Project project) {
        this.project = project;
    }

    public static @NotNull SpringConfigMetadataService getInstance(@NotNull Project project) {
        return project.getService(SpringConfigMetadataService.class);
    }

    /** How many metadata JSON files have been parsed so far (diagnostic; tests assert reuse). */
    public static int parseCount() {
        return PARSE_COUNT.get();
    }

    public @NotNull List<ConfigKey> keys() {
        return snapshot().keys();
    }

    public @NotNull ConfigKeyIndex index() {
        return snapshot().index();
    }

    public void invalidate() {
        generation.incrementAndGet();
        snapshot = null;
    }

    private @NotNull Snapshot snapshot() {
        Snapshot cached = snapshot;
        if (cached != null && isCurrent(cached)) {
            return cached;
        }
        Snapshot fresh = ReadAction.compute(this::collect);
        snapshot = fresh;
        return fresh;
    }

    /** Moves whenever files may have been created/deleted/moved or module roots changed. */
    private long structureStamp() {
        return VirtualFileManager.getInstance().getStructureModificationCount() * 31
                + ProjectRootModificationTracker.getInstance(project).getModificationCount();
    }

    private boolean isCurrent(@NotNull Snapshot snapshot) {
        if (snapshot.generation() != generation.get()) {
            return false;
        }
        List<Watched> watched = snapshot.watched();
        long structure = structureStamp();
        if (verifiedStructure != structure) {
            // the file layout may have changed: re-resolve the candidate paths (the expensive part)
            List<VirtualFile> local = moduleLocalFiles();
            if (local.size() != watched.size()) {
                return false;
            }
            for (int i = 0; i < local.size(); i++) {
                if (!local.get(i).equals(watched.get(i).file())) {
                    return false;
                }
            }
            verifiedStructure = structure;
        }
        for (Watched entry : watched) {
            if (!entry.file().isValid() || stamp(entry.file()) != entry.stamp()) {
                return false;
            }
        }
        return true;
    }

    private @NotNull Snapshot collect() {
        int generationAtStart = generation.get();
        verifiedStructure = structureStamp();
        Map<String, ConfigKey> merged = new LinkedHashMap<>();
        List<Watched> watched = new ArrayList<>();
        for (VirtualFile file : moduleLocalFiles()) {
            watched.add(new Watched(file, stamp(file)));
            addAll(merged, readLocal(file));
        }
        Set<VirtualFile> seen = new LinkedHashSet<>();
        for (Module module : ModuleManager.getInstance(project).getModules()) {
            if (module.isDisposed()) {
                continue;
            }
            for (OrderEntry entry : ModuleRootManager.getInstance(module).getOrderEntries()) {
                if (!(entry instanceof LibraryOrderEntry library)) {
                    continue;
                }
                for (VirtualFile root : library.getFiles(OrderRootType.CLASSES)) {
                    VirtualFile file = root.findFileByRelativePath("META-INF/" + METADATA);
                    if (file != null && seen.add(file)) {
                        addAll(merged, readLibrary(file));
                    }
                }
            }
        }
        libraries.keySet().retainAll(seen);
        List<ConfigKey> keys = List.copyOf(merged.values());
        return new Snapshot(generationAtStart, watched, keys, new ConfigKeyIndex(keys));
    }

    /** Metadata files of the project's own modules, deduplicated, in a stable order. */
    private @NotNull List<VirtualFile> moduleLocalFiles() {
        Set<VirtualFile> files = new LinkedHashSet<>();
        for (Module module : ModuleManager.getInstance(project).getModules()) {
            if (module.isDisposed()) {
                continue;
            }
            for (VirtualFile moduleRoot : moduleLocalRoots(module, ModuleRootManager.getInstance(module))) {
                for (String name : new String[]{METADATA, ADDITIONAL}) {
                    VirtualFile file = moduleRoot.findFileByRelativePath("META-INF/" + name);
                    if (file == null) {
                        file = moduleRoot.findFileByRelativePath("src/main/resources/META-INF/" + name);
                    }
                    if (file != null) {
                        files.add(file);
                    }
                }
            }
        }
        return List.copyOf(files);
    }

    /** Content roots, source roots (resources) and compiler output directories of the module. */
    private static @NotNull Set<VirtualFile> moduleLocalRoots(@NotNull Module module,
                                                              @NotNull ModuleRootManager roots) {
        Set<VirtualFile> result = new LinkedHashSet<>();
        for (VirtualFile contentRoot : roots.getContentRoots()) {
            result.add(contentRoot);
        }
        for (VirtualFile sourceRoot : roots.getSourceRoots(true)) {
            result.add(sourceRoot);
        }
        CompilerModuleExtension compiler = CompilerModuleExtension.getInstance(module);
        if (compiler != null) {
            for (VirtualFile output : compiler.getOutputRoots(true)) {
                result.add(output);
            }
        }
        return result;
    }

    private static void addAll(@NotNull Map<String, ConfigKey> merged, @NotNull List<ConfigKey> keys) {
        for (ConfigKey key : keys) {
            merged.putIfAbsent(key.name(), key);
        }
    }

    private @NotNull List<ConfigKey> readLibrary(@NotNull VirtualFile file) {
        long stamp = file.getModificationStamp() ^ file.getTimeStamp() ^ file.getLength();
        LibraryEntry cached = libraries.get(file);
        if (cached != null && cached.stamp() == stamp) {
            return cached.keys();
        }
        List<ConfigKey> parsed = parse(readBytes(file));
        libraries.put(file, new LibraryEntry(stamp, parsed));
        return parsed;
    }

    private static @NotNull List<ConfigKey> readLocal(@NotNull VirtualFile file) {
        Document document = FileDocumentManager.getInstance().getCachedDocument(file);
        return parse(document != null ? document.getText() : readBytes(file));
    }

    /** Version of a local file's content: unsaved editor state wins over disk. */
    private static long stamp(@NotNull VirtualFile file) {
        FileDocumentManager manager = FileDocumentManager.getInstance();
        Document document = manager.getCachedDocument(file);
        return document != null && manager.isDocumentUnsaved(document)
                ? document.getModificationStamp() : file.getModificationStamp();
    }

    private static @Nullable String readBytes(@NotNull VirtualFile file) {
        try {
            return new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null; // unreadable metadata is not worth failing completion over
        }
    }

    private static @NotNull List<ConfigKey> parse(@Nullable String json) {
        if (json == null) {
            return List.of();
        }
        PARSE_COUNT.incrementAndGet();
        try {
            return SpringConfigMetadata.parse(json);
        } catch (Exception e) {
            return List.of(); // malformed JSON
        }
    }
}
