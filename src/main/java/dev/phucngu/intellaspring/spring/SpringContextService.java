package dev.phucngu.intellaspring.spring;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.util.PsiModificationTracker;
import dev.phucngu.intellaspring.spring.model.SpringBeanDef;
import dev.phucngu.intellaspring.spring.model.SpringBeanScanner;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entry point for everything that consumes the Spring model: answers "is this a Spring
 * module" and "what beans does it have", caching per module until the PSI modification
 * counter moves (any Java or XML edit bumps it). Root/classpath changes are rarer and
 * handled by {@link SpringModelRootListener} clearing the cache.
 *
 * v1 context rule: a module with Spring on the classpath is one Spring context — the
 * single-fileset case of IDEA's facet model. Explicit multi-context UI is deferred.
 */
public final class SpringContextService {
    private final Project project;
    private final Map<String, Snapshot> cache = new ConcurrentHashMap<>();

    private record Snapshot(long modificationCount, boolean springModule, List<SpringBeanDef> beans) {
    }

    public SpringContextService(@NotNull Project project) {
        this.project = project;
    }

    public static @NotNull SpringContextService getInstance(@NotNull Project project) {
        return project.getService(SpringContextService.class);
    }

    public boolean isSpringModule(@NotNull Module module) {
        return snapshot(module).springModule();
    }

    public @NotNull List<SpringBeanDef> beans(@NotNull Module module) {
        return snapshot(module).beans();
    }

    /** Every bean in every Spring module of the project — the pool references complete against. */
    public @NotNull List<SpringBeanDef> allBeans() {
        List<SpringBeanDef> all = new ArrayList<>();
        for (Module module : com.intellij.openapi.module.ModuleManager.getInstance(project).getModules()) {
            all.addAll(beans(module));
        }
        return all;
    }

    private @NotNull Snapshot snapshot(@NotNull Module module) {
        if (module.isDisposed()) {
            return new Snapshot(-1, false, List.of());
        }
        long modificationCount = PsiModificationTracker.getInstance(project).getModificationCount();
        Snapshot cached = cache.get(module.getName());
        if (cached != null && cached.modificationCount() == modificationCount) {
            return cached;
        }
        Snapshot fresh = ReadAction.compute(() -> {
            boolean spring = SpringBeanScanner.hasSpringOnClasspath(module);
            return new Snapshot(modificationCount, spring, spring ? SpringBeanScanner.scan(module) : List.of());
        });
        cache.put(module.getName(), fresh);
        return fresh;
    }

    void invalidate() {
        cache.clear();
    }
}
