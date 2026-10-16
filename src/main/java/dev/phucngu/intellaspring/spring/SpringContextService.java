package dev.phucngu.intellaspring.spring;

import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtilCore;
import dev.phucngu.intellaspring.spring.model.JavaStructureTracker;
import dev.phucngu.intellaspring.spring.model.SpringBeanDef;
import dev.phucngu.intellaspring.spring.model.SpringBeanScanner;
import dev.phucngu.intellaspring.spring.model.SpringBeanScanner.Stereotypes;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entry point for everything that consumes the Spring model: answers "is this a Spring
 * module" and "what beans does it have".
 *
 * v1 context rule: the context of module M is the beans declared in M's own sources, plus
 * those of its (transitive, export-aware) dependency modules, plus library beans — each bean
 * once. Every layer is a {@link CachedValue}: per-module declared beans, one project-wide
 * library layer, and the composed per-module context. They depend on the Java and XML
 * structure trackers (edits inside method bodies do not move them) and on project roots, so
 * unrelated edits — a markdown file, a method body — never trigger a rescan.
 */
public final class SpringContextService implements Disposable {
    private static final Key<CachedValue<Declared>> DECLARED = Key.create("intellaspring.declaredBeans");
    private static final Key<CachedValue<Context>> CONTEXT = Key.create("intellaspring.moduleContext");
    private static final Key<CachedValue<List<SpringBeanDef>>> ALL = Key.create("intellaspring.allBeans");
    private static final Key<CachedValue<Stereotypes>> LIBRARY_STEREOTYPES = Key.create("intellaspring.libraryStereotypes");
    private static final Key<CachedValue<Stereotypes>> PROJECT_STEREOTYPES = Key.create("intellaspring.projectStereotypes");
    private static final Key<CachedValue<List<SpringBeanDef>>> LIBRARY_BEANS = Key.create("intellaspring.libraryBeans");

    private static final Context EMPTY = new Context(false, List.of());

    private final Project project;
    private final JavaStructureTracker javaStructure;
    /** Last successfully computed contexts, served while indexes are unavailable. */
    private final Map<String, Context> lastKnown = new ConcurrentHashMap<>();

    private record Declared(boolean spring, List<SpringBeanDef> beans) {
    }

    private record Context(boolean springModule, List<SpringBeanDef> beans) {
    }

    /** A bean's identity across overlapping scans: its source file and offset. */
    private record BeanKey(Object file, int offset) {
        static BeanKey of(SpringBeanDef bean) {
            PsiElement element = bean.sourceElement();
            VirtualFile file = PsiUtilCore.getVirtualFile(element);
            return new BeanKey(file != null ? file : element, element.getTextOffset());
        }
    }

    public SpringContextService(@NotNull Project project) {
        this.project = project;
        this.javaStructure = new JavaStructureTracker(project, this);
    }

    public static @NotNull SpringContextService getInstance(@NotNull Project project) {
        return project.getService(SpringContextService.class);
    }

    public boolean isSpringModule(@NotNull Module module) {
        return context(module).springModule();
    }

    public @NotNull List<SpringBeanDef> beans(@NotNull Module module) {
        return context(module).beans();
    }

    /** Every bean in every Spring module of the project, each once — the pool references complete against. */
    public @NotNull List<SpringBeanDef> allBeans() {
        if (isUnavailable()) {
            return dedupe(lastKnown.values().stream().flatMap(c -> c.beans().stream()).toList());
        }
        return ReadAction.compute(() -> CachedValuesManager.getManager(project).getCachedValue(project, ALL,
                () -> {
                    List<SpringBeanDef> all = new ArrayList<>();
                    for (Module module : ModuleManager.getInstance(project).getModules()) {
                        all.addAll(context(module).beans());
                    }
                    return CachedValueProvider.Result.create(dedupe(all), dependencies());
                }, false));
    }

    private @NotNull Context context(@NotNull Module module) {
        if (module.isDisposed()) {
            return EMPTY;
        }
        if (isUnavailable()) {
            return lastKnown.getOrDefault(module.getName(), EMPTY);
        }
        return ReadAction.compute(() -> CachedValuesManager.getManager(project).getCachedValue(module, CONTEXT,
                () -> {
                    Context fresh = compose(module);
                    lastKnown.put(module.getName(), fresh);
                    return CachedValueProvider.Result.create(fresh, dependencies());
                }, false));
    }

    private boolean isUnavailable() {
        return project.isDisposed() || DumbService.isDumb(project);
    }

    private Object[] dependencies() {
        return new Object[]{
                javaStructure,
                PsiModificationTracker.getInstance(project).forLanguage(XMLLanguage.INSTANCE),
                ProjectRootModificationTracker.getInstance(project),
                // A computation that raced a dumb-mode start falls back to last-known; this makes it retry.
                DumbService.getInstance(project).getModificationTracker()
        };
    }

    private @NotNull Context compose(@NotNull Module module) {
        try {
            Declared own = declared(module);
            if (!own.spring()) {
                return EMPTY;
            }
            Set<Module> modules = new LinkedHashSet<>();
            modules.add(module);
            OrderEnumerator.orderEntries(module).recursively().forEachModule(m -> {
                modules.add(m);
                return true;
            });
            List<SpringBeanDef> beans = new ArrayList<>(own.beans());
            for (Module dependency : modules) {
                if (dependency != module && !dependency.isDisposed()) {
                    beans.addAll(declared(dependency).beans());
                }
            }
            beans.addAll(libraryBeans());
            return new Context(true, List.copyOf(dedupe(beans)));
        } catch (IndexNotReadyException e) {
            return lastKnown.getOrDefault(module.getName(), EMPTY);
        }
    }

    private @NotNull Declared declared(@NotNull Module module) {
        return CachedValuesManager.getManager(project).getCachedValue(module, DECLARED, () -> {
            Declared fresh = SpringBeanScanner.hasSpringOnClasspath(module)
                    ? new Declared(true, SpringBeanScanner.declaredBeans(module, projectStereotypes()))
                    : new Declared(false, List.of());
            return CachedValueProvider.Result.create(fresh, dependencies());
        }, false);
    }

    /** Library stereotypes and beans depend on the classpath only, so source edits never redo them. */
    private @NotNull Stereotypes libraryStereotypes() {
        return CachedValuesManager.getManager(project).getCachedValue(project, LIBRARY_STEREOTYPES,
                () -> CachedValueProvider.Result.create(SpringBeanScanner.libraryStereotypes(project),
                        ProjectRootModificationTracker.getInstance(project)), false);
    }

    /** Computed once per rebuild and shared by every module's scan. */
    private @NotNull Stereotypes projectStereotypes() {
        return CachedValuesManager.getManager(project).getCachedValue(project, PROJECT_STEREOTYPES,
                () -> CachedValueProvider.Result.create(
                        SpringBeanScanner.projectStereotypes(project, libraryStereotypes()),
                        javaStructure,
                        ProjectRootModificationTracker.getInstance(project)), false);
    }

    private @NotNull List<SpringBeanDef> libraryBeans() {
        return CachedValuesManager.getManager(project).getCachedValue(project, LIBRARY_BEANS,
                () -> CachedValueProvider.Result.create(
                        SpringBeanScanner.libraryBeans(project, libraryStereotypes()),
                        ProjectRootModificationTracker.getInstance(project)), false);
    }

    private static @NotNull List<SpringBeanDef> dedupe(@NotNull List<SpringBeanDef> beans) {
        Map<BeanKey, SpringBeanDef> unique = new LinkedHashMap<>();
        for (SpringBeanDef bean : beans) {
            unique.putIfAbsent(BeanKey.of(bean), bean);
        }
        return List.copyOf(unique.values());
    }

    @Override
    public void dispose() {
    }

    /** Roots moved: forget the dumb-mode fallback; the cached values drop themselves via trackers. */
    void invalidate() {
        lastKnown.clear();
    }
}
