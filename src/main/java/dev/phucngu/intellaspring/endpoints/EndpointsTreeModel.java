package dev.phucngu.intellaspring.endpoints;

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import dev.phucngu.intellaspring.mvc.RequestMappingModel;
import dev.phucngu.intellaspring.spring.SpringContextService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure data shape of the endpoints tree: module → controller → endpoint. The panel only
 * renders these rows; everything PSI-touching happens here, under the caller's read action,
 * so painting never resolves anything lazily (same rule the chooser popups follow).
 */
public final class EndpointsTreeModel {

    /** One mapped handler. {@code verbsLabel} is precomputed ("GET", "GET+POST", "" if any). */
    public record EndpointRow(@NotNull String verbsLabel, @NotNull String path,
                              @NotNull List<String> httpMethods, @NotNull PsiMethod handler) {
    }

    public record ControllerRow(@NotNull String controllerName, @Nullable PsiClass controller,
                                @NotNull List<EndpointRow> endpoints) {
    }

    public record ModuleRow(@NotNull String moduleName, @NotNull List<ControllerRow> controllers) {

        /** Flat view for "group by controller" off: module → every endpoint, path-sorted. */
        public @NotNull List<EndpointRow> flatEndpoints() {
            return controllers.stream().flatMap(c -> c.endpoints().stream())
                    .sorted(Comparator.comparing(EndpointRow::path).thenComparing(EndpointRow::verbsLabel))
                    .toList();
        }
    }

    private EndpointsTreeModel() {
    }

    /** Every Spring module's mappings, grouped per controller; must run inside a read action. */
    public static @NotNull List<ModuleRow> build(@NotNull Project project) {
        ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
        List<ModuleRow> rows = new ArrayList<>();
        for (Module module : ModuleManager.getInstance(project).getModules()) {
            if (module.isDisposed()
                    || !SpringContextService.getInstance(project).isSpringModule(module)) {
                continue;
            }
            Map<String, List<EndpointRow>> byController = new LinkedHashMap<>();
            Map<String, PsiClass> controllerPsi = new LinkedHashMap<>();
            for (RequestMappingModel.Mapping mapping : RequestMappingModel.collect(module)) {
                PsiMethod handler = mapping.handler();
                if (!handler.isValid() || !isProjectSource(handler, index)) {
                    continue;
                }
                PsiClass containing = handler.getContainingClass();
                String controller = containing != null && containing.getName() != null
                        ? containing.getName() : "(no controller)";
                byController.computeIfAbsent(controller, k -> new ArrayList<>())
                        .add(new EndpointRow(verbLabel(mapping.httpMethods()), mapping.path(),
                                mapping.httpMethods(), handler));
                controllerPsi.putIfAbsent(controller, containing);
            }
            if (byController.isEmpty()) {
                continue;
            }
            List<ControllerRow> controllers = new ArrayList<>();
            for (Map.Entry<String, List<EndpointRow>> entry : byController.entrySet()) {
                List<EndpointRow> endpoints = entry.getValue().stream()
                        .sorted(Comparator.comparing(EndpointRow::path)
                                .thenComparing(EndpointRow::verbsLabel))
                        .toList();
                controllers.add(new ControllerRow(entry.getKey(), controllerPsi.get(entry.getKey()), endpoints));
            }
            controllers.sort(Comparator.comparing(ControllerRow::controllerName, String.CASE_INSENSITIVE_ORDER));
            rows.add(new ModuleRow(module.getName(), controllers));
        }
        rows.sort(Comparator.comparing(ModuleRow::moduleName, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    /** Handlers in project sources only — library mappings (from decompiled classes) stay out. */
    private static boolean isProjectSource(@NotNull PsiMethod handler, @NotNull ProjectFileIndex index) {
        VirtualFile file = handler.getContainingFile() != null ? handler.getContainingFile().getVirtualFile() : null;
        return file != null && index.isInSourceContent(file);
    }

    /** "" (any verb) is shown as ANY by the renderer; the label here keeps it honest. */
    public static @NotNull String verbLabel(@NotNull List<String> httpMethods) {
        if (httpMethods.isEmpty()) {
            return "ANY";
        }
        return String.join("+", httpMethods);
    }

    /**
     * Copy-pasteable curl for an endpoint. The port follows the project's own
     * {@code server.port} when it declares one; single quotes in the path are escaped.
     */
    public static @NotNull String curlCommand(@NotNull EndpointRow endpoint, int port) {
        String url = "http://localhost:" + port + endpoint.path().replace("'", "'\\''");
        String verb = endpoint.httpMethods().isEmpty() ? "" : "-X " + endpoint.httpMethods().get(0) + " ";
        return "curl " + verb + "'" + url + "'";
    }

    /** The project's declared {@code server.port}, or 8080. Must run inside a read action. */
    public static int serverPort(@NotNull Project project) {
        for (VirtualFile file : FilenameIndex.getVirtualFilesByName(
                project, "application.properties", GlobalSearchScope.projectScope(project))) {
            if (!(PsiManager.getInstance(project).findFile(file) instanceof PropertiesFile properties)) {
                continue;
            }
            IProperty property = properties.findPropertyByKey("server.port");
            if (property != null && property.getValue() != null) {
                try {
                    return Integer.parseInt(property.getValue().trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return 8080;
    }
}
