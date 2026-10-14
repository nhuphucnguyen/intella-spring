package dev.phucngu.intellaspring.mvc;

import com.intellij.openapi.module.Module;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.psi.search.GlobalSearchScope;
import dev.phucngu.intellaspring.spring.SpringAnnotations;
import dev.phucngu.intellaspring.spring.model.AnnotationValues;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The request-mapping landscape of a module: every handler method with its full path
 * (class-level {@code @RequestMapping} prefix + method path) and HTTP methods. Consumed by
 * the ambiguity inspection now, the endpoints tool window in S7.2.
 */
public final class RequestMappingModel {

    /** One handler: full path with a single leading slash, and the HTTP verbs it serves. */
    public record Mapping(@NotNull String path, @NotNull List<String> httpMethods,
                          @NotNull PsiMethod handler, @NotNull String annotationFqn) {
    }

    private RequestMappingModel() {
    }

    public static @NotNull List<Mapping> collect(@NotNull Module module) {
        GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false);
        JavaPsiFacade psi = JavaPsiFacade.getInstance(module.getProject());
        Map<PsiClass, String> classPrefixes = new LinkedHashMap<>();
        List<Mapping> mappings = new ArrayList<>();
        PsiClass requestMapping = psi.findClass(SpringAnnotations.REQUEST_MAPPING, scope);
        if (requestMapping != null) {
            for (PsiClass annotated : AnnotatedElementsSearch.searchPsiClasses(requestMapping, scope)) {
                PsiAnnotation annotation = annotated.getAnnotation(SpringAnnotations.REQUEST_MAPPING);
                List<String> paths = AnnotationValues.stringValues(annotation, "value");
                if (paths.isEmpty()) {
                    paths = AnnotationValues.stringValues(annotation, "path");
                }
                classPrefixes.put(annotated, paths.isEmpty() ? "" : paths.get(0));
            }
        }
        for (String annotationFqn : SpringAnnotations.MAPPING_ANNOTATIONS.keySet()) {
            PsiClass annotation = psi.findClass(annotationFqn, scope);
            if (annotation == null) {
                continue;
            }
            for (PsiMethod handler : AnnotatedElementsSearch.searchPsiMethods(annotation, scope)) {
                if (!handler.isValid()) {
                    continue;
                }
                mappings.add(mappingOf(handler, annotationFqn, classPrefixes));
            }
        }
        return mappings;
    }

    private static @NotNull Mapping mappingOf(@NotNull PsiMethod handler, @NotNull String annotationFqn,
                                              @NotNull Map<PsiClass, String> classPrefixes) {
        PsiAnnotation annotation = handler.getAnnotation(annotationFqn);
        List<String> paths = AnnotationValues.stringValues(annotation, "value");
        if (paths.isEmpty()) {
            paths = AnnotationValues.stringValues(annotation, "path");
        }
        String methodPath = paths.isEmpty() ? "" : paths.get(0);
        String prefix = "";
        PsiClass containing = handler.getContainingClass();
        for (Map.Entry<PsiClass, String> entry : classPrefixes.entrySet()) {
            if (entry.getKey().equals(containing)
                    || isSubclass(containing, entry.getKey())) {
                prefix = entry.getValue();
                break;
            }
        }
        List<String> httpMethods = new ArrayList<>(SpringAnnotations.MAPPING_ANNOTATIONS.get(annotationFqn));
        if (httpMethods.isEmpty()) {
            // plain @RequestMapping: verbs come from its method attribute (RequestMethod.GET, …)
            for (String value : AnnotationValues.stringValues(annotation, "method")) {
                httpMethods.add(value.substring(value.lastIndexOf('.') + 1));
            }
        }
        return new Mapping(joinPaths(prefix, methodPath), httpMethods, handler, annotationFqn);
    }

    private static boolean isSubclass(@Nullable PsiClass candidate, @NotNull PsiClass superclass) {
        while (candidate != null) {
            if (candidate.equals(superclass)) {
                return true;
            }
            candidate = candidate.getSuperClass();
        }
        return false;
    }

    /** "/api" + "/users" → "/api/users"; "" + "/users" → "/users"; no leading slash → add one. */
    public static @NotNull String joinPaths(@NotNull String prefix, @NotNull String methodPath) {
        String path = (prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix)
                + (methodPath.startsWith("/") ? methodPath : "/" + methodPath);
        return path.isBlank() ? "/" : path;
    }
}
